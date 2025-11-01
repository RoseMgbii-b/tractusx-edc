# OAuth2 Hot Reload Extension

## Purpose

This extension enables **hot reload** of OAuth2/OIDC configuration (JWKS URL, audience) without restarting the connector.

## How Configuration Works in EDC

### Where Config Comes From

1. **File-based (Primary)**:
   - Config is read from a `.properties` file specified via system property: `-Dedc.fs.config=/path/to/configuration.properties`
   - Example: `-Dedc.fs.config=/Users/princeangellos/Documents/Projects/tractusx-edc/dev/configuration.properties`
   - EDC reads this file **ONCE at startup**

2. **System Properties / Environment Variables**:
   - EDC can also read from environment variables (automatically converted from `UPPER_CASE` to `lower.case`)
   - Example: `WEB_HTTP_MANAGEMENT_AUTH_DAC_KEY_URL` → `web.http.management.auth.dac.key.url`

3. **Database** (Currently NOT used):
   - The current implementation does NOT store config in database
   - You would need to implement a custom ConfigSource for this

### Current Setup (In-Memory Runtime)

- **Config Source**: File (`configuration.properties`)
- **Storage**: File system (not database)
- **When Read**: At startup only
- **Hot Reload**: Requires restart (until this extension is used)

## Configuration Properties for OAuth2

Add these to your `configuration.properties`:

```properties
# Enable OAuth2/OIDC delegated authentication
web.http.management.auth.type=delegated
web.http.management.auth.dac.key.url=https://keycloak.example.com/realms/myrealm/protocol/openid-connect/certs
web.http.management.auth.dac.audience=management-api
```

## How This Extension Works

1. **Monitors the config file** for changes (checks every 30 seconds)
2. **When file is modified**, reads new OAuth2 properties
3. **Detects JWKS URL changes** and triggers reload
4. **Reloads the DAC service** with new JWKS URL (NOTE: Requires DAC service to expose reload method)

## Integration

Add this extension to your runtime's `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":edc-extensions:oauth2-hot-reload"))
}
```

## Limitations

**Important**: The DAC service from Eclipse EDC (`auth-delegated` module) does not currently expose a `reload()` method. This extension detects config changes but cannot fully reload the JWT validator without one of these approaches:

1. **Modify Eclipse EDC** to expose a reload method in the DAC extension
2. **Use reflection** to access internal JWT validator (fragile, may break)
3. **Implement custom JWT validator** that supports hot reload

The extension logs what needs to be done when config changes are detected.

