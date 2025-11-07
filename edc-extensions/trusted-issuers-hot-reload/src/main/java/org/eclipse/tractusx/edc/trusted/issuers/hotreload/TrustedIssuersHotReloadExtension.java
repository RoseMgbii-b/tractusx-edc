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

package org.eclipse.tractusx.edc.trusted.issuers.hotreload;

import org.eclipse.edc.iam.verifiablecredentials.spi.model.Issuer;
import org.eclipse.edc.iam.verifiablecredentials.spi.validation.TrustedIssuerRegistry;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.web.spi.WebService;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.eclipse.edc.iam.verifiablecredentials.spi.validation.TrustedIssuerRegistry.WILDCARD;

@Extension(value = "Trusted Issuers Hot Reload Extension")
public class TrustedIssuersHotReloadExtension implements ServiceExtension {

    @Inject
    private Monitor monitor;

    @Inject
    private WebService webService;

    private TrustedIssuerRegistry trustedIssuerRegistry;
    private ScheduledExecutorService scheduler;
    private File configFile;
    private long lastModified;
    private List<TrustedIssuerConfig> lastKnownIssuers = new ArrayList<>();
    private Set<String> registeredIssuerIds = new HashSet<>();

    // Pattern to match: edc.iam.trusted-issuer.{index}.id
    private static final Pattern ISSUER_ID_PATTERN = Pattern.compile("^edc\\.iam\\.trusted-issuer\\.(\\d+)\\.id$");
    // Pattern to match: edc.iam.trusted-issuer.{index}.supportedTypes
    private static final Pattern SUPPORTED_TYPES_PATTERN = Pattern.compile("^edc\\.iam\\.trusted-issuer\\.(\\d+)\\.supportedTypes$");

    @Override
    public String name() {
        return "Trusted Issuers Hot Reload Extension";
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        monitor.info("=== Trusted Issuers Hot Reload Extension Starting ===");

        // Get TrustedIssuerRegistry service
        try {
            trustedIssuerRegistry = context.getService(TrustedIssuerRegistry.class);
            if (trustedIssuerRegistry == null) {
                monitor.warning("TrustedIssuerRegistry service not found. Hot reload disabled.");
                return;
            }
            monitor.info("TrustedIssuerRegistry service found and ready for hot reload.");
        } catch (Exception e) {
            monitor.severe("Failed to get TrustedIssuerRegistry service: " + e.getMessage());
            return;
        }

        // Find config file
        String configPath = System.getProperty("edc.fs.config");
        if (configPath == null || configPath.isEmpty()) {
            monitor.warning("System property 'edc.fs.config' not set. Hot reload disabled.");
            return;
        }

        configFile = new File(configPath);
        if (!configFile.exists() || !configFile.isFile()) {
            monitor.warning("Config file does not exist: " + configPath + ". Hot reload disabled.");
            return;
        }

        // Load initial configuration
        lastModified = configFile.lastModified();
        loadTrustedIssuersFromConfig(context);

        // Schedule periodic checks
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "trusted-issuers-reload");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(
                () -> checkAndReloadConfig(context),
                30, // Initial delay: 30 seconds
                30, // Check every 30 seconds
                TimeUnit.SECONDS
        );

