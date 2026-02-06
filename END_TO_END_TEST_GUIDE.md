# End-to-End Test Guide with Data Plane

This guide provides complete payloads for testing the full data plane flow from asset creation to data transfer.

## Configuration

Based on your setup:
- **Management API:** `http://localhost:8181/api/management`
- **Protocol API:** `http://localhost:28081/protocol`
- **Control API:** `http://localhost:28082/control`
- **Data Plane Public API:** `http://localhost:8185/api/public` (if configured)
- **Consumer Proxy API:** `http://localhost:8186/proxy` (if configured)
- **Participant ID (BPN):** `BPNL1234567890ZZ`
- **DID:** `did:web:localhost%3A19443:dev-connector`

---

## Step 1: Create Access Policy

**Endpoint:** `POST http://localhost:8181/api/management/v3/policydefinitions`

**Headers:**
```
Content-Type: application/json
X-Api-Key: password
```

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

**Endpoint:** `POST http://localhost:8181/api/management/v3/policydefinitions`

**Headers:**
```
Content-Type: application/json
X-Api-Key: password
```

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
  "@type": "PolicyDefinition",
  "@id": "test-contract-policy",
  "policy": {
    "@type": "Set",
    "permission": [
      {
        "action": "use",
        "constraint": [
          {
            "and": [
              {
                "leftOperand": "FrameworkAgreement",
                "operator": "eq",
                "rightOperand": "DataExchangeGovernance:1.0"
              },
              {
                "leftOperand": "UsagePurpose",
                "operator": "isAnyOf",
                "rightOperand": "cx.pcf.base:1"
              }
            ]
          }
        ]
      }
    ]
  }
}
```

---

## Step 3: Create Asset

**Endpoint:** `POST http://localhost:8181/api/management/v3/assets`

**Headers:**
```
Content-Type: application/json
X-Api-Key: password
```

**Body:**
```json
{
  "@context": {
    "@vocab": "https://w3id.org/edc/v0.0.1/ns/",
    "dct": "http://purl.org/dc/terms/"
  },
  "@type": "Asset",
  "@id": "test-asset-1",
  "properties": {
    "dct:description": "Test asset for end-to-end testing"
  },
  "dataAddress": {
    "@type": "DataAddress",
    "type": "HttpData",
    "baseUrl": "https://jsonplaceholder.typicode.com/todos/1",
    "proxyQueryParams": "true",
    "proxyPath": "true",
    "proxyMethod": "true"
  }
}
```

**Note:** The `baseUrl` points to a publicly accessible test API. You can change this to any HTTP endpoint you want to proxy.

---

## Step 4: Create Contract Definition

**Endpoint:** `POST http://localhost:8181/api/management/v3/contractdefinitions`

**Headers:**
```
Content-Type: application/json
X-Api-Key: password
```

**Body:**
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
    "operandRight": "test-asset-1"
  }
}
```

---

## Step 5a: Verify Contract Policy Format (Recommended)

Before starting negotiation, verify the exact format of your contract policy to ensure the negotiation request matches it:

**Endpoint:** `GET http://localhost:8181/api/management/v3/policydefinitions/test-contract-policy`

**Headers:**
```
X-Api-Key: password
```

**Expected Response:**
```json
{
  "@type": "PolicyDefinition",
  "@id": "test-contract-policy",
  "policy": {
    "@type": "Set",
    "permission": [
      {
        "action": "use",
        "constraint": [
          {
            "and": [
              {
                "leftOperand": "FrameworkAgreement",
                "operator": "eq",
                "rightOperand": "DataExchangeGovernance:1.0"
              },
              {
                "leftOperand": "UsagePurpose",
                "operator": "isAnyOf",
                "rightOperand": "cx.pcf.base:1"
              }
            ]
          }
        ]
      }
    ]
  },
  "@context": { ... }
}
```

**Important:** The policy in your negotiation request (Step 6) must match this structure exactly, including:
- The `constraint` format (array vs object)
- All constraint values
- The `"and"` wrapper structure

---

## Step 5: Verify Asset in Catalog (Optional)

**Note:** For single-connector testing, the catalog request requires credentials. If you get an error "Unable to obtain credentials: Empty optional", you can skip this step and proceed directly to contract negotiation (Step 6) if you already know the asset ID.

**Endpoint:** `POST http://localhost:8181/api/management/v3/catalog/request`

