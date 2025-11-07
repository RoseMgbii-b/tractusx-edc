/********************************************************************************
 * Copyright (c) 2025 Your Company
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

import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.ApiContext;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Custom Extension: OAuth2 Configuration Hot Reload
 *
 * PURPOSE:
 * This extension provides a CUSTOM OAuth2/JWT validation implementation that REPLACES
 * the native EDC DAC (Delegated Authentication Client) and supports hot reload of
 * OAuth2 configuration (JWKS URL, audience, issuer) without restarting the connector.
 *
 * HOW IT WORKS:
 * 1. On startup, reads initial OAuth2 config from properties file
 * 2. Creates and registers a custom JWT validation filter (HotReloadableJwtValidationFilter)
 *    that validates JWT tokens using JWKS
 * 3. Periodically checks if config has changed (every 30 seconds)
 * 4. If changed, hot-reloads the JWT validator with new JWKS URL/audience/issuer
 *
 * KEY DIFFERENCE FROM NATIVE DAC:
 * - Native DAC: Cannot be reloaded at runtime (requires restart)
 * - Custom Implementation: Supports hot reload of configuration
 *
 * CONFIG PROPERTIES SOURCE:
 * - Reads from: configuration.properties file (specified via -Dedc.fs.config)
 * - Properties: web.http.management.auth.dac.key.url, web.http.management.auth.dac.audience
 * - Optional: edc.oauth.provider.issuer (or extracted from JWKS URL)
 *
 * IMPORTANT:
 * To use this custom implementation instead of native DAC, you may need to disable
 * the native DAC extension or ensure this extension runs with higher priority.
 */
@Extension(value = "OAuth Hot Reload Extension", categories = { "security", "oauth2" })
public class OauthHotReloadExtension implements ServiceExtension {

    @Inject
    private Monitor monitor;

    @Inject
    private WebService webService;

    private ScheduledExecutorService scheduler;
    private volatile String lastJwksUrl;
    private volatile String lastAudience;
    private volatile String lastIssuer;
    private volatile String configFilePath;
    private volatile long lastConfigFileModified;
    private HotReloadableJwtValidationFilter jwtValidationFilter;

    @Override
    public String name() {
        return "OAuth Hot Reload Extension";
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        monitor.info("=== OAuth Hot Reload Extension Starting ===");

        // Step 1: Find the config file location
        // EDC loads config from system property: edc.fs.config
        configFilePath = System.getProperty("edc.fs.config");
        if (configFilePath == null || configFilePath.isEmpty()) {
            monitor.warning("OAuth Hot Reload: 'edc.fs.config' system property not set. " +
                    "Hot reload will monitor ServiceExtensionContext instead.");
            // Fallback: monitor via ServiceExtensionContext (requires restart to pick up changes)
            monitorConfigViaContext(context);
            return;
        }

        // Step 2: Load initial config
        loadConfigFromFile();

        // Step 3: Create and register custom JWT validation filter (replaces native DAC)
        jwtValidationFilter = new HotReloadableJwtValidationFilter(context.getMonitor());
        
        // Initialize filter with current configuration
        if (lastJwksUrl != null && !lastJwksUrl.isEmpty()) {
            jwtValidationFilter.updateConfiguration(lastJwksUrl, lastAudience, lastIssuer);
            monitor.info("Custom JWT validation filter initialized with JWKS URL: " + lastJwksUrl);
        } else {
            monitor.warning("JWKS URL not configured. JWT validation filter will reject all requests until configured.");
        }
        
        // Register JWT validation filter FIRST (high priority - runs before other filters)
        webService.registerResource(ApiContext.MANAGEMENT, jwtValidationFilter);
        monitor.info("=== Custom JWT validation filter registered for Management API context ===");
        monitor.info("This filter REPLACES the native EDC DAC implementation and supports hot reload");

        // Step 4: Register RBAC filter for Management API (runs after JWT validation)
        RoleBasedAccessFilter rbacFilter = new RoleBasedAccessFilter(context.getMonitor());
        webService.registerResource(ApiContext.MANAGEMENT, rbacFilter);
        monitor.info("=== RBAC filter registered for Management API context ===");
        monitor.info("Filter will intercept all requests to /api/management/*");

        // Step 5: Start periodic monitoring (every 30 seconds)
        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleWithFixedDelay(
                () -> checkAndReloadConfig(),
                30, 30, TimeUnit.SECONDS
        );

        monitor.info("OAuth Hot Reload Extension started - monitoring config file every 30 seconds");
        monitor.info("Config file path: " + configFilePath);


        // Register HTTPS enforcement filter for Management API
        boolean allowRedirect = context.getSetting("web.http.management.https.redirect", false);
        int httpsPort = context.getSetting("web.http.management.https.port", 8443);

        HttpsEnforcementFilter httpsFilter = new HttpsEnforcementFilter(
                context.getMonitor(),
                allowRedirect,  // Set to true to redirect, false to reject
                httpsPort
        );

        //        webService.registerResource(ApiContext.MANAGEMENT, httpsFilter);
        //        monitor.info("HTTPS enforcement filter registered for Management API");

    }

