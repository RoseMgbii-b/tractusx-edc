/********************************************************************************
 * Custom Extension: OAuth2 Configuration Hot Reload
 * 
 * PURPOSE:
 * This extension monitors OAuth2 configuration changes (JWKS URL, audience)
 * and reloads the DAC (Delegated Authentication Client) service without restarting
 * the entire connector.
 * 
 * HOW IT WORKS:
 * 1. On startup, reads initial OAuth2 config from properties file
 * 2. Periodically checks if config has changed (every 30 seconds)
 * 3. If changed, reloads the JWT validator with new JWKS URL
 * 
 * CONFIG PROPERTIES SOURCE:
 * - Reads from: configuration.properties file (specified via -Dedc.fs.config)
 * - Or from: System properties / Environment variables
 * - Currently: File-based (you can extend to DB/config server)
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

@Extension(value = "OAuth2 Hot Reload Extension", categories = { "security", "oauth2" })
public class OAuth2HotReloadExtension implements ServiceExtension {

    @Inject
    private Monitor monitor;

    @Inject
    private WebService webService;

    private ScheduledExecutorService scheduler;
    private volatile String lastJwksUrl;
    private volatile String lastAudience;
    private volatile String configFilePath;
    private volatile long lastConfigFileModified;

    @Override
    public String name() {
        return "OAuth2 Hot Reload Extension";
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        monitor.info("=== OAuth2 Hot Reload Extension Starting ===");
        
        // Step 1: Find the config file location
        // EDC loads config from system property: edc.fs.config
        configFilePath = System.getProperty("edc.fs.config");
        if (configFilePath == null || configFilePath.isEmpty()) {
            monitor.warning("OAuth2 Hot Reload: 'edc.fs.config' system property not set. " +
                    "Hot reload will monitor ServiceExtensionContext instead.");
            // Fallback: monitor via ServiceExtensionContext (requires restart to pick up changes)
            monitorConfigViaContext(context);
            return;
        }

        // Step 2: Load initial config
        loadConfigFromFile();

        // Step 3: Start periodic monitoring (every 30 seconds)
        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleWithFixedDelay(
            () -> checkAndReloadConfig(),
            30, 30, TimeUnit.SECONDS
        );

        monitor.info("OAuth2 Hot Reload Extension started - monitoring config file every 30 seconds");
        monitor.info("Config file path: " + configFilePath);

        // Register RBAC filter for Management API
        RoleBasedAccessFilter rbacFilter = new RoleBasedAccessFilter(context.getMonitor());
        webService.registerResource(ApiContext.MANAGEMENT, rbacFilter);
        monitor.info("=== RBAC filter registered for Management API context ===");
        monitor.info("Filter will intercept all requests to /api/management/*");

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
     * Gets JWKS URL from context, trying multiple property name variants
     */
    private String getJwksUrlFromContext(ServiceExtensionContext context) {
        // Try the delegated auth property first (correct one for Management API)
        String url = context.getSetting("web.http.management.auth.dac.key.url", null);
        if (url != null && !url.isEmpty()) {
            return url;
        }
        // Try alternative property names
        url = context.getSetting("edc.oauth.jwk.url", null);
        if (url != null && !url.isEmpty()) {
            return url;
        }
        return null;
    }

    /**
     * Gets audience from context, trying multiple property name variants
     */
    private String getAudienceFromContext(ServiceExtensionContext context) {
        String audience = context.getSetting("web.http.management.auth.dac.audience", null);
        if (audience != null && !audience.isEmpty()) {
            return audience;
        }
        audience = context.getSetting("edc.oauth.audience", null);
        if (audience != null && !audience.isEmpty()) {
            return audience;
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

            // Try multiple property name variants (in order of preference)
            String jwksUrl = props.getProperty("web.http.management.auth.dac.key.url");
            if (jwksUrl == null || jwksUrl.trim().isEmpty()) {
                // Fallback to alternative property name
                jwksUrl = props.getProperty("edc.oauth.jwk.url");
            }
            
            String audience = props.getProperty("web.http.management.auth.dac.audience");
            if (audience == null || audience.trim().isEmpty()) {
                // Fallback to alternative property name
                audience = props.getProperty("edc.oauth.audience");
            }

            if (jwksUrl != null && !jwksUrl.trim().isEmpty()) {
                lastJwksUrl = jwksUrl.trim();
                lastAudience = (audience != null && !audience.trim().isEmpty()) ? audience.trim() : null;
                lastConfigFileModified = configFile.lastModified();
                monitor.info("OAuth2 config loaded - JWKS URL: " + lastJwksUrl + 
                           (lastAudience != null ? ", Audience: " + lastAudience : ""));
            } else {
                monitor.warning("OAuth2 config property not found in config file. " +
                        "Tried: 'web.http.management.auth.dac.key.url' and 'edc.oauth.jwk.url'");
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
                
                String previousJwksUrl = lastJwksUrl;
                loadConfigFromFile();

                // Check if JWKS URL actually changed (with null safety)
                if (lastJwksUrl != null) {
                    if (previousJwksUrl == null || !lastJwksUrl.equals(previousJwksUrl)) {
                        monitor.info("JWKS URL changed from [" + 
                                   (previousJwksUrl != null ? previousJwksUrl : "null") + 
                                   "] to [" + lastJwksUrl + "]");
                        reloadDacService();
                    } else {
                        monitor.info("Config file changed but JWKS URL unchanged - no action needed");
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
     * THIS IS THE KEY METHOD: Reloads the DAC (Delegated Authentication Client) service
     * 
     * CHALLENGE: The DAC service is in Eclipse EDC's auth-delegated module.
     * We need to access it to reload the JWT validator with the new JWKS URL.
     * 
     * APPROACH OPTIONS:
     * 1. Use ServiceExtensionContext to find the service (if it's registered)
     * 2. Use reflection to access internal services (risky, may break on EDC updates)
     * 3. Extend the Eclipse EDC DAC extension to expose a reload method (best, but requires EDC changes)
     * 
     * For now, we'll log what needs to be done and provide a placeholder.
     */
    private void reloadDacService() {
        monitor.info("=== Attempting to reload DAC service with new JWKS URL ===");
        
        // OPTION 1: Try to find DAC service via context (if it's accessible)
        // ServiceExtensionContext context = ...; // We'd need to store this
        // AuthenticationService authService = context.getService(AuthenticationService.class);
        // if (authService instanceof DelegatedAuthenticationService) {
        //     ((DelegatedAuthenticationService) authService).reloadJwksUrl(lastJwksUrl);
        // }
        
        // OPTION 2: Use reflection to access internal JWT validator
        // This is fragile but might work:
        try {
            // The actual implementation is in: org.eclipse.edc.auth.delegated.*
            // We'd need to:
            // 1. Get the WebService instance
            // 2. Find the JWT validation filter
            // 3. Update its JWKS URL

            monitor.warning("DAC service reload not yet fully implemented. " +
                    "The DAC service from Eclipse EDC does not expose a reload method. " +
                    "Consider extending the Eclipse EDC auth-delegated extension or " +
                    "restart the connector for changes to take effect.");
            
            // TODO: Implement actual reload logic here
            // This would require either:
            // - Modifying Eclipse EDC's auth-delegated extension to expose reload()
            // - Or using reflection to access and update the internal JWT validator
            
        } catch (Exception e) {
            monitor.severe("Failed to reload DAC service: " + e.getMessage(), e);
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

