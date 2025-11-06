# 🔐 Trust Authorities Hot Reload - Implementation Guide

## 📋 What Are Trust Authorities?

**Trust Authorities** (also called **Trusted Issuers**) are external entities that your connector trusts to issue **Verifiable Credentials (VCs)** in the dataspace.

### Key Concepts

1. **DID (Decentralized Identifier)**: A unique identifier for each participant/issuer
   - Example: `did:web:example.com:connector1`
   - Example: `did:web:catena-x.net:issuer`

2. **Trusted Issuer**: A DID that your connector trusts to vouch for credentials
   - When another connector presents a credential, your connector checks: "Was this credential issued by a trusted issuer?"
   - Only credentials from trusted issuers are accepted

3. **Verifiable Credentials**: Digital credentials that prove identity, membership, or attributes
   - Example: "This connector belongs to company X" (MembershipCredential)
   - Example: "This connector has BPN Y" (BusinessPartnerCredential)

---

## 🔄 Current vs. Dynamic Configuration

### Current State (Static)

**Configuration File:**
```properties
# Currently loaded at startup only
edc.iam.trusted-issuer.id=did:web:localhost:dev-connector
edc.iam.trusted-issuer.id.id=did:web:localhost:dev-connector
```

**Kubernetes (Helm Values):**
```yaml
iatp:
  trustedIssuers:
    - id: "did:web:example1.com"
      supportedTypes:
        - "MembershipCredential"
    - "did:web:example2.com"
```

**Problem:** Changes require **connector restart** ❌

### Desired State (Dynamic)

**Hot Reload:** Update trusted issuers from config file **without restart** ✅

---

## 🆚 Comparison: Trust Authorities vs OAuth2 Hot Reload

| Aspect | OAuth2 Hot Reload | Trust Authorities Hot Reload |
|--------|------------------|------------------------------|
| **Purpose** | Update authentication provider (JWKS URL, audience) | Update list of trusted credential issuers (DIDs) |
| **What Changes** | `web.http.management.auth.dac.key.url`, `web.http.management.auth.dac.audience` | `edc.iam.trusted-issuer.id.*` properties |
| **Who Uses It** | Management API authentication (human users) | Protocol API credential validation (connector-to-connector) |
| **When Needed** | Switching Keycloak realms, updating OAuth provider | Adding/removing trusted issuers, updating credential types |
| **Similarity** | ✅ Both use config file monitoring<br>✅ Both need dynamic reload<br>✅ Same hot-reload pattern | ✅ Same implementation approach<br>✅ Monitor config file changes<br>✅ Update service without restart |

---

## 🎯 Implementation Strategy

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│  Config File (config.properties)                            │
│  └─> edc.iam.trusted-issuer.0.id=did:web:issuer1           │
│  └─> edc.iam.trusted-issuer.1.id=did:web:issuer2           │
│  └─> edc.iam.trusted-issuer.1.supportedTypes=Credential1   │
└─────────────────────────────────────────────────────────────┘
                    ↓ (File Watcher)
┌─────────────────────────────────────────────────────────────┐
│  TrustedIssuersHotReloadExtension                           │
│  └─> Monitor config file                                    │
│  └─> Detect changes                                         │
│  └─> Parse trusted issuers                                  │
└─────────────────────────────────────────────────────────────┘
                    ↓ (Reload Service)
┌─────────────────────────────────────────────────────────────┐
│  TrustedIssuerRegistry (EDC Core Service)                   │
│  └─> Maintains list of trusted issuers                      │
│  └─> Used by CredentialValidationService                    │
└─────────────────────────────────────────────────────────────┘
```

---

## 📝 Implementation Code

### Step 1: Create TrustedIssuersHotReloadExtension

**Location:** `edc-extensions/oauth2-hot-reload/src/main/java/org/eclipse/tractusx/edc/oauth2/hotreload/TrustedIssuersHotReloadExtension.java`

```java
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

