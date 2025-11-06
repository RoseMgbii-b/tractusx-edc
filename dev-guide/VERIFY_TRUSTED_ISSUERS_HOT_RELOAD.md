# How to Verify Trusted Issuers Hot Reload

## ✅ Current Status

Your logs show the extension is working! You can see:
```
INFO ... === Trusted Issuers configuration changed! ===
INFO ... Found 3 trusted issuer(s)
INFO ... Registered trusted issuer: did:web:localhost:dev-connector with types: [MembershipCredential, BusinessPartnerCredential]
INFO ... Successfully reloaded 3 trusted issuer(s)
```

---

## 🧪 Test: Add a New Issuer (Hot Reload)

### Step 1: Add a New Issuer to Config

Edit your `configuration/config.properties` file and add a new issuer:

```properties
# Existing issuers...
edc.iam.trusted-issuer.0.id=did:web:localhost:dev-connector
edc.iam.trusted-issuer.0.supportedTypes=MembershipCredential,BusinessPartnerCredential

edc.iam.trusted-issuer.1.id=did:web:catena-x.net:issuer
edc.iam.trusted-issuer.1.supportedTypes=MembershipCredential

edc.iam.trusted-issuer.2.id=did:web:example.com:authority
edc.iam.trusted-issuer.2.supportedTypes=*

# ADD THIS NEW ISSUER (index 3):
edc.iam.trusted-issuer.3.id=did:web:test-issuer.example.com
edc.iam.trusted-issuer.3.supportedTypes=MembershipCredential,BusinessPartnerCredential,SomeOtherCredential
```

### Step 2: Save the File

Just save the file. The extension checks every **30 seconds** for changes.

### Step 3: Watch the Logs

Within 30 seconds, you should see:

```
INFO ... === Config file changed! Reloading Trusted Issuers ===
INFO ... === Trusted Issuers configuration changed! ===
INFO ... Found 4 trusted issuer(s)  ← Changed from 3 to 4!
INFO ...   - did:web:localhost:dev-connector (types: [MembershipCredential, BusinessPartnerCredential])
INFO ...   - did:web:catena-x.net:issuer (types: [MembershipCredential])
INFO ...   - did:web:example.com:authority (types: [*])
INFO ...   - did:web:test-issuer.example.com (types: [MembershipCredential, BusinessPartnerCredential, SomeOtherCredential])  ← NEW!
INFO ... ✅ Registered trusted issuer: did:web:test-issuer.example.com with types: [MembershipCredential, BusinessPartnerCredential, SomeOtherCredential]
INFO ... ✅ Successfully reloaded 4 trusted issuer(s)
INFO ... ═══════════════════════════════════════════════════════════════
INFO ... 📋 TRUSTED ISSUERS REGISTRY SUMMARY
INFO ... ═══════════════════════════════════════════════════════════════
INFO ... Total registered issuers: 4
INFO ...   • did:web:localhost:dev-connector
INFO ...   • did:web:catena-x.net:issuer
INFO ...   • did:web:example.com:authority
INFO ...   • did:web:test-issuer.example.com  ← NEW!
INFO ... ═══════════════════════════════════════════════════════════════
```

---

## 📋 How to Verify All Registered Issuers

### Method 1: Check Logs (Automatic Summary)

After each reload, the extension now prints a summary:

```
INFO ... 📋 TRUSTED ISSUERS REGISTRY SUMMARY
INFO ... Total registered issuers: 4
INFO ...   • did:web:localhost:dev-connector
INFO ...   • did:web:catena-x.net:issuer
INFO ...   • did:web:example.com:authority
INFO ...   • did:web:test-issuer.example.com
```

### Method 2: Check Config File

Look at your `configuration/config.properties` file - all issuers listed there should be registered.

### Method 3: Trigger a Manual Reload (Force Check)

To force an immediate check (without waiting 30 seconds), you can:

1. **Touch the config file** (update its timestamp):
   ```bash
   touch configuration/config.properties
   ```
   This triggers the file watcher immediately.

2. **Or wait** - the extension checks every 30 seconds automatically.

---

## 🔍 Verification Checklist

When you add a new issuer, verify:

