# FIWARE-Style Integration Guide: Registry & Compliance APIs in EDC

## Overview

This guide shows how to properly integrate Gaia-X Registry and Compliance APIs into the EDC connector using **FIWARE-style integration points** - making them **enforceable** rather than just informational.

---

## Part 1: Understanding DID Documents and Keys

### What is a DID Document?

A DID (Decentralized Identifier) document is a JSON-LD document that describes a decentralized identity. For `did:web`, it's hosted at `/.well-known/did.json`.

**Example from your setup:**
```json
{
  "@context": "https://www.w3.org/ns/did/v1",
  "id": "did:web:test1.ecdc.es",
  "publicKey": [
    {
      "id": "did:web:test1.ecdc.es#key-1",
      "type": "RsaVerificationKey2018",
      "controller": "did:web:test1.ecdc.es",
      "publicKeyPem": "-----BEGIN PUBLIC KEY-----\n..."
    }
  ],
  "authentication": [
    {
      "type": "RsaSignatureAuthentication2018",
      "publicKey": "did:web:test1.ecdc.es#key-1"
    }
  ]
}
```

### How DID Documents Work

1. **DID Resolution**: `did:web:test1.ecdc.es` → `https://test1.ecdc.es/.well-known/did.json`
2. **Public Key Extraction**: Extract `publicKeyPem` from DID document
3. **Verification**: Use public key to verify signatures on VCs/VPs
4. **Authentication**: Use keys for authentication in dataspace protocols

### How Keys Are Used

**Public Key (from DID document):**
- ✅ **Verify signatures** on Verifiable Credentials/Presentations
- ✅ **Verify JWT tokens** in dataspace protocols
- ✅ **Verify certificate chains** (if using X.509)
- ✅ **Authenticate requests** (if using DID-based auth)

**Private Key (stored securely, not in DID document):**
- ✅ **Sign Verifiable Credentials** you issue
- ✅ **Sign Verifiable Presentations** you create
- ✅ **Sign JWT tokens** for authentication
- ✅ **Sign requests** in dataspace protocols

### DID → Certificate Chain Mapping

For `did:web`, the certificate chain is at:
- DID: `did:web:test1.ecdc.es`
- Certificate Chain: `https://test1.ecdc.es/.well-known/x509CertificateChain.pem`
- DID Document: `https://test1.ecdc.es/.well-known/did.json`

**Both are needed:**
- **DID Document**: Contains public keys for VC/VP verification
- **Certificate Chain**: Used for X.509-based trust validation via Registry

---

## Part 2: Integration Points (FIWARE-Style)

### Integration Point 1: Policy Constraint Function

**Purpose**: Enforce Registry trust anchor validation in policies

**Implementation:**

```java
package org.eclipse.tractusx.edc.clearinghouse.policy;

import org.eclipse.edc.policy.engine.spi.AtomicConstraintRuleFunction;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.policy.model.PolicyContext;
import org.eclipse.tractusx.edc.clearinghouse.client.GaiaXRegistryComplianceClient;
import org.eclipse.tractusx.edc.spi.identity.mapper.BdrsClient;

/**
 * Constraint function that validates certificate chains against Registry trust anchors.
 * 
 * Policy constraint:
 * {
 *   "leftOperand": "https://registry.gaia-x.eu/TrustAnchor",
 *   "operator": "eq",
 *   "rightOperand": "required"
 * }
 */
public class RegistryTrustAnchorConstraintFunction 
        implements AtomicConstraintRuleFunction<Permission, ContractNegotiationPolicyContext> {
    
    private static final String TRUST_ANCHOR_CONSTRAINT = 
        "https://registry.gaia-x.eu/TrustAnchor";
    
    private final GaiaXRegistryComplianceClient gxClient;
    private final BdrsClient bdrsClient;
    private final Monitor monitor;
    
    @Override
    public boolean evaluate(Operator operator, Object rightOperand, 
                          Permission rule, ContractNegotiationPolicyContext context) {
        
        // Extract DID from participant
        String did = extractDid(context);
        if (did == null) {
            context.reportProblem("DID not found in negotiation context");
            return false; // BLOCK negotiation
        }
        
        // Construct certificate chain URI from DID
        String certChainUri = constructCertChainUri(did);
        if (certChainUri == null) {
            context.reportProblem("Cannot construct certificate chain URI from DID: " + did);
            return false; // BLOCK negotiation
        }
        
        // Verify against Registry trust anchors
        try {
            var result = gxClient.verifyTrustAnchorChainFromUri(certChainUri)
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);
            
            if (result.failed()) {
                context.reportProblem("Registry validation failed: " + 
                    result.getFailureDetail());
                return false; // BLOCK negotiation
            }
            
            boolean isValid = result.getContent();
            if (!isValid) {
                context.reportProblem("Certificate chain not trusted by Registry");
                return false; // BLOCK negotiation
            }
            
            monitor.info("Certificate chain validated against Registry trust anchors");
            return true; // ALLOW negotiation
            
        } catch (Exception e) {
            context.reportProblem("Registry validation error: " + e.getMessage());
            return false; // BLOCK negotiation
        }
    }
    
    @Override
    public boolean canHandle(Object leftOperand) {
        return TRUST_ANCHOR_CONSTRAINT.equals(leftOperand);
    }
    
    private String extractDid(ContractNegotiationPolicyContext context) {
        // Extract DID from participant agent or negotiation request
        var participantAgent = context.participantAgent();
        // Try to get DID from claims or context
        return participantAgent.getClaims().get("did");
    }
    
    private String constructCertChainUri(String did) {
        if (did.startsWith("did:web:")) {
            String domain = did.substring(8).replace(":", ".");
            return "https://" + domain + "/.well-known/x509CertificateChain.pem";
        }
        return null;
    }
}
```

