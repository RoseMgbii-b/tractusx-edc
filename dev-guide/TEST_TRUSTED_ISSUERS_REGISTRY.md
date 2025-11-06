# How to Test & Verify Trusted Issuers Registry is Working

## 🎯 Overview

This guide shows you how to verify that:
1. ✅ **Hot reload is working** - Config changes are detected
2. ✅ **Issuers are registered** - They're actually added to the registry
3. ✅ **Registry is functional** - The registry accepts and stores issuers correctly

---

## ✅ Method 1: Log-Based Verification (Automatic)

### What to Look For

After each reload, you should see these logs in order:

```
1. INFO ... === Config file changed! Reloading Trusted Issuers ===
2. INFO ... === Trusted Issuers configuration changed! ===
3. INFO ... Found X trusted issuer(s)
4. INFO ... Registered trusted issuer: did:web:... with types: [...]
5. INFO ... 🔍 Verifying registered issuers...          ← NEW!
6. INFO ... ✅ Verification successful: All X issuer(s) registered and verified  ← NEW!
7. INFO ... Successfully reloaded X trusted issuer(s)
8. INFO ... 📋 TRUSTED ISSUERS REGISTRY SUMMARY
```

### Verification Checklist

- [ ] **Change detected** - "Config file changed!" appears
- [ ] **Issuers parsed** - "Found X trusted issuer(s)" matches config count
- [ ] **Registration succeeded** - "Registered trusted issuer" for each issuer
- [ ] **Verification passed** - "✅ Verification successful" message
- [ ] **Summary matches** - Summary count matches config count
- [ ] **No errors** - No "SEVERE" or "ERROR" messages

---

## 🧪 Method 2: Add/Remove Test (Functional Test)

### Test Steps

1. **Initial State:** Note the current issuer count from logs
   ```
   INFO ... Total registered issuers: 3
   ```

2. **Add a new issuer** to `config.properties`:
   ```properties
   edc.iam.trusted-issuer.4.id=did:web:verification-test.com
   edc.iam.trusted-issuer.4.supportedTypes=MembershipCredential
   ```

3. **Wait 30 seconds** (or touch the file to trigger immediately)

4. **Verify in logs:**
   - Count increases: `Total registered issuers: 4` (was 3)
   - New issuer appears: `• did:web:verification-test.com`
   - Verification passes: `✅ Verification successful: All 4 issuer(s) registered and verified`

5. **Remove the issuer** (delete the lines from config)

6. **Wait 30 seconds**

7. **Verify in logs:**
   - Count decreases: `Total registered issuers: 3` (back to original)
   - Issuer removed message: `Issuer removed from config: did:web:verification-test.com`

---

## 🔍 Method 3: Registry Verification (Code-Level)

The extension now includes automatic verification that:
- ✅ Registry is not null
- ✅ All issuers can be registered (no exceptions)
- ✅ Registration is idempotent (can be called multiple times)

### What the Verification Does

```java
// For each issuer in config:
1. Create Issuer object
2. Register it with the registry
3. Check for exceptions
4. Count successful registrations
5. Report success/failure
```

### Expected Log Output

**Success:**
```
INFO ... 🔍 Verifying registered issuers...
INFO ... ✅ Verification successful: All 4 issuer(s) registered and verified
```

**Failure (if registry had issues):**
```
INFO ... 🔍 Verifying registered issuers...
WARN ... ⚠️ Failed to verify issuer did:web:some-issuer.com: [error message]
WARN ... ⚠️ Verification incomplete: 3/4 issuers verified
```

---

## 📊 Method 4: End-to-End Test (Real-World Scenario)

### Prerequisites

This test requires:
- Another connector or test framework
- Ability to present credentials from different issuers

### Test Flow

1. **Add a test issuer:**
   ```properties
   edc.iam.trusted-issuer.4.id=did:web:test-trusted-issuer.com
   edc.iam.trusted-issuer.4.supportedTypes=MembershipCredential
   ```

2. **Wait for hot reload** (verify in logs)

3. **Present a credential** from `did:web:test-trusted-issuer.com`:
   - **Expected:** Credential should be **accepted** ✅
   - **Why:** Issuer is in the trusted registry

