# Runtime Access Phase - Design Document

## Document Information

- **Version:** 1.0
- **Date:** 2025-01-XX
- **Status:** Design Phase
- **Author:** Development Team
- **Reviewer:** Tech Lead

---

## Executive Summary

This document outlines the design for the **Runtime Access Phase** of service exchange in Tractus-X EDC. This phase enables real-time, service-oriented API access using Verifiable Credentials (VP-based authentication) instead of contract-based data transfers.

**Key Design Principles:**
- ✅ **Decoupled from Discovery Phase** - Runtime phase reads from assets/policies, not catalog structure
- ✅ **FIWARE-Style Pattern** - Follows FIWARE/EBSI architectural pattern
- ✅ **Dataspace Compliant** - Uses standard protocols (OAuth 2.0, OIDC, W3C VC)
- ✅ **Extensible** - Modular design allows future enhancements

---

## 1. Problem Statement

### Current State
- EDC supports **data exchange** via contract negotiation → transfer process
- Services are treated as datasets, requiring contract negotiation before access
- No real-time, service-oriented API access mechanism

### Requirements
- Enable **service-oriented exchange** (APIs, not just data files)
- Support **real-time API calls** with identity-based authorization
- Use **Verifiable Credentials** for authentication (not contracts)
- Maintain **dataspace compliance** (DSP, DCAT standards)
- **Decouple from discovery phase** (independent development)

---

## 2. Architecture Overview

### High-Level Flow

