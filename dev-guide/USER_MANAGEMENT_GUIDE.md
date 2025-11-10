# User Management Guide for EDC Connector

## 🎯 Overview

This guide explains how to implement user management for the Tractus-X EDC connector, covering different user types, authentication methods, and integration with Keycloak.

---

## 📊 Understanding User Types in EDC

The EDC connector handles **three distinct types of "users"** that serve different purposes:

### 1. **Human Users** (Management API)
- **Who:** Administrators, operators, developers
- **Purpose:** Manage the connector (create assets, policies, monitor transfers)
- **Authentication:** OAuth2/OIDC via Keycloak
- **API:** `/api/management/*`
- **Token Type:** JWT from Keycloak
- **Example:** Admin creates an asset via Management API

### 2. **Connectors** (Protocol API)
- **Who:** Other EDC connectors in the dataspace
- **Purpose:** Connector-to-connector communication (catalog requests, contract negotiation, data transfer)
- **Authentication:** Verifiable Credentials (VCs) from trusted issuers
- **API:** `/protocol/*`
- **Token Type:** Verifiable Credential (W3C standard)
- **Example:** Supplier's connector requests catalog from your connector

### 3. **Applications/Service Accounts** (Management API)
- **Who:** Automated systems, CI/CD pipelines, monitoring tools
- **Purpose:** Programmatic access to Management API
- **Authentication:** OAuth2/OIDC via Keycloak (service accounts)
- **API:** `/api/management/*`
- **Token Type:** JWT from Keycloak (client credentials flow)
- **Example:** CI/CD pipeline creates assets automatically

---

