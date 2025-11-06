# OAuth2 (Keycloak) vs Trusted Issuers: Key Differences

## 🎯 TL;DR: They're COMPLETELY DIFFERENT!

**OAuth2/Keycloak:**
- **For:** Management API (`/api/management/*`)
- **Who:** Human users, applications, admins
- **What:** JWT tokens from Keycloak
- **Config:** `web.http.management.auth.dac.key.url`

**Trusted Issuers:**
- **For:** Protocol API (`/protocol/*`)
- **Who:** Other connectors (connector-to-connector)
- **What:** Verifiable Credentials (VCs) from trusted issuers
- **Config:** `edc.iam.trusted-issuer.*.id`

**They DON'T interact with each other!** They secure different APIs for different purposes.

---

## 📊 Side-by-Side Comparison

| Aspect | OAuth2/Keycloak | Trusted Issuers |
|--------|----------------|-----------------|
| **API Protected** | `/api/management/*` | `/protocol/*` |
| **Who Uses It** | Human users, applications, admins | Other connectors (machines) |
| **Token Type** | JWT (OAuth2/OIDC) | Verifiable Credential (VC) |
| **Issuer** | Keycloak realm | Catena-X authority (DID) |
| **Validation** | JWT signature (JWKS) | Credential signature + issuer check |
| **Config Property** | `web.http.management.auth.dac.key.url` | `edc.iam.trusted-issuer.*.id` |
| **Purpose** | Who can manage your connector | Which connectors you trust |
| **Example** | Admin logs into UI, calls Management API | Connector requests catalog, presents VC |
| **When Used** | Every Management API call | Every Protocol API call |

---

## 🔐 Two Separate Security Layers

### Layer 1: Management API (OAuth2/Keycloak)

```
┌─────────────────────────────────────────────────────────────┐
│  Management API Security                                     │
│  /api/management/*                                           │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  User/Application                                            │
│  • Admin user                                               │
│  • Management application                                   │
│  • API client                                               │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  1. Get JWT from Keycloak                                    │
│     POST https://keycloak.../token                          │
│     → Receives: JWT token                                   │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  2. Call Management API                                      │
│     GET /api/management/v3/assets                           │
│     Authorization: Bearer <JWT from Keycloak>                │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  3. Connector Validates JWT                                  │
│     a) Extract JWT from Authorization header                │
│     b) Validate signature using JWKS from Keycloak          │
│     c) Check issuer: "https://keycloak.../realms/..."      │
│     d) Check audience: "account"                            │
│     e) ✅ If valid → Allow access                           │
│     f) ❌ If invalid → 401 Unauthorized                     │
└─────────────────────────────────────────────────────────────┘
```

**Config:**
```properties
web.http.management.auth.type=delegated
web.http.management.auth.dac.key.url=https://keycloak.../certs  ← JWKS URL
web.http.management.auth.dac.audience=account
```

**JWT Token (from Keycloak):**
```json
{
  "iss": "https://keycloak.../realms/ceptra",  ← Keycloak issuer
  "aud": "account",
  "sub": "user-id",
  "realm_access": {
    "roles": ["ADMIN", "USER"]
  },
  "exp": 1234567890
}
```

---

### Layer 2: Protocol API (Trusted Issuers)

```
┌─────────────────────────────────────────────────────────────┐
│  Protocol API Security                                       │
│  /protocol/*                                                 │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  Other Connector (Machine-to-Machine)                        │
│  • Supplier A's connector                                   │
│  • BMW's connector                                          │
│  • Any Catena-X connector                                   │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  1. Get Verifiable Credential                                │
│     • Connector gets credential from MIW                    │
│     • Credential issued by Catena-X                         │
│     • Contains MembershipCredential                         │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  2. Call Protocol API                                        │
│     POST /protocol/v2/catalog/request                       │
│     Authorization: Bearer <JWT containing VerifiableCredential> │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  3. Connector Validates Credential                           │
│     a) Extract VerifiableCredential from JWT                │
│     b) Check issuer: "did:web:catena-x.net:issuer"         │
│     c) Check TrustedIssuerRegistry:                         │
│        → Is issuer trusted? ✅                              │
│     d) Check credential type: "MembershipCredential"        │
│        → Is this type accepted? ✅                          │
│     e) Validate cryptographic signature                     │
│     f) ✅ If valid → Allow access                           │
│     g) ❌ If invalid → 401 Unauthorized                     │
└─────────────────────────────────────────────────────────────┘
```