```
┌─────────────────────────────────────────────────────────────┐
│                    CONSUMER SIDE                            │
├─────────────────────────────────────────────────────────────┤
│ 1. Discover Service (from catalog or asset)                 │
│    → Get asset ID, endpoint URL                            │
│                                                             │
│ 2. Call Service API → 401 Unauthorized                      │
│                                                             │
│ 3. Discover Token Endpoint                                  │
│    GET /.well-known/openid-configuration                   │
│    → { "token_endpoint": "https://provider/token" }         │
│                                                             │
│ 4. Build Verifiable Presentation (VP)                       │
│    - Include Verifiable Credentials (VCs)                  │
│    - Sign with DID private key                             │
│    - Add nonce, timestamps, audience                        │
│                                                             │
│ 5. Request Access Token                                      │
│    POST /token                                              │
│    grant_type=vp_token&vp_token=<SIGNED_VP>                │
│    → { "access_token": "<JWT>", ... }                      │
│                                                             │
│ 6. Call Service API with Token                              │
│    POST /service/telemetry                                 │
│    Authorization: Bearer <JWT>                             │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                    PROVIDER SIDE                            │
├─────────────────────────────────────────────────────────────┤
│ 7. OpenID Config Endpoint                                   │
│    /.well-known/openid-configuration                       │
│    → Advertises token endpoint                             │
│                                                             │
│ 8. Token Endpoint                                           │
│    POST /token                                              │
│    ├─ Verify VP signature (DID resolution)                 │
│    ├─ Verify VC signatures                                 │
│    ├─ Check trusted issuers (Registry API)                │
│    ├─ Validate VC types vs scope                           │
│    └─ Issue JWT access token                                │
│                                                             │
│ 9. Service Authorization Filter (PDP)                       │
│    ├─ Extract JWT from Authorization header                │
│    ├─ Validate JWT signature                               │
│    ├─ Extract claims (DID, roles, attributes)              │
│    ├─ Resolve service policy (from Asset/PolicyStore)      │
│    ├─ Evaluate policy: PolicyEngine.evaluate(claims)        │
│    └─ Allow/Deny request                                    │
│                                                             │
│ 10. Service API Execution                                   │
│     ├─ Service logic (transformation, 3rd-party call)      │
│     └─ Return result                                        │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. Design Principles

### 3.1 Decoupling from Discovery Phase

**Principle:** Runtime phase must NOT depend on catalog structure.

**Implementation:**
- ✅ Read service endpoint from **Asset properties** (not catalog)
- ✅ Read service policy from **PolicyStore** (not catalog)
- ✅ Use **AssetIndex** for asset lookup (not catalog parsing)
- ✅ Catalog only provides **asset ID** (flexible format)

**Benefit:** Discovery phase can change catalog structure without breaking runtime phase.

### 3.2 Standards Compliance

**Standards Used:**
- **OAuth 2.0** - Token endpoint format
- **OpenID Connect** - Discovery pattern (`/.well-known/openid-configuration`)
- **W3C Verifiable Credentials** - VC/VP format
- **W3C DID** - Identity format (`did:web`)
- **ODRL** - Policy format (via EDC Policy Engine)

**Compliance Level:**
- ✅ **Pattern Compliant** with FIWARE/EBSI
- ✅ **Protocol Compliant** with OAuth 2.0, OIDC
- ⚠️ **Extension** to Dataspace Protocol (not in DSP spec, but uses standards)

### 3.3 Modularity

**Component Separation:**
- **Auth Layer** - VP verification, token issuance
- **Authorization Layer** - PDP policy evaluation
- **Service Layer** - Business logic APIs

**Benefits:**
- Independent testing
- Easy to extend
- Clear responsibilities

---

## 4. Component Design

### 4.1 Module Structure

```
edc-extensions/service-auth/
├── build.gradle.kts
├── src/main/java/org/eclipse/tractusx/edc/serviceauth/
│   ├── ServiceAuthExtension.java          # Main extension
│   │
│   ├── controller/                        # REST Controllers
│   │   ├── OpenIdConfigurationController.java
│   │   └── VpTokenController.java
│   │
│   ├── filter/                            # Request Filters
│   │   └── ServiceAuthorizationFilter.java
│   │
│   ├── service/                           # Business Logic
│   │   ├── VpVerifier.java                # Interface
│   │   ├── VpVerifierImpl.java            # VP verification
│   │   ├── TokenSigner.java               # Interface
│   │   └── TokenSignerImpl.java           # JWT signing
│   │
│   ├── api/                               # Service APIs (Examples)
│   │   └── ServiceApiController.java
│   │
│   └── model/                             # Data Models
│       ├── TokenRequest.java
│       ├── TokenResponse.java
│       └── ServicePolicyContext.java
│
└── src/test/java/...
```

### 4.2 Component Responsibilities

#### 4.2.1 ServiceAuthExtension
**Purpose:** Main extension that wires all components together.

**Responsibilities:**
- Register OpenID config endpoint
- Register token endpoint
- Register PDP authorization filter
- Register service API controllers
- Inject dependencies

**Dependencies:**
- `WebService` - Register REST resources
- `VpVerifier` - VP verification service
- `TokenSigner` - JWT signing service
- `PolicyEngine` - Policy evaluation
- `AssetIndex` - Asset lookup
- `Monitor` - Logging

#### 4.2.2 OpenIdConfigurationController
**Purpose:** Advertise token endpoint and supported grant types.

**Endpoint:** `GET /.well-known/openid-configuration`

**Response:**
```json
{
  "grant_types_supported": ["vp_token"],
  "token_endpoint": "https://provider/token",
  "issuer": "https://provider"
}
```

**Implementation:**
- JAX-RS `@Path("/.well-known/openid-configuration")`
- `@GET` method
- Builds absolute token endpoint URL from `UriInfo`
- Returns JSON response

**Dependencies:**
- `Monitor` - Logging

#### 4.2.3 VpTokenController
**Purpose:** Accept VP, verify it, issue JWT access token.

**Endpoint:** `POST /token`

**Request:**
```
Content-Type: application/x-www-form-urlencoded

grant_type=vp_token
vp_token=<SIGNED_VP_JWT>
scope=service:telemetry
```

**Response:**
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIs...",
  "token_type": "Bearer",
  "expires_in": 7200,
  "scope": "service:telemetry"
}
```

**Flow:**
1. Extract `vp_token` from form parameters
2. Call `VpVerifier.verify(vp_token, scope)`
3. If successful, extract claims
4. Call `TokenSigner.sign(claims)` to create JWT
5. Return token response

**Dependencies:**
- `VpVerifier` - VP verification
- `TokenSigner` - JWT signing
- `Monitor` - Logging

#### 4.2.4 VpVerifier (Interface + Implementation)
**Purpose:** Verify Verifiable Presentation and extract claims.

**Interface:**
```java
public interface VpVerifier {
    Result<Map<String, Object>> verify(String vpToken, String scope);
}
```

**Verification Steps:**
1. Parse VP JWT
2. Verify VP signature:
   - Extract `iss` (issuer DID)
   - Resolve DID document
   - Extract public key
   - Verify JWT signature
3. Extract VCs from VP payload
4. For each VC:
   - Verify VC signature (resolve issuer DID)
   - Check issuer against trusted registry (Gaia-X Registry API)
   - Check issuer against local trusted issuers list
   - Validate VC type matches required scope
5. Extract claims:
   - Subject DID (`sub`)
   - VC attributes (roles, labels, etc.)
   - Issuer information
