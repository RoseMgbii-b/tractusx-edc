# Discovery Phase vs Runtime Phase - Decoupling Analysis

## Critical Question

**If the discovery phase structure changes, will it affect the runtime phase implementation?**

**Short Answer:** ⚠️ **It depends on how they're designed** - but they CAN be decoupled.

---

## Current Coupling Analysis

### What Discovery Phase Provides

1. **Catalog JSON-LD** with:
   - `dcat:service[]` array
   - Service entries with:
     - `@id` (asset ID)
     - `dcat:endpointURL` (service endpoint)
     - `odrl:hasPolicy` (service policy)
     - `dcat:endpointDescription` (optional)

### What Runtime Phase Needs

1. **Service Endpoint URL** - Where to call the service API
2. **Service Policy** - For PDP authorization
3. **Service Identification** - To map requests to services

---

## Coupling Scenarios

### ❌ Tightly Coupled (BAD - Breaks if Discovery Changes)

**Scenario:** Runtime phase directly parses catalog JSON-LD structure

```java
// BAD: Tightly coupled to catalog structure
public class ServiceClient {
    public void callService(JsonObject catalog) {
        JsonArray services = catalog.getJsonArray("dcat:service");
        JsonObject service = services.getJsonObject(0);
        String endpoint = service.getString("dcat:endpointURL"); // BREAKS if structure changes
        // ...
    }
}
```

**Problems:**
- ❌ If `dcat:service[]` becomes `dcat:dataService[]` → breaks
- ❌ If `dcat:endpointURL` becomes `dcat:endpoint` → breaks
- ❌ If catalog structure changes → runtime phase breaks

### ✅ Loosely Coupled (GOOD - Resilient to Changes)

**Scenario:** Runtime phase uses asset properties directly, not catalog structure

```java
// GOOD: Loosely coupled - uses asset properties
public class ServiceClient {
    @Inject private AssetIndex assetIndex;
    
    public void callService(String assetId) {
        Asset asset = assetIndex.findById(assetId);
        String endpoint = asset.getProperties().get("dcat:endpointURL"); // Works regardless of catalog
        Policy policy = getPolicyForAsset(assetId); // From policy store, not catalog
        // ...
    }
}
```

**Benefits:**
- ✅ Catalog structure can change without affecting runtime
- ✅ Uses source of truth (asset properties)
- ✅ Works even if catalog is not used

---

## Recommended Decoupling Strategy

### Design Principle: **Runtime Phase Should NOT Depend on Catalog Structure**

The runtime phase should get information from:
1. **Asset Properties** (source of truth)
2. **Policy Store** (for policies)
3. **Asset Index** (for asset lookup)

**NOT from:**
- ❌ Catalog JSON-LD structure
- ❌ Catalog response format
- ❌ Catalog transformation logic

### Implementation Pattern

#### ✅ Correct: Runtime Phase Uses Asset Properties

```java
// Runtime phase gets service info from asset, not catalog
public class ServiceAuthorizationFilter {
    @Inject private AssetIndex assetIndex;
    @Inject private PolicyStore policyStore;
    
    public void authorize(String assetId, String requestPath) {
        // Get asset (source of truth)
        Asset asset = assetIndex.findById(assetId);
        
        // Get endpoint from asset properties (not catalog)
        String endpoint = asset.getProperties().get("dcat:endpointURL");
        
        // Get policy from policy store (not catalog)
        Policy policy = policyStore.findByAssetId(assetId);
        
        // Use for authorization
        // ...
    }
}
```

#### ✅ Correct: Consumer Gets Asset ID from Catalog, Then Uses Asset

```java
// Consumer side: Get asset ID from catalog, then look up asset
public class ServiceConsumer {
    @Inject private AssetIndex assetIndex; // Or call provider's asset API
    
    public void discoverAndCallService(JsonObject catalog) {
        // Step 1: Get asset ID from catalog (this can change)
        String assetId = extractAssetIdFromCatalog(catalog); // Flexible parsing
        
        // Step 2: Get service details from asset (source of truth)
        Asset asset = assetIndex.findById(assetId);
        String endpoint = asset.getProperties().get("dcat:endpointURL");
        
        // Step 3: Call service (independent of catalog structure)
        callService(endpoint, assetId);
    }
}
```

---

## What Can Change in Discovery Phase Without Breaking Runtime

### ✅ Safe Changes (Runtime Phase Unaffected)

1. **Catalog Structure:**
   - `dcat:service[]` → `dcat:dataService[]` ✅
   - `dcat:service` → `dcat:services` ✅
   - Nested structure changes ✅

2. **Field Names:**
   - `dcat:endpointURL` in catalog → different name ✅
   - (As long as asset property name stays same)

3. **Catalog Format:**
   - JSON-LD → JSON ✅
   - Different JSON-LD context ✅

