/********************************************************************************
 * Copyright (c) 2025 Contributors
 *
 * SPDX-License-Identifier: Apache-2.0
 ********************************************************************************/

package org.eclipse.tractusx.edc.api;

import jakarta.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.edc.spi.monitor.Monitor;

/**
 * Example service API controller.
 *
 * This is a protected service endpoint that requires VP-based authentication.
 * The ServiceAuthorizationFilter will validate the JWT token and evaluate policy
 * before this method is called.
 *
 * IMPORTANT: The path segment after "/service/" must match the asset ID.
 * For example, if the asset ID is "telemetry-service", the path should be "/service/telemetry-service".
 *
 * Endpoint: POST /service/{asset-id}
 * 
 * Note: This is an example controller. In production, you may want to create
 * a dynamic controller that handles all service endpoints based on asset ID.
 */
@Path("/service/telemetry")
public class ServiceApiController {

    private final Monitor monitor;

    public ServiceApiController(Monitor monitor) {
        this.monitor = monitor;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response processTelemetry(JsonObject input) {
        // At this point:
        // - JWT was validated by ServiceAuthorizationFilter
        // - Policy was evaluated and access was granted
        // - Request is authorized

        monitor.info("[ServiceAPI] Processing telemetry service request");

        // TODO: Implement actual service logic
        // - Data transformation
        // - Third-party API call
        // - Processing

        // For now, return echo response
        var response = jakarta.json.Json.createObjectBuilder()
                .add("status", "success")
                .add("message", "Service executed successfully")
                .add("input", input)
                .build();

        return Response.ok(response).build();
    }
}
