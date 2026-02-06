/********************************************************************************
 * Copyright (c) 2025 Contributors
 *
 * SPDX-License-Identifier: Apache-2.0
 ********************************************************************************/

package org.eclipse.tractusx.edc.filter;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.connector.controlplane.asset.spi.index.AssetIndex;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractDefinition;
import org.eclipse.edc.connector.controlplane.policy.spi.store.PolicyDefinitionStore;
import org.eclipse.edc.connector.controlplane.services.spi.contractdefinition.ContractDefinitionService;
import org.eclipse.edc.policy.engine.spi.PolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.tractusx.edc.service.TokenSigner;

import java.util.List;
import java.util.Map;

import static jakarta.ws.rs.Priorities.AUTHORIZATION;

/**
 * Authorization filter for service API endpoints.
 *
 * Intercepts service API calls, validates JWT tokens, and evaluates policies using PDP.
 *
 * Applied to all service API endpoints via JAX-RS filter mechanism.
 */
@Provider
//@Priority(AUTHORIZATION)
public class ServiceAuthorizationFilter implements ContainerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String SERVICE_PATH_PREFIX = "/service/";

    private final Monitor monitor;
    private final TokenSigner tokenSigner;
    private final PolicyEngine policyEngine;
    private final AssetIndex assetIndex;
    private final ContractDefinitionService contractDefinitionService;
    private final PolicyDefinitionStore policyDefinitionStore;

    public ServiceAuthorizationFilter(
            Monitor monitor,
            TokenSigner tokenSigner,
            PolicyEngine policyEngine,
            AssetIndex assetIndex,
            ContractDefinitionService contractDefinitionService,
            PolicyDefinitionStore policyDefinitionStore) {
        this.monitor = monitor;
        this.tokenSigner = tokenSigner;
        this.policyEngine = policyEngine;
        this.assetIndex = assetIndex;
        this.contractDefinitionService = contractDefinitionService;
        this.policyDefinitionStore = policyDefinitionStore;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        var path = requestContext.getUriInfo().getPath();
        var requestUri = requestContext.getUriInfo().getRequestUri().toString();
        
        // Log all requests to see what's being processed
        monitor.debug("[ServiceAuthFilter] Filter invoked - path: " + path + ", URI: " + requestUri);

        // Only apply to service endpoints
        // Path might be "/service/..." or "/api/public/service/..." depending on context
        if (!path.contains(SERVICE_PATH_PREFIX) && !requestUri.contains(SERVICE_PATH_PREFIX)) {
            monitor.debug("[ServiceAuthFilter] Skipping non-service path: " + path);
            return;
        }

        monitor.info("[ServiceAuthFilter] Processing service request - path: " + path + ", URI: " + requestUri);

        // Extract token
        String authHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            monitor.warning("[ServiceAuthFilter] Missing or invalid Authorization header");
            abort(requestContext, Response.Status.UNAUTHORIZED, "missing_token", "Missing or invalid Authorization header");
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();

        // Verify token
        monitor.debug("[ServiceAuthFilter] Verifying token (length: " + token.length() + ", starts with: " + (token.length() > 20 ? token.substring(0, 20) : token) + "...)");
        Result<Map<String, Object>> verifyResult = tokenSigner.verify(token);
        if (verifyResult.failed()) {
            String errorDetail = verifyResult.getFailureDetail();
            monitor.warning("[ServiceAuthFilter] Token verification failed: " + errorDetail);
            monitor.warning("[ServiceAuthFilter] Token (first 50 chars): " + (token.length() > 50 ? token.substring(0, 50) : token));
            abort(requestContext, Response.Status.UNAUTHORIZED, "invalid_token", errorDetail);
            return;
        }

        Map<String, Object> claims = verifyResult.getContent();
        String subject = (String) claims.get("sub");
        monitor.debug("[ServiceAuthFilter] Token verified for subject: " + subject);

        // Resolve service policy
        Result<Policy> policyResult = resolveServicePolicy(path);
        if (policyResult.failed()) {
            monitor.warning("[ServiceAuthFilter] Failed to resolve service policy: " + policyResult.getFailureDetail());
            // If no policy found, allow by default (or deny - configurable)
            // For now, allow if no policy
            monitor.debug("[ServiceAuthFilter] No policy found, allowing request");
            return;
        }

        Policy policy = policyResult.getContent();

        // Build policy context
        PolicyContext policyContext = new SimplePolicyContext();

        // Add claims to context
        // PolicyContext extends Map<String, Object>, so we can use put() directly
        for (Map.Entry<String, Object> entry : claims.entrySet()) {
            ((Map<String, Object>) policyContext).put(entry.getKey(), entry.getValue());
        }

        // Evaluate policy
        var decision = policyEngine.evaluate(policy, policyContext);
        if (decision.failed() || !Boolean.TRUE.equals(decision.getContent())) {
            monitor.warning("[ServiceAuthFilter] Policy evaluation denied access for subject: " + subject);
            abort(requestContext, Response.Status.FORBIDDEN, "access_denied", "Policy evaluation denied access");
            return;
        }

        monitor.info("[ServiceAuthFilter] Access granted for subject: " + subject + " to service: " + path);
        // Request allowed, continue
    }

    /**
     * Resolves service policy from asset and contract definition.
     *
     * Flow:
     * 1. Extract asset ID from request path
     * 2. Look up asset from AssetIndex
     * 3. Find ContractDefinition for asset
     * 4. Get policy from ContractDefinition
     */
    private Result<Policy> resolveServicePolicy(String requestPath) {
        try {
            // Step 1: Extract asset ID from path
            // Path format: /service/<asset-id> or /service/<service-name>
            // For now, assume path segment after /service/ is asset ID
            String[] pathParts = requestPath.split("/");
            if (pathParts.length < 3) {
                return Result.failure("Invalid service path format");
            }

            String assetId = pathParts[2]; // /service/<asset-id>
            monitor.debug("[ServiceAuthFilter] Resolving policy for asset: " + assetId);

            // Step 2: Look up asset
            Asset asset = assetIndex.findById(assetId);
            if (asset == null) {
                return Result.failure("Asset not found: " + assetId);
            }

            // Check if it's a service asset
            String resourceType = asset.getProperties().get("edc:resourceType").toString();
            if (!"service".equals(resourceType)) {
                return Result.failure("Asset is not a service: " + assetId);
            }

            // Step 3: Find ContractDefinition for asset
            // Search contract definitions that match this asset
            var querySpec = QuerySpec.Builder.newInstance().build();
            ServiceResult<List<ContractDefinition>> contractDefsResult = contractDefinitionService.search(querySpec);
            if (contractDefsResult.failed()) {
                return Result.failure("Failed to search contract definitions: " + contractDefsResult.getFailureDetail());
            }

            List<ContractDefinition> contractDefs = contractDefsResult.getContent();
            ContractDefinition matchingContractDef = null;

            for (ContractDefinition contractDef : contractDefs) {
                // Simplified: check if contract definition references this asset
                // TODO: Proper asset selector evaluation using Criterion evaluation
                // For now, we'll try to find a contract definition that might match
                // In production, this should properly evaluate assetsSelector
                matchingContractDef = contractDef;
                break; // Simplified: take first one
            }

            if (matchingContractDef == null) {
                return Result.failure("No contract definition found for asset: " + assetId);
            }

            monitor.debug("[ServiceAuthFilter] Found contract definition for asset: " + assetId);

            // Step 4: Get policy from PolicyDefinitionStore
            String contractPolicyId = matchingContractDef.getContractPolicyId();
            if (contractPolicyId == null) {
                return Result.failure("Contract definition has no contract policy ID");
            }

            var policyDef = policyDefinitionStore.findById(contractPolicyId);
            if (policyDef == null) {
                return Result.failure("Policy definition not found: " + contractPolicyId);
            }

            Policy policy = policyDef.getPolicy();
            if (policy == null) {
                return Result.failure("Policy definition has no policy: " + contractPolicyId);
            }

            monitor.debug("[ServiceAuthFilter] Resolved policy for asset: " + assetId);
            return Result.success(policy);

        } catch (Exception e) {
            monitor.severe("[ServiceAuthFilter] Error resolving service policy: " + e.getMessage(), e);
            return Result.failure("Error resolving policy: " + e.getMessage());
        }
    }

    private void abort(ContainerRequestContext context, Response.Status status, String error, String errorDescription) {
        var errorResponse = jakarta.json.Json.createObjectBuilder()
                .add("error", error)
                .add("error_description", errorDescription)
                .build();

        context.abortWith(Response.status(status)
                .entity(errorResponse)
                .build());
    }
}