4. **Catalog Transformation Logic:**
   - Different transformer ✅
   - Post-processing changes ✅

### ⚠️ Breaking Changes (Would Affect Runtime)

1. **Asset Property Names:**
   - `dcat:endpointURL` → `dcat:endpoint` ❌
   - (If runtime reads from asset properties)

2. **Asset Structure:**
   - Removing `edc:resourceType` property ❌
   - (If runtime uses it to identify services)

3. **Policy Storage:**
   - Policy not in policy store ❌
   - (If runtime reads from policy store)

---

## Contract Between Discovery and Runtime

### Stable Contract (Should Not Change)

1. **Asset Properties:**
   - `edc:resourceType = "service"` (marker)
   - `dcat:endpointURL` (service endpoint)
   - `dcat:endpointDescription` (optional)

2. **Asset ID:**
   - Asset ID format
   - Asset ID uniqueness

3. **Policy Storage:**
   - Policy linked to asset via ContractDefinition
   - Policy accessible via PolicyStore

### Flexible Contract (Can Change)

1. **Catalog Structure:**
   - How services appear in catalog
   - Catalog JSON-LD format
   - Catalog transformation logic

2. **Catalog Field Names:**
   - Field names in catalog JSON
   - (As long as asset properties stay same)

---

## Implementation Recommendations

### For Discovery Phase Developer

**You Can Change:**
- ✅ Catalog JSON-LD structure
- ✅ How services are transformed
- ✅ Field names in catalog response
- ✅ Catalog transformation logic

**You Must Keep Stable:**
- ⚠️ Asset property names (`edc:resourceType`, `dcat:endpointURL`)
- ⚠️ Asset structure
- ⚠️ Policy storage mechanism

### For Runtime Phase Developer

**You Should:**
- ✅ Read from `AssetIndex` (not catalog)
- ✅ Read from `PolicyStore` (not catalog)
- ✅ Use asset properties (not catalog fields)
- ✅ Only use catalog to get asset ID

**You Should NOT:**
- ❌ Parse catalog JSON-LD structure
- ❌ Depend on catalog field names
- ❌ Depend on catalog transformation logic

---

## Example: Decoupled Implementation

### Discovery Phase (Can Change Freely)

```java
// Discovery: Transform to any catalog structure
public JsonObject transformToCatalog(Asset asset) {
    // Can change this structure without affecting runtime
    return Json.createObjectBuilder()
        .add("@id", asset.getId())
        .add("@type", "dcat:DataService")  // Can change to "dcat:Service"
        .add("dcat:endpointURL", asset.getProperties().get("dcat:endpointURL"))
        .build();
}
```

### Runtime Phase (Independent)

```java
// Runtime: Uses asset directly, not catalog
public class ServiceAuthFilter {
    public void authorize(String assetId) {
        Asset asset = assetIndex.findById(assetId); // Source of truth
        String endpoint = asset.getProperties().get("dcat:endpointURL"); // From asset, not catalog
        // ...
    }
}
```

---

## Testing Strategy

### Test Discovery Phase Independently

```java
@Test
void testCatalogTransformation() {
    // Test catalog structure
    // Can change catalog format without breaking runtime tests
}
```

### Test Runtime Phase Independently

```java
@Test
void testServiceAuthorization() {
    // Test with asset directly
    Asset asset = createTestAsset();
    // Don't depend on catalog structure
}
```

### Integration Test (Both Together)

```java
@Test
void testEndToEndFlow() {
    // Test that discovery → runtime works
    // But runtime should work even if discovery changes
}
```

---

## Answer to Your Question

### Will Discovery Changes Affect Runtime?

**If Properly Decoupled:** ✅ **NO**

**If Tightly Coupled:** ❌ **YES**

### How to Ensure Decoupling

1. **Runtime phase should:**
   - Read from `AssetIndex` (not catalog)
   - Read from `PolicyStore` (not catalog)
   - Use asset properties (not catalog fields)

2. **Discovery phase should:**
   - Only transform assets to catalog format
   - Not change asset properties
   - Not change policy storage

3. **Contract:**
   - Asset properties are the contract
   - Catalog structure is just a view

---

## Summary

| Aspect | Discovery Phase | Runtime Phase | Coupling |
|--------|----------------|--------------|----------|
| **Service Endpoint** | Shows in catalog | Reads from asset | ✅ Decoupled |
| **Service Policy** | Shows in catalog | Reads from PolicyStore | ✅ Decoupled |
| **Service ID** | Shows in catalog | Uses asset ID | ✅ Decoupled |
| **Catalog Structure** | Creates it | Doesn't use it | ✅ Decoupled |

**Conclusion:** If runtime phase reads from assets/policies (not catalog), then discovery phase changes **will NOT affect** runtime phase implementation.