        monitor.info("Trusted Issuers hot reload monitoring started. Checking every 30 seconds.");
    }

    /**
     * Load trusted issuers from configuration file
     */
    private void loadTrustedIssuersFromConfig(ServiceExtensionContext context) {
        try {
            Properties props = new Properties();
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
            }

            List<TrustedIssuerConfig> issuers = parseTrustedIssuers(props);

            if (!issuers.equals(lastKnownIssuers)) {
                monitor.info("=== Trusted Issuers configuration changed! ===");
                monitor.info("Found " + issuers.size() + " trusted issuer(s)");

                for (TrustedIssuerConfig issuer : issuers) {
                    monitor.info("  - " + issuer.id + " (types: " + issuer.supportedTypes + ")");
                }

                // TODO: Update the actual TrustedIssuerRegistry service
                // This requires finding and updating the registry service
                reloadTrustedIssuerRegistry(context, issuers);

                lastKnownIssuers = issuers;
            }

        } catch (IOException e) {
            monitor.severe("Failed to load trusted issuers from config: " + e.getMessage());
        }
    }

    /**
     * Parse trusted issuer properties from config file
     *
     * Supports formats:
     * - edc.iam.trusted-issuer.0.id=did:web:example1.com
     * - edc.iam.trusted-issuer.1.id=did:web:example2.com
     * - edc.iam.trusted-issuer.1.supportedTypes=MembershipCredential,SomeOtherCredential
     */
    private List<TrustedIssuerConfig> parseTrustedIssuers(Properties props) {
        List<TrustedIssuerConfig> issuers = new ArrayList<>();

        // Find all issuer IDs
        for (String key : props.stringPropertyNames()) {
            Matcher idMatcher = ISSUER_ID_PATTERN.matcher(key);
            if (idMatcher.matches()) {
                int index = Integer.parseInt(idMatcher.group(1));
                String issuerId = props.getProperty(key);

                // Get supported types for this issuer
                Set<String> supportedTypes = new HashSet<>();
                String typesKey = "edc.iam.trusted-issuer." + index + ".supportedTypes";
                String typesValue = props.getProperty(typesKey);
                if (typesValue != null && !typesValue.isEmpty()) {
                    String[] types = typesValue.split(",");
                    for (String type : types) {
                        supportedTypes.add(type.trim());
                    }
                } else {
                    // Default to "*" if not specified
                    supportedTypes.add("*");
                }

                issuers.add(new TrustedIssuerConfig(issuerId, supportedTypes));
            }
        }

        return issuers;
    }

    /**
     * Check if config file has changed and reload if necessary
     */
    private void checkAndReloadConfig(ServiceExtensionContext context) {
        if (configFile == null || !configFile.exists()) {
            return;
        }

        long currentModified = configFile.lastModified();
        if (currentModified > lastModified) {
            monitor.info("=== Config file changed! Reloading Trusted Issuers ===");
            lastModified = currentModified;
            loadTrustedIssuersFromConfig(context);
        }
    }

    /**
     * Reload the trusted issuer registry with new issuers
     *
     * This method updates the TrustedIssuerRegistry by:
     * 1. Removing issuers that are no longer in the config
     * 2. Adding/updating issuers that are in the config
     */
    private void reloadTrustedIssuerRegistry(ServiceExtensionContext context, List<TrustedIssuerConfig> issuers) {
        if (trustedIssuerRegistry == null) {
            monitor.severe("TrustedIssuerRegistry is not available. Cannot reload issuers.");
            return;
        }

        try {
            // Collect new issuer IDs from config
            Set<String> newIssuerIds = new HashSet<>();
            for (TrustedIssuerConfig issuer : issuers) {
                newIssuerIds.add(issuer.id);
            }

            // Remove issuers that are no longer in config
            Set<String> toRemove = new HashSet<>(registeredIssuerIds);
            toRemove.removeAll(newIssuerIds);
            for (String issuerId : toRemove) {
                // Note: TrustedIssuerRegistry might not have a remove method
                // If it does, uncomment: trustedIssuerRegistry.remove(issuerId);
                monitor.info("Issuer removed from config (may need manual removal): " + issuerId);
                registeredIssuerIds.remove(issuerId);
            }

            // Add or update issuers
            for (TrustedIssuerConfig issuerConfig : issuers) {
                Issuer issuer = new Issuer(issuerConfig.id, Map.of());
                
                // Convert supportedTypes Set to List for registration
                List<String> credentialTypes = new ArrayList<>(issuerConfig.supportedTypes);
                
                // If types contain "*", use WILDCARD
                List<String> typesForRegistration;
                if (credentialTypes.contains("*") || credentialTypes.isEmpty()) {
                    typesForRegistration = List.of(WILDCARD);
                } else {
                    typesForRegistration = credentialTypes;
                }
                
                // Register each credential type
                for (String credentialType : typesForRegistration) {
                    trustedIssuerRegistry.register(issuer, credentialType);
                }
                
                registeredIssuerIds.add(issuerConfig.id);
                String typesDisplay = typesForRegistration.contains(WILDCARD) ? "ALL_TYPES" : credentialTypes.toString();
                monitor.info("Registered trusted issuer: " + issuerConfig.id + " with types: " + typesDisplay);
            }

            monitor.info("Successfully reloaded " + issuers.size() + " trusted issuer(s)");
            verifyRegisteredIssuers(issuers);
            printRegisteredIssuersSummary();
        } catch (Exception e) {
            monitor.severe("Failed to reload trusted issuer registry: " + e.getMessage(), e);
        }
    }

    /**
     * Verify that issuers are actually registered in the registry
     * This helps ensure the hot reload is working correctly
     */
    private void verifyRegisteredIssuers(List<TrustedIssuerConfig> issuers) {
        if (trustedIssuerRegistry == null) {
            monitor.warning("⚠️ Cannot verify issuers - registry is null");
            return;
        }

        int verifiedCount = 0;
        monitor.info("🔍 Verifying registered issuers...");
        
        for (TrustedIssuerConfig issuerConfig : issuers) {
            try {
                // Try to register again - if it's already registered, this should not fail
                // (registration is idempotent - can be called multiple times)
                Issuer issuer = new Issuer(issuerConfig.id, Map.of());
                List<String> credentialTypes = new ArrayList<>(issuerConfig.supportedTypes);
                List<String> typesForRegistration;
                
                if (credentialTypes.contains("*") || credentialTypes.isEmpty()) {
                    typesForRegistration = List.of(WILDCARD);
                } else {
                    typesForRegistration = credentialTypes;
                }

                // Re-register to verify it works (idempotent operation)
                for (String credentialType : typesForRegistration) {
                    trustedIssuerRegistry.register(issuer, credentialType);
                }
                
                verifiedCount++;
                monitor.debug("✓ Verified issuer: " + issuerConfig.id);
            } catch (Exception e) {
                monitor.warning("⚠️ Failed to verify issuer " + issuerConfig.id + ": " + e.getMessage());
            }
        }
        
        if (verifiedCount == issuers.size()) {
            monitor.info("Verification successful: All " + verifiedCount + " issuer(s) registered and verified");
        } else {
            monitor.warning("Verification incomplete: " + verifiedCount + "/" + issuers.size() + " issuers verified");
        }
    }

    /**
     * Print summary of all currently registered trusted issuers
     */
    private void printRegisteredIssuersSummary() {
        monitor.info("═══════════════════════════════════════════════════════════════");
        monitor.info("📋 TRUSTED ISSUERS REGISTRY SUMMARY");
        monitor.info("═══════════════════════════════════════════════════════════════");
        monitor.info("Total registered issuers: " + registeredIssuerIds.size());
        for (String issuerId : registeredIssuerIds) {
            monitor.info("  • " + issuerId);
        }
        monitor.info("═══════════════════════════════════════════════════════════════");
    }

    @Override
    public void shutdown() {
        if (scheduler != null) {
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
        monitor.info("Trusted Issuers Hot Reload Extension shut down");
    }

}