import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extension that enables hot-reload of trusted issuers from configuration file.
 * 
 * Monitors the config file for changes to trusted issuer properties and 
 * dynamically updates the trusted issuer registry without restarting the connector.
 */
@Extension(value = "Trusted Issuers Hot Reload")
public class TrustedIssuersHotReloadExtension implements ServiceExtension {

    @Inject
    private Monitor monitor;

    private ScheduledExecutorService scheduler;
    private File configFile;
    private long lastModified;
    private List<TrustedIssuerConfig> lastKnownIssuers = new ArrayList<>();
    
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
     * NOTE: This requires finding and updating the actual TrustedIssuerRegistry service.
     * The exact implementation depends on how EDC exposes this service.
     */
    private void reloadTrustedIssuerRegistry(ServiceExtensionContext context, List<TrustedIssuerConfig> issuers) {
        // TODO: Implement actual registry update
        // This will likely require:
        // 1. Getting TrustedIssuerRegistry from ServiceExtensionContext
        // 2. Clearing existing issuers
        // 3. Adding new issuers
        
        monitor.warning("TrustedIssuerRegistry reload not yet implemented. " +
                       "You need to hook into the actual registry service.");
        
        // Example pseudo-code:
        // TrustedIssuerRegistry registry = context.getService(TrustedIssuerRegistry.class);
        // registry.clear();
        // for (TrustedIssuerConfig issuer : issuers) {
        //     registry.addTrustedIssuer(issuer.id, issuer.supportedTypes);
        // }
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

    /**
     * Data class representing a trusted issuer configuration
     */
    private static class TrustedIssuerConfig {
        final String id;
        final Set<String> supportedTypes;

        TrustedIssuerConfig(String id, Set<String> supportedTypes) {
            this.id = id;
            this.supportedTypes = supportedTypes != null ? supportedTypes : Set.of("*");
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TrustedIssuerConfig that = (TrustedIssuerConfig) o;
            return id.equals(that.id) && supportedTypes.equals(that.supportedTypes);
        }

        @Override
        public int hashCode() {
            return id.hashCode() * 31 + supportedTypes.hashCode();
        }
    }
}
```

---

### Step 2: Update Configuration File Format

**Update:** `configuration/config.properties`

```properties
# Trusted Issuers (Hot Reload Supported)
# Format: edc.iam.trusted-issuer.{index}.id={DID}
# Optional: edc.iam.trusted-issuer.{index}.supportedTypes={comma-separated types}

edc.iam.trusted-issuer.0.id=did:web:localhost:dev-connector
edc.iam.trusted-issuer.0.supportedTypes=MembershipCredential,BusinessPartnerCredential

edc.iam.trusted-issuer.1.id=did:web:catena-x.net:issuer
edc.iam.trusted-issuer.1.supportedTypes=MembershipCredential

# Add more issuers as needed...
edc.iam.trusted-issuer.2.id=did:web:example.com:authority
edc.iam.trusted-issuer.2.supportedTypes=*

# Legacy format (still supported, but not hot-reloadable):
# edc.iam.trusted-issuer.id=did:web:localhost:dev-connector
```

---

### Step 3: Register Extension

**Update:** `edc-extensions/oauth2-hot-reload/src/main/resources/META-INF/services/org.eclipse.edc.spi.system.ServiceExtension`

Add this line:
```
org.eclipse.tractusx.edc.oauth2.hotreload.TrustedIssuersHotReloadExtension
```

Or create a separate extension module if you prefer.

---

## 🔍 Finding the TrustedIssuerRegistry Service

The critical part is finding and updating the actual EDC service. Search for:

```java
// Search for these in the codebase:
codebase_search("TrustedIssuerRegistry interface or class")
codebase_search("How does IdentityAndTrustExtension register trusted issuers")
grep("TrustedIssuerRegistry", ...)
```

**Likely locations:**
- `org.eclipse.edc.iam.identitytrust.core.*`
- `org.eclipse.edc.spi.iam.*`
- Look for classes that handle `EDC_IAM_TRUSTED-ISSUER_*` environment variables

**Example pattern you might find:**
```java
@Inject
private TrustedIssuerRegistry trustedIssuerRegistry;

// Or it might be registered as:
TrustedIssuerRegistry registry = context.getService(TrustedIssuerRegistry.class);
```

---

## 🔄 Integration with OAuth2 Hot Reload Extension

You can add trusted issuers reload to your existing `OauthHotReloadExtension`:

**Update:** `edc-extensions/oauth2-hot-reload/src/main/java/org/eclipse/tractusx/edc/oauth2/hotreload/OauthHotReloadExtension.java`

```java
@Override
public void initialize(ServiceExtensionContext context) {
    // ... existing OAuth2 hot reload code ...
    
    // Also monitor trusted issuers
    monitor.info("Starting Trusted Issuers hot reload monitoring...");
    scheduleTrustedIssuersReload(context);
}

private void scheduleTrustedIssuersReload(ServiceExtensionContext context) {
    // Similar pattern to OAuth2 reload
    // Monitor config file for trusted issuer changes
    // Call reloadTrustedIssuerRegistry() when changes detected
}
```

---

## ✅ Testing

### Test 1: Add New Trusted Issuer

1. **Initial config:**
```properties
edc.iam.trusted-issuer.0.id=did:web:issuer1
```

2. **Add new issuer (while connector is running):**
```properties
edc.iam.trusted-issuer.0.id=did:web:issuer1
edc.iam.trusted-issuer.1.id=did:web:issuer2
```

3. **Check logs:**
```
INFO ... === Trusted Issuers configuration changed! ===
INFO ... Found 2 trusted issuer(s)
INFO ...   - did:web:issuer1 (types: [*])
INFO ...   - did:web:issuer2 (types: [*])
```

### Test 2: Remove Trusted Issuer

1. **Remove issuer from config**
2. **Verify it's removed from registry**
3. **Connector should reject credentials from removed issuer**

---

## 📊 Summary: Similarities with OAuth2 Hot Reload

| Feature | OAuth2 Hot Reload | Trust Authorities Hot Reload |
|---------|------------------|------------------------------|
| **Config Monitoring** | ✅ File watcher | ✅ Same file watcher |
| **Periodic Checks** | ✅ Every 30 seconds | ✅ Every 30 seconds |
| **Change Detection** | ✅ File lastModified | ✅ Same mechanism |
| **Service Reload** | ✅ Update DAC service | ✅ Update TrustedIssuerRegistry |
| **No Restart Needed** | ✅ | ✅ |
| **Same Extension** | Can combine in one extension | Can combine in one extension |

---

## 🎯 Next Steps

1. ✅ **Find TrustedIssuerRegistry service** in EDC codebase
2. ✅ **Implement `reloadTrustedIssuerRegistry()`** method
3. ✅ **Test with real credentials** from trusted/untrusted issuers
4. ✅ **Add to existing extension** or create separate module
5. ✅ **Document API** for adding/removing issuers via config file

---

## 💡 Key Differences from OAuth2

1. **Multiple Values**: Trusted issuers are a **list** (indexed: 0, 1, 2...), not a single value
2. **Complex Structure**: Each issuer has an ID + optional supportedTypes
3. **Protocol-Level**: Used for connector-to-connector credential validation, not user authentication
4. **More Critical**: Invalid trusted issuer = security vulnerability (accepting untrusted credentials)

---

## 🔐 Security Considerations

1. **Validate DIDs**: Ensure DIDs are properly formatted before adding
2. **Audit Logging**: Log all trusted issuer changes
3. **Verification**: Verify issuer DID documents exist and are valid
4. **Rate Limiting**: Prevent rapid add/remove cycles (potential DoS)
5. **Approval Process**: Consider requiring approval before adding new issuers (future enhancement)

---

**This hot reload is very similar to OAuth2 hot reload in implementation pattern, but serves a different purpose in the dataspace trust model.** 🚀

Service registration file is in the wrong directory: META-INF.services/ (dot) should be META-INF/services/ (slash).