6. Return claims map

**Dependencies:**
- `GaiaXRegistryComplianceClient` - Trusted issuers
- DID resolver (existing DID logic)
- VC library (`org.eclipse.edc:verifiable-credentials-spi`)
- `Monitor` - Logging

**Reuse Existing Components:**
- `ParticipantValidationSubscriber` - DID document validation patterns
- `GaiaXRegistryComplianceClient.getTrustedIssuers()` - Registry API

#### 4.2.5 TokenSigner (Interface + Implementation)
**Purpose:** Sign JWT access tokens with VC claims.

**Interface:**
```java
public interface TokenSigner {
    String sign(Map<String, Object> claims);
    Result<Map<String, Object>> verify(String token);
}
```

**JWT Payload Structure:**
```json
{
  "sub": "did:web:consumer.example.com",
  "iss": "did:web:provider.example.com",
  "aud": "https://provider/service/telemetry",
  "exp": 1234567890,
  "iat": 1234567890,
  "nbf": 1234567890,
  "scope": "service:telemetry",
  "vc": {
    "roles": ["manufacturer"],
    "labels": ["gold_customer"]
  }
}
```

**Dependencies:**
- EDC JWT signing infrastructure
- `Monitor` - Logging

#### 4.2.6 ServiceAuthorizationFilter
**Purpose:** Intercept service API calls, validate JWT, evaluate policy.

**Implementation:**
- JAX-RS `ContainerRequestFilter`
- `@Priority(Priorities.AUTHORIZATION)`
- Applied to service API endpoints

**Authorization Flow:**
1. Extract `Authorization: Bearer <token>` header
2. If missing → `401 Unauthorized`
3. Parse and validate JWT:
   - Call `TokenSigner.verify(token)`
   - Extract claims
4. If invalid → `401 Unauthorized`
5. Resolve service policy:
   - Extract service path from request
   - Map path to asset ID
   - Look up asset from `AssetIndex`
   - Get policy from `PolicyStore` (via ContractDefinition)
6. Build `PolicyContext`:
   - Add JWT claims
   - Add service metadata
   - Add request context
7. Evaluate policy:
   - `PolicyEngine.evaluate(policy, context)`
8. If denied → `403 Forbidden`
9. If allowed → Continue to service API

**Dependencies:**
- `TokenSigner` - JWT verification
- `PolicyEngine` - Policy evaluation
- `AssetIndex` - Asset lookup
- `PolicyStore` / `ContractDefinitionService` - Policy retrieval
- `Monitor` - Logging

**Key Design Decision:**
- Policy resolution from **Asset/PolicyStore** (not catalog)
- Service identification via **request path → asset ID mapping**

#### 4.2.7 ServiceApiController (Example)
**Purpose:** Example protected service endpoint.

**Endpoint:** `POST /service/telemetry`

**Implementation:**
- At this point, PDP has already validated the request
- Extract claims from thread-local or context if needed
- Execute service logic:
  - Data transformation
  - Third-party API call
  - Processing
- Return result

**Dependencies:**
- `Monitor` - Logging
- Business logic services

---

## 5. Data Flow and Integration Points

### 5.1 Service Discovery → Runtime Access

**Decoupling Strategy:**
```
Discovery Phase                    Runtime Phase
     │                                  │
     │ Provides asset ID                │
     │ (from catalog)                   │
     │                                  │
     └──────────────┬───────────────────┘
                    │
                    ▼
            AssetIndex.findById(assetId)
                    │
                    ▼
            Asset Properties:
            - dcat:endpointURL
            - edc:resourceType
                    │
                    ▼
            PolicyStore.findByAssetId(assetId)
```

**Key Point:** Runtime phase reads from **AssetIndex** and **PolicyStore**, not catalog structure.

### 5.2 Integration with Existing Components

#### 5.2.1 Reuse from Clearing House Client
- `GaiaXRegistryComplianceClient.getTrustedIssuers()` - Trusted issuers validation
- DID document validation patterns from `ParticipantValidationSubscriber`

#### 5.2.2 Reuse from EDC Core
- `PolicyEngine` - Policy evaluation
- `AssetIndex` - Asset lookup
- `PolicyStore` / `ContractDefinitionService` - Policy retrieval
- JWT infrastructure - Token signing/verification
- `WebService` - REST resource registration

#### 5.2.3 New Dependencies
- `org.eclipse.edc:verifiable-credentials-spi` - VC/VP parsing
- `org.eclipse.edc:jwt-spi` - JWT handling (if not already included)

