# How Trusted Issuers Work in Practice - Catena-X Dataspace

## 🎯 What Are Trusted Issuers? (Real-World Example)

Think of **Trusted Issuers** like **passport offices** in the real world:

- **Passport Office** = Trusted Issuer (e.g., `did:web:catena-x.net:issuer`)
- **Passport** = Verifiable Credential (e.g., `MembershipCredential`)
- **You** = Data Connector trying to access data
- **Border Control** = Your connector checking if the passport is valid

**In Catena-X:**
- Companies need **credentials** to prove they're legitimate members
- Only **trusted issuers** (like Catena-X authority) can issue valid credentials
- Your connector **trusts** credentials from these issuers
- **Other connectors** must present credentials from trusted issuers to access your data

---

## 🔄 How Hot Reload Works (Step-by-Step)

### The Mechanism

```
┌─────────────────────────────────────────────────────────────┐
│ 1. Config File Watcher                                      │
│    Extension checks config.properties every 30 seconds      │
│    Compares file.lastModified() timestamp                   │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 2. Change Detected                                          │
│    File modified → Read new properties                      │
│    Parse: edc.iam.trusted-issuer.{N}.id=...                │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 3. Parse & Convert                                          │
│    Extract DID: "did:web:catena-x.net:issuer"              │
│    Extract types: "MembershipCredential"                    │
│    Create Issuer object                                     │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 4. Register with Registry                                   │
│    trustedIssuerRegistry.register(issuer, credentialType)   │
│    Registry now knows: "Trust credentials from this issuer" │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 5. Verification                                             │
│    Re-register to verify it works                           │
│    Log success/failure                                      │
└─────────────────────────────────────────────────────────────┘
```

**Key Point:** The registry is **updated in memory** without restarting the connector.

---

## 🏭 Practical Use in Catena-X Dataspace

### Real-World Scenario: Car Manufacturer Sharing Data

**Participants:**
- **BMW** (Data Provider) - Wants to share production data
- **Supplier A** (Data Consumer) - Wants to access BMW's data
- **Catena-X Authority** (Trusted Issuer) - Issues credentials

### Step 1: Setup (What You're Doing)

**BMW's Connector Configuration:**
```properties
# BMW trusts credentials from Catena-X authority
edc.iam.trusted-issuer.0.id=did:web:catena-x.net:issuer
edc.iam.trusted-issuer.0.supportedTypes=MembershipCredential,BusinessPartnerCredential
```

**What This Means:**
- BMW's connector will **accept** credentials issued by Catena-X
- Only companies with valid Catena-X credentials can access BMW's data

### Step 2: Supplier A Gets Credentials

**Supplier A goes to Catena-X Portal:**
1. Registers as a Catena-X member
2. Gets a **MembershipCredential** from `did:web:catena-x.net:issuer`
3. Credential says: "Supplier A is a valid Catena-X member with BPN: BPNL1234567890"

**The Credential (simplified):**
```json
{
  "type": ["VerifiableCredential", "MembershipCredential"],
  "issuer": "did:web:catena-x.net:issuer",  ← This is in your trusted list!
  "credentialSubject": {
    "id": "did:web:supplier-a.com:connector",
    "holderIdentifier": "BPNL1234567890"
  },
  "proof": { ... }  // Digital signature
}
```

### Step 3: Supplier A Requests Data

**Supplier A's connector tries to access BMW's data:**

```
Supplier A Connector
    ↓
1. Gets access token from Keycloak
2. Requests credential from MIW (Managed Identity Wallet)
3. Receives MembershipCredential (signed by Catena-X)
4. Sends request to BMW's connector with credential:
   POST /protocol/v2/catalog/request
   Authorization: Bearer <JWT containing MembershipCredential>
```

### Step 4: BMW's Connector Validates

**BMW's connector receives the request:**

