## Clearing House Integration Architecture (Gaia-X Registry Only)

This document describes how the **clearing-house extension** integrates with the EDC contract negotiation flow **using only the Gaia-X Registry / Compliance APIs** via `GaiaXRegistryComplianceClient`.  
The old CHN HTTP client (`ClearingHouseClient` and `/api/v1/events/*` etc.) is **not used** and is therefore **not part of this architecture**.

> **📊 Sequence Diagrams**: For detailed Mermaid sequence diagrams showing the participant validation flow, see [`SEQUENCE_DIAGRAM.md`](SEQUENCE_DIAGRAM.md)

---

## 1. High-Level Architecture

### 1.1 Component Overview

```text
┌────────────────────────────────────────────────────────────────────┐
│                        EDC Connector (this node)                  │
│                                                                    │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │  Contract Negotiation Engine                                 │  │
│  │  (consumer or provider role)                                 │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                    │                                      │        │
│                    ▼                                      ▼        │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                    EventRouter (EDC core)                    │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                    │                                              │
│        ContractNegotiationInitiated                               │
│                    ▼                                              │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │    Clearing House Extension (Gaia-X participant validation)  │  │
│  │                                                              │  │
│  │  - `ClearingHouseExtension`                                  │  │
│  │  - `ParticipantValidationSubscriber`                         │  │
│  │  - `GaiaXRegistryComplianceClient`                           │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                    │                                              │
└────────────────────▼──────────────────────────────────────────────┘
                     │
                     ▼
┌────────────────────────────────────────────────────────────────────┐
│                       External Gaia-X Services                     │
│                                                                    │
│  ┌────────────────────────────┐   ┌────────────────────────────┐   │
│  │ Gaia-X Registry            │   │ BDRS (Business Data Reg.) │   │
│  │  - Trust Anchors          │   │  - DID ↔ BPN resolution   │   │
│  │  - Trusted Issuers        │   └────────────────────────────┘   │
│  │  - `/api/trustAnchor/*`   │                                     │
│  │  - `/api/trusted-issuers` │   ┌────────────────────────────┐   │
│  └────────────────────────────┘   │ Participant Connector(s)  │   │
│                                   │  - Hosts                  │   │
│                                   │    `/.well-known/        │   │
│                                   │     x509CertificateChain.pem`││
│                                   └────────────────────────────┘   │
└────────────────────────────────────────────────────────────────────┘
```

**Key point:** The only outbound trust-related calls from this extension go to:
- **Gaia-X Registry / Compliance** (`GaiaXRegistryComplianceClient`)
- **Participant connectors** (HTTP GET of `/.well-known/x509CertificateChain.pem` done by the Registry itself, not by this connector)
- **BDRS** (if configured) for DID/BPN resolution

There is **no direct CHN API (`/api/v1/events/log`, `/receipts/verify`, etc.) in this architecture.**

---

## 2. Contract Negotiation & Participant Validation Flow

### 2.1 Contract Negotiation Lifecycle (EDC Core)

```text
INITIATED  →  REQUESTING  →  OFFERED  →  ACCEPTED  →  AGREED  →  VERIFIED  →  FINALIZED
    ▲
    │
    └── `ContractNegotiationInitiated` event (this is where validation hooks in)
```

The clearing-house extension hooks into the **`ContractNegotiationInitiated`** event to perform **pre‑negotiation participant validation**.

### 2.2 Participant Validation Sequence

```text
EDC Connector                 ParticipantValidationSubscriber         Gaia-X Registry / BDRS
───────────────              ────────────────────────────────        ───────────────────────
   │                                       │                                  │
   │ ContractNegotiationInitiated          │                                  │
   ├──────────────────────────────────────>│                                  │
   │                                       │                                  │
   │                                       │ Extract `counterPartyId`         │
   │                                       │  - may be BPN or DID            │
   │                                       │                                  │
   │                                       │ If BPN: resolve DID via BDRS    │
   │                                       ├───────────────────────────────>  │
   │                                       │  (optional)                      │
   │                                       │<───────────────────────────────  │
   │                                       │ DID                              │
   │                                       │                                  │
   │                                       │ Fetch trust anchors              │
   │                                       ├───────────────────────────────>  │
   │                                       │ GET /api/trustAnchor/latest      │
   │                                       │<───────────────────────────────  │
   │                                       │                                  │
   │                                       │ Fetch trusted issuers            │
   │                                       ├───────────────────────────────>  │
   │                                       │ GET /api/trusted-issuers        │
   │                                       │<───────────────────────────────  │
   │                                       │                                  │
   │                                       │ Construct chain URI from DID     │
   │                                       │  did:web:example.com            │
   │                                       │  → https://example.com/         │
   │                                       │    .well-known/x509...          │
   │                                       │                                  │
   │                                       │ Verify chain via Registry        │
   │                                       ├───────────────────────────────>  │
   │                                       │ POST /api/trustAnchor/chain/file │
   │                                       │  { \"uri\": \"https://...\" }   │
   │                                       │                                  │
   │                                       │<───────────────────────────────  │
   │                                       │  { \"result\": true/false }      │
   │                                       │                                  │
   │                                       │ Log result (info/warning)        │
   │                                       │  (does NOT block negotiation)    │
   │                                       │                                  │
   │ Negotiation continues                  │                                  │
   │ (independent of validation result)    │                                  │
   │                                       │                                  │
```

**Behavior:**
- Validation is **best‑effort and non‑blocking**:
  - If Registry/BDRS are down, the connector logs a warning and continues.
  - If the certificate chain is invalid, this is logged, but the EDC core contract flow is not aborted by this extension.

---

## 3. Internal Components

### 3.1 `ClearingHouseExtension`

**File:** `clearing-house-client/src/main/java/.../ClearingHouseExtension.java`

**Responsibilities:**
- Reads settings:
  - `edc.clearinghouse.participant.validation.enabled`
  - `edc.gaiax.registry.base.url`
  - `edc.gaiax.compliance.base.url`
- Builds `GaiaXRegistryComplianceConfig`
- Instantiates `GaiaXRegistryComplianceClient`
- Registers `ParticipantValidationSubscriber` on the `EventRouter` for `ContractNegotiationInitiated` events
- Optionally exposes a small management API for testing Registry/Compliance connectivity

### 3.2 `ParticipantValidationSubscriber`

**File:** `.../validation/ParticipantValidationSubscriber.java`

**Purpose:**  
Event subscriber that performs participant trust checks **before** negotiation proceeds.

**Key steps in `on(EventEnvelope<?> envelope)`**:
- Ignore if:
  - the extension is disabled, or
  - the event is not `ContractNegotiationInitiated`, or
  - `counterPartyId` is missing
- Determine BPN/DID:
  - If `counterPartyId` starts with `"did"` → treat as DID (e.g., `did:web:example.com`)
  - Else → treat as BPN and optionally resolve DID via `BdrsClient`
- Log:  
  `"[ParticipantValidationSubscriber] Validating participant before negotiation: BPN=..., DID=..."`  
- Call `validateWithGaiaX(bpn, did)`

**Inside `validateWithGaiaX`**:
- Fetch **trust anchors** via `gxClient.getTrustAnchors("latest")`
- Fetch **trusted issuers** via `gxClient.getTrustedIssuers()`
- If DID is present → call `validateParticipantCertificateChain(did, trustAnchors, trustedIssuers)`
- Summarize outcome with either a warning (limited validation) or info (completed)

**Inside `validateParticipantCertificateChain`**:
- Build certificate chain URI from DID using `constructCertificateChainUri(did)`:
  - `did:web:participant.com` → `https://participant.com/.well-known/x509CertificateChain.pem`
  - `did:web:participant.com:path` → `https://participant.com/path/.well-known/x509CertificateChain.pem`
- Call `gxClient.verifyTrustAnchorChainFromUri(certChainUri)`
  - On success:
    - `true` → info: chain verified
    - `false` → warning: chain **not** verified
  - On failure:
    - warning with `verifyResult.getFailureDetail()` or exception message

**Important:**  
No CHN/`ClearingHouseClient` involvement. All checks are done via **Gaia-X Registry / Compliance**.

### 3.3 `GaiaXRegistryComplianceClient`

**File:** `.../client/GaiaXRegistryComplianceClient.java`

**Focus for participant validation:**
- `getTrustAnchors(String version)`
  - `GET {registryBaseUrl}/api/trustAnchor/{version}`
- `getTrustedIssuers()`
  - `GET {registryBaseUrl}/api/trusted-issuers`
- `verifyTrustAnchorChainFromUri(String uri)`
  - `POST {registryBaseUrl}/api/trustAnchor/chain/file`
  - Body: `{ "uri": "<HTTPS URL to .pem chain>" }`

Other methods (context, OWL/LinkML/shapes, compliance checks, etc.) are part of the shared client but are **not required** for the participant-validation‑only architecture.

---

## 4. Certificate Chain Hosting & DID → URI Mapping

### 4.1 DID to Certificate Chain URI

The URI is constructed dynamically from the participant’s DID. Examples:

```text
did:web:connector.company-a.com
  → https://connector.company-a.com/.well-known/x509CertificateChain.pem

did:web:manufacturer.example.org
  → https://manufacturer.example.org/.well-known/x509CertificateChain.pem

did:web:localhost:28081
  → https://localhost:28081/.well-known/x509CertificateChain.pem

did:web:example.com:api:v1
  → https://example.com/api/v1/.well-known/x509CertificateChain.pem
```

Only `did:web` is supported for automatic URI construction in this extension. Other DID methods would require an external DID resolver.

### 4.2 Participant Responsibilities

Each participant must:
- Use a `did:web` DID whose domain/path they control
- Host a PEM‑encoded certificate chain at:
  - `https://<domain>/.well-known/x509CertificateChain.pem` (or with the DID path)
- Ensure:
  - Endpoint is publicly reachable (no auth)
  - Certificate chain is valid and chains to a **trusted anchor** that the Gaia-X Registry knows

The **Gaia-X Registry** pulls the chain from that URI when `/api/trustAnchor/chain/file` is called.

---

## 5. Error Handling & Resilience (Gaia-X Only)

### 5.1 Typical Failure Scenarios

- Registry unavailable:
  - `getTrustAnchors` / `getTrustedIssuers` / `verifyTrustAnchorChainFromUri` fail or timeout
  - Subscriber logs warnings:
    - e.g., `"Exception fetching trust anchors: ..."`
  - Negotiation continues (no hard fail).

- Certificate chain cannot be loaded:
  - Registry returns HTTP 400 with message `"File containing certificate chain could not be loaded."`
  - `GaiaXRegistryComplianceClient` reads error JSON and exposes it in `Result.failure()`
  - Subscriber logs:
    - `"Failed to verify certificate chain: File containing certificate chain could not be loaded."`

- Certificate chain not trusted:
  - Registry returns `{ "result": false }`
  - Subscriber logs:
    - `"✗ Certificate chain NOT verified against Registry trust anchors for DID: ..."`

In all cases, **contract negotiation is not aborted by this extension**. Policy enforcement or stricter behavior (e.g., blocking on invalid chains) would need to be implemented separately at the policy / negotiation level.

---

## 6. Configuration Summary

Minimal configuration for this architecture:

```properties
# Enable participant validation
edc.clearinghouse.participant.validation.enabled=true

# Gaia-X Registry and Compliance base URLs
edc.gaiax.registry.base.url=https://registry.lab.gaia-x.eu/development
edc.gaiax.compliance.base.url=https://compliance.lab.gaia-x.eu/development
```

**Not used / not required in this architecture:**
- Any `edc.clearinghouse.base.url`
- Any `edc.clearinghouse.api.key.alias`
- Any CHN `/api/v1/...` endpoints

---

## 7. What Is Explicitly Out of Scope Here

For clarity, this architecture **does not** include:
- CHN event logging (`/api/v1/events/log`)
- CHN receipt verification (`/api/v1/receipts/verify`)
- CHN-issued compliance certificates

Those features would involve `ClearingHouseClient` and a separate CHN deployment, which are intentionally **excluded** from this Gaia-X‑only participant validation architecture.


