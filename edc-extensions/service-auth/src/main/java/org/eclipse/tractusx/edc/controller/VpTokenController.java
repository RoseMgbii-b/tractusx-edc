/********************************************************************************
 * Copyright (c) 2025 Contributors
 *
 * SPDX-License-Identifier: Apache-2.0
 ********************************************************************************/

package org.eclipse.tractusx.edc.controller;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.tractusx.edc.service.TokenSigner;
import org.eclipse.tractusx.edc.service.VpVerifier;

import java.util.Map;

/**
 * Token endpoint for VP-based authentication.
 *
 * Accepts Verifiable Presentation (VP) and issues JWT access token.
 *
 * Endpoint: POST /vp-token
 *
 * Request:
 *   grant_type=vp_token
 *   vp_token=<SIGNED_VP_JWT>
 *   scope=<SERVICE_SCOPE>
 *
 * Response:
 *   {
 *     "access_token": "<JWT>",
 *     "token_type": "Bearer",
 *     "expires_in": 7200,
 *     "scope": "service:telemetry"
 *   }
 */

@Path("/vp-token")
public class VpTokenController {

    private static final String GRANT_TYPE_VP_TOKEN = "vp_token";

    private final Monitor monitor;
    private final VpVerifier vpVerifier;
    private final TokenSigner tokenSigner;

    public VpTokenController(Monitor monitor, VpVerifier vpVerifier, TokenSigner tokenSigner) {
        this.monitor = monitor;
        this.vpVerifier = vpVerifier;
        this.tokenSigner = tokenSigner;
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Response token(
            @FormParam("grant_type") String grantType,
            @FormParam("vp_token") String vpToken,
            @FormParam("scope") String scope) {

        monitor.debug("[VpToken] Token request: grant_type=" + grantType + ", scope=" + scope);

        // Validate grant type
        if (!GRANT_TYPE_VP_TOKEN.equals(grantType)) {
            monitor.warning("[VpToken] Unsupported grant type: " + grantType);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(errorResponse("unsupported_grant_type", "Only 'vp_token' grant type is supported"))
                    .build();
        }

        // Validate vp_token
        if (vpToken == null || vpToken.isBlank()) {
            monitor.warning("[VpToken] Missing vp_token parameter");
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(errorResponse("invalid_request", "vp_token parameter is required"))
                    .build();
        }

        // Verify VP
        Result<Map<String, Object>> verificationResult = vpVerifier.verify(vpToken, scope);
        if (verificationResult.failed()) {
            monitor.warning("[VpToken] VP verification failed: " + verificationResult.getFailureDetail());
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(errorResponse("invalid_vp_token", verificationResult.getFailureDetail()))
                    .build();
        }

        // Extract claims
        Map<String, Object> claims = verificationResult.getContent();
        monitor.info("[VpToken] VP verified successfully for subject: " + claims.get("sub"));

        // Sign JWT access token
        String accessToken = tokenSigner.sign(claims);
        if (accessToken == null) {
            monitor.severe("[VpToken] Failed to sign access token");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(errorResponse("server_error", "Failed to issue access token"))
                    .build();
        }

        // Build token response
        var response = Json.createObjectBuilder()
                .add("access_token", accessToken)
                .add("token_type", "Bearer")
                .add("expires_in", tokenSigner.getExpirationSeconds())
                .add("scope", scope != null ? scope : "")
                .build();

        monitor.info("[VpToken] Access token issued successfully");
        return Response.ok(response).build();
    }

    private JsonObject errorResponse(String error, String errorDescription) {
        return Json.createObjectBuilder()
                .add("error", error)
                .add("error_description", errorDescription)
                .build();
    }
}