**Registration:**

```java
// In ClearingHouseExtension.java
@Override
public void initialize(ServiceExtensionContext context) {
    // ... existing code ...
    
    // Register constraint function
    PolicyEngine policyEngine = context.getService(PolicyEngine.class);
    policyEngine.registerFunction(
        ContractNegotiationPolicyContext.class,
        Permission.class,
        "https://registry.gaia-x.eu/TrustAnchor",
        new RegistryTrustAnchorConstraintFunction(gxClient, bdrsClient, monitor)
    );
}
```

---

### Integration Point 2: Compliance Policy Validator

**Purpose**: Validate Verifiable Presentations against Compliance API

**Implementation:**

```java
package org.eclipse.tractusx.edc.clearinghouse.policy;

import org.eclipse.edc.policy.engine.spi.PolicyValidatorRule;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.policy.model.PolicyContext;
import org.eclipse.tractusx.edc.clearinghouse.client.GaiaXRegistryComplianceClient;

/**
 * Validates Verifiable Presentations via Compliance API during policy evaluation.
 */
public class CompliancePolicyValidator 
        implements PolicyValidatorRule<ContractNegotiationPolicyContext> {
    
    private final GaiaXRegistryComplianceClient gxClient;
    private final Monitor monitor;
    
    @Override
    public Result<Void> validate(Policy policy, ContractNegotiationPolicyContext context) {
        
        // Extract Verifiable Presentation from context
        VerifiablePresentation vp = extractVerifiablePresentation(context);
        if (vp == null) {
            return Result.failure("Verifiable Presentation not found in negotiation context");
        }
        
        // Check standard compliance
        try {
            var complianceResult = gxClient.checkStandardCompliance(vp)
                .toCompletableFuture()
                .get(30, TimeUnit.SECONDS);
            
            if (complianceResult.failed()) {
                return Result.failure("Compliance check failed: " + 
                    complianceResult.getFailureDetail());
            }
            
            ComplianceCheckResponse response = complianceResult.getContent();
            if (!response.isCompliant()) {
                return Result.failure("Participant does not meet compliance requirements: " + 
                    response.getErrors());
            }
            
            // Check label level if required by policy
            if (requiresLabelLevel(policy)) {
                int labelLevel = extractRequiredLabelLevel(policy);
                var labelResult = checkLabelLevel(vp, labelLevel);
                if (labelResult.failed()) {
                    return labelResult;
                }
            }
            
            monitor.info("Compliance validation passed");
            return Result.success();
            
        } catch (Exception e) {
            return Result.failure("Compliance validation error: " + e.getMessage());
        }
    }
    
    private Result<Void> checkLabelLevel(VerifiablePresentation vp, int level) {
        CompletionStage<Result<ComplianceCheckResponse>> result;
        switch (level) {
            case 1: result = gxClient.checkLabelLevel1(vp); break;
            case 2: result = gxClient.checkLabelLevel2(vp); break;
            case 3: result = gxClient.checkLabelLevel3(vp); break;
            default: return Result.failure("Invalid label level: " + level);
        }
        
        try {
            var checkResult = result.toCompletableFuture().get(30, TimeUnit.SECONDS);
            if (checkResult.failed() || !checkResult.getContent().isCompliant()) {
                return Result.failure("Label level " + level + " compliance check failed");
            }
            return Result.success();
        } catch (Exception e) {
            return Result.failure("Label level check error: " + e.getMessage());
        }
    }
    
    private VerifiablePresentation extractVerifiablePresentation(
            ContractNegotiationPolicyContext context) {
        // Extract VP from participant agent claims
        var participantAgent = context.participantAgent();
        Object vpClaim = participantAgent.getClaims().get("vp");
        if (vpClaim instanceof VerifiablePresentation) {
            return (VerifiablePresentation) vpClaim;
        }
        return null;
    }
}
```

