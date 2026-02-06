# Runtime Access Phase - Implementation Guide

This guide covers the implementation steps for the **runtime access phase** of service exchange, which enables real-time API calls using Verifiable Credentials instead of contract-based data transfers.

## Overview

The runtime access phase implements:
1. **OpenID Configuration Endpoint** (`/.well-known/openid-configuration`)
2. **Token Endpoint** (`/token`) - VP → JWT
3. **PDP Authorization Filter** - JWT + Policy → Allow/Deny
4. **Service API Endpoints** - Protected business APIs

## Architecture Flow

```
Consumer calls service → 401 Unauthorized
    ↓
Consumer discovers token endpoint via /.well-known/openid-configuration
    ↓
Consumer builds VP (Verifiable Presentation with VC)
    ↓
Consumer POSTs vp_token to /token
    ↓
Provider verifies VP/VC (DID + Registry + Trusted Issuers)
    ↓
Provider issues JWT access token
    ↓
Consumer calls service API with Authorization: Bearer <token>
    ↓
PDP evaluates token claims + service policy → Allow/Deny
    ↓
Service executes (if allowed)
```

## Implementation Steps

### Step 1: Create Service Auth Extension Module

**Create:** `edc-extensions/service-auth/` module structure

**Directory Structure:**
```
edc-extensions/service-auth/
├── build.gradle.kts
├── src/main/java/org/eclipse/tractusx/edc/serviceauth/
│   ├── ServiceAuthExtension.java
│   ├── controller/
│   │   ├── OpenIdConfigurationController.java
│   │   └── VpTokenController.java
│   ├── filter/
│   │   └── ServiceAuthorizationFilter.java
│   ├── service/
│   │   ├── VpVerifier.java
│   │   ├── VpVerifierImpl.java
│   │   ├── TokenSigner.java
│   │   └── TokenSignerImpl.java
│   └── api/
│       └── ServiceApiController.java (example)
└── src/main/resources/META-INF/services/
    └── org.eclipse.edc.spi.system.ServiceExtension
```

### Step 2: Implement OpenID Configuration Endpoint

**File:** `OpenIdConfigurationController.java`

**Purpose:** Advertise token endpoint and supported grant types

**Endpoint:** `GET /.well-known/openid-configuration`

**Response:**
```json
{
  "grant_types_supported": ["vp_token"],
  "token_endpoint": "https://provider/token"
}
```

**Implementation:**
- JAX-RS `@Path("/.well-known/openid-configuration")`
- `@GET` method
- Returns JSON with `grant_types_supported` and `token_endpoint`
- Uses `UriInfo` to build absolute token endpoint URL

### Step 3: Implement Token Endpoint

**File:** `VpTokenController.java`

**Purpose:** Accept VP, verify it, issue JWT access token

**Endpoint:** `POST /token`

**Request:**
```
grant_type=vp_token
vp_token=<SIGNED_VP_JWT>
scope=<SERVICE_SCOPE>
```

**Response:**
```json
{
  "access_token": "<JWT>",
  "token_type": "Bearer",
  "expires_in": 7200,
  "scope": "openid service:telemetry"
}
```

**Implementation Steps:**
1. Extract `vp_token` from form parameters
2. Call `VpVerifier.verify(vp_token, scope)`
3. If verification succeeds, extract claims (DID, VC attributes, roles)
4. Call `TokenSigner.sign(claims)` to create JWT
5. Return token response

### Step 4: Implement VP Verifier

**File:** `VpVerifierImpl.java`

**Purpose:** Verify Verifiable Presentation and extract claims

**Dependencies:**
- DID resolution (your existing DID logic)
- Gaia-X Registry trusted issuers (your existing `GaiaXRegistryComplianceClient`)
- VC library for signature verification

**Verification Steps:**
1. Parse VP JWT
2. Verify VP signature (resolve DID, get public key)
3. Extract VCs from VP
4. For each VC:
   - Verify VC signature
   - Resolve issuer DID
   - Check issuer against trusted registry (Registry API)
   - Check issuer against local trusted issuers list
   - Validate VC type matches required scope