```
1. Extract credential from JWT
2. Check: Who issued this credential?
   → issuer: "did:web:catena-x.net:issuer"
3. Check TrustedIssuerRegistry:
   → Is "did:web:catena-x.net:issuer" in trusted list? ✅ YES
4. Check credential type:
   → Type: "MembershipCredential"
   → Is this type accepted? ✅ YES
5. Validate signature (cryptographic proof)
6. ✅ ACCEPT - Credential is trusted!
```

### Step 5: Business Logic

**BMW's connector checks policy:**
```json
{
  "policy": {
    "permission": [{
      "action": "access",
      "constraint": {
        "leftOperand": "BusinessPartnerNumber",
        "operator": "eq",
        "rightOperand": "BPNL1234567890"  // From credential
      }
    }]
  }
}
```

**Result:** Supplier A gets access because:
- ✅ Credential is from a trusted issuer (Catena-X)
- ✅ Credential type matches (MembershipCredential)
- ✅ BPN matches policy requirements

---

## 📊 Real Example: Catena-X Trusted Issuers

### Common Catena-X Issuers

```properties
# Catena-X Main Authority (Most Common)
edc.iam.trusted-issuer.0.id=did:web:catena-x.net:issuer
edc.iam.trusted-issuer.0.supportedTypes=MembershipCredential,BusinessPartnerCredential

# Catena-X Regional Authority (Europe)
edc.iam.trusted-issuer.1.id=did:web:catena-x.eu:issuer
edc.iam.trusted-issuer.1.supportedTypes=MembershipCredential

# Catena-X Test Environment
edc.iam.trusted-issuer.2.id=did:web:catena-x-test.net:issuer
edc.iam.trusted-issuer.2.supportedTypes=*
```

### What Each Credential Type Means

| Credential Type | What It Proves | Example Use Case |
|----------------|----------------|------------------|
| **MembershipCredential** | "This company is a Catena-X member" | Required to access any Catena-X data |
| **BusinessPartnerCredential** | "This company has BPN X" | Used for BPN-based access policies |
| **FrameworkAgreementCredential** | "This company signed agreement Y" | Used for compliance checks (e.g., Traceability) |
| **PcfCredential** | "This company can use PCF use case" | Used for Product Carbon Footprint data sharing |

---

## 🔄 Complete Flow: Contract Negotiation Example

### Scenario: Supplier wants BMW's production data

```
┌─────────────────────────────────────────────────────────────┐
│ STEP 1: Supplier Requests Catalog                           │
│                                                              │
│ POST /protocol/v2/catalog/request                           │
│ Authorization: Bearer <JWT with MembershipCredential>       │
│                                                              │
│ Credential inside JWT:                                      │
│ {                                                           │
│   "issuer": "did:web:catena-x.net:issuer",                 │
│   "type": "MembershipCredential",                           │
│   "credentialSubject": {                                    │
│     "holderIdentifier": "BPNL1234567890"                   │
│   }                                                         │
│ }                                                           │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ STEP 2: BMW's Connector Validates Credential                │
│                                                              │
│ 1. Extract issuer: "did:web:catena-x.net:issuer"           │
│ 2. Check TrustedIssuerRegistry:                             │
│    → Is "did:web:catena-x.net:issuer" trusted? ✅          │
│ 3. Check credential type:                                   │
│    → Is "MembershipCredential" accepted? ✅                 │
│ 4. Validate signature (cryptographic)                       │
│ 5. ✅ ACCEPT                                                │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ STEP 3: BMW Returns Catalog                                 │
│                                                              │
│ Returns available assets with policies:                     │
│ {                                                           │
│   "assets": [{                                              │
│     "id": "production-data-asset",                          │
│     "policy": {                                             │
│       "constraint": {                                       │
│         "leftOperand": "BusinessPartnerNumber",            │
│         "rightOperand": "BPNL1234567890"                   │
│       }                                                     │
│     }                                                       │
│   }]                                                        │
│ }                                                           │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ STEP 4: Supplier Initiates Contract Negotiation             │
│                                                              │
│ POST /protocol/v2/contractnegotiations                      │
│ Authorization: Bearer <JWT with MembershipCredential>       │
│                                                              │
│ BMW's connector checks:                                     │
│ 1. Credential from trusted issuer? ✅                       │
│ 2. BPN matches policy? ✅                                   │
│ 3. Framework agreement required? (if policy says so)        │
│ 4. ✅ CONTRACT AGREEMENT CREATED                            │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ STEP 5: Data Transfer                                       │
│                                                              │
│ Supplier can now transfer data                              │
│ (Credentials validated at each step)                        │
└─────────────────────────────────────────────────────────────┘
```