---

## 6. Service Identification and Policy Resolution

### 6.1 Service Identification

**Challenge:** Map incoming service API request to asset/policy.

**Options:**

#### Option A: Path-Based Mapping (Recommended)
```java
// Map request path to asset ID
/service/telemetry → assetId: "svc-telemetry"
/service/analytics → assetId: "svc-analytics"
```

**Implementation:**
- Store mapping in asset properties: `edc:servicePath = "/service/telemetry"`
- Or use convention: asset ID matches path segment

#### Option B: Header-Based
```java
// Use custom header
X-Service-Id: svc-telemetry
```

#### Option C: Query Parameter
```java
// Use query parameter
/service/telemetry?serviceId=svc-telemetry
```

**Recommendation:** **Option A (Path-Based)** - Clean, RESTful, no extra headers.

### 6.2 Policy Resolution

**Flow:**
1. Identify asset ID from request
2. Look up asset: `Asset asset = assetIndex.findById(assetId)`
3. Find ContractDefinition for asset:
   - Query ContractDefinitionService
   - Match by `assetsSelector`
4. Get policy:
   - `contractDefinition.getContractPolicyId()`
   - Look up in PolicyStore
5. Use policy for PDP evaluation

**Alternative:** Store policy reference directly in asset properties (simpler but less flexible).

---

## 7. Security Considerations

### 7.1 VP Verification
- ✅ Always verify VP signature (DID resolution)
- ✅ Always verify VC signatures
- ✅ Always check issuer against trusted registry
- ✅ Validate VC types match required scope
- ✅ Check VC expiration
- ✅ Validate nonce (prevent replay attacks)

### 7.2 Token Security
- ✅ Use secure signing keys (from vault)
- ✅ Set appropriate expiration times (configurable)
- ✅ Include nonce in VP to prevent replay
- ✅ Validate token signature on every request
- ✅ Check token expiration

### 7.3 Policy Enforcement
- ✅ Policies stored securely
- ✅ Policy evaluation is fast (consider caching)
- ✅ Log all authorization decisions
- ✅ Fail-secure (deny if policy evaluation fails)

### 7.4 Service API Security
- ✅ All service APIs protected by PDP filter
- ✅ No direct access without token
- ✅ Rate limiting (optional, via gateway)
- ✅ Input validation

---

## 8. Configuration

### 8.1 Extension Configuration

```properties
# Service Auth Extension
edc.serviceauth.enabled=true
edc.serviceauth.token.expiration.seconds=7200
edc.serviceauth.token.issuer=did:web:provider.example.com
edc.serviceauth.openid.config.path=/.well-known/openid-configuration
edc.serviceauth.token.endpoint.path=/token
```

### 8.2 Service Registration

Services are registered as assets with:
```json
{
  "properties": {
    "edc:resourceType": "service",
    "dcat:endpointURL": "https://provider/service/telemetry",
    "dcat:endpointDescription": "https://provider/docs/telemetry",
    "edc:servicePath": "/service/telemetry"  // For path mapping
  }
}
```

---

## 9. Error Handling

### 9.1 Token Endpoint Errors

| Error | HTTP Status | Response |
|-------|-------------|----------|
| Missing `vp_token` | 400 Bad Request | `{"error": "invalid_request"}` |
| Invalid VP signature | 401 Unauthorized | `{"error": "invalid_vp_token"}` |
| Untrusted issuer | 401 Unauthorized | `{"error": "untrusted_issuer"}` |
| VC type mismatch | 403 Forbidden | `{"error": "insufficient_scope"}` |
| Internal error | 500 Internal Server Error | `{"error": "server_error"}` |

### 9.2 Service API Errors

| Error | HTTP Status | Response |
|-------|-------------|----------|
| Missing token | 401 Unauthorized | `{"error": "missing_token"}` |
| Invalid token | 401 Unauthorized | `{"error": "invalid_token"}` |
| Policy denied | 403 Forbidden | `{"error": "access_denied"}` |
| Service not found | 404 Not Found | `{"error": "service_not_found"}` |

---

## 10. Testing Strategy

### 10.1 Unit Tests

- **VpVerifierImpl:**
  - VP signature verification
  - VC signature verification
  - Trusted issuer validation
  - Scope validation

- **TokenSignerImpl:**
  - JWT signing
  - JWT verification
  - Claims extraction

- **ServiceAuthorizationFilter:**
  - JWT extraction
  - Policy resolution
  - Policy evaluation
  - Allow/deny logic

