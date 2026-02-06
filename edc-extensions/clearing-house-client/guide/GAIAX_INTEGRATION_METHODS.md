# Gaia-X Registry & Compliance API Integration Methods

## Overview

The Tractus-X EDC connector integrates with Gaia-X public APIs using **multiple methods**, not just certificate chains. Certificate chains are used for **trust validation**, but the APIs themselves are **public and don't require authentication**.

## Integration Methods

### 1. **Public API Access (No Authentication Required)**

The Registry and Compliance APIs are **public APIs** that don't require API keys, Bearer tokens, or other authentication mechanisms. The connector makes direct HTTP GET/POST requests:

**Registry API Endpoints:**
- `GET /api/trustAnchor/{version}` - Fetch trust anchors
- `GET /api/trustAnchor` - Search trust anchors (returns XML)
- `POST /api/trustAnchor/chain/file` - Validate certificate chain from URI
- `GET /api/trusted-issuers` - Get trusted issuers
- `GET /api/certification-notary` - Get certification notaries
- `GET /context/{version}` - Get JSON-LD context
- `GET /owl/{version}` - Get OWL ontology
- `GET /linkml/types.yaml` - Get LinkML ontology
- `GET /did.json` - Get Registry DID
- `GET /.well-known/x509CertificateChain.pem` - Get Registry certificate chain

**Compliance API Endpoints:**
- `POST /api/credential-offers/standard-compliance` - Standard compliance check
- `POST /api/credential-offers/label-level-1` - Label level 1 check
- `POST /api/credential-offers/label-level-2` - Label level 2 check
- `POST /api/credential-offers/label-level-3` - Label level 3 check
- `GET /did.json` - Get Compliance DID
- `GET /.well-known/x509CertificateChain.pem` - Get Compliance certificate chain

**Code Reference:**
```java
// From GaiaXRegistryComplianceClient.java
// No authentication headers are added - just plain HTTP requests
var request = new Request.Builder()
    .url(url)
    .get()  // or .post() for POST requests
    .build();
```

### 2. **Certificate Chain Validation (Trust Establishment)**

Certificate chains are used for **trust validation**, not API authentication. The process works as follows:

1. **Participant provides certificate chain URI** (via DID resolution)
   - For `did:web:example.com`, the URI is: `https://example.com/.well-known/x509CertificateChain.pem`

2. **Registry validates the chain** by:
   - Fetching the certificate chain from the URI
   - Verifying the chain's integrity (each cert signed by the next)
   - Checking if the root certificate matches a registered trust anchor
   - Ensuring the root is self-signed

3. **Validation endpoint:**
   ```bash
   POST https://registry.lab.gaia-x.eu/development/api/trustAnchor/chain/file
   Content-Type: application/json
   
   {
     "uri": "https://example.com/.well-known/x509CertificateChain.pem"
   }
   ```

**Code Reference:**
```java
// From ParticipantValidationSubscriber.java
// Constructs certificate chain URI from DID
String certChainUri = constructCertificateChainUri(did);

// Validates via Registry API
gxClient.verifyTrustAnchorChainFromUri(certChainUri);
```

### 3. **DID-Based Identity Resolution**

The connector uses **DIDs (Decentralized Identifiers)** to identify participants:

- **DID Format**: `did:web:example.com` or `did:web:example.com:path`
- **Certificate Chain URI Construction**: 
  - `did:web:example.com` → `https://example.com/.well-known/x509CertificateChain.pem`
  - `did:web:example.com:path` → `https://example.com/path/.well-known/x509CertificateChain.pem`

**Code Reference:**
```java
// From ParticipantValidationSubscriber.java
private String constructCertificateChainUri(String did) {
    if (did.startsWith("did:web:")) {
        String rest = did.substring(8);
        String[] parts = rest.split(":", 2);
        String domain = parts[0].replace(":", ".");
        // ... constructs HTTPS URI
    }
}
```

### 4. **Verifiable Presentations (Compliance Checks)**

For compliance checks, the connector sends **Verifiable Presentations (VPs)** to the Compliance API:

```java
// From GaiaXRegistryComplianceClient.java
// Sends Verifiable Presentation directly as request body
var bodyJson = mapper.writeValueAsString(verifiablePresentation);
var req = new Request.Builder()
    .url(url)
    .post(RequestBody.create(bodyJson, JSON))
    .build();
```

The Verifiable Presentation contains:
- Verifiable Credentials (VCs) issued by trusted issuers
- Proof of authenticity
- Participant identity information

## Summary: Integration Methods

| Method | Purpose | Authentication Required? |
|--------|---------|------------------------|
| **Public API Access** | Fetch trust anchors, trusted issuers, ontologies, etc. | ❌ No - APIs are public |
| **Certificate Chain Validation** | Validate participant's trust chain | ❌ No - validation is done server-side |
| **DID Resolution** | Identify participants and construct certificate URIs | ❌ No - DID is just an identifier |
| **Verifiable Presentations** | Compliance checks (sends VPs, not certs) | ❌ No - VP contains its own proof |

## Key Points

1. **Certificate chains are NOT for API authentication** - they're for **trust validation**
2. **No API keys or tokens needed** - the Registry and Compliance APIs are public
3. **Certificate chain validation is server-side** - you provide a URI, Registry validates it
4. **Multiple integration methods** - not just certificate chains:
   - Direct API calls (GET/POST)
   - Certificate chain validation (via URI)
   - Verifiable Presentations (for compliance)
   - DID-based identity resolution

## Configuration

The connector is configured with base URLs (no authentication credentials):

```properties
# From config.properties
edc.gaiax.registry.base.url=https://registry.lab.gaia-x.eu/development
edc.gaiax.compliance.base.url=https://compliance.lab.gaia-x.eu/development
```

## Documentation References

- **Registry API Docs**: https://registry.lab.gaia-x.eu/development/docs#
- **Compliance API Docs**: https://compliance.lab.gaia-x.eu/development/docs#/

## Example: Complete Integration Flow

1. **Fetch Trust Anchors** (Public API, no auth):
   ```bash
   GET https://registry.lab.gaia-x.eu/development/api/trustAnchor/latest
   ```

2. **Validate Participant Certificate Chain** (Public API, no auth):
   ```bash
   POST https://registry.lab.gaia-x.eu/development/api/trustAnchor/chain/file
   Content-Type: application/json
   {"uri": "https://participant.com/.well-known/x509CertificateChain.pem"}
   ```

3. **Check Compliance** (Public API, no auth, sends VP):
   ```bash
   POST https://compliance.lab.gaia-x.eu/development/api/credential-offers/standard-compliance
   Content-Type: application/json
   {<verifiable-presentation>}
   ```

All of these are **public API calls** - no authentication required. The certificate chain is used for **trust validation**, not API access.