**Registration:**

```java
// In ClearingHouseExtension.java
PolicyEngine policyEngine = context.getService(PolicyEngine.class);
policyEngine.registerValidator(
    ContractNegotiationPolicyContext.class,
    new CompliancePolicyValidator(gxClient, monitor)
);
```

---

### Integration Point 3: Trusted Issuer Validator

**Purpose**: Validate that VCs come from trusted issuers

**Implementation:**

```java
package org.eclipse.tractusx.edc.clearinghouse.policy;

import org.eclipse.edc.policy.engine.spi.PolicyValidatorRule;
import org.eclipse.tractusx.edc.clearinghouse.client.GaiaXRegistryComplianceClient;

/**
 * Validates that Verifiable Credentials are issued by trusted issuers from Registry.
 */
public class TrustedIssuerValidator 
        implements PolicyValidatorRule<ContractNegotiationPolicyContext> {
    
    private final GaiaXRegistryComplianceClient gxClient;
    private final Monitor monitor;
    private List<String> cachedTrustedIssuers;
    private Instant lastUpdate;
    private static final Duration CACHE_TTL = Duration.ofHours(1);
    
    @Override
    public Result<Void> validate(Policy policy, ContractNegotiationPolicyContext context) {
        
        // Get trusted issuers (with caching)
        List<String> trustedIssuers = getTrustedIssuers();
        if (trustedIssuers == null || trustedIssuers.isEmpty()) {
            return Result.failure("Cannot fetch trusted issuers from Registry");
        }
        
        // Extract VCs from participant
        List<VerifiableCredential> vcs = extractVerifiableCredentials(context);
        if (vcs == null || vcs.isEmpty()) {
            return Result.failure("No Verifiable Credentials found");
        }
        
        // Validate each VC issuer
        for (VerifiableCredential vc : vcs) {
            String issuer = extractIssuer(vc);
            if (issuer == null) {
                return Result.failure("VC missing issuer");
            }
            
            // Check if issuer is trusted
            if (!trustedIssuers.contains(issuer)) {
                return Result.failure("VC issuer not in trusted issuers list: " + issuer);
            }
            
            // Verify VC signature using DID document public key
            if (!verifyVCSignature(vc, issuer)) {
                return Result.failure("VC signature verification failed for issuer: " + issuer);
            }
        }
        
        monitor.info("All VCs validated against trusted issuers");
        return Result.success();
    }
    
    private List<String> getTrustedIssuers() {
        // Check cache
        if (cachedTrustedIssuers != null && 
            lastUpdate != null && 
            Duration.between(lastUpdate, Instant.now()).compareTo(CACHE_TTL) < 0) {
            return cachedTrustedIssuers;
        }
        
        // Fetch from Registry
        try {
            var result = gxClient.getTrustedIssuers()
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);
            
            if (result.succeeded()) {
                cachedTrustedIssuers = result.getContent();
                lastUpdate = Instant.now();
                return cachedTrustedIssuers;
            }
        } catch (Exception e) {
            monitor.warning("Failed to fetch trusted issuers: " + e.getMessage());
        }
        
        return cachedTrustedIssuers; // Return cached if fetch fails
    }
    
    private boolean verifyVCSignature(VerifiableCredential vc, String issuerDid) {
        try {
            // Resolve DID document
            Map<String, Object> didDoc = resolveDidDocument(issuerDid);
            
            // Extract public key from DID document
            String publicKeyPem = extractPublicKeyFromDidDoc(didDoc);
            
            // Verify VC proof/signature using public key
            return verifySignature(vc.getProof(), vc, publicKeyPem);
            
        } catch (Exception e) {
            monitor.warning("VC signature verification failed: " + e.getMessage());
            return false;
        }
    }
    
    private Map<String, Object> resolveDidDocument(String did) {
        // For did:web:test1.ecdc.es -> https://test1.ecdc.es/.well-known/did.json
        if (did.startsWith("did:web:")) {
            String domain = did.substring(8).replace(":", ".");
            String didUrl = "https://" + domain + "/.well-known/did.json";
            
            // Fetch DID document
            // Use httpClient to fetch and parse JSON
            // Return as Map<String, Object>
        }
        return null;
    }
}
```