4. **Remove the issuer** from config

5. **Wait for hot reload** (verify in logs)

6. **Present the same credential** again:
   - **Expected:** Credential should be **rejected** ❌
   - **Why:** Issuer is no longer in the trusted registry

---

## 🎯 Method 5: Stress Test (Multiple Changes)

### Test Steps

1. **Add multiple issuers quickly:**
   ```properties
   edc.iam.trusted-issuer.4.id=did:web:test1.com
   edc.iam.trusted-issuer.5.id=did:web:test2.com
   edc.iam.trusted-issuer.6.id=did:web:test3.com
   ```

2. **Save and wait 30 seconds**

3. **Verify:**
   - All 3 issuers appear in summary
   - Verification passes for all
   - Count increases by 3

4. **Remove all 3 issuers**

5. **Save and wait 30 seconds**

6. **Verify:**
   - Count decreases by 3
   - All 3 removed messages appear

---

## 📋 Complete Test Checklist

### Basic Functionality

- [ ] Extension loads on startup
- [ ] Initial issuers loaded from config
- [ ] Summary shows correct count
- [ ] Verification passes for initial issuers

### Hot Reload

- [ ] Adding issuer triggers reload
- [ ] New issuer appears in summary
- [ ] Verification passes for new issuer
- [ ] Removing issuer triggers reload
- [ ] Removed issuer disappears from summary
- [ ] Updating issuer types triggers reload
- [ ] Updated types appear in logs

### Edge Cases

- [ ] Wildcard issuer (`*`) works correctly
- [ ] Multiple credential types per issuer
- [ ] Empty credential types (defaults to `*`)
- [ ] Invalid DID format (should fail gracefully)
- [ ] Duplicate issuer IDs (should handle correctly)

### Error Handling

- [ ] Missing config file (should log warning)
- [ ] Invalid config syntax (should log error)
- [ ] Registry unavailable (should log error)
- [ ] File read errors (should log error)

---

## 🚨 Troubleshooting Verification

### Issue: Verification fails

**Check:**
- Is `TrustedIssuerRegistry` service available?
- Are there any exceptions in logs?
- Is the registry initialized?

**Fix:**
- Check startup logs for "TrustedIssuerRegistry service found"
- Verify registry extension is loaded
- Check for dependency conflicts

### Issue: Issuers not verified

**Check:**
- Are issuers being registered (no exceptions)?
- Is the registry null?
- Are there permission issues?

**Fix:**
- Check logs for "Failed to verify issuer" warnings
- Verify registry is injected correctly
- Check for runtime exceptions

### Issue: Count doesn't match

**Check:**
- Are all issuers in config valid?
- Are there duplicate indices?
- Are there parsing errors?

**Fix:**
- Verify config syntax
- Check for "Failed to load trusted issuers" errors
- Ensure unique indices

---

## 📊 Verification Summary

| Test Method | What It Verifies | When to Use |
|-------------|------------------|-------------|
| **Log-Based** | Config parsing, registration, summary | Always (automatic) |
| **Add/Remove** | Hot reload functionality | During development |
| **Registry Verification** | Registry is functional | After code changes |
| **End-to-End** | Real credential validation | Production testing |
| **Stress Test** | Multiple rapid changes | Performance testing |

---

## 🎯 Quick Verification Command

To quickly verify the registry is working:

1. **Check logs** for:
   ```
   grep "Verification successful" logs/connector.log
   ```

2. **Check summary** for:
   ```
   grep "TRUSTED ISSUERS REGISTRY SUMMARY" logs/connector.log
   ```

3. **Count issuers**:
   ```
   grep "Total registered issuers" logs/connector.log | tail -1
   ```

---

## ✅ Success Criteria

Your trusted issuers hot reload is working correctly if:

- ✅ All issuers from config appear in summary
- ✅ Verification passes for all issuers
- ✅ Adding issuers increases count
- ✅ Removing issuers decreases count
- ✅ No errors in logs
- ✅ Hot reload happens within 30 seconds

---

**The verification method automatically checks that all issuers are properly registered. Just watch the logs!** 🎉