**Headers:**
```
Content-Type: application/json
X-Api-Key: password
```

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

This should return a catalog containing your asset `test-asset-1` as a `dcat:DataSet`.

**If you get "Unable to obtain credentials: Empty optional" error:**
- This means the credential service is not running or not accessible
- For single-connector testing, you can skip the catalog request and proceed directly to contract negotiation
- To fix this, ensure the mock credential service is running (see Troubleshooting section below)

---

## Step 6: Start Contract Negotiation

**Endpoint:** `POST http://localhost:8181/api/management/v3/contractnegotiations`

**Headers:**
```
Content-Type: application/json
X-Api-Key: password
```

**Body (Try Format 1 - Simplified with FrameworkAgreement only):**

**⚠️ IMPORTANT: Start with this simplified format to isolate the issue!**

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
    "@id": "test-offer-1",
    "target": "test-asset-1",
    "assigner": "BPNL1234567890ZZ",
    "permission": {
      "action": "use",
      "constraint": {
        "leftOperand": "FrameworkAgreement",
        "operator": "eq",
        "rightOperand": "DataExchangeGovernance:1.0"
      }
    },
    "prohibition": [],
    "obligation": []
  },
  "callbackAddresses": []
}
```

**If Format 1 works, then try Format 2 (with both constraints and @type):**
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
    "@id": "test-offer-1",
    "target": "test-asset-1",
    "assigner": "BPNL1234567890ZZ",
    "permission": {
      "action": "use",
      "constraint": {
        "@type": "LogicalConstraint",
        "and": [
          {
            "leftOperand": "FrameworkAgreement",
            "operator": "eq",
            "rightOperand": "DataExchangeGovernance:1.0"
          },
          {
            "leftOperand": "UsagePurpose",
            "operator": "isAnyOf",
            "rightOperand": "cx.pcf.base:1"
          }
        ]
      }
    },
    "prohibition": [],
    "obligation": []
  },
  "callbackAddresses": [
    {
      "transactional": false,
      "uri": "https://webhook.site/587d3b09-2cb6-4feb-878b-e4eb1764e6c1",
      "events": [
        "contract.negotiation"
      ],
      "authKey": "auth-key",
      "authCodeId": "auth-code-id"
    }
  ]
}
```

**Critical: Before trying Format 2, you MUST:**
1. **Create a simpler contract policy** with only FrameworkAgreement (no UsagePurpose)
2. **Update your contract definition** to use the simpler policy
3. **Try Format 1** with the simpler policy

**To create simpler contract policy:**
```json
POST http://localhost:8181/api/management/v3/policydefinitions
{
  "@context": [
    "https://w3id.org/catenax/2025/9/policy/odrl.jsonld",
    "https://w3id.org/catenax/2025/9/policy/context.jsonld",
    { "@vocab": "https://w3id.org/edc/v0.0.1/ns/" }
  ],
  "@type": "PolicyDefinition",
  "@id": "test-contract-policy-simple",
  "policy": {
    "@type": "Set",
    "permission": [{
      "action": "use",
      "constraint": [{
        "leftOperand": "FrameworkAgreement",
        "operator": "eq",
        "rightOperand": "DataExchangeGovernance:1.0"
      }]
    }]
  }
}
```

**Then update contract definition to use `test-contract-policy-simple` instead of `test-contract-policy`**

**Expected Response:**
```json
{
  "@type": "IdResponse",
  "@id": "<negotiation-id>",
  "createdAt": <timestamp>,
  "@context": { ... }
}
```

**Save the `@id` value as `negotiationId` for the next step.**

---

## Step 7: Check Negotiation Status

**Endpoint:** `GET http://localhost:8181/api/management/v3/contractnegotiations/{negotiationId}`

**Headers:**
```
X-Api-Key: password
```

**Poll this endpoint until `"state": "FINALIZED"`**

**Expected Response (when finalized):**
```json
{
  "@type": "ContractNegotiation",
  "@id": "<negotiation-id>",
  "type": "CONSUMER",
  "protocol": "dataspace-protocol-http:2025-1",
  "state": "FINALIZED",
  "counterPartyId": "BPNL1234567890ZZ",
  "counterPartyAddress": "http://localhost:28081/protocol",
  "contractAgreementId": "<contract-agreement-id>",
  "createdAt": <timestamp>,
  "@context": { ... }
}
```

**Save the `contractAgreementId` value for the transfer step.**