---

### Integration Point 4: Registry Ontology Loader

**Purpose**: Load Registry ontologies for JSON-LD policy validation

**Implementation:**

```java
package org.eclipse.tractusx.edc.clearinghouse.policy;

import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.tractusx.edc.clearinghouse.client.GaiaXRegistryComplianceClient;

/**
 * Loads Registry ontologies and contexts for proper JSON-LD policy validation.
 */
public class RegistryOntologyLoader {
    
    private final GaiaXRegistryComplianceClient gxClient;
    private final JsonLd jsonLd;
    private final Monitor monitor;
    
    public void loadOntologies() {
        try {
            // Load JSON-LD context
            var contextResult = gxClient.getContext("latest")
                .toCompletableFuture()
                .get(30, TimeUnit.SECONDS);
            
            if (contextResult.succeeded()) {
                String context = contextResult.getContent();
                // Register context with JsonLd service
                jsonLd.registerContext("https://registry.gaia-x.eu/context/v1", context);
                monitor.info("Registry JSON-LD context loaded");
            }
            
            // Load OWL ontology
            var ontologyResult = gxClient.getOwlOntology("latest")
                .toCompletableFuture()
                .get(30, TimeUnit.SECONDS);
            
            if (ontologyResult.succeeded()) {
                String ontology = ontologyResult.getContent();
                // Register ontology for validation
                monitor.info("Registry OWL ontology loaded");
            }
            
            // Load SHACL shapes
            var shapesResult = gxClient.getShapes("latest")
                .toCompletableFuture()
                .get(30, TimeUnit.SECONDS);
            
            if (shapesResult.succeeded()) {
                String shapes = shapesResult.getContent();
                // Register shapes for validation
                monitor.info("Registry SHACL shapes loaded");
            }
            
        } catch (Exception e) {
            monitor.severe("Failed to load Registry ontologies: " + e.getMessage(), e);
        }
    }
}
```

**Initialization:**

```java
// In ClearingHouseExtension.java
@Override
public void initialize(ServiceExtensionContext context) {
    // ... existing code ...
    
    // Load ontologies at startup
    JsonLd jsonLd = context.getService(JsonLd.class);
    RegistryOntologyLoader loader = new RegistryOntologyLoader(gxClient, jsonLd, monitor);
    loader.loadOntologies();
}
```

---

## Part 3: Complete Integration Flow

### Flow Diagram

```
Contract Negotiation Request
    ↓
1. Extract DID & Verifiable Presentation
    ├─ DID: did:web:test1.ecdc.es
    ├─ VP: { verifiableCredential: [...], proof: {...} }
    └─ Certificate Chain URI: https://test1.ecdc.es/.well-known/x509CertificateChain.pem
    ↓
2. Policy Evaluation (with Registry integration)
    ├─ RegistryTrustAnchorConstraintFunction
    │   └─ verifyTrustAnchorChainFromUri() → BLOCK if invalid
    ├─ CompliancePolicyValidator
    │   ├─ checkStandardCompliance(vp) → BLOCK if fails
    │   └─ checkLabelLevel*(vp) → BLOCK if fails
    ├─ TrustedIssuerValidator
    │   ├─ getTrustedIssuers() → Get from Registry
    │   ├─ Verify VC issuers → BLOCK if not trusted
    │   └─ Verify VC signatures using DID public keys → BLOCK if invalid
    └─ Policy expansion with Registry ontologies
        ├─ Load Registry context
        ├─ Expand JSON-LD
        └─ Validate against SHACL shapes
    ↓
3. Negotiation Decision
    ├─ All checks pass → Continue negotiation
    └─ Any check fails → BLOCK negotiation (HTTP 403/400)
```

---

## Part 4: DID Document Usage

### How DID Documents Are Used

1. **DID Resolution**:
   ```java
   // did:web:test1.ecdc.es → https://test1.ecdc.es/.well-known/did.json
   String did = "did:web:test1.ecdc.es";
   String didUrl = "https://test1.ecdc.es/.well-known/did.json";
   Map<String, Object> didDoc = fetchDidDocument(didUrl);
   ```

2. **Public Key Extraction**:
   ```java
   // Extract public key from DID document
   List<Map<String, Object>> publicKeys = (List) didDoc.get("publicKey");
   Map<String, Object> key1 = publicKeys.get(0);
   String publicKeyPem = (String) key1.get("publicKeyPem");
   ```