**Config:**
```properties
edc.iam.trusted-issuer.0.id=did:web:catena-x.net:issuer  ← Trusted issuer DID
edc.iam.trusted-issuer.0.supportedTypes=MembershipCredential
```

**Verifiable Credential (from Catena-X):**
```json
{
  "type": ["VerifiableCredential", "MembershipCredential"],
  "issuer": "did:web:catena-x.net:issuer",  ← Trusted issuer DID
  "credentialSubject": {
    "holderIdentifier": "BPNL1234567890"
  },
  "proof": { ... }  // Cryptographic signature
}
```

---

## 🔍 Key Differences Explained

### 1. Different APIs

**OAuth2/Keycloak:**
- **Endpoint:** `/api/management/*`
- **Port:** Usually `28080`
- **Purpose:** Human/application management operations
- **Example:** Create asset, view policies, initiate transfers

**Trusted Issuers:**
- **Endpoint:** `/protocol/*`
- **Port:** Usually `28081`
- **Purpose:** Connector-to-connector communication
- **Example:** Catalog request, contract negotiation, data transfer

### 2. Different Token Types

**OAuth2 JWT (from Keycloak):**
```json
{
  "iss": "https://keycloak.../realms/ceptra",  ← HTTPS URL
  "aud": "account",
  "sub": "user-id",
  "realm_access": {
    "roles": ["ADMIN"]
  }
}
```
- **Format:** Standard OAuth2/OIDC JWT
- **Issuer:** Keycloak realm URL (HTTPS)
- **Contains:** User roles, permissions
- **Used for:** User authentication

**Verifiable Credential (from Catena-X):**
```json
{
  "type": ["VerifiableCredential", "MembershipCredential"],
  "issuer": "did:web:catena-x.net:issuer",  ← DID (Decentralized ID)
  "credentialSubject": {
    "holderIdentifier": "BPNL1234567890"
  }
}
```
- **Format:** Verifiable Credential (W3C standard)
- **Issuer:** DID (Decentralized Identifier)
- **Contains:** BPN, membership proof, business info
- **Used for:** Connector identity in dataspace

### 3. Different Validation

**OAuth2 Validation:**
```
1. Extract JWT from Authorization header
2. Get JWKS from Keycloak (JWKS URL)
3. Validate JWT signature using JWKS
4. Check issuer claim matches Keycloak realm
5. Check audience claim
6. Extract roles from realm_access.roles
```

**Trusted Issuer Validation:**
```
1. Extract VerifiableCredential from JWT
2. Get issuer DID from credential
3. Check TrustedIssuerRegistry: Is issuer trusted?
4. Check credential type: Is type accepted?
5. Validate credential signature (cryptographic)
6. Extract BPN from credentialSubject
```

### 4. Different Config Properties

**OAuth2 Config:**
```properties
# Management API OAuth2
web.http.management.auth.type=delegated
web.http.management.auth.dac.key.url=https://keycloak.../certs  ← JWKS URL
web.http.management.auth.dac.audience=account
```

**Trusted Issuers Config:**
```properties
# Protocol API Trusted Issuers
edc.iam.trusted-issuer.0.id=did:web:catena-x.net:issuer  ← DID
edc.iam.trusted-issuer.0.supportedTypes=MembershipCredential
```

---

## 🎯 Real-World Example: Both in Action

### Scenario: Admin creates asset, then connector shares it

**Step 1: Admin Uses Management API (OAuth2/Keycloak)**

```
Admin User
    ↓
1. Logs into Keycloak UI
2. Gets JWT token from Keycloak
3. Calls Management API:
   POST /api/management/v3/assets
   Authorization: Bearer <Keycloak JWT>
    ↓
4. Connector validates JWT:
   - Check issuer: Keycloak realm ✅
   - Extract roles: ["ADMIN"] ✅
   - ✅ Allow access
    ↓
5. Asset created successfully
```

