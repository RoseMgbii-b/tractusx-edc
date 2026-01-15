/********************************************************************************
 * Copyright (c) 2025 Contributors
 *
 * SPDX-License-Identifier: Apache-2.0
 ********************************************************************************/

package org.eclipse.tractusx.edc.clearinghouse.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.tractusx.edc.clearinghouse.client.dto.*;
import org.eclipse.tractusx.edc.clearinghouse.config.GaiaXRegistryComplianceConfig;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import static org.eclipse.edc.spi.result.Result.failure;
import static org.eclipse.edc.spi.result.Result.success;

/**
 * Client for interacting with Gaia-X Registry and Compliance APIs.
 * 
 * This client integrates with:
 * - Registry APIs: Trust anchors, trusted issuers, certification notaries, ontologies
 * - Compliance APIs: Standard compliance checks, label level checks, DID/certificate collection
 * 
 * Similar to FIWARE's approach, this provides a unified interface for CHN integration
 * in dataspace connectors for participant validation and trust establishment.
 */
public class GaiaXRegistryComplianceClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final MediaType TEXT_PLAIN = MediaType.get("text/plain; charset=utf-8");
    private static final MediaType APPLICATION_YAML = MediaType.get("application/x-yaml; charset=utf-8");

    // Registry API paths (base URL already includes /development, so paths start with /)
    private static final String REGISTRY_TRUST_ANCHOR_VERSION = "/api/trustAnchor/{version}";
    private static final String REGISTRY_TRUST_ANCHOR_SEARCH = "/api/trustAnchor";
    private static final String REGISTRY_TRUST_ANCHOR_CHAIN = "/api/trustAnchor/chain";
    private static final String REGISTRY_TRUST_ANCHOR_CHAIN_FILE = "/api/trustAnchor/chain/file";
    private static final String REGISTRY_TRUSTED_ISSUERS = "/api/trusted-issuers";
    private static final String REGISTRY_TRUSTED_ISSUERS_SERVICE_TYPE = "/api/trusted-issuers/{serviceType}";
    private static final String REGISTRY_CERTIFICATION_NOTARY = "/api/certification-notary";
    private static final String REGISTRY_CERTIFICATION_NOTARY_STANDARD = "/api/certification-notary/{permissibleStandard}";
    private static final String REGISTRY_CONTEXT = "/context/{version}";
    private static final String REGISTRY_OWL = "/owl/{version}";
    private static final String REGISTRY_LINKML_VERSION = "/linkml/{version}/types.yaml";
    private static final String REGISTRY_LINKML_LATEST = "/linkml/types.yaml";
    private static final String REGISTRY_SHAPES = "/shapes/{version}";
    private static final String REGISTRY_DID = "/did.json";
    private static final String REGISTRY_DID_WELL_KNOWN = "/.well-known/did.json";
    private static final String REGISTRY_CERT_CHAIN = "/x509CertificateChain.pem";
    private static final String REGISTRY_CERT_CHAIN_WELL_KNOWN = "/.well-known/x509CertificateChain.pem";

    private static final String COMPLIANCE_STANDARD = "/api/credential-offers/standard-compliance";
    private static final String COMPLIANCE_LABEL_1 = "/api/credential-offers/label-level-1";
    private static final String COMPLIANCE_LABEL_2 = "/api/credential-offers/label-level-2";
    private static final String COMPLIANCE_LABEL_3 = "/api/credential-offers/label-level-3";
    private static final String COMPLIANCE_DID = "/did.json";
    private static final String COMPLIANCE_DID_WELL_KNOWN = "/.well-known/did.json";
    private static final String COMPLIANCE_CERT_CHAIN = "/x509CertificateChain.pem";
    private static final String COMPLIANCE_CERT_CHAIN_WELL_KNOWN = "/.well-known/x509CertificateChain.pem";

    private final EdcHttpClient httpClient;
    private final GaiaXRegistryComplianceConfig config;
    private final ObjectMapper mapper;
    private final Monitor monitor;
    private final Executor executor;

    public GaiaXRegistryComplianceClient(EdcHttpClient httpClient,
                                         GaiaXRegistryComplianceConfig config,
                                         ObjectMapper mapper,
                                         Monitor monitor,
                                         Executor executor) {
        this.httpClient = httpClient;
        this.config = config;
        this.mapper = mapper;
        this.monitor = monitor;
        this.executor = executor;
    }

    // ========== Registry API Methods ==========

    /**
     * Get the list of all Trust Anchors used by the registry (format ETSI TS 119 612)
     */
    public CompletionStage<Result<List<TrustAnchorResponse.TrustAnchor>>> getTrustAnchors(String version) {
        return CompletableFuture.supplyAsync(() -> doGetTrustAnchors(version), executor);
    }

    /**
     * Search for a TrustAnchor certificate in the registry
     */
    public CompletionStage<Result<TrustAnchorResponse.TrustAnchor>> searchTrustAnchor(TrustAnchorRequestDto request) {
        return CompletableFuture.supplyAsync(() -> doSearchTrustAnchor(request), executor);
    }

    /**
     * Verify root of a certificate chain to be a TrustAnchor in the registry
     */
    public CompletionStage<Result<Boolean>> verifyTrustAnchorChain(String certificateChain) {
        return CompletableFuture.supplyAsync(() -> doVerifyTrustAnchorChain(certificateChain), executor);
    }

    /**
     * Verify root of a certificate chain from URI to be a TrustAnchor in the registry
     */
    public CompletionStage<Result<Boolean>> verifyTrustAnchorChainFromUri(String uri) {
        return CompletableFuture.supplyAsync(() -> doVerifyTrustAnchorChainFromUri(uri), executor);
    }

    /**
     * Get list of authorized issuers URLs from Registry
     * 
     * API returns: {"registry": ["url1", "url2", ...]}
     */
    public CompletionStage<Result<List<String>>> getTrustedIssuers() {
        return CompletableFuture.supplyAsync(this::doGetTrustedIssuers, executor);
    }

    /**
     * Get list of authorized issuers URL for designated type of service
     */
    public CompletionStage<Result<String[]>> getTrustedIssuersByServiceType(String serviceType) {
        return CompletableFuture.supplyAsync(() -> doGetTrustedIssuersByServiceType(serviceType), executor);
    }

    /**
     * Get list of authorized certification notaries URLs from Registry
     * 
     * API returns: {"registry": ["url1", "url2", ...]}
     */
    public CompletionStage<Result<List<String>>> getCertificationNotaries() {
        return CompletableFuture.supplyAsync(this::doGetCertificationNotaries, executor);
    }

    /**
     * Get list of authorized certification notaries URL for designated permissible standard
     */
    public CompletionStage<Result<String[]>> getCertificationNotariesByStandard(String permissibleStandard) {
        return CompletableFuture.supplyAsync(() -> doGetCertificationNotariesByStandard(permissibleStandard), executor);
    }

    /**
     * Get the specified JSON-LD context version
     */
    public CompletionStage<Result<String>> getContext(String version) {
        return CompletableFuture.supplyAsync(() -> doGetContext(version), executor);
    }

    /**
     * Get a version of the Gaia-X OWL ontology in Turtle format
     */
    public CompletionStage<Result<String>> getOwlOntology(String version) {
        return CompletableFuture.supplyAsync(() -> doGetOwlOntology(version), executor);
    }

    /**
     * Get a version of the Gaia-X LinkML ontology in YAML format
     */
    public CompletionStage<Result<String>> getLinkMlOntology(String version) {
        return CompletableFuture.supplyAsync(() -> doGetLinkMlOntology(version), executor);
    }

    /**
     * Get the latest development version of the Gaia-X LinkML ontology in YAML format
     */
    public CompletionStage<Result<String>> getLinkMlOntologyLatest() {
        return CompletableFuture.supplyAsync(this::doGetLinkMlOntologyLatest, executor);
    }

    /**
     * Get a version of the Gaia-X ontology SHACL shapes in Turtle format
     */
    public CompletionStage<Result<String>> getShapes(String version) {
        return CompletableFuture.supplyAsync(() -> doGetShapes(version), executor);
    }

    /**
     * Get the Registry DID
     */
    public CompletionStage<Result<Map<String, Object>>> getRegistryDid() {
        return CompletableFuture.supplyAsync(this::doGetRegistryDid, executor);
    }

    /**
     * Get the Registry x509 certificate chain
     */
    public CompletionStage<Result<List<X509Certificate>>> getRegistryCertificateChain() {
        return CompletableFuture.supplyAsync(this::doGetRegistryCertificateChain, executor);
    }

    // ========== Compliance API Methods ==========

    /**
     * Checks Gaia-X standard compliance rules and outputs a Verifiable Credential
     */
    public CompletionStage<Result<ComplianceCheckResponse>> checkStandardCompliance(Object verifiablePresentation) {
        return CompletableFuture.supplyAsync(() -> doCheckCompliance(COMPLIANCE_STANDARD, verifiablePresentation), executor);
    }

    /**
     * Checks Gaia-X label level 1 rules and outputs a Verifiable Credential
     */
    public CompletionStage<Result<ComplianceCheckResponse>> checkLabelLevel1(Object verifiablePresentation) {
        return CompletableFuture.supplyAsync(() -> doCheckCompliance(COMPLIANCE_LABEL_1, verifiablePresentation), executor);
    }

    /**
     * Checks Gaia-X label level 2 rules and outputs a Verifiable Credential
     */
    public CompletionStage<Result<ComplianceCheckResponse>> checkLabelLevel2(Object verifiablePresentation) {
        return CompletableFuture.supplyAsync(() -> doCheckCompliance(COMPLIANCE_LABEL_2, verifiablePresentation), executor);
    }

    /**
     * Checks Gaia-X label level 3 rules and outputs a Verifiable Credential
     */
    public CompletionStage<Result<ComplianceCheckResponse>> checkLabelLevel3(Object verifiablePresentation) {
        return CompletableFuture.supplyAsync(() -> doCheckCompliance(COMPLIANCE_LABEL_3, verifiablePresentation), executor);
    }

    /**
     * Get the Compliance DID
     */
    public CompletionStage<Result<Map<String, Object>>> getComplianceDid() {
        return CompletableFuture.supplyAsync(this::doGetComplianceDid, executor);
    }

    /**
     * Get the Compliance x509 certificate chain
     */
    public CompletionStage<Result<List<X509Certificate>>> getComplianceCertificateChain() {
        return CompletableFuture.supplyAsync(this::doGetComplianceCertificateChain, executor);
    }

    // ========== Private Implementation Methods ==========

    private Result<List<TrustAnchorResponse.TrustAnchor>> doGetTrustAnchors(String version) {
        var url = config.getRegistryBaseUrl() + REGISTRY_TRUST_ANCHOR_VERSION.replace("{version}", version != null ? version : "latest");

        try {
            var request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = httpClient.execute(request)) {
                if (!response.isSuccessful()) {
                    return failure("Registry getTrustAnchors failed: HTTP " + response.code());
                }
                if (response.body() == null) {
                    return failure("Registry trust anchors response body is empty");
                }

                var json = response.body().string();
                var responseDto = mapper.readValue(json, TrustAnchorResponse.class);
                return success(responseDto.getTrustAnchors());
            }
        } catch (Exception e) {
            monitor.severe("[GaiaXRegistryComplianceClient] getTrustAnchors failed: " + e.getMessage(), e);
            return failure("getTrustAnchors failed: " + e.getMessage());
        }
    }

    private Result<TrustAnchorResponse.TrustAnchor> doSearchTrustAnchor(TrustAnchorRequestDto request) {
        if (request == null) return failure("request is null");
        var url = config.getRegistryBaseUrl() + REGISTRY_TRUST_ANCHOR_SEARCH;

        try {
            var bodyJson = mapper.writeValueAsString(request);
            var req = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(bodyJson, JSON))
                    .build();

            try (Response response = httpClient.execute(req)) {
                if (!response.isSuccessful()) {
                    return failure("Registry searchTrustAnchor failed: HTTP " + response.code());
                }
                if (response.body() == null) {
                    return failure("Registry searchTrustAnchor response body is empty");
                }

                var json = response.body().string();
                var trustAnchor = mapper.readValue(json, TrustAnchorResponse.TrustAnchor.class);
                return success(trustAnchor);
            }
        } catch (Exception e) {
            monitor.severe("[GaiaXRegistryComplianceClient] searchTrustAnchor failed: " + e.getMessage(), e);
            return failure("searchTrustAnchor failed: " + e.getMessage());
        }
    }

    private Result<Boolean> doVerifyTrustAnchorChain(String certificateChain) {
        if (certificateChain == null || certificateChain.isBlank()) {
            return failure("certificateChain is blank");
        }
        var url = config.getRegistryBaseUrl() + REGISTRY_TRUST_ANCHOR_CHAIN;

        try {
            var payload = new HashMap<String, Object>();
            payload.put("certificateChain", certificateChain);

            var bodyJson = mapper.writeValueAsString(payload);
            var req = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(bodyJson, JSON))
                    .build();

            try (Response response = httpClient.execute(req)) {
                if (!response.isSuccessful()) {
                    return failure("Registry verifyTrustAnchorChain failed: HTTP " + response.code());
                }
                if (response.body() == null) {
                    return success(false);
                }

                var json = response.body().string();
                var tree = mapper.readTree(json);
                var valid = tree.has("valid") && tree.get("valid").asBoolean();
                return success(valid);
            }
        } catch (Exception e) {
            monitor.severe("[GaiaXRegistryComplianceClient] verifyTrustAnchorChain failed: " + e.getMessage(), e);
            return failure("verifyTrustAnchorChain failed: " + e.getMessage());
        }
    }

    private Result<Boolean> doVerifyTrustAnchorChainFromUri(String uri) {
        if (uri == null || uri.isBlank()) {
            return failure("uri is blank");
        }
        var url = config.getRegistryBaseUrl() + REGISTRY_TRUST_ANCHOR_CHAIN_FILE;

        try {
            var requestDto = new TrustAnchorChainUriRequestDto();
            requestDto.setUri(uri);
            var bodyJson = mapper.writeValueAsString(requestDto);

            var req = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(bodyJson, JSON))
                    .build();

            try (Response response = httpClient.execute(req)) {
                if (!response.isSuccessful()) {
                    // Read error response body to get detailed error message
                    String errorMessage = "HTTP " + response.code();
                    if (response.body() != null) {
                        try {
                            var errorJson = response.body().string();
                            var errorTree = mapper.readTree(errorJson);
                            if (errorTree.has("message")) {
                                errorMessage = errorTree.get("message").asText();
                            } else if (errorTree.has("error")) {
                                errorMessage = errorTree.get("error").asText();
                            } else {
                                errorMessage = errorJson; // Use full JSON if no specific field
                            }
                        } catch (Exception e) {
                            // If we can't parse the error body, just use the code
                            monitor.debug("[GaiaXRegistryComplianceClient] Could not parse error response body: " + e.getMessage());
                        }
                    }
                    return failure("Registry verifyTrustAnchorChainFromUri failed: " + errorMessage);
                }
                if (response.body() == null) {
                    return success(false);
                }

                var json = response.body().string();
                var tree = mapper.readTree(json);
                var valid = tree.has("valid") && tree.get("valid").asBoolean();
                return success(valid);
            }
        } catch (Exception e) {
            monitor.severe("[GaiaXRegistryComplianceClient] verifyTrustAnchorChainFromUri failed: " + e.getMessage(), e);
            return failure("verifyTrustAnchorChainFromUri failed: " + e.getMessage());
        }
    }

    private Result<List<String>> doGetTrustedIssuers() {
        var url = config.getRegistryBaseUrl() + REGISTRY_TRUSTED_ISSUERS;

        try {
            var request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = httpClient.execute(request)) {
                if (!response.isSuccessful()) {
                    return failure("Registry getTrustedIssuers failed: HTTP " + response.code());
                }
                if (response.body() == null) {
                    return failure("Registry trusted issuers response body is empty");
                }

                var json = response.body().string();
                var responseDto = mapper.readValue(json, TrustedIssuersResponse.class);
                return success(responseDto.getRegistry());
            }
        } catch (Exception e) {
            monitor.severe("[GaiaXRegistryComplianceClient] getTrustedIssuers failed: " + e.getMessage(), e);
            return failure("getTrustedIssuers failed: " + e.getMessage());
        }
    }

    private Result<String[]> doGetTrustedIssuersByServiceType(String serviceType) {
        if (serviceType == null || serviceType.isBlank()) {
            return failure("serviceType is blank");
        }
        var url = config.getRegistryBaseUrl() + REGISTRY_TRUSTED_ISSUERS_SERVICE_TYPE.replace("{serviceType}", serviceType);

        try {
            var request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = httpClient.execute(request)) {
                if (!response.isSuccessful()) {
                    return failure("Registry getTrustedIssuersByServiceType failed: HTTP " + response.code());
                }
                if (response.body() == null) {
                    return failure("Registry trusted issuers response body is empty");
                }

                var json = response.body().string();
                var issuers = mapper.readValue(json, String[].class);
                return success(issuers);
            }
        } catch (Exception e) {
            monitor.severe("[GaiaXRegistryComplianceClient] getTrustedIssuersByServiceType failed: " + e.getMessage(), e);
            return failure("getTrustedIssuersByServiceType failed: " + e.getMessage());
        }
    }

    private Result<List<String>> doGetCertificationNotaries() {
        var url = config.getRegistryBaseUrl() + REGISTRY_CERTIFICATION_NOTARY;

        try {
            var request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = httpClient.execute(request)) {
                if (!response.isSuccessful()) {
                    return failure("Registry getCertificationNotaries failed: HTTP " + response.code());
                }
                if (response.body() == null) {
                    return failure("Registry certification notaries response body is empty");
                }

                var json = response.body().string();
                var responseDto = mapper.readValue(json, CertificationNotaryResponse.class);
                return success(responseDto.getRegistry());
            }
        } catch (Exception e) {
            monitor.severe("[GaiaXRegistryComplianceClient] getCertificationNotaries failed: " + e.getMessage(), e);
            return failure("getCertificationNotaries failed: " + e.getMessage());
        }
    }

    private Result<String[]> doGetCertificationNotariesByStandard(String permissibleStandard) {
        if (permissibleStandard == null || permissibleStandard.isBlank()) {
            return failure("permissibleStandard is blank");
        }
        var url = config.getRegistryBaseUrl() + REGISTRY_CERTIFICATION_NOTARY_STANDARD.replace("{permissibleStandard}", permissibleStandard);

        try {
            var request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = httpClient.execute(request)) {
                if (!response.isSuccessful()) {
                    return failure("Registry getCertificationNotariesByStandard failed: HTTP " + response.code());
                }
                if (response.body() == null) {
                    return failure("Registry certification notaries response body is empty");
                }

                var json = response.body().string();
                var notaries = mapper.readValue(json, String[].class);
                return success(notaries);
            }
        } catch (Exception e) {
            monitor.severe("[GaiaXRegistryComplianceClient] getCertificationNotariesByStandard failed: " + e.getMessage(), e);
            return failure("getCertificationNotariesByStandard failed: " + e.getMessage());
        }
    }

    private Result<String> doGetContext(String version) {
        var url = config.getRegistryBaseUrl() + REGISTRY_CONTEXT.replace("{version}", version != null ? version : "latest");

        try {
            var request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = httpClient.execute(request)) {
                if (!response.isSuccessful()) {
                    return failure("Registry getContext failed: HTTP " + response.code());
                }
                if (response.body() == null) {
                    return failure("Registry context response body is empty");
                }

                return success(response.body().string());
            }
        } catch (Exception e) {
            monitor.severe("[GaiaXRegistryComplianceClient] getContext failed: " + e.getMessage(), e);
            return failure("getContext failed: " + e.getMessage());
        }
    }

    private Result<String> doGetOwlOntology(String version) {
        var url = config.getRegistryBaseUrl() + REGISTRY_OWL.replace("{version}", version != null ? version : "latest");

        try {
            var request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = httpClient.execute(request)) {
                if (!response.isSuccessful()) {
                    return failure("Registry getOwlOntology failed: HTTP " + response.code());
                }
                if (response.body() == null) {
                    return failure("Registry OWL ontology response body is empty");
                }

                return success(response.body().string());
            }
        } catch (Exception e) {
            monitor.severe("[GaiaXRegistryComplianceClient] getOwlOntology failed: " + e.getMessage(), e);
            return failure("getOwlOntology failed: " + e.getMessage());
        }
    }

    private Result<String> doGetLinkMlOntology(String version) {
        var url = config.getRegistryBaseUrl() + REGISTRY_LINKML_VERSION.replace("{version}", version != null ? version : "latest");

        try {
            var request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = httpClient.execute(request)) {
                if (!response.isSuccessful()) {
                    return failure("Registry getLinkMlOntology failed: HTTP " + response.code());
                }
                if (response.body() == null) {
                    return failure("Registry LinkML ontology response body is empty");
                }

                return success(response.body().string());
            }
        } catch (Exception e) {
            monitor.severe("[GaiaXRegistryComplianceClient] getLinkMlOntology failed: " + e.getMessage(), e);
            return failure("getLinkMlOntology failed: " + e.getMessage());
        }
    }

    private Result<String> doGetLinkMlOntologyLatest() {
        var url = config.getRegistryBaseUrl() + REGISTRY_LINKML_LATEST;

        try {
            var request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = httpClient.execute(request)) {
                if (!response.isSuccessful()) {
                    return failure("Registry getLinkMlOntologyLatest failed: HTTP " + response.code());
                }
                if (response.body() == null) {
                    return failure("Registry LinkML ontology response body is empty");
                }

                return success(response.body().string());
            }
        } catch (Exception e) {
            monitor.severe("[GaiaXRegistryComplianceClient] getLinkMlOntologyLatest failed: " + e.getMessage(), e);
            return failure("getLinkMlOntologyLatest failed: " + e.getMessage());
        }
    }

    private Result<String> doGetShapes(String version) {
        var url = config.getRegistryBaseUrl() + REGISTRY_SHAPES.replace("{version}", version != null ? version : "latest");

        try {
            var request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = httpClient.execute(request)) {
                if (!response.isSuccessful()) {
                    return failure("Registry getShapes failed: HTTP " + response.code());
                }
                if (response.body() == null) {
                    return failure("Registry shapes response body is empty");
                }

                return success(response.body().string());
            }
        } catch (Exception e) {
            monitor.severe("[GaiaXRegistryComplianceClient] getShapes failed: " + e.getMessage(), e);
            return failure("getShapes failed: " + e.getMessage());
        }
    }

    private Result<Map<String, Object>> doGetRegistryDid() {
        var url = config.getRegistryBaseUrl() + REGISTRY_DID;

        try {
            var request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = httpClient.execute(request)) {
                if (!response.isSuccessful()) {
                    return failure("Registry getDid failed: HTTP " + response.code());
                }
                if (response.body() == null) {
                    return failure("Registry DID response body is empty");
                }

                var json = response.body().string();
                @SuppressWarnings("unchecked")
                var did = mapper.readValue(json, Map.class);
                return success(did);
            }
        } catch (Exception e) {
            monitor.severe("[GaiaXRegistryComplianceClient] getRegistryDid failed: " + e.getMessage(), e);
            return failure("getRegistryDid failed: " + e.getMessage());
        }
    }

    private Result<List<X509Certificate>> doGetRegistryCertificateChain() {
        var url = config.getRegistryBaseUrl() + REGISTRY_CERT_CHAIN;

        try {
            var request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = httpClient.execute(request)) {
                if (!response.isSuccessful()) {
                    return failure("Registry getCertificateChain failed: HTTP " + response.code());
                }
                if (response.body() == null) {
                    return failure("Registry certificate chain response body is empty");
                }

                var pem = response.body().string();
                return parseCertificateChain(pem);
            }
        } catch (Exception e) {
            monitor.severe("[GaiaXRegistryComplianceClient] getRegistryCertificateChain failed: " + e.getMessage(), e);
            return failure("getRegistryCertificateChain failed: " + e.getMessage());
        }
    }

    private Result<ComplianceCheckResponse> doCheckCompliance(String path, Object verifiablePresentation) {
        if (verifiablePresentation == null) {
            return failure("verifiablePresentation is null");
        }
        var url = config.getComplianceBaseUrl() + path;

        try {
            // Send Verifiable Presentation directly (not wrapped)
            // The API expects the VP as the request body directly
            var bodyJson = mapper.writeValueAsString(verifiablePresentation);

            var req = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(bodyJson, JSON))
                    .build();

            try (Response response = httpClient.execute(req)) {
                var responseBody = response.body();
                if (responseBody == null) {
                    return failure("Compliance check response body is empty");
                }
                
                var json = responseBody.string();
                
                if (!response.isSuccessful()) {
                    monitor.warning("[GaiaXRegistryComplianceClient] Compliance API returned HTTP " + response.code() + ": " + json);
                    return failure("Compliance check failed: HTTP " + response.code() + " - " + json);
                }

                var responseDto = mapper.readValue(json, ComplianceCheckResponse.class);
                return success(responseDto);
            }
        } catch (Exception e) {
            monitor.severe("[GaiaXRegistryComplianceClient] compliance check failed: " + e.getMessage(), e);
            return failure("compliance check failed: " + e.getMessage());
        }
    }

    private Result<Map<String, Object>> doGetComplianceDid() {
        var url = config.getComplianceBaseUrl() + COMPLIANCE_DID;

        try {
            var request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = httpClient.execute(request)) {
                if (!response.isSuccessful()) {
                    return failure("Compliance getDid failed: HTTP " + response.code());
                }
                if (response.body() == null) {
                    return failure("Compliance DID response body is empty");
                }

                var json = response.body().string();
                @SuppressWarnings("unchecked")
                var did = mapper.readValue(json, Map.class);
                return success(did);
            }
        } catch (Exception e) {
            monitor.severe("[GaiaXRegistryComplianceClient] getComplianceDid failed: " + e.getMessage(), e);
            return failure("getComplianceDid failed: " + e.getMessage());
        }
    }

    private Result<List<X509Certificate>> doGetComplianceCertificateChain() {
        var url = config.getComplianceBaseUrl() + COMPLIANCE_CERT_CHAIN;

        try {
            var request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = httpClient.execute(request)) {
                if (!response.isSuccessful()) {
                    return failure("Compliance getCertificateChain failed: HTTP " + response.code());
                }
                if (response.body() == null) {
                    return failure("Compliance certificate chain response body is empty");
                }

                var pem = response.body().string();
                return parseCertificateChain(pem);
            }
        } catch (Exception e) {
            monitor.severe("[GaiaXRegistryComplianceClient] getComplianceCertificateChain failed: " + e.getMessage(), e);
            return failure("getComplianceCertificateChain failed: " + e.getMessage());
        }
    }

    private Result<List<X509Certificate>> parseCertificateChain(String pem) {
        try {
            var cf = CertificateFactory.getInstance("X.509");
            var certificates = new java.util.ArrayList<X509Certificate>();

            // Split PEM by certificate boundaries
            var pattern = java.util.regex.Pattern.compile(
                    "-+BEGIN CERTIFICATE-+([^-]+)-+END CERTIFICATE-+",
                    java.util.regex.Pattern.DOTALL
            );
            var matcher = pattern.matcher(pem);

            while (matcher.find()) {
                var certPem = matcher.group(1).replaceAll("\\s+", "");
                var decoded = Base64.getDecoder().decode(certPem);
                var cert = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(decoded));
                certificates.add(cert);
            }

            if (certificates.isEmpty()) {
                return failure("No certificates found in chain");
            }

            return success(certificates);
        } catch (CertificateException | IllegalArgumentException e) {
            monitor.severe("[GaiaXRegistryComplianceClient] parseCertificateChain failed: " + e.getMessage(), e);
            return failure("parseCertificateChain failed: " + e.getMessage());
        }
    }
}

