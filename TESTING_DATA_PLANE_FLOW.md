# Testing the Full Data Plane Flow

This guide walks you through testing the complete data plane flow with your single-connector setup.

## Prerequisites
- ✅ Asset created with ID: `asset-id`
- ✅ Data plane running and healthy
- ✅ Control plane running on `http://localhost:28080`

## Your Configuration
- **Management API:** `http://localhost:28080/api/management`
- **Protocol API:** `http://localhost:28081/protocol`
- **Control API:** `http://localhost:28082/control`
- **Data Plane Public API:** `http://localhost:8185/api/public`
- **Consumer Proxy API:** `http://localhost:8186/proxy`
- **Participant ID (BPN):** `BPNL1234567890ZZ`

## Step 1: Create Access Policy

**Endpoint:** `POST http://localhost:28080/api/management/v3/policydefinitions`

**Body:** (see `test-flow-step1-access-policy.json`)

```json
{
  "@context": [
    "https://w3id.org/catenax/2025/9/policy/odrl.jsonld",
    "https://w3id.org/catenax/2025/9/policy/context.jsonld",
    {
      "@vocab": "https://w3id.org/edc/v0.0.1/ns/"
    }
  ],
  "@type": "PolicyDefinition",
  "@id": "test-access-policy",
  "policy": {
    "@type": "Set",
    "permission": [
      {
        "action": "access"
      }
    ]
  }
}
```

**Expected Response:**
```json
{
  "@type": "IdResponse",
  "@id": "test-access-policy",
  "createdAt": <timestamp>,
  "@context": { ... }
}
```

---

## Step 2: Create Contract Policy

**Endpoint:** `POST http://localhost:28080/api/management/v3/policydefinitions`

**Body:** (see `test-flow-step2-contract-policy.json`)

```json
{
  "@context": [
    "https://w3id.org/catenax/2025/9/policy/odrl.jsonld",
    "https://w3id.org/catenax/2025/9/policy/context.jsonld",
    {
      "@vocab": "https://w3id.org/edc/v0.0.1/ns/"
    }
  ],
  "@type": "PolicyDefinition",
  "@id": "test-contract-policy",
  "policy": {
    "@type": "Set",
    "permission": [
      {
        "action": "use"
      }
    ]
  }
}
```

**Expected Response:**
```json
{
  "@type": "IdResponse",
  "@id": "test-contract-policy",
  "createdAt": <timestamp>,
  "@context": { ... }
}
```

---

## Step 3: Create Contract Definition

**Endpoint:** `POST http://localhost:28080/api/management/v3/contractdefinitions`

**Body:** (see `test-flow-step3-contract-definition.json`)

```json
{
  "@context": {
    "@vocab": "https://w3id.org/edc/v0.0.1/ns/"
  },
  "@type": "ContractDefinition",
  "@id": "test-contract-definition",
  "accessPolicyId": "test-access-policy",
  "contractPolicyId": "test-contract-policy",
  "assetsSelector": {
    "@type": "CriterionDto",
    "operandLeft": "https://w3id.org/edc/v0.0.1/ns/id",
    "operator": "=",
    "operandRight": "asset-id"
  }
}
```

**Expected Response:**
```json
{
  "@type": "IdResponse",
  "@id": "test-contract-definition",
  "createdAt": <timestamp>,
  "@context": { ... }
}
```

---

## Step 4: Verify Asset is Offered (Optional - Test Catalog)

**Endpoint:** `POST http://localhost:28080/api/management/v3/catalog/request`

**Body:**
```json
{
  "@context": {
    "@vocab": "https://w3id.org/edc/v0.0.1/ns/"
  },
  "@type": "CatalogRequest",
  "counterPartyAddress": "http://localhost:28081/protocol",
  "protocol": "dataspace-protocol-http:2025-1",
  "querySpec": {
    "@type": "QuerySpec"
  }
}
```

This should return a catalog with your asset `asset-id` as a `dcat:DataSet`.

---

## Step 5: Test Data Plane - Consumer Proxy API

Since you're running a single connector, you can test the **Consumer Proxy API** which simplifies data access.

**Endpoint:** `POST http://localhost:8186/proxy/aas/request`

**Body:**
```json
{
  "assetId": "asset-id",
  "providerId": null,
  "transferProcessId": null,
  "pathSegments": null,
  "queryParams": null
}
```