**Step 2: Another Connector Accesses Catalog (Trusted Issuers)**

```
Supplier A's Connector
    ↓
1. Gets VerifiableCredential from Catena-X
2. Calls Protocol API:
   POST /protocol/v2/catalog/request
   Authorization: Bearer <JWT with VC>
    ↓
3. Connector validates VC:
   - Extract issuer: "did:web:catena-x.net:issuer"
   - Check TrustedIssuerRegistry: ✅ Trusted
   - Check type: "MembershipCredential" ✅ Accepted
   - ✅ Allow access
    ↓
4. Catalog returned with assets
```

**Notice:** These are TWO SEPARATE requests to TWO DIFFERENT APIs!

---

## 📋 Summary: What Governs What?

### Management API Security (OAuth2/Keycloak)

**Governed by:**
- `web.http.management.auth.dac.key.url` → JWKS URL
- `web.http.management.auth.dac.audience` → Audience
- Keycloak realm configuration

**What it controls:**
- Who can call `/api/management/*`
- User roles (ADMIN, USER, etc.)
- RBAC (Role-Based Access Control)

**Example:**
```
User → Keycloak → JWT → Management API → Connector
```

### Protocol API Security (Trusted Issuers)

**Governed by:**
- `edc.iam.trusted-issuer.*.id` → Trusted issuer DIDs
- `edc.iam.trusted-issuer.*.supportedTypes` → Accepted credential types

**What it controls:**
- Which connectors can call `/protocol/*`
- Which credential issuers are trusted
- Which credential types are accepted

**Example:**
```
Connector → Catena-X → VC → Protocol API → Connector
```

---

## ❌ Common Misconceptions

### ❌ "Trusted issuers are like OAuth providers"

**Wrong!** They're completely different:
- **OAuth provider:** Issues JWT tokens for users
- **Trusted issuer:** Issues Verifiable Credentials for connectors

### ❌ "Trusted issuers use JWKS URL"

**Wrong!** Trusted issuers use **DIDs**, not JWKS URLs:
- **OAuth2:** `https://keycloak.../certs` (JWKS URL)
- **Trusted Issuers:** `did:web:catena-x.net:issuer` (DID)

### ❌ "They validate the same thing"

**Wrong!** They validate different things:
- **OAuth2:** Validates JWT signature, issuer (Keycloak), audience, roles
- **Trusted Issuers:** Validates VC signature, issuer DID, credential type, BPN

### ❌ "One replaces the other"

**Wrong!** They work together:
- **OAuth2** secures Management API (human access)
- **Trusted Issuers** secures Protocol API (connector access)

---

## ✅ Correct Understanding

### Two Security Systems, Two APIs