---

## Step 8: Start Transfer Process

**Endpoint:** `POST http://localhost:8181/api/management/v3/transferprocesses`

**Headers:**
```
Content-Type: application/json
X-Api-Key: password
```

**Body:**
```json
{
  "@context": {
    "@vocab": "https://w3id.org/edc/v0.0.1/ns/"
  },
  "@type": "TransferRequest",
  "assetId": "test-asset-1",
  "contractId": "<contract-agreement-id-from-step-7>",
  "counterPartyAddress": "http://localhost:28081/protocol",
  "dataDestination": {
    "type": "HttpProxy"
  },
  "protocol": "dataspace-protocol-http:2025-1",
  "transferType": "HttpData-PULL",
  "callbackAddresses": [
    {
      "transactional": false,
      "uri": "https://webhook.site/587d3b09-2cb6-4feb-878b-e4eb1764e6c1",
      "events": [
        "transfer.process"
      ],
      "authKey": "auth-key",
      "authCodeId": "auth-code-id"
    }
  ]
}
```

**Expected Response:**
```json
{
  "@type": "IdResponse",
  "@id": "<transfer-process-id>",
  "createdAt": <timestamp>,
  "@context": { ... }
}
```

**Save the `@id` value as `transferProcessId` for the next step.**

---

## Step 9: Check Transfer Process Status

**Endpoint:** `GET http://localhost:8181/api/management/v3/transferprocesses/{transferProcessId}`

**Headers:**
```
X-Api-Key: password
```

**Poll until `"state": "STARTED"`**

**Expected Response (when started):**
```json
{
  "@id": "<transfer-process-id>",
  "@type": "TransferProcess",
  "state": "STARTED",
  "stateTimestamp": <timestamp>,
  "type": "CONSUMER",
  "assetId": "test-asset-1",
  "contractId": "<contract-agreement-id>",
  "transferType": "HttpData-PULL",
  "dataDestination": {
    "@type": "DataAddress",
    "type": "HttpProxy"
  },
  "@context": { ... }
}
```

---

## Step 10: Get EDR (Endpoint Data Reference)

**Endpoint:** `GET http://localhost:8181/api/management/v3/edrs/{transferProcessId}/dataaddress`

**Headers:**
```
X-Api-Key: password
```

**Expected Response:**
```json
{
  "@type": "DataAddress",
  "type": "HttpData",
  "endpoint": "http://localhost:8185/api/public",
  "authorization": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 300
}
```

**Save the `endpoint` and `authorization` values.**

---

## Step 11: Fetch Data Using EDR

**Endpoint:** `GET {endpoint_from_step_10}`

**Headers:**
```
Authorization: Bearer {authorization_token_from_step_10}
Content-Type: application/json
```

**Example:**
```bash
curl -X GET "http://localhost:8185/api/public" \
  -H "Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json"
```

This should return the data from your asset's `baseUrl` (in this case, from `https://jsonplaceholder.typicode.com/todos/1`).

---

## Alternative: Simplified Flow Using EDR API

Instead of Steps 6-10, you can use the EDR API which combines negotiation + transfer:

**Endpoint:** `POST http://localhost:8181/api/management/v3/edrs`

**Headers:**
```
Content-Type: application/json
X-Api-Key: password
```

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
    "@id": "test-offer-1",
    "@type": "Offer",
    "assigner": "BPNL1234567890ZZ",
    "permission": [
      {
        "action": "use",
        "constraint": {
          "@type": "LogicalConstraint",
          "and": [
            {
              "leftOperand": "FrameworkAgreement",
              "operator": "eq",
              "rightOperand": "DataExchangeGovernance:1.0"
            },
            {
              "leftOperand": "UsagePurpose",
              "operator": "isAnyOf",
              "rightOperand": "cx.pcf.base:1"
            }
          ]
        }
      }
    ],
    "prohibition": [],
    "obligation": [],
    "target": "test-asset-1"
  },
  "callbackAddresses": []
}
```

**Expected Response:**
```json
{
  "@type": "IdResponse",
  "@id": "<transfer-process-id>",
  "createdAt": <timestamp>,
  "@context": { ... }
}
```

Then proceed to Step 10 to get the EDR.

---

## Troubleshooting

### Issue: "Unable to obtain credentials: Empty optional" (Catalog Request)

**Cause:** The connector cannot fetch credentials from the credential service. This is common in single-connector testing scenarios.

**Solutions:**

1. **Skip Catalog Request (Recommended for Single-Connector Testing):**
   - If you already know the asset ID, you can skip Step 5 (catalog request) and proceed directly to Step 6 (contract negotiation)
   - The catalog request is optional if you know the asset ID and policy details

2. **Start Mock Credential Service:**
   - If you have the `local-iatp-mocks` directory, start the mock services:
     ```bash
     cd local-iatp-mocks
     docker-compose up -d credential-service bdrs didweb-https
     ```
   - Ensure the credential service is accessible at `http://127.0.0.1:14000`
   - Verify your config has: `tx.edc.iam.iatp.credentialservice.url=http://127.0.0.1:14000`