### 10.2 Integration Tests

- **End-to-End Flow:**
  - OpenID config discovery
  - VP → Token flow
  - Token → Service API flow
  - Policy enforcement

- **Component Integration:**
  - VpVerifier + Registry API
  - TokenSigner + Policy Engine
  - Filter + AssetIndex + PolicyStore

### 10.3 Test Data

- Mock VP/VC with valid signatures
- Mock trusted issuers
- Mock assets and policies
- Mock Registry API responses

---

## 11. Dependencies

### 11.1 Required Dependencies

```kotlin
dependencies {
    // EDC Core
    implementation(libs.edc.runtime.metamodel)
    implementation(libs.edc.spi.boot)
    implementation(libs.edc.spi.core)
    implementation(libs.edc.spi.web)
    implementation(libs.edc.spi.controlplane)  // AssetIndex
    
    // JWT and VC
    implementation(libs.edc.spi.jwt)
    implementation(libs.edc.spi.vc)
    implementation(libs.edc.spi.identitytrust)
    
    // Policy
    implementation(libs.edc.spi.policyengine)
    
    // Existing components
    implementation(project(":edc-extensions:clearing-house-client"))
}
```

### 11.2 Optional Dependencies

- Rate limiting (if needed)
- Caching (for policy evaluation)
- Metrics/monitoring

---

## 12. Implementation Phases

### Phase 1: Core Infrastructure (Week 1)
- [ ] Create module structure
- [ ] Implement OpenID config endpoint
- [ ] Implement token endpoint (basic)
- [ ] Implement VpVerifier (basic)
- [ ] Implement TokenSigner

### Phase 2: Authorization (Week 2)
- [ ] Implement ServiceAuthorizationFilter
- [ ] Integrate with PolicyEngine
- [ ] Implement policy resolution
- [ ] Add service identification logic

### Phase 3: Integration (Week 3)
- [ ] Integrate with Registry API
- [ ] Integrate with AssetIndex
- [ ] Integrate with PolicyStore
- [ ] Wire all components

### Phase 4: Testing & Documentation (Week 4)
- [ ] Unit tests
- [ ] Integration tests
- [ ] End-to-end tests
- [ ] API documentation
- [ ] Integration guide

---

## 13. Risks and Mitigations

### 13.1 Risks

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| VP verification complexity | High | Medium | Reuse existing DID/VC logic, incremental implementation |
| Policy resolution performance | Medium | Low | Cache policies, optimize queries |
| Token security vulnerabilities | High | Low | Use EDC's JWT infrastructure, security review |
| Integration with existing components | Medium | Medium | Clear interfaces, incremental integration |
| Discovery phase changes | Low | Low | Decoupled design (read from assets) |

### 13.2 Mitigations

- ✅ Incremental implementation (phase by phase)
- ✅ Reuse existing components (DID, VC, Policy)
- ✅ Comprehensive testing
- ✅ Security review
- ✅ Documentation

---

## 14. Success Criteria

### 14.1 Functional Requirements
- ✅ Services discoverable in catalog
- ✅ VP-based authentication works
- ✅ Token issuance works
- ✅ PDP authorization works
- ✅ Service APIs accessible with token

### 14.2 Non-Functional Requirements
- ✅ Response time < 500ms (token endpoint)
- ✅ Response time < 100ms (authorization filter)
- ✅ 99.9% uptime
- ✅ Security audit passed
- ✅ Documentation complete

---

## 15. Open Questions

1. **Scope Format:** Standardize scope format? (`service:telemetry` vs `urn:dataspace:service:telemetry`)
2. **Token Caching:** Cache tokens on consumer side?
3. **Policy Caching:** Cache policies for performance?
4. **Service Path Mapping:** Convention-based or configuration-based?
5. **EBSI Support:** Add EBSI adapters now or later?

---

## 16. Appendix

### 16.1 Related Documents
- `SERVICE_CATALOG_TESTING_GUIDE.md` - Discovery phase testing
- `COMPLIANCE_ANALYSIS.md` - Standards compliance
- `DISCOVERY_RUNTIME_DECOUPLING.md` - Decoupling strategy

### 16.2 References
- FIWARE Data Space Connector documentation
- EBSI VP Token specification
- OAuth 2.0 specification
- OpenID Connect specification
- W3C Verifiable Credentials specification

---

## Approval

- [ ] Tech Lead Review
- [ ] Architecture Review
- [ ] Security Review
- [ ] Implementation Approval

---

**Document Status:** Ready for Review

