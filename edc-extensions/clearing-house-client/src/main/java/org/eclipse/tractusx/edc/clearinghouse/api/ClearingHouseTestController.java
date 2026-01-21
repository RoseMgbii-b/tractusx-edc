/********************************************************************************
 * Copyright (c) 2025 Contributors
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 ********************************************************************************/

package org.eclipse.tractusx.edc.clearinghouse.api;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.tractusx.edc.clearinghouse.client.GaiaXRegistryComplianceClient;
import org.eclipse.tractusx.edc.clearinghouse.client.dto.*;

import java.util.List;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.nio.file.Files;

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;

/**
 * API endpoint for Clearing House Client
 * 
 * Provides REST endpoints for Clearing House functionality:
 * - Log events
 * - Verify receipts
 * - Validate participants
 * - Fetch trust anchor
 */
@Path("/v3/clearinghouse")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ClearingHouseTestController {

    private final GaiaXRegistryComplianceClient gxClient;
    private final Monitor monitor;
    private final String certificateChainPath;

    /**
     * Controller for testing Gaia-X Registry and Compliance APIs.
     * 
     * Uses ONLY the public Registry and Compliance APIs:
     * - Registry: https://registry.lab.gaia-x.eu/development
     * - Compliance: https://compliance.lab.gaia-x.eu/development
     * 
     * The old CHN client (ClearingHouseClient) with paths like /api/v1/events/log, 
     * /api/v1/participants/validate are NOT used - those paths are not valid.
     */
    public ClearingHouseTestController(GaiaXRegistryComplianceClient gxClient, Monitor monitor, String certificateChainPath) {
        this.gxClient = gxClient;
        this.monitor = monitor;
        this.certificateChainPath = certificateChainPath != null && !certificateChainPath.isBlank() 
                ? certificateChainPath 
                : "x509CertificateChain.pem"; // Default to working directory
        if (monitor != null) {
            if (gxClient == null) {
                monitor.warning("[ClearingHouseTestController] Controller initialized without Gaia-X client. " +
                        "Configure edc.gaiax.registry.base.url and edc.gaiax.compliance.base.url to enable operations.");
            } else {
                monitor.info("[ClearingHouseTestController] Controller initialized with Gaia-X Registry/Compliance client");
            }
        }
    }

    /**
     * Serve the participant's X.509 certificate chain at a well-known location.
     * <p>
     * Note: The file {@code x509CertificateChain.pem} must contain a valid PEM-encoded
     * certificate chain (leaf + intermediates, no private key) that chains to a
     * trust anchor known by the Gaia-X Registry.
     * <p>
     * Full external URL (assuming default management context):
     *   https://<host>/api/management/v3/clearinghouse/.well-known/x509CertificateChain.pem
     * <p>
     * For use with did:web:test1.ecdc.es, map
     *   https://test1.ecdc.es/.well-known/x509CertificateChain.pem
     * to this endpoint via a reverse proxy.
     */
    @GET
    @Path("/.well-known/x509CertificateChain.pem")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getCertificateChain() {
        try {
            // Resolve the certificate chain path - try multiple locations
            java.nio.file.Path certPath = resolveCertificateChainPath(certificateChainPath);
            
            if (!Files.exists(certPath)) {
                String errorMsg = "Certificate chain file not found at: " + certPath.toAbsolutePath() + 
                        ". Please configure edc.clearinghouse.certificate.chain.path with an absolute path.";
                if (monitor != null) {
                    monitor.severe("[ClearingHouseTestController] " + errorMsg);
                }
                return Response.status(INTERNAL_SERVER_ERROR)
                        .entity(errorMsg)
                        .build();
            }
            
            String pem = Files.readString(certPath);
            if (monitor != null) {
                monitor.debug("[ClearingHouseTestController] Serving certificate chain from: " + certPath.toAbsolutePath());
            }
            return Response.ok(pem, MediaType.TEXT_PLAIN).build();
        } catch (Exception e) {
            String errorMsg = "Error loading certificate chain from " + certificateChainPath + ": " + e.getMessage();
            if (monitor != null) {
                monitor.severe("[ClearingHouseTestController] " + errorMsg, e);
            }
            return Response.status(INTERNAL_SERVER_ERROR)
                    .entity(errorMsg + ". Configure edc.clearinghouse.certificate.chain.path with an absolute path.")
                    .build();
        }
    }
    
    /**
     * Resolves the certificate chain path, trying multiple locations:
     * 1. Path as-is (works for absolute paths)
     * 2. Relative to current working directory
     * 3. Relative to project root (detected by walking up to find .git, settings.gradle.kts, or build.gradle.kts)
     * 4. Relative to user home directory
     */
    private java.nio.file.Path resolveCertificateChainPath(String path) {
        java.nio.file.Path certPath = java.nio.file.Path.of(path);
        
        // If absolute path or exists as-is, return it
        if (certPath.isAbsolute() || Files.exists(certPath)) {
            return certPath.toAbsolutePath();
        }
        
        // Try relative to current working directory
        java.nio.file.Path workingDirPath = java.nio.file.Path.of(System.getProperty("user.dir"), path);
        if (Files.exists(workingDirPath)) {
            return workingDirPath.toAbsolutePath();
        }
        
        // Try relative to project root (walk up directory tree to find project markers)
        java.nio.file.Path projectRoot = findProjectRoot();
        if (projectRoot != null) {
            java.nio.file.Path projectRootPath = projectRoot.resolve(path);
            if (Files.exists(projectRootPath)) {
                return projectRootPath.toAbsolutePath();
            }
        }
        
        // Try relative to user home
        java.nio.file.Path homePath = java.nio.file.Path.of(System.getProperty("user.home"), path);
        if (Files.exists(homePath)) {
            return homePath.toAbsolutePath();
        }
        
        // Return the original path (will fail with a clear error message)
        return certPath.toAbsolutePath();
    }
    
    /**
     * Finds the project root by walking up the directory tree from the current working directory
     * looking for project markers (.git, settings.gradle.kts, build.gradle.kts, pom.xml, etc.)
     */
    private java.nio.file.Path findProjectRoot() {
        java.nio.file.Path currentDir = java.nio.file.Path.of(System.getProperty("user.dir"));
        java.nio.file.Path root = currentDir.getRoot(); // Get filesystem root (C:\ on Windows, / on Unix)
        
        // Walk up the directory tree
        while (currentDir != null && !currentDir.equals(root)) {
            // Check for common project root markers
            if (Files.exists(currentDir.resolve(".git")) ||
                Files.exists(currentDir.resolve("settings.gradle.kts")) ||
                Files.exists(currentDir.resolve("settings.gradle")) ||
                Files.exists(currentDir.resolve("build.gradle.kts")) ||
                Files.exists(currentDir.resolve("build.gradle")) ||
                Files.exists(currentDir.resolve("pom.xml")) ||
                Files.exists(currentDir.resolve(".project"))) {
                return currentDir;
            }
            
            // Move up one directory
            java.nio.file.Path parent = currentDir.getParent();
            if (parent == null || parent.equals(currentDir)) {
                break; // Reached filesystem root or can't go higher
            }
            currentDir = parent;
        }
        
        return null; // Project root not found
    }



    /**
     * Validate a participant using Registry trust anchors
     * 
     * GET /api/management/v3/clearinghouse/participants/validate?bpn=BPNL123&did=did:web:example.com
     * 
     * Uses Registry API to fetch trust anchors and validate participant.
     * Query params:
     * - bpn: Business Partner Number (optional)
     * - did: Decentralized Identifier (optional)
     */
    @GET
    @Path("/participants/validate")
    public Response validateParticipant(
            @QueryParam("bpn") String bpn,
            @QueryParam("did") String did) {
        
        Response configCheck = checkGxClientConfigured();
        if (configCheck != null) return configCheck;

        try {
            monitor.info("Validating participant using Registry: BPN=" + bpn + ", DID=" + did);
            
            // Fetch trust anchors from Registry to validate participant
            CompletableFuture<Result<List<TrustAnchorResponse.TrustAnchor>>> future = 
                    gxClient.getTrustAnchors("latest").toCompletableFuture();
            Result<List<TrustAnchorResponse.TrustAnchor>> result = future.get(30, TimeUnit.SECONDS);
            
            if (result.succeeded()) {
                var trustAnchors = result.getContent();
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Participant validation using Registry trust anchors");
                response.put("bpn", bpn);
                response.put("did", did);
                response.put("trustAnchorsCount", trustAnchors.size());
                response.put("trustAnchors", trustAnchors);
                monitor.info("Participant validation completed: " + trustAnchors.size() + " trust anchors retrieved");
                return Response.ok()
                        .entity(response)
                        .build();
            } else {
                monitor.warning("Failed to validate participant: " + result.getFailureDetail());
                return Response.status(BAD_REQUEST)
                        .entity(createErrorResponse("Failed to validate participant: " + result.getFailureDetail()))
                        .build();
            }
        } catch (Exception e) {
            monitor.severe("Error validating participant: " + e.getMessage(), e);
            return Response.status(INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Error validating participant: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Fetch trust anchors from Registry
     * 
     * GET /api/management/v3/clearinghouse/trust-anchor?version=latest
     * 
     * Uses Registry API to fetch trust anchors (replaces old CHN trust anchor endpoint)
     */
    @GET
    @Path("/trust-anchor")
    public Response fetchTrustAnchor(@QueryParam("version") String version) {
        Response configCheck = checkGxClientConfigured();
        if (configCheck != null) return configCheck;

        try {
            monitor.info("Fetching trust anchors from Registry");
            
            CompletableFuture<Result<List<TrustAnchorResponse.TrustAnchor>>> future = 
                    gxClient.getTrustAnchors(version).toCompletableFuture();
            Result<List<TrustAnchorResponse.TrustAnchor>> result = future.get(60, TimeUnit.SECONDS);
            
            if (result.succeeded()) {
                var trustAnchors = result.getContent();
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("count", trustAnchors.size());
                response.put("trustAnchors", trustAnchors);
                monitor.info("Trust anchors fetched successfully: " + trustAnchors.size() + " anchors");
                return Response.ok()
                        .entity(response)
                        .build();
            } else {
                monitor.warning("Failed to fetch trust anchors: " + result.getFailureDetail());
                return Response.status(BAD_REQUEST)
                        .entity(createErrorResponse("Failed to fetch trust anchors: " + result.getFailureDetail()))
                        .build();
            }
        } catch (Exception e) {
            monitor.severe("Error fetching trust anchors: " + e.getMessage(), e);
            return Response.status(INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Error fetching trust anchors: " + e.getMessage()))
                    .build();
        }
    }


    private Map<String, Object> createSuccessResponse(String message, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("data", data);
        return response;
    }

    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        return response;
    }

    // ========== Gaia-X Registry API Endpoints ==========

    /**
     * Get trust anchors from Registry
     * GET /api/management/v3/clearinghouse/registry/trust-anchors?version=latest
     */
    @GET
    @Path("/registry/trust-anchors")
    public Response getTrustAnchors(@QueryParam("version") String version) {
        Response configCheck = checkGxClientConfigured();
        if (configCheck != null) return configCheck;

        try {
            CompletableFuture<Result<List<TrustAnchorResponse.TrustAnchor>>> future = 
                    gxClient.getTrustAnchors(version).toCompletableFuture();
            Result<List<TrustAnchorResponse.TrustAnchor>> result = future.get(60, TimeUnit.SECONDS);

            if (result.succeeded()) {
                return Response.ok()
                        .entity(createSuccessResponse("Trust anchors retrieved successfully", result.getContent()))
                        .build();
            } else {
                return Response.status(BAD_REQUEST)
                        .entity(createErrorResponse("Failed to get trust anchors: " + result.getFailureDetail()))
                        .build();
            }
        } catch (Exception e) {
            monitor.severe("Error getting trust anchors: " + e.getMessage(), e);
            return Response.status(INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Error getting trust anchors: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Get trusted issuers from Registry
     * GET /api/management/v3/clearinghouse/registry/trusted-issuers
     */
    @GET
    @Path("/registry/trusted-issuers")
    public Response getTrustedIssuers(@QueryParam("serviceType") String serviceType) {
        Response configCheck = checkGxClientConfigured();
        if (configCheck != null) return configCheck;

        try {
            if (serviceType != null && !serviceType.isBlank()) {
                CompletableFuture<Result<String[]>> future = 
                        gxClient.getTrustedIssuersByServiceType(serviceType).toCompletableFuture();
                Result<String[]> result = future.get(60, TimeUnit.SECONDS);

                if (result.succeeded()) {
                    return Response.ok()
                            .entity(createSuccessResponse("Trusted issuers retrieved successfully", result.getContent()))
                            .build();
                } else {
                    return Response.status(BAD_REQUEST)
                            .entity(createErrorResponse("Failed to get trusted issuers: " + result.getFailureDetail()))
                            .build();
                }
            } else {
            CompletableFuture<Result<List<String>>> future = 
                    gxClient.getTrustedIssuers().toCompletableFuture();
            Result<List<String>> result = future.get(60, TimeUnit.SECONDS);

                if (result.succeeded()) {
                    return Response.ok()
                            .entity(createSuccessResponse("Trusted issuers retrieved successfully", result.getContent()))
                            .build();
                } else {
                    return Response.status(BAD_REQUEST)
                            .entity(createErrorResponse("Failed to get trusted issuers: " + result.getFailureDetail()))
                            .build();
                }
            }
        } catch (Exception e) {
            monitor.severe("Error getting trusted issuers: " + e.getMessage(), e);
            return Response.status(INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Error getting trusted issuers: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Get certification notaries from Registry
     * GET /api/management/v3/clearinghouse/registry/certification-notaries
     */
    @GET
    @Path("/registry/certification-notaries")
    public Response getCertificationNotaries(@QueryParam("standard") String standard) {
        Response configCheck = checkGxClientConfigured();
        if (configCheck != null) return configCheck;

        try {
            if (standard != null && !standard.isBlank()) {
                CompletableFuture<Result<String[]>> future = 
                        gxClient.getCertificationNotariesByStandard(standard).toCompletableFuture();
                Result<String[]> result = future.get(60, TimeUnit.SECONDS);

                if (result.succeeded()) {
                    return Response.ok()
                            .entity(createSuccessResponse("Certification notaries retrieved successfully", result.getContent()))
                            .build();
                } else {
                    return Response.status(BAD_REQUEST)
                            .entity(createErrorResponse("Failed to get certification notaries: " + result.getFailureDetail()))
                            .build();
                }
            } else {
                CompletableFuture<Result<List<String>>> future = 
                        gxClient.getCertificationNotaries().toCompletableFuture();
                Result<List<String>> result = future.get(60, TimeUnit.SECONDS);

                if (result.succeeded()) {
                    return Response.ok()
                            .entity(createSuccessResponse("Certification notaries retrieved successfully", result.getContent()))
                            .build();
                } else {
                    return Response.status(BAD_REQUEST)
                            .entity(createErrorResponse("Failed to get certification notaries: " + result.getFailureDetail()))
                            .build();
                }
            }
        } catch (Exception e) {
            monitor.severe("Error getting certification notaries: " + e.getMessage(), e);
            return Response.status(INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Error getting certification notaries: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Get Registry DID
     * GET /api/management/v3/clearinghouse/registry/did
     */
    @GET
    @Path("/registry/did")
    public Response getRegistryDid() {
        Response configCheck = checkGxClientConfigured();
        if (configCheck != null) return configCheck;

        try {
            CompletableFuture<Result<Map<String, Object>>> future = 
                    gxClient.getRegistryDid().toCompletableFuture();
            Result<Map<String, Object>> result = future.get(60, TimeUnit.SECONDS);

            if (result.succeeded()) {
                return Response.ok()
                        .entity(createSuccessResponse("Registry DID retrieved successfully", result.getContent()))
                        .build();
            } else {
                return Response.status(BAD_REQUEST)
                        .entity(createErrorResponse("Failed to get Registry DID: " + result.getFailureDetail()))
                        .build();
            }
        } catch (Exception e) {
            monitor.severe("Error getting Registry DID: " + e.getMessage(), e);
            return Response.status(INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Error getting Registry DID: " + e.getMessage()))
                    .build();
        }
    }

    // ========== Gaia-X Compliance API Endpoints ==========

    /**
     * Check standard compliance
     * POST /api/management/v3/clearinghouse/compliance/standard-compliance
     */
    @POST
    @Path("/compliance/standard-compliance")
    public Response checkStandardCompliance(Map<String, Object> verifiablePresentation) {
        Response configCheck = checkGxClientConfigured();
        if (configCheck != null) return configCheck;

        try {
            CompletableFuture<Result<ComplianceCheckResponse>> future = 
                    gxClient.checkStandardCompliance(verifiablePresentation).toCompletableFuture();
            Result<ComplianceCheckResponse> result = future.get(60, TimeUnit.SECONDS);

            if (result.succeeded()) {
                return Response.ok()
                        .entity(createSuccessResponse("Compliance check completed", result.getContent()))
                        .build();
            } else {
                return Response.status(BAD_REQUEST)
                        .entity(createErrorResponse("Compliance check failed: " + result.getFailureDetail()))
                        .build();
            }
        } catch (Exception e) {
            monitor.severe("Error checking compliance: " + e.getMessage(), e);
            return Response.status(INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Error checking compliance: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Check label level compliance
     * POST /api/management/v3/clearinghouse/compliance/label-level-{level}
     */
    @POST
    @Path("/compliance/label-level-{level}")
    public Response checkLabelLevel(@PathParam("level") int level, Map<String, Object> verifiablePresentation) {
        Response configCheck = checkGxClientConfigured();
        if (configCheck != null) return configCheck;

        try {
            CompletableFuture<Result<ComplianceCheckResponse>> future;
            if (level == 1) {
                future = gxClient.checkLabelLevel1(verifiablePresentation).toCompletableFuture();
            } else if (level == 2) {
                future = gxClient.checkLabelLevel2(verifiablePresentation).toCompletableFuture();
            } else if (level == 3) {
                future = gxClient.checkLabelLevel3(verifiablePresentation).toCompletableFuture();
            } else {
                return Response.status(BAD_REQUEST)
                        .entity(createErrorResponse("Invalid label level. Must be 1, 2, or 3"))
                        .build();
            }

            Result<ComplianceCheckResponse> result = future.get(30, TimeUnit.SECONDS);

            if (result.succeeded()) {
                return Response.ok()
                        .entity(createSuccessResponse("Label level " + level + " check completed", result.getContent()))
                        .build();
            } else {
                return Response.status(BAD_REQUEST)
                        .entity(createErrorResponse("Label level check failed: " + result.getFailureDetail()))
                        .build();
            }
        } catch (Exception e) {
            monitor.severe("Error checking label level: " + e.getMessage(), e);
            return Response.status(INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Error checking label level: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Get Compliance DID
     * GET /api/management/v3/clearinghouse/compliance/did
     */
    @GET
    @Path("/compliance/did")
    public Response getComplianceDid() {
        Response configCheck = checkGxClientConfigured();
        if (configCheck != null) return configCheck;

        try {
            CompletableFuture<Result<Map<String, Object>>> future = 
                    gxClient.getComplianceDid().toCompletableFuture();
            Result<Map<String, Object>> result = future.get(30, TimeUnit.SECONDS);

            if (result.succeeded()) {
                return Response.ok()
                        .entity(createSuccessResponse("Compliance DID retrieved successfully", result.getContent()))
                        .build();
            } else {
                return Response.status(BAD_REQUEST)
                        .entity(createErrorResponse("Failed to get Compliance DID: " + result.getFailureDetail()))
                        .build();
            }
        } catch (Exception e) {
            monitor.severe("Error getting Compliance DID: " + e.getMessage(), e);
            return Response.status(INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Error getting Compliance DID: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Check if Gaia-X client is configured
     */
    private Response checkGxClientConfigured() {
        if (gxClient == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Gaia-X Registry/Compliance client is not configured. " +
                    "Please set edc.gaiax.registry.base.url and edc.gaiax.compliance.base.url in configuration.");
            error.put("requiredSettings", Map.of(
                    "registryBaseUrl", "edc.gaiax.registry.base.url",
                    "complianceBaseUrl", "edc.gaiax.compliance.base.url"
            ));
            if (monitor != null) {
                monitor.warning("[ClearingHouseTestController] Gaia-X operation attempted but client is not configured");
            }
            return Response.status(BAD_REQUEST)
                    .entity(error)
                    .build();
        }
        return null;
    }
}