5. Extract claims (subject DID, roles, attributes)
6. Return claims map

**Integration Points:**
- Reuse `GaiaXRegistryComplianceClient.getTrustedIssuers()`
- Reuse DID document validation logic from `ParticipantValidationSubscriber`
- Use VC library (e.g., `org.eclipse.edc:verifiable-credentials-spi`)

### Step 5: Implement Token Signer

**File:** `TokenSignerImpl.java`

**Purpose:** Sign JWT access tokens with VC claims

**Implementation:**
- Use EDC's JWT signing infrastructure
- Include claims in JWT payload:
  - `sub`: Subject DID
  - `iss`: Provider DID
  - `aud`: Service endpoint
  - `exp`, `iat`, `nbf`: Timestamps
  - Custom claims from VC (roles, attributes, etc.)

### Step 6: Implement PDP Authorization Filter

**File:** `ServiceAuthorizationFilter.java`

**Purpose:** Intercept service API calls, validate JWT, evaluate policy

**Implementation:**
- JAX-RS `ContainerRequestFilter`
- `@Priority(Priorities.AUTHORIZATION)`
- Extract `Authorization: Bearer <token>` header
- Parse and validate JWT
- Extract claims from JWT
- Resolve service policy (from asset properties or policy store)
- Use EDC Policy Engine to evaluate: `policy.evaluate(claims, context)`
- Allow or deny request (403 Forbidden if denied)

**Policy Evaluation:**
- Build `PolicyContext` with JWT claims
- Use existing EDC `PolicyEngine.evaluate(policy, context)`
- Check result: `decision.succeeded() && decision.getContent()`

### Step 7: Create Service API Controller (Example)

**File:** `ServiceApiController.java`

**Purpose:** Example protected service endpoint

**Implementation:**
- `@Path("/service/telemetry")`
- `@POST` method
- At this point, PDP has already validated the request
- Extract claims from thread-local or context if needed
- Execute service logic (transformation, third-party API call, etc.)
- Return result

### Step 8: Wire Everything Together

**File:** `ServiceAuthExtension.java`

**Purpose:** Main extension that registers all components

**Implementation:**
```java
@Extension("Service Auth Extension")
public class ServiceAuthExtension implements ServiceExtension {
    
    @Inject private WebService webService;
    @Inject private VpVerifier vpVerifier;
    @Inject private TokenSigner tokenSigner;
    @Inject private PolicyEngine policyEngine;
    @Inject private AssetIndex assetIndex; // For service policy lookup
    
    @Override
    public void initialize(ServiceExtensionContext context) {
        // Register OpenID config endpoint
        webService.registerResource(ApiContext.PUBLIC, 
            new OpenIdConfigurationController(monitor, "/token"));
        
        // Register token endpoint
        webService.registerResource(ApiContext.PUBLIC,
            new VpTokenController(monitor, vpVerifier, tokenSigner));
        
        // Register PDP filter for service APIs
        var pdpFilter = new ServiceAuthorizationFilter(
            monitor, policyEngine, assetIndex);
        webService.registerResource(ApiContext.PUBLIC, pdpFilter);
        
        // Register example service API
        webService.registerResource(ApiContext.PUBLIC,
            new ServiceApiController(monitor));
    }
}
```

## Dependencies Needed

**Add to `build.gradle.kts`:**
```kotlin
dependencies {
    // Existing dependencies...
    
    // JWT and VC support
    implementation(libs.edc.spi.jwt)
    implementation(libs.edc.spi.vc)
    implementation(libs.edc.spi.identitytrust)
    
    // Policy engine
    implementation(libs.edc.spi.policyengine)
    
    // Web/JAX-RS
    implementation(libs.edc.spi.web)
    implementation(libs.edc.runtime.metamodel)
    
    // Asset lookup
    implementation(libs.edc.spi.controlplane)
    
    // Your existing clearing house client (for Registry API)
    implementation(project(":edc-extensions:clearing-house-client"))
}
```

## Testing Steps

### Test 1: OpenID Configuration Discovery

**Request:**
```bash
GET https://provider/.well-known/openid-configuration
```