---

## 🎯 Why Hot Reload Matters (Practical Benefits)

### Scenario 1: Add New Regional Authority

**Before Hot Reload:**
```
1. New Catena-X regional authority launches
2. You need to add: did:web:catena-x-asia.net:issuer
3. ❌ Must restart connector (downtime!)
4. ❌ Any active transfers interrupted
```

**With Hot Reload:**
```
1. Add to config.properties:
   edc.iam.trusted-issuer.3.id=did:web:catena-x-asia.net:issuer
2. Save file
3. ✅ Registry updated in 30 seconds
4. ✅ No downtime, no interruption
5. ✅ Asian companies can now access your data
```

### Scenario 2: Remove Compromised Issuer

**Security Incident:**
```
1. Security team discovers: Issuer X was compromised
2. Need to remove from trusted list immediately
3. ❌ Without hot reload: Must restart (risky delay)
4. ✅ With hot reload: Remove from config, save (30 seconds)
5. ✅ Connector immediately stops accepting credentials from X
```

### Scenario 3: Update Credential Types

**Compliance Update:**
```
1. New regulation requires: Only accept FrameworkAgreementCredential
2. Update config:
   edc.iam.trusted-issuer.0.supportedTypes=FrameworkAgreementCredential
3. ✅ Hot reload updates immediately
4. ✅ Connector now only accepts FrameworkAgreementCredential
```

---

## 🔐 Security Model: Trust Hierarchy

```
┌─────────────────────────────────────────────────────────────┐
│ Level 1: Trusted Issuers (What You Configure)              │
│                                                              │
│ Your connector trusts these authorities:                    │
│ • did:web:catena-x.net:issuer                               │
│ • did:web:catena-x.eu:issuer                                │
│                                                              │
│ These are like "passport offices" you trust                 │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ Level 2: Credentials (What Connectors Present)              │
│                                                              │
│ Other connectors present credentials from trusted issuers:  │
│ • MembershipCredential from Catena-X                        │
│ • BusinessPartnerCredential from Catena-X                   │
│                                                              │
│ These are like "passports" issued by trusted offices        │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ Level 3: Policies (What Data They Can Access)               │
│                                                              │
│ Your policies decide what data they can access:             │
│ • BPN-based access                                          │
│ • Framework agreement requirements                          │
│ • Use case restrictions                                     │
│                                                              │
│ These are like "visa requirements"                          │
└─────────────────────────────────────────────────────────────┘
```

---

## 📝 Example: Adding a New Trusted Issuer (Catena-X Regional)

### Step 1: Identify the Issuer

**Catena-X launches European region:**
- New issuer DID: `did:web:catena-x.eu:issuer`
- Issues: `MembershipCredential`, `BusinessPartnerCredential`

### Step 2: Add to Config

```properties
# Add after existing issuers
edc.iam.trusted-issuer.3.id=did:web:catena-x.eu:issuer
edc.iam.trusted-issuer.3.supportedTypes=MembershipCredential,BusinessPartnerCredential
```

### Step 3: Save and Verify

**Watch logs:**
```
INFO ... === Config file changed! Reloading Trusted Issuers ===
INFO ... Found 4 trusted issuer(s)
INFO ... Registered trusted issuer: did:web:catena-x.eu:issuer with types: [MembershipCredential, BusinessPartnerCredential]
INFO ... ✅ Verification successful: All 4 issuer(s) registered and verified
```

### Step 4: Test in Practice