```
┌─────────────────────────────────────────────────────────────┐
│  Your Connector                                               │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  Management API (Port 28080)                         │  │
│  │  /api/management/*                                    │  │
│  │                                                      │  │
│  │  Security: OAuth2/Keycloak                           │  │
│  │  • Validates JWT from Keycloak                       │  │
│  │  • Checks JWKS URL                                   │  │
│  │  • Extracts user roles                               │  │
│  │                                                      │  │
│  │  Config:                                             │  │
│  │  web.http.management.auth.dac.key.url=...            │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  Protocol API (Port 28081)                           │  │
│  │  /protocol/*                                          │  │
│  │                                                      │  │
│  │  Security: Trusted Issuers                           │  │
│  │  • Validates Verifiable Credentials                  │  │
│  │  • Checks TrustedIssuerRegistry                      │  │
│  │  • Validates credential types                        │  │
│  │                                                      │  │
│  │  Config:                                             │  │
│  │  edc.iam.trusted-issuer.*.id=...                     │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

---

## 🎯 Quick Reference

### OAuth2/Keycloak (Management API)

| Property | Value | Purpose |
|----------|-------|---------|
| `web.http.management.auth.type` | `delegated` | Enable OAuth2 |
| `web.http.management.auth.dac.key.url` | JWKS URL from Keycloak | Where to get signing keys |
| `web.http.management.auth.dac.audience` | Audience (e.g., `account`) | What audience to accept |
| **API** | `/api/management/*` | Human/application access |
| **Token** | JWT from Keycloak | OAuth2/OIDC token |

### Trusted Issuers (Protocol API)

| Property | Value | Purpose |
|----------|-------|---------|
| `edc.iam.trusted-issuer.0.id` | DID (e.g., `did:web:catena-x.net:issuer`) | Which issuer to trust |
| `edc.iam.trusted-issuer.0.supportedTypes` | Credential types (e.g., `MembershipCredential`) | Which types to accept |
| **API** | `/protocol/*` | Connector-to-connector |
| **Token** | Verifiable Credential | W3C VC format |

---

## 📊 Complete Flow: Both Systems Working

### Example: Admin creates asset, then connector shares it

```
┌─────────────────────────────────────────────────────────────┐
│  STEP 1: Admin Creates Asset (Management API)               │
│                                                              │
│  1. Admin logs into Keycloak                                │
│  2. Gets JWT: {iss: "keycloak...", roles: ["ADMIN"]}       │
│  3. POST /api/management/v3/assets                          │
│     Authorization: Bearer <Keycloak JWT>                    │
│  4. Connector validates using OAuth2/JWKS                   │
│  5. ✅ Asset created                                        │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  STEP 2: Connector Shares Asset (Protocol API)              │
│                                                              │
│  1. Supplier's connector gets VC from Catena-X              │
│  2. VC: {issuer: "did:web:catena-x.net:issuer", ...}       │
│  3. POST /protocol/v2/catalog/request                       │
│     Authorization: Bearer <JWT with VC>                     │
│  4. Connector validates using TrustedIssuerRegistry         │
│  5. ✅ Catalog returned (includes asset from Step 1)        │
└─────────────────────────────────────────────────────────────┘
```

**Notice:** 
- Step 1 uses **OAuth2** (Management API)
- Step 2 uses **Trusted Issuers** (Protocol API)
- They're **completely separate**!

---

## 🔐 Security Model Summary

### Management API: OAuth2/Keycloak

**Question:** "Who can manage my connector?"
**Answer:** Users with valid Keycloak JWT tokens

**Flow:**
```
User → Keycloak → JWT → Management API → RBAC Filter → Resource
```

### Protocol API: Trusted Issuers

**Question:** "Which connectors can I trust?"
**Answer:** Connectors with valid Verifiable Credentials from trusted issuers

**Flow:**
```
Connector → Catena-X → VC → Protocol API → TrustedIssuerRegistry → Policy Check → Resource
```

---

## ✅ Answer to Your Questions

### Q: "Are trusted issuers just like OAuth provider issuers?"

**A:** **NO!** They're completely different:
- **OAuth provider (Keycloak):** Issues JWT tokens for **users** to access **Management API**
- **Trusted issuer (Catena-X):** Issues Verifiable Credentials for **connectors** to access **Protocol API**

### Q: "If their request data, issuer and membership type extracted from JWT, does it mean securing the app is governed by trusted issuers, not auth JWKS URL?"

**A:** **DEPENDS ON WHICH API!**
- **Management API:** Secured by **OAuth2/JWKS URL** (Keycloak)
- **Protocol API:** Secured by **Trusted Issuers** (Catena-X)

**They secure DIFFERENT APIs!**

### Q: "Currently requests are being governed by Keycloak from OAuth2 configuration values"

**A:** **YES, but only for Management API!**
- **Management API** (`/api/management/*`) → Uses **OAuth2/Keycloak**
- **Protocol API** (`/protocol/*`) → Uses **Trusted Issuers**

**Both are active simultaneously, securing different APIs!**

---

## 🎯 Bottom Line

**You have TWO security systems:**

1. **OAuth2/Keycloak** → Secures Management API (human access)
   - Config: `web.http.management.auth.dac.key.url`
   - Validates: JWT from Keycloak

2. **Trusted Issuers** → Secures Protocol API (connector access)
   - Config: `edc.iam.trusted-issuer.*.id`
   - Validates: Verifiable Credentials from trusted issuers

**They work together but don't interfere with each other!** 🚀