**Expected Response:**
```json
{
  "grant_types_supported": ["vp_token"],
  "token_endpoint": "https://provider/token"
}
```

### Test 2: Token Endpoint - VP Verification

**Request:**
```bash
POST https://provider/token
Content-Type: application/x-www-form-urlencoded

grant_type=vp_token&vp_token=<SIGNED_VP>&scope=service:telemetry
```

**Expected Response:**
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIs...",
  "token_type": "Bearer",
  "expires_in": 7200,
  "scope": "service:telemetry"
}
```

**Verification:**
- VP signature is valid
- VC issuer is in trusted registry
- VC type matches scope
- JWT token is issued

### Test 3: Service API Call with Token

**Request:**
```bash
POST https://provider/service/telemetry
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json

{
  "data": "..."
}
```

**Expected Flow:**
1. Filter extracts JWT
2. JWT is validated
3. Claims are extracted
4. Policy is evaluated
5. Request is allowed/denied
6. Service executes (if allowed)

### Test 4: Policy Enforcement

**Test Cases:**
- Valid token + matching policy → 200 OK
- Valid token + non-matching policy → 403 Forbidden
- Invalid token → 401 Unauthorized
- Missing token → 401 Unauthorized
- Expired token → 401 Unauthorized

## Integration with Existing Components

### Reuse from Clearing House Client

**From `GaiaXRegistryComplianceClient`:**
- `getTrustedIssuers()` - Check VC issuers
- `verifyTrustAnchorChainFromUri()` - Certificate chain validation (if needed)

**From `ParticipantValidationSubscriber`:**
- DID document validation logic
- DID resolution patterns

### Reuse from EDC Core

**Policy Engine:**
- `PolicyEngine.evaluate(policy, context)`
- Existing constraint functions (eq, in, isAnyOf, etc.)

**JWT Infrastructure:**
- EDC's JWT signing/verification
- Token validation utilities

## Security Considerations

1. **VP Verification:**
   - Always verify VP signature
   - Always check issuer against trusted registry
   - Validate VC types match scopes
   - Check VC expiration

2. **Token Security:**
   - Use secure signing keys
   - Set appropriate expiration times
   - Include nonce in VP to prevent replay

3. **PDP Policy:**
   - Policies should be stored securely
   - Policy evaluation should be fast (cache if needed)
   - Log all authorization decisions

## Next Steps After Implementation

1. **Service Registration:**
   - Services should be registered as assets with `edc:resourceType=service`
   - Service policies should be attached to service assets

2. **Client SDK:**
   - Create client library for VP building
   - Simplify VP → Token → API call flow

3. **Monitoring:**
   - Log all token issuances
   - Track authorization decisions
   - Monitor service API usage

4. **Documentation:**
   - API documentation for service endpoints
   - Client integration guide
   - VP/VC format specifications

## File Checklist

- [ ] `ServiceAuthExtension.java` - Main extension
- [ ] `OpenIdConfigurationController.java` - OpenID config endpoint
- [ ] `VpTokenController.java` - Token endpoint
- [ ] `VpVerifier.java` - VP verification interface
- [ ] `VpVerifierImpl.java` - VP verification implementation
- [ ] `TokenSigner.java` - Token signing interface
- [ ] `TokenSignerImpl.java` - Token signing implementation
- [ ] `ServiceAuthorizationFilter.java` - PDP filter
- [ ] `ServiceApiController.java` - Example service API
- [ ] `build.gradle.kts` - Dependencies
- [ ] `META-INF/services/org.eclipse.edc.spi.system.ServiceExtension` - Registration
- [ ] Unit tests for each component
- [ ] Integration tests for full flow

## Estimated Implementation Time

- **Step 1-2 (OpenID Config):** 2-4 hours
- **Step 3-4 (Token Endpoint + VP Verifier):** 1-2 days
- **Step 5 (Token Signer):** 4-8 hours
- **Step 6 (PDP Filter):** 1-2 days
- **Step 7 (Service API):** 4-8 hours
- **Step 8 (Integration):** 1 day
- **Testing:** 2-3 days

**Total:** ~1-2 weeks

