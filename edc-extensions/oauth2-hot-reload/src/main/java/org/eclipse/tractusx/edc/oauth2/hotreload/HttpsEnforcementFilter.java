/********************************************************************************
 * Copyright (c) 2025 Your Company Name
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

package org.eclipse.tractusx.edc.oauth2.hotreload;


import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import org.eclipse.edc.spi.monitor.Monitor;

import java.io.IOException;
import java.net.URI;

/**
 * Filter that enforces HTTPS/TLS for all incoming requests.
 *
 * Rejects HTTP requests and optionally redirects to HTTPS.
 */
public class HttpsEnforcementFilter implements ContainerRequestFilter {

    private final Monitor monitor;
    private final boolean allowRedirect;
    private final int httpsPort;

    public HttpsEnforcementFilter(Monitor monitor, boolean allowRedirect, int httpsPort) {
        this.monitor = monitor;
        this.allowRedirect = allowRedirect;
        this.httpsPort = httpsPort;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        URI requestUri = requestContext.getUriInfo().getRequestUri();
        String scheme = requestUri.getScheme();
        boolean isSecure = requestContext.getSecurityContext().isSecure();

        monitor.debug("HTTPS Check - Scheme: " + scheme + ", isSecure: " + isSecure + ", URI: " + requestUri);

        // Check if request is HTTP (not HTTPS)
        if (!"https".equalsIgnoreCase(scheme) && !isSecure) {
            String path = requestUri.getPath();
            String query = requestUri.getQuery();

            if (allowRedirect) {
                // OPTION 1: Redirect HTTP to HTTPS (301 Moved Permanently)
                String httpsUrl = buildHttpsUrl(requestUri, query);
                monitor.warning("Redirecting HTTP request to HTTPS: " + requestUri + " -> " + httpsUrl);

                requestContext.abortWith(
                        Response.status(Response.Status.MOVED_PERMANENTLY)
                                .location(URI.create(httpsUrl))
                                .header("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
                                .entity("{\"message\":\"HTTPS required\",\"redirect\":\"" + httpsUrl + "\"}")
                                .build()
                );
            } else {
                // OPTION 2: Reject HTTP requests with 403 Forbidden
                monitor.severe("Rejected HTTP request - HTTPS required: " + requestUri);

                requestContext.abortWith(
                        Response.status(Response.Status.FORBIDDEN)
                                .header("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
                                .entity("{\"message\":\"HTTPS required\",\"type\":\"Forbidden\",\"scheme\":\"" + scheme + "\"}")
                                .build()
                );
            }
        }
    }


    /**
     * Build HTTPS URL from HTTP request URI
     */
    private String buildHttpsUrl(URI requestUri, String query) {
        String host = requestUri.getHost();
        String path = requestUri.getPath();

        // Use configured HTTPS port, or default to 443, or keep original port if different
        int port = httpsPort > 0 ? httpsPort : (requestUri.getPort() == -1 ? 443 : requestUri.getPort());

        StringBuilder httpsUrl = new StringBuilder("https://");
        httpsUrl.append(host);

        if (port != 443) {
            httpsUrl.append(":").append(port);
        }

        httpsUrl.append(path);

        if (query != null && !query.isEmpty()) {
            httpsUrl.append("?").append(query);
        }

        return httpsUrl.toString();
    }
}
