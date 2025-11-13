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

package org.eclipse.tractusx.edc.usermanagement;

import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.ApiContext;

/**
 * Extension for managing users in Keycloak via Management API
 */
@Extension(value = "User Management Extension", categories = { "management", "user-management", "keycloak" })
public class UserManagementExtension implements ServiceExtension {

    @Setting(value = "Keycloak server URL (e.g., https://keycloak.example.com)", required = true)
    public static final String KEYCLOAK_SERVER_URL = "edc.user.management.keycloak.server.url";

    @Setting(value = "Keycloak realm name", required = true)
    public static final String KEYCLOAK_REALM = "edc.user.management.keycloak.realm";

    @Setting(value = "Keycloak admin client ID (must have admin privileges)", required = true)
    public static final String KEYCLOAK_CLIENT_ID = "edc.user.management.keycloak.client.id";

    @Setting(value = "Keycloak admin client secret", required = true)
    public static final String KEYCLOAK_CLIENT_SECRET = "edc.user.management.keycloak.client.secret";

    @Inject
    private Monitor monitor;

    @Inject
    private WebService webService;

    @Override
    public String name() {
        return "User Management Extension";
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        monitor.info("=== User Management Extension Starting ===");

        // Get configuration (try explicit settings first, then fallback to OAuth2 provider config)
        String serverUrl = context.getSetting(KEYCLOAK_SERVER_URL, null);
        String realmName = context.getSetting(KEYCLOAK_REALM, null);
        String clientId = context.getSetting(KEYCLOAK_CLIENT_ID, null);
        String clientSecret = context.getSetting(KEYCLOAK_CLIENT_SECRET, null);

        // Fallback: Extract from OAuth2 provider issuer if not explicitly set
        if (serverUrl == null || realmName == null) {
            String issuer = context.getSetting("edc.oauth.provider.issuer", null);
            if (issuer != null && issuer.contains("/realms/")) {
                // Extract server URL and realm from issuer: https://keycloak.../realms/{realm}
                int realmsIndex = issuer.indexOf("/realms/");
                if (realmsIndex > 0) {
                    if (serverUrl == null) {
                        serverUrl = issuer.substring(0, realmsIndex);
                        monitor.info("Using Keycloak server URL from OAuth2 provider issuer: " + serverUrl);
                    }
                    if (realmName == null) {
                        realmName = issuer.substring(realmsIndex + "/realms/".length());
                        monitor.info("Using Keycloak realm from OAuth2 provider issuer: " + realmName);
                    }
                }
            }
        }

        // Validate configuration
        if (serverUrl == null || serverUrl.isEmpty()) {
            monitor.severe("Keycloak server URL not configured. Set: " + KEYCLOAK_SERVER_URL + 
                    " or configure edc.oauth.provider.issuer");
            throw new IllegalStateException("Keycloak server URL is required");
        }

        if (realmName == null || realmName.isEmpty()) {
            monitor.severe("Keycloak realm not configured. Set: " + KEYCLOAK_REALM + 
                    " or configure edc.oauth.provider.issuer");
            throw new IllegalStateException("Keycloak realm is required");
        }

        if (clientId == null || clientId.isEmpty()) {
            monitor.severe("Keycloak client ID not configured. Set: " + KEYCLOAK_CLIENT_ID);
            throw new IllegalStateException("Keycloak client ID is required");
        }

        if (clientSecret == null || clientSecret.isEmpty()) {
            monitor.severe("Keycloak client secret not configured. Set: " + KEYCLOAK_CLIENT_SECRET);
            throw new IllegalStateException("Keycloak client secret is required");
        }

        // Create Keycloak user service
        KeycloakUserService userService = new KeycloakUserService(
                serverUrl,
                realmName,
                clientId,
                clientSecret,
                monitor
        );

        // Register API controller
        UserManagementApiController controller = new UserManagementApiController(userService, monitor);
        webService.registerResource(ApiContext.MANAGEMENT, controller);

        monitor.info("User Management Extension started successfully");
        monitor.info("Keycloak Server: " + serverUrl);
        monitor.info("Keycloak Realm: " + realmName);
        monitor.info("Management API endpoints registered at: /api/management/v3/users");
    }
}