**European company tries to access your data:**
1. Presents credential from `did:web:catena-x.eu:issuer`
2. Your connector checks: ✅ Issuer is trusted!
3. ✅ Access granted (if policy allows)

---

## 🎯 Real Catena-X Use Cases

### Use Case 1: Supply Chain Transparency

**Scenario:** BMW shares production data with suppliers

**Trusted Issuers:**
```properties
edc.iam.trusted-issuer.0.id=did:web:catena-x.net:issuer
edc.iam.trusted-issuer.0.supportedTypes=MembershipCredential,BusinessPartnerCredential
```

**Policy:**
```json
{
  "constraint": {
    "leftOperand": "BusinessPartnerNumber",
    "operator": "in",
    "rightOperand": ["BPN123", "BPN456", "BPN789"]  // Approved suppliers
  }
}
```

**Flow:**
1. Supplier presents `MembershipCredential` from Catena-X
2. BMW's connector checks: ✅ Trusted issuer
3. BMW's connector checks: ✅ BPN in approved list
4. ✅ Access granted

### Use Case 2: Product Carbon Footprint (PCF)

**Scenario:** Companies share PCF data for compliance

**Trusted Issuers:**
```properties
edc.iam.trusted-issuer.0.id=did:web:catena-x.net:issuer
edc.iam.trusted-issuer.0.supportedTypes=MembershipCredential,PcfCredential
```

**Policy:**
```json
{
  "constraint": {
    "leftOperand": "FrameworkAgreement",
    "operator": "eq",
    "rightOperand": "PCF"
  }
}
```

**Flow:**
1. Company presents `PcfCredential` from Catena-X
2. Connector checks: ✅ Trusted issuer
3. Connector checks: ✅ Has PCF credential
4. ✅ Access granted

---

## 🔍 How Validation Works (Technical Flow)

### Step-by-Step Validation Process

```
1. Request arrives at your connector
   ↓
2. Extract JWT token from Authorization header
   ↓
3. Decode JWT to get VerifiablePresentation
   ↓
4. Extract VerifiableCredential from presentation
   ↓
5. Check credential.issuer (e.g., "did:web:catena-x.net:issuer")
   ↓
6. Query TrustedIssuerRegistry:
   registry.isTrusted(issuer, credentialType)
   ↓
7. Registry checks:
   - Is issuer in trusted list? ✅
   - Is credential type accepted? ✅
   ↓
8. Validate cryptographic signature
   ↓
9. ✅ Credential accepted → Proceed with policy check
   ❌ Credential rejected → Return 401 Unauthorized
```

---

## 📊 Summary: What Trusted Issuers Do

| Aspect | Description | Example |
|--------|-------------|---------|
| **Purpose** | Define which authorities can issue valid credentials | Catena-X authority |
| **What They Control** | Who can prove they're legitimate | Only Catena-X members |
| **When Used** | Every connector-to-connector interaction | Catalog requests, contract negotiations |
| **Security Impact** | Critical - wrong issuer = security breach | Only trusted issuers accepted |
| **Business Impact** | Controls who can access your data | Only legitimate partners |

---

## 🎯 Key Takeaways

1. **Trusted Issuers = Trusted Authorities**
   - Like passport offices - you trust credentials from them
   - In Catena-X: Usually `did:web:catena-x.net:issuer`

2. **Credentials = Proof of Identity**
   - MembershipCredential: "I'm a Catena-X member"
   - BusinessPartnerCredential: "My BPN is X"

3. **Hot Reload = Operational Flexibility**
   - Add/remove issuers without downtime
   - Critical for security incidents
   - Enables dynamic trust management

4. **Validation Flow**
   - Every request checks credential issuer
   - Only trusted issuers accepted
   - Then policy checks apply

---

**Bottom Line:** Trusted Issuers are the **foundation of trust** in the Catena-X dataspace. They determine which companies can prove they're legitimate members and access your data. Hot reload lets you manage this trust dynamically without restarting your connector! 🚀

