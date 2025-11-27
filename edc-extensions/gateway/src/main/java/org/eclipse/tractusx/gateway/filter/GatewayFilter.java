/********************************************************************************
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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

package org.eclipse.tractusx.gateway.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.tractusx.gateway.model.AccessControlResult;
import org.eclipse.tractusx.gateway.model.RequestInfo;
import org.eclipse.tractusx.gateway.service.GatewayService;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class GatewayFilter implements ContainerRequestFilter {

    private final GatewayService gatewayService;
    private final Monitor monitor;

    @Context
    private HttpServletRequest servletRequest;

    public GatewayFilter(GatewayService gatewayService, Monitor monitor) {
        this.gatewayService = gatewayService;
        this.monitor = monitor;
    }


    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        try {
            // Extract request information
            String clientIp = extractClientIp(requestContext);
            String path = requestContext.getUriInfo().getRequestUri().getPath();
            String method = requestContext.getMethod();
            Map<String, String> headers = extractHeaders(requestContext);
            String userIdentifier = extractUserIdentifier(requestContext);

            // Create request info
            RequestInfo requestInfo = new RequestInfo(
                    clientIp,
                    path,
                    method,
                    headers,
                    Instant.now(),
                    userIdentifier
            );

            // Evaluate access control
            AccessControlResult result =
                    gatewayService.evaluateRequest(requestInfo);

            // Block request if denied
            if (!result.isAllowed()) {
                monitor.warning(String.format("Access denied: %s - %s", requestInfo.getPath(), result.getReason()));
                requestContext.abortWith(
                        Response.status(Response.Status.FORBIDDEN)
                                .entity(Map.of("error", result.getReason()))
                                .build()
                );
                return;
            }

            // Request allowed, continue processing
            monitor.debug(String.format("Access allowed: %s %s from %s", method, path, clientIp));

        } catch (Exception e) {
            monitor.severe("Error in access control filter: " + e.getMessage(), e);
            // On error, allow request (fail open) - you may want to change this to fail closed
            monitor.warning(String.format("Access control error, allowing request: %s", e.getMessage()));
        }
    }

    private String extractClientIp(ContainerRequestContext requestContext) {
        // Check X-Forwarded-For header (for proxies/load balancers)
        String xForwardedFor = requestContext.getHeaderString("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // Take the first IP (original client)
            return xForwardedFor.split(",")[0].trim();
        }

        // Check X-Real-IP header
        String xRealIp = requestContext.getHeaderString("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        if (servletRequest != null) {
            String forwarded = servletRequest.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            String realIp = servletRequest.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }
            return servletRequest.getRemoteAddr();
        }

        // Last resort fallbacks
        if (requestContext.getProperty("jakarta.servlet.http.HttpServletRequest") instanceof HttpServletRequest req) {
            return req.getRemoteAddr();
        }

        return "unknown";
    }

    private Map<String, String> extractHeaders(ContainerRequestContext requestContext) {
        Map<String, String> headers = new HashMap<>();
        requestContext.getHeaders().forEach((key, values) -> {
            if (values != null && !values.isEmpty()) {
                headers.put(key, values.get(0));
            }
        });
        return headers;
    }

    private String extractUserIdentifier(ContainerRequestContext requestContext) {
        // Try to extract user ID from JWT token if available
        String authHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // In a real implementation, you might decode the JWT to get user ID
            // For now, we'll use a simple approach
            return extractUserIdFromToken(authHeader);
        }
        return null;
    }

    private String extractUserIdFromToken(String authHeader) {
        // Simplified - in production, decode JWT and extract 'sub' or 'preferred_username'
        // This is a placeholder - implement proper JWT parsing if needed
        return null;
    }
}