3. **Check Credential Service Configuration:**
   - Verify `tx.edc.iam.iatp.credentialservice.url` in your `config.properties`
   - Ensure the credential service is running and accessible
   - Check connector logs for credential service connection errors

### Issue: "Failed to request contract to provider"
- **Cause:** Invalid `counterPartyAddress` (e.g., `"http://provider-address"` is a placeholder)
- **Fix:** Use a valid provider endpoint: `"http://localhost:28081/protocol"` for local testing

### Issue: "Policy not found"
- **Cause:** Steps 1-2 (policy creation) didn't complete successfully
- **Fix:** Verify policies exist: `GET http://localhost:8181/api/management/v3/policydefinitions`

### Issue: "Asset not found"
- **Cause:** Asset wasn't created or has wrong ID
- **Fix:** Verify asset exists: `GET http://localhost:8181/api/management/v3/assets/test-asset-1`

### Issue: "Contract definition not found"
- **Cause:** Step 4 (contract definition) didn't complete
- **Fix:** Verify contract definition exists: `GET http://localhost:8181/api/management/v3/contractdefinitions/test-contract-definition`

### Issue: Negotiation returns 400 "Bad request" and TERMINATED state

**Cause:** The policy in your negotiation request doesn't match the contract policy definition exactly.

**What this error means:**
- The provider received your negotiation request
- The provider validated the policy you sent against the contract policy
- The policies don't match (format, constraints, or structure)
- The provider rejected it with HTTP 400

**Progress so far:**
1. ✅ **Fixed:** Protocol address (`http://localhost:28081/protocol` instead of placeholder)
2. ✅ **Fixed:** Policy constraints (added `FrameworkAgreement` and `UsagePurpose`)
3. ✅ **Fixed:** Constraint format (object with `and` array, not array wrapper)
4. ✅ **Fixed:** Added `@type: "LogicalConstraint"` to constraint
5. ⚠️ **Current issue:** Still getting 400 - need to check logs or try alternative formats

**Critical Diagnostic Steps:**

1. **Check connector logs** - This is the most important step! Look for:
   - Policy validation errors
   - Constraint parsing errors
   - JSON-LD expansion errors
   - Specific field names that are mismatched
   
   Search logs for: `ContractNegotiation`, `policy validation`, `constraint`, `400`, `Bad request`

2. **Try Format 1** (with `@type: "LogicalConstraint"`):
   - Use the updated format in Step 6 above
   - This matches the test helper format from the codebase

3. **Try Format 2** (simplified - FrameworkAgreement only):
   - This isolates whether the issue is with the `and` structure or `UsagePurpose`
   - If this works, gradually add back `UsagePurpose`

4. **Compare with catalog response** (if you can get one):
   - The negotiation request must match the policy from catalog's `odrl:hasPolicy` field
   - Even if catalog request fails, check if there's a partial response

5. **Alternative: Create simpler contract policy**:
   - Create a contract policy with only FrameworkAgreement (no UsagePurpose)
   - Update contract definition to use the simpler policy
   - Try negotiation with matching simple policy
   - If this works, the issue is specifically with UsagePurpose constraint format
   - Create a new contract policy with only FrameworkAgreement
   - Update contract definition to use the simpler policy
   - Try negotiation with the simpler policy

### Issue: Negotiation stuck in "REQUESTING" state
- **Cause:** Provider connector not reachable or participant validation failing
- **Fix:** 
  - Check provider connector is running
  - Verify `counterPartyAddress` is correct
  - Check logs for participant validation errors