3. **VC/VP Signature Verification**:
   ```java
   // Verify VC proof using public key from DID document
   VerifiableCredential vc = ...;
   String issuerDid = vc.getIssuer(); // e.g., "did:web:test1.ecdc.es"
   Map<String, Object> didDoc = resolveDidDocument(issuerDid);
   String publicKeyPem = extractPublicKey(didDoc);
   boolean isValid = verifySignature(vc.getProof(), vc, publicKeyPem);
   ```

4. **Certificate Chain Resolution**:
   ```java
   // For did:web, certificate chain is at same domain
   String did = "did:web:test1.ecdc.es";
   String certChainUri = "https://test1.ecdc.es/.well-known/x509CertificateChain.pem";
   // Verify chain via Registry API
   gxClient.verifyTrustAnchorChainFromUri(certChainUri);
   ```

---

## Part 5: Key Management

### Your Setup

**DID Document** (public):
- URL: `https://test1.ecdc.es/.well-known/did.json`
- Contains: Public key (`publicKeyPem`)
- Purpose: Others use this to verify your signatures

**Private Key** (secret):
- Stored: Securely in vault/keystore
- Purpose: You use this to sign VCs/VPs/JWTs
- **Never exposed** in DID document

### How Keys Work Together

```
┌─────────────────────────────────────────┐
│  Your Connector (test1.ecdc.es)        │
│                                         │
│  Private Key (secret)                   │
│  └─ Sign VCs/VPs                       │
│  └─ Sign JWTs                          │
│  └─ Sign requests                      │
└─────────────────────────────────────────┘
              │
              │ Signs
              ▼
┌─────────────────────────────────────────┐
│  Verifiable Credential/Presentation    │
│  {                                      │
│    "proof": {                          │
│      "type": "RsaSignature2018",       │
│      "jws": "eyJ..."                   │
│    }                                    │
│  }                                      │
└─────────────────────────────────────────┘
              │
              │ Contains signature
              ▼
┌─────────────────────────────────────────┐
│  Other Connector (verifier)             │
│                                         │
│  1. Resolve DID: did:web:test1.ecdc.es │
│  2. Fetch: https://test1.ecdc.es/     │
│     .well-known/did.json               │
│  3. Extract publicKeyPem               │
│  4. Verify signature using public key  │
└─────────────────────────────────────────┘
```

---

## Part 6: Implementation Checklist

### Step 1: Create Constraint Functions
- [ ] `RegistryTrustAnchorConstraintFunction`
- [ ] Register with `PolicyEngine`

### Step 2: Create Policy Validators
- [ ] `CompliancePolicyValidator`
- [ ] `TrustedIssuerValidator`
- [ ] Register with `PolicyEngine`

### Step 3: Load Ontologies
- [ ] `RegistryOntologyLoader`
- [ ] Load at startup
- [ ] Register contexts with `JsonLd` service

### Step 4: DID Resolution
- [ ] Implement DID resolver for `did:web`
- [ ] Extract public keys from DID documents
- [ ] Use for VC/VP signature verification

### Step 5: Integration Testing
- [ ] Test with valid certificate chains
- [ ] Test with invalid chains (should block)
- [ ] Test with compliant VPs (should allow)
- [ ] Test with non-compliant VPs (should block)

---

## Part 7: Configuration

```properties
# Enable Registry/Compliance integration
edc.gaiax.registry.base.url=https://registry.lab.gaia-x.eu/development
edc.gaiax.compliance.base.url=https://compliance.lab.gaia-x.eu/development

# Enable policy enforcement (not just logging)
edc.clearinghouse.policy.enforcement.enabled=true

# Enable compliance validation
edc.clearinghouse.compliance.validation.enabled=true

# Enable trusted issuer validation
edc.clearinghouse.trusted.issuer.validation.enabled=true

# Cache settings
edc.clearinghouse.trusted.issuers.cache.ttl=3600
edc.clearinghouse.trust.anchors.cache.ttl=86400
```

---

## Summary

**Current State**: APIs are used for informational logging only

**FIWARE-Style Integration**: APIs are integrated into policy evaluation and **block invalid negotiations**

**Key Integration Points**:
1. **Policy Constraint Functions** - Enforce Registry trust validation
2. **Policy Validators** - Validate compliance and trusted issuers
3. **Ontology Loaders** - Load Registry contexts for policy validation
4. **DID Resolution** - Extract public keys for signature verification

**DID & Keys**:
- **DID Document**: Public identity document with public keys
- **Public Key**: Used by others to verify your signatures
- **Private Key**: Used by you to sign VCs/VPs/JWTs
- **Certificate Chain**: Used for X.509 trust validation via Registry

This transforms the integration from **informational** to **enforceable**, making Registry and Compliance APIs integral to the trust and policy evaluation process.