    /**
     * ALTERNATIVE: If no file path is provided, monitor via ServiceExtensionContext
     * (Note: This requires EDC to be restarted to pick up changes, so less useful)
     */
    private void monitorConfigViaContext(ServiceExtensionContext context) {
        // Try multiple possible property names for compatibility
        String jwksUrl = getJwksUrlFromContext(context);
        String audience = getAudienceFromContext(context);

        if (jwksUrl != null && !jwksUrl.isEmpty()) {
            lastJwksUrl = jwksUrl;
            lastAudience = audience;
            monitor.info("Initial OAuth2 config loaded from context - JWKS URL: " + jwksUrl);
            // Note: This won't detect changes without restart
        } else {
            monitor.warning("OAuth2 config not found. Make sure OAuth2 JWKS URL property is set.");
        }
    }

    /**
     * Gets JWKS URL from context using OAuth2 provider properties
     */
    private String getJwksUrlFromContext(ServiceExtensionContext context) {
        return context.getSetting("edc.oauth.provider.jwks.url", null);
    }

    /**
     * Gets audience from context using OAuth2 provider properties
     */
    private String getAudienceFromContext(ServiceExtensionContext context) {
        return context.getSetting("edc.oauth.provider.audience", null);
    }

    /**
     * Gets issuer from context, trying multiple property name variants
     */
    private String getIssuerFromContext(ServiceExtensionContext context) {
        String issuer = context.getSetting("edc.oauth.provider.issuer", null);
        if (issuer != null && !issuer.isEmpty()) {
            return issuer;
        }
        // Try to extract issuer from JWKS URL (common pattern: .../realms/{realm})
        String jwksUrl = getJwksUrlFromContext(context);
        if (jwksUrl != null && jwksUrl.contains("/realms/")) {
            // Extract issuer from JWKS URL pattern: .../realms/{realm}/protocol/...
            int realmsIndex = jwksUrl.indexOf("/realms/");
            if (realmsIndex > 0) {
                String baseUrl = jwksUrl.substring(0, realmsIndex);
                int protocolIndex = jwksUrl.indexOf("/protocol/", realmsIndex);
                if (protocolIndex > 0) {
                    String realm = jwksUrl.substring(realmsIndex + "/realms/".length(), protocolIndex);
                    return baseUrl + "/realms/" + realm;
                }
            }
        }
        return null;
    }

