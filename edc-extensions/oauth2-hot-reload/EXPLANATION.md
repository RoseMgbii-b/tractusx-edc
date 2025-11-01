# How I Knew OAuth2 Works (From the Test)

## The Test That Proves OAuth2 Works

Looking at this test file:
```
edc-tests/e2e/management-tests/src/test/java/org/eclipse/tractusx/edc/tests/auth/DelegatedAuthEndToEndTest.java
```

**What the test does:**
1. Sets up Keycloak (OIDC provider)
2. Configures EDC with:
   ```java
   "web.http.management.auth.type", "delegated",
   "web.http.management.auth.dac.audience", KEYCLOAK.audience(),
   "web.http.management.auth.dac.key.url", KEYCLOAK.jwksUrl()
   ```
3. Gets a JWT token from Keycloak
4. Makes an API call with `Authorization: Bearer <token>`
5. **Asserts the call succeeds (status 200)**

**Conclusion:** If the test passes, OAuth2/OIDC JWT validation **works**. The test wouldn't pass if EDC couldn't validate JWT tokens.

## Why It's Only in Tests (Not Main Code)

The actual OAuth2 implementation is in the **Eclipse EDC platform** (upstream dependency), not in Tractus-X code:

1. **Dependency**: `org.eclipse.edc:auth-delegated` (line 89 in `gradle/libs.versions.toml`)
2. **Included via BOM**: `runtimeOnly(libs.edc.bom.controlplane.base)` includes it automatically
3. **Source location**: `github.com/eclipse-edc/Connector` repository

This is why you don't see the implementation code in this repo - it's pulled in as a compiled dependency.

---

# Configuration Properties Explained

## Where Config Comes From

### 1. **File System (Primary - What You're Using)**

```bash
# You run with:
./gradlew :edc-controlplane:edc-runtime-memory:run \
  -Dedc.fs.config=/Users/princeangellos/Documents/Projects/tractusx-edc/dev/configuration.properties
```

**What happens:**
- EDC reads the file **once at startup**
- File path comes from system property: `edc.fs.config`
- All properties in that file are loaded into memory
- **Changes require restart** (unless using hot reload extension)

### 2. **System Properties / Environment Variables**

EDC can also read from environment variables:
```bash
export WEB_HTTP_MANAGEMENT_AUTH_TYPE=delegated
export WEB_HTTP_MANAGEMENT_AUTH_DAC_KEY_URL=https://keycloak.../certs
```

These are converted: `WEB_HTTP_MANAGEMENT_AUTH_TYPE` → `web.http.management.auth.type`

### 3. **Database (NOT Currently Used)**

The current implementation does NOT read config from database. The in-memory runtime:
- Uses **file-based config** only
- Data (assets, policies) is in-memory (lost on restart)
- Config is file-based (needs restart to reload)

To use database config, you'd need to:
- Write a custom `ConfigSource` implementation
- Store config in PostgreSQL
- Make EDC read from DB instead of file

---

## Configuration Properties for OAuth2

### Current Setup (File-Based)

Add to your `configuration.properties`:

```properties
# OAuth2/OIDC Configuration
web.http.management.auth.type=delegated
web.http.management.auth.dac.key.url=https://keycloak.example.com/realms/myrealm/protocol/openid-connect/certs
web.http.management.auth.dac.audience=management-api

# Optional: Disable API key (JWT only)
# web.http.management.auth.key=
```

### How EDC Uses These Properties

1. **At startup:**
   - EDC reads `web.http.management.auth.type`
   - If `delegated`, it loads the DAC (Delegated Authentication Client) extension
   - DAC extension reads `web.http.management.auth.dac.key.url` and fetches JWKS
   - Sets up JWT validator filter for Management API

2. **At runtime:**
   - Every API request to `/api/management/*` goes through JWT validator
   - Validator checks `Authorization: Bearer <token>` header
   - Validates token signature using JWKS from Keycloak
   - Validates issuer, audience, expiration
   - If valid → request proceeds to controller
   - If invalid → returns 401 Unauthorized

---

# Hot Reload Extension - How It Works

## Overview

The `OAuth2HotReloadExtension` monitors your config file and reloads OAuth2 settings when they change.

## Step-by-Step Flow

### 1. **Startup**
```
Extension starts → Finds config file path → Loads initial OAuth2 settings → Starts monitoring thread
```

### 2. **Monitoring (Every 30 seconds)**
```
Check file last modified time → If changed → Read new properties → Compare JWKS URL → If different → Trigger reload
```

### 3. **Reload Process**
```
Call reloadDacService() → Update JWT validator with new JWKS URL → Log success/failure
```

## Code Structure

```
OAuth2HotReloadExtension.java
├── initialize()
│   ├── Find config file (from -Dedc.fs.config)
│   ├── Load initial OAuth2 config
│   └── Start scheduler (checks every 30s)
│
├── loadConfigFromFile()
│   ├── Read configuration.properties
│   ├── Extract JWKS URL and audience
│   └── Store in memory (lastJwksUrl, lastAudience)
│
├── checkAndReloadConfig()
│   ├── Check file modified time
│   ├── If changed, reload from file
│   └── If JWKS URL changed, call reloadDacService()
│
└── reloadDacService()  ⚠️ NOT FULLY IMPLEMENTED
    └── Needs DAC service to expose reload() method
```

## Current Limitation

**The DAC service from Eclipse EDC doesn't expose a reload method.**

The extension can:
- ✅ Detect config file changes
- ✅ Read new OAuth2 properties
- ✅ Detect when JWKS URL changes
- ❌ Actually reload the JWT validator (needs DAC service support)

## Solutions

### Option 1: Modify Eclipse EDC (Best, but requires upstream change)
1. Fork `github.com/eclipse-edc/Connector`
2. Add `reloadJwksUrl(String newJwksUrl)` method to DAC extension
3. Call it from this extension

### Option 2: Use Reflection (Fragile)
```java
// Access internal JWT validator and update it
// Risk: Breaks if EDC updates internal structure
```

### Option 3: Custom JWT Validator (Most control)
1. Implement your own JWT validation filter
2. Make it support hot reload
3. Replace DAC service with your implementation

---

# Summary

## Config Properties Location

| Source | Currently Used? | When Read | Hot Reload? |
|--------|----------------|-----------|-------------|
| File (`-Dedc.fs.config`) | ✅ Yes | At startup | ❌ No (until extension) |
| Environment Variables | ✅ Yes | At startup | ❌ No |
| Database | ❌ No | N/A | N/A |

## OAuth2 Support

- ✅ **Native support**: Yes, via Eclipse EDC `auth-delegated` module
- ✅ **Keycloak compatible**: Yes, works with Keycloak JWKS endpoint
- ✅ **Configurable**: Via `configuration.properties`
- ⚠️ **Hot reload**: Partial (extension detects changes, but needs DAC reload support)

## Next Steps

1. **Test OAuth2 works**: Add config to your `configuration.properties` and restart
2. **Use hot reload extension**: Add it to your build (already done)
3. **Complete reload logic**: Choose one of the 3 options above to actually reload DAC service