## 🔐 Authentication Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    EDC Connector                                 │
│                                                                  │
│  ┌──────────────────────┐      ┌──────────────────────┐        │
│  │  Management API      │      │  Protocol API        │        │
│  │  /api/management/*   │      │  /protocol/*         │        │
│  └──────────────────────┘      └──────────────────────┘        │
│           │                              │                      │
│           │ OAuth2/JWT                   │ Verifiable           │
│           │ (Keycloak)                   │ Credentials          │
│           │                              │ (Trusted Issuers)    │
│           ↓                              ↓                      │
│  ┌──────────────────────┐      ┌──────────────────────┐        │
│  │  JWT Validation      │      │  VC Validation       │        │
│  │  Filter              │      │  (TrustedIssuer      │        │
│  │                      │      │   Registry)          │        │
│  └──────────────────────┘      └──────────────────────┘        │
│           │                              │                      │
│           │                              │                      │
│           ↓                              ↓                      │
│  ┌──────────────────────────────────────────────────┐          │
│  │         Resource Controllers                     │          │
│  └──────────────────────────────────────────────────┘          │
└─────────────────────────────────────────────────────────────────┘
           │                              │
           │                              │
           ↓                              ↓
┌──────────────────────┐      ┌──────────────────────┐
│  Keycloak            │      │  Catena-X /          │
│  (OAuth2 Provider)   │      │  Trusted Issuers     │
│                      │      │  (VC Issuers)        │
│  • User accounts     │      │                      │
│  • Service accounts  │      │  • MembershipCred    │
│  • Roles & Perms     │      │  • BusinessPartner   │
└──────────────────────┘      └──────────────────────┘
```

**Key Point:** These are **completely separate** authentication systems that don't interact with each other!

---

## 👥 User Management for Human Users & Applications

This section focuses on managing **human users and applications** that access the Management API via Keycloak.

### Current Setup

Your connector uses Keycloak for OAuth2/OIDC authentication:
- **JWKS URL:** `edc.oauth.provider.jwks.url`
- **Issuer:** `edc.oauth.provider.issuer`
- **Audience:** `edc.oauth.provider.audience`
- **Custom Filter:** `HotReloadableJwtValidationFilter` validates JWT tokens

**All user management happens in Keycloak**, not in the EDC connector itself.

---

## 🎨 User Management Approaches

### Option 1: Admin-Created Users (Recommended for Enterprise)

**Best for:** Organizations with centralized IT management, strict access control

**How it works:**
1. Keycloak administrator creates user accounts manually
2. Admin assigns roles (ADMIN, USER, READ_ONLY, etc.)
3. Users receive credentials via secure channel
4. Users log in to Keycloak and get JWT tokens
5. Users call Management API with JWT tokens

**Pros:**
- ✅ Full control over who has access
- ✅ Centralized user management
- ✅ Audit trail of user creation
- ✅ No risk of unauthorized sign-ups
- ✅ Easy to revoke access

**Cons:**
- ❌ Requires admin intervention for each new user
- ❌ Manual process can be slow
- ❌ Admin overhead

**Implementation:**
```
Keycloak Admin Console
    ↓
Create User → Assign Roles → Set Password
    ↓
User logs in → Gets JWT → Accesses Management API
```

**Keycloak Configuration:**
- **User Registration:** Disabled
- **Email Verification:** Optional (recommended)
- **Password Policy:** Enforced
- **Roles:** Defined in Keycloak realm

---

### Option 2: Self-Registration (User Sign-Up)

**Best for:** Open platforms, developer portals, self-service scenarios

**How it works:**
1. User visits Keycloak registration page
2. User creates account (email, password)
3. Email verification (optional but recommended)
4. Admin approval (optional - can be automatic)
5. User receives default role (e.g., USER)
6. User can access Management API

**Pros:**
- ✅ No admin overhead for user creation
- ✅ Faster onboarding
- ✅ Self-service model
- ✅ Good for developer portals

**Cons:**
- ❌ Risk of unauthorized sign-ups
- ❌ Requires email verification
- ❌ May need admin approval workflow
- ❌ Spam/abuse potential

**Implementation:**
```
User → Keycloak Registration Page → Create Account
    ↓
Email Verification (if enabled)
    ↓
Admin Approval (if enabled) OR Auto-approve
    ↓
User gets default role → Accesses Management API
```

**Keycloak Configuration:**
- **User Registration:** Enabled
- **Email Verification:** **Required** (strongly recommended)
- **Admin Approval:** Optional (recommended for production)
- **Default Role:** Assign automatically (e.g., USER)
- **Password Policy:** Enforced

**Security Considerations:**
- Enable email verification to prevent fake accounts
- Consider admin approval for production environments
- Implement rate limiting on registration
- Monitor for spam/abuse

---

### Option 3: Social Sign-In (OAuth2/OIDC Providers)

**Best for:** Developer-friendly platforms, integration with corporate SSO

**How it works:**
1. User clicks "Sign in with Google/GitHub/etc."
2. Redirected to social provider (Google, GitHub, Microsoft, etc.)
3. User authenticates with social provider
4. Keycloak receives OAuth2 callback
5. Keycloak creates user account (if first time) or links to existing
6. User gets JWT from Keycloak
7. User accesses Management API

**Pros:**
- ✅ No password management
- ✅ Familiar UX for users
- ✅ Integration with corporate SSO (Azure AD, Google Workspace)
- ✅ Reduced support burden (no password resets)

**Cons:**
- ❌ Dependency on external providers
- ❌ Privacy considerations
- ❌ Requires OAuth2 client setup for each provider
- ❌ May need account linking logic

**Supported Providers:**
- Google
- GitHub
- Microsoft Azure AD
- Facebook
- Any OAuth2/OIDC provider

**Implementation:**
```
User → "Sign in with Google" → Google OAuth
    ↓
Keycloak receives callback → Creates/Links account
    ↓
User gets JWT from Keycloak → Accesses Management API
```

**Keycloak Configuration:**
- **Identity Providers:** Configure Google, GitHub, etc.
- **First Login Flow:** Create user automatically or require admin approval
- **Account Linking:** Link social accounts to existing Keycloak users
- **Role Mapping:** Assign roles based on provider/email domain

**Example: Auto-assign ADMIN role for corporate email domain:**
```
If user email ends with "@yourcompany.com" → Assign ADMIN role
Else → Assign USER role
```

---

### Option 4: Hybrid Approach (Recommended)

**Best for:** Most production scenarios

**Combination of:**
- **Admin-created accounts** for privileged users (admins, operators)
- **Self-registration** for regular users (developers, analysts)
- **Social sign-in** as optional convenience

**Implementation:**
```
┌─────────────────────────────────────────────────┐
│           Keycloak User Management              │
│                                                 │
│  ┌──────────────┐  ┌──────────────┐           │
│  │   Admin      │  │  Self-       │           │
│  │   Created    │  │  Registration│           │
│  │              │  │              │           │
│  │  • Admins    │  │  • Developers│           │
│  │  • Operators │  │  • Analysts  │           │
│  └──────────────┘  └──────────────┘           │
│           │              │                     │
│           └──────┬───────┘                     │
│                  │                             │
│           ┌──────▼──────┐                      │
│           │  Social     │                      │
│           │  Sign-In    │                      │
│           │  (Optional) │                      │
│           └─────────────┘                      │
│                  │                             │
│                  ↓                             │
│           ┌──────────────┐                     │
│           │  Keycloak    │                     │
│           │  Realm       │                     │
│           │  (Users +    │                     │
│           │   Roles)     │                     │
│           └──────────────┘                     │
│                  │                             │
│                  ↓                             │
│           JWT Tokens → Management API          │
└─────────────────────────────────────────────────┘
```

---

## 🔧 Implementation Recommendations

### For Enterprise/Production Environments

**Recommended Setup:**
1. **Admin-created accounts** for all users initially
2. **Email verification** required
3. **Strong password policy** enforced
4. **Role-based access control** (RBAC) via Keycloak roles
5. **Audit logging** enabled
6. **Regular access reviews**

**Keycloak Configuration:**
```properties
# User Registration
User Registration: Disabled
Email Verification: Required
Admin Approval: Required (for new sign-ups)

# Password Policy
Minimum Length: 12 characters
Require Uppercase: Yes
Require Lowercase: Yes
Require Numbers: Yes
Require Special Characters: Yes
Password History: 5 previous passwords

# Session Management
Session Timeout: 30 minutes
Remember Me: Enabled (7 days)
```

---

### For Developer/Test Environments

**Recommended Setup:**
1. **Self-registration** enabled
2. **Email verification** required
3. **Auto-approval** for convenience
4. **Social sign-in** optional (GitHub, Google)
5. **Default role:** USER (limited permissions)

**Keycloak Configuration:**
```properties
# User Registration
User Registration: Enabled
Email Verification: Required
Admin Approval: Disabled (auto-approve)

# Default Role
Default Role: USER

# Identity Providers
GitHub: Enabled (for developers)
Google: Enabled (optional)
```

---

## 🎭 Role-Based Access Control (RBAC)

### Recommended Roles

Define roles in Keycloak that map to Management API permissions:

| Role | Permissions | Use Case |
|------|-------------|----------|
| **ADMIN** | Full access to all Management API endpoints | System administrators |
| **ASSET_MANAGER** | Create, update, delete assets | Data stewards |
| **POLICY_WRITER** | Create, update policies | Policy administrators |
| **CONTRACT_WRITER** | Create contract definitions | Business users |
| **USER** | Read-only access (GET requests) | Analysts, viewers |
| **SERVICE_ACCOUNT** | Programmatic access (specific endpoints) | CI/CD, automation |

### Role Assignment

**Option 1: Manual Assignment (Admin Console)**
- Admin assigns roles manually in Keycloak
- Full control, but requires admin action

**Option 2: Automatic Assignment (Based on Email Domain)**
- Users with `@yourcompany.com` → ADMIN
- Users with `@partner.com` → USER
- Configured in Keycloak user federation or authentication flow

**Option 3: Group-Based Roles**
- Create groups in Keycloak (e.g., "Data Stewards", "Analysts")
- Assign roles to groups
- Users inherit roles from groups

---

## 🔌 Integration with EDC Connector

### Current Implementation

Your connector uses a **custom JWT validation filter** (`HotReloadableJwtValidationFilter`) that:
1. Validates JWT tokens from Keycloak
2. Extracts roles from JWT claims
3. Sets security context for downstream filters

### Role Extraction

The JWT token from Keycloak contains roles in this format:
```json
{
  "iss": "https://keycloak.../realms/ceptra",
  "aud": "account",
  "sub": "user-123",
  "realm_access": {
    "roles": ["ADMIN", "USER"]
  },
  "resource_access": {
    "management-api": {
      "roles": ["ASSET_MANAGER"]
    }
  }
}
```

**Your `RoleBasedAccessFilter` already extracts these roles** and enforces RBAC on Management API endpoints.

### No Additional Extension Needed

**You don't need a separate extension for user management** because:
- ✅ Keycloak handles all user management (creation, authentication, roles)
- ✅ Your custom JWT filter validates tokens
- ✅ Your RBAC filter enforces permissions
- ✅ The connector doesn't store user data (stateless)

**The connector is a consumer of Keycloak's user management, not a provider.**

---

## 📋 User Management Workflow

### Complete Flow: User Creation to API Access

```
┌─────────────────────────────────────────────────────────────┐
│  STEP 1: User Creation (Keycloak)                          │
│                                                             │
│  Option A: Admin Creates User                              │
│  ┌─────────────────────────────────────┐                   │
│  │ Keycloak Admin Console              │                   │
│  │  • Create user                      │                   │
│  │  • Set email/password               │                   │
│  │  • Assign roles (ADMIN, USER, etc.) │                   │
│  └─────────────────────────────────────┘                   │
│                                                             │
│  Option B: Self-Registration                               │
│  ┌─────────────────────────────────────┐                   │
│  │ User → Registration Page            │                   │
│  │  • Enter email/password             │                   │
│  │  • Email verification               │                   │
│  │  • Auto-assign default role (USER)  │                   │
│  └─────────────────────────────────────┘                   │
│                                                             │
│  Option C: Social Sign-In                                  │
│  ┌─────────────────────────────────────┐                   │
│  │ User → "Sign in with Google"        │                   │
│  │  • Authenticate with Google         │                   │
│  │  • Keycloak creates/links account   │                   │
│  │  • Assign roles based on email      │                   │
│  └─────────────────────────────────────┘                   │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  STEP 2: User Authentication (Keycloak)                    │
│                                                             │
│  User logs in to Keycloak                                  │
│    ↓                                                        │
│  Keycloak validates credentials                             │
│    ↓                                                        │
│  Keycloak issues JWT token with roles                      │
│    ↓                                                        │
│  JWT: {roles: ["ADMIN", "USER"], sub: "user-123", ...}    │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  STEP 3: API Access (EDC Connector)                        │
│                                                             │
│  User calls Management API                                 │
│  POST /api/management/v3/assets                            │
│  Authorization: Bearer <JWT from Keycloak>                 │
│    ↓                                                        │
│  HotReloadableJwtValidationFilter                          │
│    • Validates JWT signature (JWKS)                        │
│    • Validates issuer, audience, expiration                │
│    • Extracts roles from JWT                               │
│    ↓                                                        │
│  RoleBasedAccessFilter                                     │
│    • Checks if user has required role                      │
│    • ADMIN → ✅ Allow                                     │
│    • USER → ❌ Deny (needs ASSET_MANAGER)                 │
│    ↓                                                        │
│  Asset Controller                                          │
│    • Creates asset                                         │
│    • Returns success                                       │
└─────────────────────────────────────────────────────────────┘
```

---

## 🚀 Implementation Steps

### Step 1: Configure Keycloak

1. **Create Realm** (if not exists)
   - Name: `ceptra` (or your realm name)
   - Enabled: Yes

2. **Configure User Registration** (choose approach)
   - Admin-created: Registration disabled
   - Self-registration: Registration enabled, email verification required
   - Social sign-in: Configure identity providers

3. **Define Roles**
   - Create roles: ADMIN, USER, ASSET_MANAGER, POLICY_WRITER, etc.
   - Map roles to Management API permissions

4. **Configure Client** (for Management API)
   - Client ID: `management-api` (or your client ID)
   - Access Type: `confidential` or `public`
   - Valid Redirect URIs: Your connector URLs
   - Audience: `account` (matches your config)

### Step 2: Update EDC Configuration

Your connector already uses:
```properties
edc.oauth.provider.jwks.url=https://keycloak.../realms/ceptra/protocol/openid-connect/certs
edc.oauth.provider.issuer=https://keycloak.../realms/ceptra
edc.oauth.provider.audience=account
```

**No changes needed** - your custom filter already reads these properties.

### Step 3: Test User Management

1. **Create test user in Keycloak**
2. **Assign role** (e.g., ADMIN)
3. **Get JWT token** from Keycloak
4. **Call Management API** with JWT
5. **Verify RBAC** works correctly

---

## 🔒 Security Best Practices

### 1. **Password Policy**
- Minimum 12 characters
- Require complexity (uppercase, lowercase, numbers, special chars)
- Password history (prevent reuse)
- Regular password rotation (optional)

### 2. **Session Management**
- Short session timeout (30 minutes)
- Remember me option (7 days max)
- Single sign-out (logout from all devices)

### 3. **Email Verification**
- **Always required** for self-registration
- Prevents fake accounts
- Enables password reset

### 4. **Admin Approval**
- **Recommended** for production
- Prevents unauthorized access
- Allows review before granting access

### 5. **Role Management**
- Principle of least privilege
- Regular access reviews
- Remove unused roles
- Audit role assignments

### 6. **Monitoring & Auditing**
- Log all user creation/deletion
- Log role assignments
- Monitor failed login attempts
- Alert on suspicious activity

---

## 📊 Comparison: User Management Approaches

| Aspect | Admin-Created | Self-Registration | Social Sign-In | Hybrid |
|--------|---------------|-------------------|----------------|--------|
| **Security** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| **User Experience** | ⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| **Admin Overhead** | High | Low | Low | Medium |
| **Control** | Full | Limited | Limited | Full |
| **Best For** | Enterprise | Developer Portal | Modern Apps | Production |

---

## 🎯 Recommendations by Use Case

### Enterprise/Production
- ✅ **Admin-created accounts** for all users
- ✅ **Email verification** required
- ✅ **Strong password policy**
- ✅ **Admin approval** for new accounts
- ✅ **Regular access reviews**

### Developer Portal
- ✅ **Self-registration** enabled
- ✅ **Email verification** required
- ✅ **Social sign-in** (GitHub, Google)
- ✅ **Auto-approval** with default USER role
- ✅ **Admin can promote** to higher roles

### Internal Tools
- ✅ **Admin-created** for admins
- ✅ **Self-registration** for developers
- ✅ **Corporate SSO** (Azure AD, Google Workspace)
- ✅ **Group-based roles**

### Public API
- ✅ **Self-registration** with email verification
- ✅ **Social sign-in** options
- ✅ **Rate limiting** on registration
- ✅ **Admin approval** workflow
- ✅ **Default limited role** (USER)

---

## 🔑 Token Generation: Keycloak Only

### Important: No Internal Token API

**The EDC connector does NOT have an internal API to generate OAuth2/JWT tokens from credentials.**

**Users and clients must connect to Keycloak directly to obtain tokens.**

### Token Generation Flow

```
┌─────────────────────────────────────────────────────────────┐
│  User/Client                                                 │
│  (Username/Password OR Client Credentials)                  │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  STEP 1: Request Token from Keycloak                        │
│                                                             │
│  POST https://keycloak.../realms/ceptra/protocol/          │
│       openid-connect/token                                  │
│                                                             │
│  For User (Resource Owner Password Credentials):           │
│  • grant_type=password                                      │
│  • username=user@example.com                               │
│  • password=userpassword                                    │
│  • client_id=management-api                                 │
│  • client_secret=secret (if confidential client)           │
│                                                             │
│  OR                                                         │
│                                                             │
│  For Service Account (Client Credentials):                 │
│  • grant_type=client_credentials                           │
│  • client_id=service-account-client                        │
│  • client_secret=client-secret                             │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  STEP 2: Keycloak Validates & Issues Token                 │
│                                                             │
│  Keycloak validates credentials                            │
│    ↓                                                        │
│  Returns JWT token:                                        │
│  {                                                          │
│    "access_token": "eyJhbGc...",                          │
│    "token_type": "Bearer",                                 │
│    "expires_in": 3600,                                     │
│    "refresh_token": "..."                                  │
│  }                                                          │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  STEP 3: Use Token with Management API                     │
│                                                             │
│  POST /api/management/v3/assets                            │
│  Authorization: Bearer <access_token from Keycloak>        │
│                                                             │
│  Connector validates token using JWKS from Keycloak        │
└─────────────────────────────────────────────────────────────┘
```

### Why No Internal Token API?

**Security & Architecture Reasons:**
1. **Separation of Concerns:** Keycloak is the identity provider - it should handle authentication
2. **Security:** The connector doesn't store user credentials (passwordless)
3. **Standards Compliance:** OAuth2/OIDC standard requires token endpoint at the authorization server (Keycloak)
4. **Stateless Design:** Connector only validates tokens, doesn't issue them

### Token Endpoints

**Keycloak Token Endpoint:**
```
POST https://keycloak.example.com/realms/ceptra/protocol/openid-connect/token
```

**EDC Connector Endpoints:**
- ❌ **No token generation endpoint** - Users must use Keycloak
- ✅ **Token validation** - Built into `HotReloadableJwtValidationFilter`
- ✅ **Management API** - `/api/management/*` (requires valid JWT from Keycloak)

### Example: Getting a Token

**For Human Users (Password Grant):**
```bash
curl -X POST https://keycloak.example.com/realms/ceptra/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "username=admin@example.com" \
  -d "password=adminpassword" \
  -d "client_id=management-api" \
  -d "client_secret=your-client-secret"
```

**Response:**
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "refresh_token": "..."
}
```

**For Service Accounts (Client Credentials):**
```bash
curl -X POST https://keycloak.example.com/realms/ceptra/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=service-account-client" \
  -d "client_secret=client-secret"
```

### Can You Add an Internal Token API?

**Technically possible, but NOT recommended:**

You could create a Management API extension that:
1. Accepts username/password or client credentials
2. Calls Keycloak's token endpoint internally
3. Returns the token to the client

**However, this is NOT recommended because:**
- ❌ Violates OAuth2 security best practices
- ❌ Exposes credentials to the connector (security risk)
- ❌ Adds unnecessary complexity
- ❌ Bypasses Keycloak's security features (MFA, rate limiting, etc.)
- ❌ Makes the connector stateful (needs to handle credentials)

**Best Practice:** Always have clients connect directly to Keycloak for tokens.

---

## ❓ FAQ

### Q: Do I need a separate extension for user management?

**A:** **No.** Keycloak handles all user management. The EDC connector only validates JWT tokens and enforces RBAC. No additional extension needed.

### Q: Can the connector generate tokens from username/password?

**A:** **No.** The connector does NOT have an internal API to generate tokens. Users and clients must connect to Keycloak directly using Keycloak's token endpoint (`/protocol/openid-connect/token`).

### Q: Can users be managed from the EDC connector?

**A:** **Not directly.** User management happens in Keycloak. However, you could create a Management API endpoint that calls Keycloak's Admin API to create users programmatically (advanced use case).

### Q: How do I revoke user access?

**A:** **In Keycloak:**
- Disable the user account
- Remove roles
- Delete the user account
- Revoke all sessions

The connector will automatically reject JWT tokens from disabled/deleted users.

### Q: Can I use multiple Keycloak realms?

**A:** **Yes, but requires configuration changes.** Your custom filter currently uses a single JWKS URL. To support multiple realms, you'd need to:
1. Modify the filter to check issuer claim
2. Maintain a map of issuer → JWKS URL
3. Update configuration to support multiple realms

### Q: How do service accounts work?

**A:** **Service accounts in Keycloak:**
1. Create a client in Keycloak with "Service Accounts Enabled"
2. Use client credentials flow to get JWT
3. Assign roles to the service account
4. Use JWT to access Management API

### Q: What about connector-to-connector users?

**A:** **Different system entirely.** Connector-to-connector communication uses Verifiable Credentials (VCs), not Keycloak. See `OAUTH2_VS_TRUSTED_ISSUERS.md` for details.

---

## 📚 Related Documentation

- `OAUTH2_VS_TRUSTED_ISSUERS.md` - Understanding OAuth2 vs Trusted Issuers
- `OAUTH2_HOT_RELOAD_README.md` - Custom OAuth2 hot reload implementation
- Keycloak Documentation: https://www.keycloak.org/documentation

---

## ✅ Summary

**User management for the EDC connector:**
- ✅ **Handled entirely by Keycloak** (no connector extension needed)
- ✅ **Three approaches:** Admin-created, self-registration, social sign-in
- ✅ **Recommended:** Hybrid approach (admin-created + self-registration)
- ✅ **RBAC:** Roles defined in Keycloak, enforced by `RoleBasedAccessFilter`
- ✅ **Security:** Email verification, strong passwords, admin approval
- ✅ **Integration:** Your custom JWT filter validates tokens automatically

**The connector is a consumer of Keycloak's user management - configure Keycloak, and the connector will work seamlessly!**