- [ ] ✅ **Config file updated** - New issuer added to `config.properties`
- [ ] ✅ **Log shows change detected** - "=== Config file changed! Reloading Trusted Issuers ==="
- [ ] ✅ **Log shows new count** - "Found 4 trusted issuer(s)" (increased from 3)
- [ ] ✅ **Log shows registration** - "✅ Registered trusted issuer: did:web:test-issuer.example.com..."
- [ ] ✅ **Summary shows new issuer** - Appears in "TRUSTED ISSUERS REGISTRY SUMMARY"
- [ ] ✅ **No errors** - No "SEVERE" or "ERROR" messages

---

## 🧪 More Test Examples

### Test 1: Remove an Issuer

**Before:**
```properties
edc.iam.trusted-issuer.0.id=did:web:localhost:dev-connector
edc.iam.trusted-issuer.1.id=did:web:catena-x.net:issuer
edc.iam.trusted-issuer.2.id=did:web:example.com:authority
```

**After (remove index 1):**
```properties
edc.iam.trusted-issuer.0.id=did:web:localhost:dev-connector
edc.iam.trusted-issuer.1.id=did:web:example.com:authority
```

**Expected log:**
```
INFO ... Issuer removed from config (may need manual removal): did:web:catena-x.net:issuer
INFO ... Found 2 trusted issuer(s)
```

### Test 2: Update Credential Types

**Before:**
```properties
edc.iam.trusted-issuer.0.supportedTypes=MembershipCredential
```

**After:**
```properties
edc.iam.trusted-issuer.0.supportedTypes=MembershipCredential,BusinessPartnerCredential
```

**Expected log:**
```
INFO ... ✅ Registered trusted issuer: did:web:localhost:dev-connector with types: [MembershipCredential, BusinessPartnerCredential]
```

### Test 3: Add Wildcard Issuer

```properties
edc.iam.trusted-issuer.3.id=did:web:universal-issuer.com
edc.iam.trusted-issuer.3.supportedTypes=*
```

**Expected log:**
```
INFO ... ✅ Registered trusted issuer: did:web:universal-issuer.com with types: ALL_TYPES
```

---

## 📊 Example: Complete Test Flow

### Step-by-Step Test

1. **Initial state:** 3 issuers registered
   ```
   INFO ... Total registered issuers: 3
   ```

2. **Add new issuer to config:**
   ```properties
   edc.iam.trusted-issuer.3.id=did:web:new-test-issuer.com
   edc.iam.trusted-issuer.3.supportedTypes=MembershipCredential
   ```

3. **Save file** (wait up to 30 seconds)

4. **Check logs for:**
   ```
   INFO ... === Config file changed! Reloading Trusted Issuers ===
   INFO ... Found 4 trusted issuer(s)
   INFO ...   - did:web:new-test-issuer.com (types: [MembershipCredential])
   INFO ... ✅ Registered trusted issuer: did:web:new-test-issuer.com with types: [MembershipCredential]
   INFO ... ✅ Successfully reloaded 4 trusted issuer(s)
   INFO ... 📋 TRUSTED ISSUERS REGISTRY SUMMARY
   INFO ... Total registered issuers: 4
   INFO ...   • did:web:localhost:dev-connector
   INFO ...   • did:web:catena-x.net:issuer
   INFO ...   • did:web:example.com:authority
   INFO ...   • did:web:new-test-issuer.com  ← NEW!
   ```

5. **Verify:** Count increased from 3 to 4 ✅

---

## 🚨 Troubleshooting

### Issue: No logs after adding issuer

**Check:**
- Is the config file path correct? (`-Dedc.fs.config=...`)
- Did you save the file?
- Wait 30 seconds (check runs periodically)
- Check for errors in logs

### Issue: Issuer not appearing in summary

**Check:**
- Syntax correct? (no typos in property names)
- Index unique? (no duplicate indices)
- DID format correct? (should start with `did:web:` or similar)

### Issue: "TrustedIssuerRegistry is not available"

**Cause:** The registry service isn't loaded yet.

**Fix:** This usually means the extension loaded before the registry. Check that the registry extension is also loaded (it should be part of the base EDC distribution).

---

## 📝 Quick Reference

### Add New Issuer
```properties
edc.iam.trusted-issuer.{N}.id=did:web:your-issuer.com
edc.iam.trusted-issuer.{N}.supportedTypes=Type1,Type2
```

### Remove Issuer
Delete the lines from config file, or change index to gap (not recommended).

### Verify
Check logs for:
- "✅ Successfully reloaded X trusted issuer(s)"
- "📋 TRUSTED ISSUERS REGISTRY SUMMARY"

---

**Your extension is working! Just add issuers to the config file and watch the logs. 🎉**

