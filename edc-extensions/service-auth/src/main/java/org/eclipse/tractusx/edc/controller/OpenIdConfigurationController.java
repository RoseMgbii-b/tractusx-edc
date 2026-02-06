/********************************************************************************
 * Copyright (c) 2025 Contributors
 *
 * SPDX-License-Identifier: Apache-2.0
 ********************************************************************************/

package org.eclipse.tractusx.edc.controller;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.edc.spi.monitor.Monitor;

/**
 * OpenID Configuration endpoint.
 *
 * Advertises the token endpoint and supported grant types for VP-based authentication.
 *
 * Endpoint: GET /.well-known/openid-configuration
 */
@Path("/.well-known/openid-configuration")
public class OpenIdConfigurationController {

    private final Monitor monitor;
    private final String tokenEndpointPath;

    public OpenIdConfigurationController(Monitor monitor, String tokenEndpointPath) {
        this.monitor = monitor;
        this.tokenEndpointPath = tokenEndpointPath;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject getConfiguration(@Context UriInfo uriInfo) {
        var baseUri = uriInfo.getBaseUri().toString();
        // Remove trailing slash if present
        if (baseUri.endsWith("/")) {
            baseUri = baseUri.substring(0, baseUri.length() - 1);
        }

        var tokenEndpoint = baseUri + tokenEndpointPath;

        var config = Json.createObjectBuilder()
                .add("grant_types_supported", Json.createArrayBuilder().add("vp_token"))
                .add("token_endpoint", tokenEndpoint)
                .add("issuer", baseUri)
                .build();

        monitor.debug("[OpenIDConfig] Returning configuration: " + config);
        return config;
    }
}