    /**
     * Loads OAuth2 config from the properties file
     * Supports multiple property name variants for compatibility
     */
    private void loadConfigFromFile() {
        try {
            File configFile = new File(configFilePath);
            if (!configFile.exists()) {
                monitor.warning("Config file does not exist: " + configFilePath);
                return;
            }

            Properties props = new Properties();
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
            }

            // Use OAuth2 provider properties as primary source
            String jwksUrl = props.getProperty("edc.oauth.provider.jwks.url");
            String audience = props.getProperty("edc.oauth.provider.audience");

            String issuer = props.getProperty("edc.oauth.provider.issuer");
            if (issuer == null || issuer.trim().isEmpty()) {
                // Try to extract issuer from JWKS URL (common pattern: .../realms/{realm})
                if (jwksUrl != null && jwksUrl.contains("/realms/")) {
                    int realmsIndex = jwksUrl.indexOf("/realms/");
                    if (realmsIndex > 0) {
                        String baseUrl = jwksUrl.substring(0, realmsIndex);
                        int protocolIndex = jwksUrl.indexOf("/protocol/", realmsIndex);
                        if (protocolIndex > 0) {
                            String realm = jwksUrl.substring(realmsIndex + "/realms/".length(), protocolIndex);
                            issuer = baseUrl + "/realms/" + realm;
                        }
                    }
                }
            }

            if (jwksUrl != null && !jwksUrl.trim().isEmpty()) {
                lastJwksUrl = jwksUrl.trim();
                lastAudience = (audience != null && !audience.trim().isEmpty()) ? audience.trim() : null;
                lastIssuer = (issuer != null && !issuer.trim().isEmpty()) ? issuer.trim() : null;
                lastConfigFileModified = configFile.lastModified();
                monitor.info("OAuth2 config loaded - JWKS URL: " + lastJwksUrl +
                        (lastAudience != null ? ", Audience: " + lastAudience : "") +
                        (lastIssuer != null ? ", Issuer: " + lastIssuer : ""));
            } else {
                monitor.warning("OAuth2 config property not found in config file. " +
                        "Required: 'edc.oauth.provider.jwks.url'");
            }

        } catch (IOException e) {
            monitor.severe("Failed to load OAuth2 config from file: " + e.getMessage(), e);
        }
    }

    /**
     * Checks if config file has changed and reloads if needed
     */
    private void checkAndReloadConfig() {
        try {
            File configFile = new File(configFilePath);
            if (!configFile.exists()) {
                return;
            }

            long currentModified = configFile.lastModified();
            if (currentModified > lastConfigFileModified) {
                monitor.info("=== Config file changed! Reloading OAuth2 configuration ===");

                // Store previous values
                String previousJwksUrl = lastJwksUrl;
                String previousAudience = lastAudience;
                String previousIssuer = lastIssuer;
                
                // Reload config from file
                loadConfigFromFile();

                // Check if any OAuth2 config actually changed
                boolean configChanged = false;
                if (lastJwksUrl != null) {
                    if (previousJwksUrl == null || !lastJwksUrl.equals(previousJwksUrl)) {
                        monitor.info("JWKS URL changed from [" +
                                (previousJwksUrl != null ? previousJwksUrl : "null") +
                                "] to [" + lastJwksUrl + "]");
                        configChanged = true;
                    }
                    
                    // Check audience change
                    if (!java.util.Objects.equals(previousAudience, lastAudience)) {
                        monitor.info("Audience changed from [" +
                                (previousAudience != null ? previousAudience : "null") +
                                "] to [" + (lastAudience != null ? lastAudience : "null") + "]");
                        configChanged = true;
                    }
                    
                    // Check issuer change
                    if (!java.util.Objects.equals(previousIssuer, lastIssuer)) {
                        monitor.info("Issuer changed from [" +
                                (previousIssuer != null ? previousIssuer : "null") +
                                "] to [" + (lastIssuer != null ? lastIssuer : "null") + "]");
                        configChanged = true;
                    }
                    
                    if (configChanged) {
                        reloadDacService();
                    } else {
                        monitor.info("Config file changed but OAuth2 configuration unchanged - no action needed");
                    }
                } else {
                    monitor.warning("Config file changed but JWKS URL is still not configured");
                }
            }

        } catch (Exception e) {
            monitor.warning("Error checking OAuth2 config: " + e.getMessage(), e);
        }
    }

    /**
     * Reloads the custom JWT validation filter with new configuration
     * This method is called when the config file changes and JWKS URL/audience/issuer are updated
     */
    private void reloadDacService() {
        monitor.info("=== Reloading custom JWT validation filter with new configuration ===");

        if (jwtValidationFilter == null) {
            monitor.warning("JWT validation filter not initialized. Cannot reload.");
            return;
        }

        if (lastJwksUrl == null || lastJwksUrl.isEmpty()) {
            monitor.warning("JWKS URL is not configured. Cannot reload JWT validator.");
            return;
        }

        try {
            // Update the filter with new configuration (thread-safe)
            jwtValidationFilter.updateConfiguration(lastJwksUrl, lastAudience, lastIssuer);
            monitor.info("✅ JWT validation filter successfully reloaded with new configuration");
            monitor.info("   JWKS URL: " + lastJwksUrl +
                    (lastAudience != null ? ", Audience: " + lastAudience : "") +
                    (lastIssuer != null ? ", Issuer: " + lastIssuer : ""));
        } catch (Exception e) {
            monitor.severe("Failed to reload JWT validation filter: " + e.getMessage(), e);
        }
    }

    @Override
    public void shutdown() {
        if (scheduler != null) {
            monitor.info("Shutting down OAuth2 Hot Reload Extension");
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}