**Note:** For a single-connector test, the Consumer Proxy API will handle the negotiation and transfer internally if needed. However, you may need to first trigger a contract negotiation and transfer process.

---

## Alternative: Full Flow with EDR (Two-Connector Scenario)

If you want to test the complete flow as if you had two connectors:

### Step 5a: Start Contract Negotiation

**Endpoint:** `POST http://localhost:28080/api/management/v3/contractnegotiations`

**Body:**
```json
{
  "@context": [
    "https://w3id.org/catenax/2025/9/policy/odrl.jsonld",
    "https://w3id.org/catenax/2025/9/policy/context.jsonld",
    {
      "@vocab": "https://w3id.org/edc/v0.0.1/ns/"
    }
  ],
  "@type": "ContractRequest",
  "counterPartyAddress": "http://localhost:28081/protocol",
  "protocol": "dataspace-protocol-http:2025-1",
  "policy": {
    "@type": "Offer",
    "@id": "test-offer",
    "target": "asset-id",
    "assigner": "BPNL1234567890ZZ",
    "permission": {
      "action": "use"
    },
    "prohibition": [],
    "obligation": []
  },
  "callbackAddresses": []
}
```

**Response:** Returns a `negotiationId`. Poll it until state is `FINALIZED`.

### Step 5b: Check Negotiation Status

**Endpoint:** `GET http://localhost:28080/api/management/v3/contractnegotiations/{negotiationId}`

Wait for `"state": "FINALIZED"` and note the `contractAgreementId`.

### Step 5c: Start Transfer Process

**Endpoint:** `POST http://localhost:28080/api/management/v3/transferprocesses`

**Body:**
```json
{
  "@context": {
    "@vocab": "https://w3id.org/edc/v0.0.1/ns/"
  },
  "@type": "TransferRequest",
  "assetId": "asset-id",
  "contractId": "<CONTRACT_AGREEMENT_ID_FROM_STEP_5b>",
  "counterPartyAddress": "http://localhost:28081/protocol",
  "dataDestination": {
    "type": "HttpProxy"
  },
  "protocol": "dataspace-protocol-http:2025-1",
  "transferType": "HttpData-PULL",
  "callbackAddresses": []
}
```

### Step 5d: Get EDR

**Endpoint:** `GET http://localhost:28080/api/management/v3/edrs/{transferProcessId}/dataaddress`

This returns the EDR with `endpoint` and `authorization` token.

### Step 5e: Fetch Data Using EDR

**Endpoint:** `GET {endpoint_from_edr}`

**Header:**
```
Authorization: Bearer {authorization_token_from_edr}
```

This should proxy to your backend: `https://jsonplaceholder.typicode.com/todos`

---

## Simplified Single-Connector Test

For the quickest test with a single connector, use the **EDR API** that combines negotiation + transfer:

**Endpoint:** `POST http://localhost:28080/api/management/v3/edrs`

**Body:**
```json
{
  "@context": [
    "https://w3id.org/catenax/2025/9/policy/context.jsonld",
    "https://w3id.org/catenax/2025/9/policy/odrl.jsonld",
    {
      "@vocab": "https://w3id.org/edc/v0.0.1/ns/"
    }
  ],
  "@type": "ContractRequest",
  "counterPartyAddress": "http://localhost:28081/protocol",
  "protocol": "dataspace-protocol-http:2025-1",
  "policy": {
    "@id": "test-offer",
    "@type": "Offer",
    "assigner": "BPNL1234567890ZZ",
    "permission": [
      {
        "action": "use"
      }
    ],
    "prohibition": [],
    "obligation": [],
    "target": "asset-id"
  },
  "callbackAddresses": []
}
```

Then retrieve the EDR and use it to fetch data as in Step 5e.

---

## Troubleshooting

1. **"Policy not found"**: Make sure Steps 1 and 2 completed successfully.
2. **"Asset not found"**: Verify your asset `asset-id` exists.
3. **"Contract definition not found"**: Ensure Step 3 completed.
4. **Port conflicts**: Check which ports your connector is using (`28080`, `28081`, `28082`, `8185`, `8186`, etc.)

---

## Next Steps

Once this works, you can:
- Test with different asset types (S3, Azure, etc.)
- Test token refresh (`POST /api/public/token`)
- Test with multiple connectors
- Explore real-time vs bulk transfer capabilities