### Issue: Transfer stuck in "REQUESTING" state
- **Cause:** Data plane not configured or not reachable
- **Fix:** 
  - Verify data plane is running
  - Check data plane configuration in `config.properties`
  - Verify `edc.dataplane.token.validation.endpoint` is set correctly

---

## Complete cURL Example

Here's a complete bash script to run all steps:

```bash
#!/bin/bash

BASE_URL="http://localhost:8181/api/management"
API_KEY="password"

# Step 1: Create Access Policy
echo "Step 1: Creating access policy..."
curl -X POST "$BASE_URL/v3/policydefinitions" \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: $API_KEY" \
  -d '{
    "@context": [
      "https://w3id.org/catenax/2025/9/policy/odrl.jsonld",
      "https://w3id.org/catenax/2025/9/policy/context.jsonld",
      { "@vocab": "https://w3id.org/edc/v0.0.1/ns/" }
    ],
    "@type": "PolicyDefinition",
    "@id": "test-access-policy",
    "policy": {
      "@type": "Set",
      "permission": [{ "action": "access" }]
    }
  }'

# Step 2: Create Contract Policy
echo -e "\nStep 2: Creating contract policy..."
curl -X POST "$BASE_URL/v3/policydefinitions" \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: $API_KEY" \
  -d '{
    "@context": [
      "https://w3id.org/catenax/2025/9/policy/odrl.jsonld",
      "https://w3id.org/catenax/2025/9/policy/context.jsonld",
      { "@vocab": "https://w3id.org/edc/v0.0.1/ns/" }
    ],
    "@type": "PolicyDefinition",
    "@id": "test-contract-policy",
    "policy": {
      "@type": "Set",
      "permission": [{
        "action": "use",
        "constraint": [{
          "and": [
            {
              "leftOperand": "FrameworkAgreement",
              "operator": "eq",
              "rightOperand": "DataExchangeGovernance:1.0"
            },
            {
              "leftOperand": "UsagePurpose",
              "operator": "isAnyOf",
              "rightOperand": "cx.pcf.base:1"
            }
          ]
        }]
      }]
    }
  }'

# Step 3: Create Asset
echo -e "\nStep 3: Creating asset..."
curl -X POST "$BASE_URL/v3/assets" \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: $API_KEY" \
  -d '{
    "@context": {
      "@vocab": "https://w3id.org/edc/v0.0.1/ns/",
      "dct": "http://purl.org/dc/terms/"
    },
    "@type": "Asset",
    "@id": "test-asset-1",
    "properties": {
      "dct:description": "Test asset for end-to-end testing"
    },
    "dataAddress": {
      "@type": "DataAddress",
      "type": "HttpData",
      "baseUrl": "https://jsonplaceholder.typicode.com/todos/1",
      "proxyQueryParams": "true",
      "proxyPath": "true",
      "proxyMethod": "true"
    }
  }'

# Step 4: Create Contract Definition
echo -e "\nStep 4: Creating contract definition..."
curl -X POST "$BASE_URL/v3/contractdefinitions" \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: $API_KEY" \
  -d '{
    "@context": { "@vocab": "https://w3id.org/edc/v0.0.1/ns/" },
    "@type": "ContractDefinition",
    "@id": "test-contract-definition",
    "accessPolicyId": "test-access-policy",
    "contractPolicyId": "test-contract-policy",
    "assetsSelector": {
      "@type": "CriterionDto",
      "operandLeft": "https://w3id.org/edc/v0.0.1/ns/id",
      "operator": "=",
      "operandRight": "test-asset-1"
    }
  }'

# Step 5: Start Contract Negotiation
echo -e "\nStep 5: Starting contract negotiation..."
NEGOTIATION_RESPONSE=$(curl -s -X POST "$BASE_URL/v3/contractnegotiations" \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: $API_KEY" \
  -d '{
    "@context": [
      "https://w3id.org/catenax/2025/9/policy/odrl.jsonld",
      "https://w3id.org/catenax/2025/9/policy/context.jsonld",
      { "@vocab": "https://w3id.org/edc/v0.0.1/ns/" }
    ],
    "@type": "ContractRequest",
    "counterPartyAddress": "http://localhost:28081/protocol",
    "protocol": "dataspace-protocol-http:2025-1",
    "policy": {
      "@type": "Offer",
      "@id": "test-offer-1",
      "target": "test-asset-1",
      "assigner": "BPNL1234567890ZZ",
      "permission": {
        "action": "use",
        "constraint": {
          "and": [
            {
              "leftOperand": "FrameworkAgreement",
              "operator": "eq",
              "rightOperand": "DataExchangeGovernance:1.0"
            },
            {
              "leftOperand": "UsagePurpose",
              "operator": "isAnyOf",
              "rightOperand": "cx.pcf.base:1"
            }
          ]
        }
      },
      "prohibition": [],
      "obligation": []
    },
    "callbackAddresses": []
  }')

NEGOTIATION_ID=$(echo $NEGOTIATION_RESPONSE | grep -o '"@id":"[^"]*"' | cut -d'"' -f4)
echo "Negotiation ID: $NEGOTIATION_ID"

# Step 6: Wait for negotiation to finalize
echo -e "\nStep 6: Waiting for negotiation to finalize..."
for i in {1..30}; do
  sleep 2
  STATUS=$(curl -s -X GET "$BASE_URL/v3/contractnegotiations/$NEGOTIATION_ID" \
    -H "X-Api-Key: $API_KEY" | grep -o '"state":"[^"]*"' | cut -d'"' -f4)
  echo "  Status: $STATUS (attempt $i/30)"
  if [ "$STATUS" = "FINALIZED" ]; then
    CONTRACT_ID=$(curl -s -X GET "$BASE_URL/v3/contractnegotiations/$NEGOTIATION_ID" \
      -H "X-Api-Key: $API_KEY" | grep -o '"contractAgreementId":"[^"]*"' | cut -d'"' -f4)
    echo "  Contract Agreement ID: $CONTRACT_ID"
    break
  fi
done

# Step 7: Start Transfer Process
echo -e "\nStep 7: Starting transfer process..."
TRANSFER_RESPONSE=$(curl -s -X POST "$BASE_URL/v3/transferprocesses" \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: $API_KEY" \
  -d "{
    \"@context\": { \"@vocab\": \"https://w3id.org/edc/v0.0.1/ns/\" },
    \"@type\": \"TransferRequest\",
    \"assetId\": \"test-asset-1\",
    \"contractId\": \"$CONTRACT_ID\",
    \"counterPartyAddress\": \"http://localhost:28081/protocol\",
    \"dataDestination\": { \"type\": \"HttpProxy\" },
    \"protocol\": \"dataspace-protocol-http:2025-1\",
    \"transferType\": \"HttpData-PULL\",
    \"callbackAddresses\": []
  }")

TRANSFER_ID=$(echo $TRANSFER_RESPONSE | grep -o '"@id":"[^"]*"' | cut -d'"' -f4)
echo "Transfer Process ID: $TRANSFER_ID"

# Step 8: Wait for transfer to start
echo -e "\nStep 8: Waiting for transfer to start..."
for i in {1..30}; do
  sleep 2
  STATUS=$(curl -s -X GET "$BASE_URL/v3/transferprocesses/$TRANSFER_ID" \
    -H "X-Api-Key: $API_KEY" | grep -o '"state":"[^"]*"' | cut -d'"' -f4)
  echo "  Status: $STATUS (attempt $i/30)"
  if [ "$STATUS" = "STARTED" ]; then
    break
  fi
done

# Step 9: Get EDR
echo -e "\nStep 9: Getting EDR..."
EDR_RESPONSE=$(curl -s -X GET "$BASE_URL/v3/edrs/$TRANSFER_ID/dataaddress" \
  -H "X-Api-Key: $API_KEY")

ENDPOINT=$(echo $EDR_RESPONSE | grep -o '"endpoint":"[^"]*"' | cut -d'"' -f4)
TOKEN=$(echo $EDR_RESPONSE | grep -o '"authorization":"[^"]*"' | cut -d'"' -f4)

echo "Endpoint: $ENDPOINT"
echo "Token: ${TOKEN:0:50}..."

# Step 10: Fetch data
echo -e "\nStep 10: Fetching data using EDR..."
curl -X GET "$ENDPOINT" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json"

echo -e "\n\nTest complete!"
```

---

## Notes

1. **Single Connector Testing**: For single-connector testing, use `"http://localhost:28081/protocol"` as the `counterPartyAddress`
2. **Real Provider**: For testing with a real provider, replace `counterPartyAddress` with the provider's DSP endpoint
3. **Data Plane**: Ensure your data plane is running and configured correctly
4. **Participant Validation**: The participant validation happens automatically during negotiation - ensure your BPN/DID is configured correctly

