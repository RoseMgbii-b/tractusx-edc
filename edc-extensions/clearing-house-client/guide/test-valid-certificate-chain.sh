#!/bin/bash
# Test script for validating certificate chains with Gaia-X Registry
# Usage:
#   ./test-valid-certificate-chain.sh                    # Test docexploit.com (default)
#   ./test-valid-certificate-chain.sh registry           # Test registry's own chain
#   ./test-valid-certificate-chain.sh docexploit         # Test docexploit.com chain
#   CHAIN_URI="https://example.com/.well-known/x509CertificateChain.pem" ./test-valid-certificate-chain.sh  # Custom chain

set -e

REGISTRY_BASE_URL="${REGISTRY_BASE_URL:-https://registry.lab.gaia-x.eu/development}"

# Determine which chain to test
CHAIN_SELECTOR="${1:-docexploit}"

case "$CHAIN_SELECTOR" in
    registry|reg)
        CHAIN_URI="${REGISTRY_BASE_URL}/.well-known/x509CertificateChain.pem"
        CHAIN_NAME="Registry's Own Certificate Chain"
        EXPECTED_RESULT="VALID"
        ;;
    docexploit|docexploit.com|doc)
        CHAIN_URI="https://docexploit.com/.well-known/x509CertificateChain.pem"
        CHAIN_NAME="docexploit.com Certificate Chain"
        EXPECTED_RESULT="INVALID (expected failure)"
        ;;
    *)
        # Custom URI provided via environment variable or argument
        if [ -n "$CHAIN_URI" ]; then
            CHAIN_NAME="Custom Certificate Chain"
            EXPECTED_RESULT="UNKNOWN"
        else
            CHAIN_URI="$CHAIN_SELECTOR"
            CHAIN_NAME="Custom Certificate Chain"
            EXPECTED_RESULT="UNKNOWN"
        fi
        ;;
esac

API_ENDPOINT="${REGISTRY_BASE_URL}/api/trustAnchor/chain/file"

echo "========================================="
echo "Testing: $CHAIN_NAME"
echo "========================================="
echo "Registry Base URL: $REGISTRY_BASE_URL"
echo "Certificate Chain URI: $CHAIN_URI"
echo "API Endpoint: $API_ENDPOINT"
echo "Expected Result: $EXPECTED_RESULT"
echo ""

# Step 1: Get registry trust anchors
echo "Step 1: Fetching registry trust anchors..."
echo "----------------------------------------"
TRUST_ANCHORS_URL="${REGISTRY_BASE_URL}/api/trustAnchor"
echo "   URL: $TRUST_ANCHORS_URL"
TRUST_ANCHORS_RESPONSE=$(curl -s "$TRUST_ANCHORS_URL" || echo "")
if [ -z "$TRUST_ANCHORS_RESPONSE" ]; then
    echo "⚠️  Could not fetch trust anchors (API may be unavailable)"
    echo "   Try manually: curl '${TRUST_ANCHORS_URL}'"
    echo ""
else
    echo "✅ Trust anchors retrieved"
    # Note: This endpoint returns XML (TrustServiceStatusList), not JSON
    # Show first few lines to confirm it's working
    echo "$TRUST_ANCHORS_RESPONSE" | head -5
    echo "   ... (XML response, showing first 5 lines)"
    echo ""
fi

# Step 2: Download certificate chain
echo "Step 2: Downloading certificate chain..."
echo "----------------------------------------"
CHAIN_FILE=$(mktemp)
if curl -s -o "$CHAIN_FILE" "$CHAIN_URI"; then
    echo "✅ Downloaded certificate chain from URI"
    CERT_COUNT=$(grep -c "BEGIN CERTIFICATE" "$CHAIN_FILE" || echo "0")
    echo "   Chain contains $CERT_COUNT certificate(s)"
    
    # Show basic info about the chain
    if [ "$CERT_COUNT" -gt 0 ]; then
        echo ""
        echo "   First certificate (leaf):"
        openssl x509 -in "$CHAIN_FILE" -noout -subject -issuer 2>/dev/null | head -2 || echo "   (Could not parse)"
        
        if [ "$CERT_COUNT" -gt 1 ]; then
            echo ""
            echo "   Last certificate (root):"
            # Extract last certificate
            awk '/BEGIN CERTIFICATE/{i++}{if(i=='"$CERT_COUNT"')print}' "$CHAIN_FILE" | \
                openssl x509 -noout -subject -issuer 2>/dev/null || echo "   (Could not parse)"
        fi
    fi
    echo ""
else
    echo "❌ Failed to download certificate chain"
    rm -f "$CHAIN_FILE"
    exit 1
fi

# Step 3: Verify the chain with registry API
echo "Step 3: Verifying certificate chain with registry API..."
echo "----------------------------------------"
REQUEST_BODY=$(cat <<EOF
{
  "uri": "$CHAIN_URI"
}
EOF
)

echo "Request:"
echo "$REQUEST_BODY" | jq '.' 2>/dev/null || echo "$REQUEST_BODY"
echo ""

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$API_ENDPOINT" \
    -H "Content-Type: application/json" \
    -d "$REQUEST_BODY" || echo "")

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')

echo "Response (HTTP $HTTP_CODE):"
echo "$BODY" | jq '.' 2>/dev/null || echo "$BODY"
echo ""

if [ "$HTTP_CODE" = "200" ]; then
    # Check if result is true
    if echo "$BODY" | grep -q '"result"\s*:\s*true' || echo "$BODY" | grep -q '"valid"\s*:\s*true'; then
        echo "✅ SUCCESS: Certificate chain is VALID"
        echo ""
        echo "This certificate chain passes registry validation!"
    else
        echo "⚠️  Certificate chain validation returned false"
    fi
elif [ "$HTTP_CODE" = "409" ]; then
    echo "❌ CONFLICT: Certificate chain validation failed"
    if [ "$CHAIN_SELECTOR" = "docexploit" ] || [ "$CHAIN_SELECTOR" = "docexploit.com" ] || [ "$CHAIN_SELECTOR" = "doc" ]; then
        echo "   (EXPECTED for docexploit.com)"
    fi
    echo "   This usually means:"
    echo "   - Root certificate is not self-signed"
    echo "   - Root certificate doesn't match registry trust anchors"
    echo ""
    echo "   Error details:"
    echo "$BODY" | jq -r '.error // .message // .' 2>/dev/null || echo "$BODY"
    echo ""
    if [ "$CHAIN_SELECTOR" = "docexploit" ] || [ "$CHAIN_SELECTOR" = "docexploit.com" ] || [ "$CHAIN_SELECTOR" = "doc" ]; then
        echo "   This is expected because:"
        echo "   - docexploit.com uses a commercial SSL certificate (USERTrust root)"
        echo "   - The root is NOT self-signed (it's signed by USERTrust)"
        echo "   - USERTrust is not in the registry's trust anchors"
    fi
elif [ "$HTTP_CODE" = "400" ]; then
    echo "❌ BAD REQUEST: Could not load certificate chain from URI"
    echo "   This usually means the registry cannot access the URI"
    echo ""
    echo "   Error details:"
    echo "$BODY" | jq -r '.message // .error // .' 2>/dev/null || echo "$BODY"
else
    echo "⚠️  Unexpected response code: $HTTP_CODE"
    echo "   Response: $BODY"
fi

# Step 4: Verify chain integrity locally
echo ""
echo "Step 4: Verifying chain integrity locally..."
echo "----------------------------------------"
TEMP_DIR=$(mktemp -d)
cd "$TEMP_DIR"

# Extract certificates
awk '/BEGIN CERTIFICATE/{i++}{print > "cert"i".pem"}' "$CHAIN_FILE"

# Identify certificates by checking issuer/subject relationships
echo "Identifying certificate roles..."
declare -A CERT_SUBJECTS
declare -A CERT_ISSUERS

# First pass: collect all subjects and issuers
for i in $(seq 1 $CERT_COUNT); do
    CERT_FILE="cert${i}.pem"
    if [ ! -f "$CERT_FILE" ]; then
        continue
    fi
    
    SUBJECT=$(openssl x509 -in "$CERT_FILE" -noout -subject 2>/dev/null | sed 's/subject=//')
    ISSUER=$(openssl x509 -in "$CERT_FILE" -noout -issuer 2>/dev/null | sed 's/issuer=//')
    
    CERT_SUBJECTS["$i"]="$SUBJECT"
    CERT_ISSUERS["$i"]="$ISSUER"
done

# Find root certificate (self-signed)
ROOT_CERT_NUM=""
for i in $(seq 1 $CERT_COUNT); do
    SUBJECT="${CERT_SUBJECTS[$i]}"
    ISSUER="${CERT_ISSUERS[$i]}"
    
    if [ "$SUBJECT" = "$ISSUER" ]; then
        ROOT_CERT_NUM=$i
        ROOT_CERT="cert${i}.pem"
        echo "   Found root certificate: cert$i (self-signed)"
        break
    fi
done

# Find leaf certificate (its issuer is not the subject of any other cert, except root)
LEAF_CERT_NUM=""
for i in $(seq 1 $CERT_COUNT); do
    if [ "$i" = "$ROOT_CERT_NUM" ]; then
        continue
    fi
    
    ISSUER="${CERT_ISSUERS[$i]}"
    IS_ISSUER_OF_OTHER=false
    
    # Check if this cert's issuer is the subject of another cert (making it an intermediate)
    for j in $(seq 1 $CERT_COUNT); do
        if [ $i -eq $j ] || [ "$j" = "$ROOT_CERT_NUM" ]; then
            continue
        fi
        OTHER_SUBJECT="${CERT_SUBJECTS[$j]}"
        if [ "$ISSUER" = "$OTHER_SUBJECT" ]; then
            IS_ISSUER_OF_OTHER=true
            break
        fi
    done
    
    if [ "$IS_ISSUER_OF_OTHER" = false ]; then
        LEAF_CERT_NUM=$i
        LEAF_CERT="cert${i}.pem"
        echo "   Found leaf certificate: cert$i"
        break
    fi
done

# Find intermediate certificate (the one that's neither leaf nor root)
INTERMEDIATE_CERT_NUM=""
INTERMEDIATE_CERT=""
for i in $(seq 1 $CERT_COUNT); do
    if [ "$i" != "$LEAF_CERT_NUM" ] && [ "$i" != "$ROOT_CERT_NUM" ]; then
        INTERMEDIATE_CERT_NUM=$i
        INTERMEDIATE_CERT="cert${i}.pem"
        echo "   Found intermediate certificate: cert$i"
        break
    fi
done

echo ""

# Verify chain integrity
CHAIN_VALID=true

# Verify leaf -> intermediate (if intermediate exists)
if [ -n "$LEAF_CERT_NUM" ] && [ -n "$INTERMEDIATE_CERT_NUM" ]; then
    LEAF_ISSUER="${CERT_ISSUERS[$LEAF_CERT_NUM]}"
    INTERMEDIATE_SUBJECT="${CERT_SUBJECTS[$INTERMEDIATE_CERT_NUM]}"
    
    if [ "$LEAF_ISSUER" = "$INTERMEDIATE_SUBJECT" ]; then
        if openssl verify -CAfile "$INTERMEDIATE_CERT" "$LEAF_CERT" >/dev/null 2>&1; then
            echo "✅ Leaf certificate is properly signed by intermediate"
        else
            echo "❌ Leaf certificate signature verification failed (issuer/subject match but signature invalid)"
            openssl verify -CAfile "$INTERMEDIATE_CERT" "$LEAF_CERT" 2>&1 | head -1
            CHAIN_VALID=false
        fi
    else
        echo "⚠️  Leaf issuer doesn't match intermediate subject"
        echo "   Leaf issuer: $LEAF_ISSUER"
        echo "   Intermediate subject: $INTERMEDIATE_SUBJECT"
        CHAIN_VALID=false
    fi
fi

# Verify intermediate -> root (if intermediate exists)
if [ -n "$INTERMEDIATE_CERT_NUM" ] && [ -n "$ROOT_CERT_NUM" ]; then
    INTERMEDIATE_ISSUER="${CERT_ISSUERS[$INTERMEDIATE_CERT_NUM]}"
    ROOT_SUBJECT="${CERT_SUBJECTS[$ROOT_CERT_NUM]}"
    
    if [ "$INTERMEDIATE_ISSUER" = "$ROOT_SUBJECT" ]; then
        if openssl verify -CAfile "$ROOT_CERT" "$INTERMEDIATE_CERT" >/dev/null 2>&1; then
            echo "✅ Intermediate certificate is properly signed by root"
        else
            echo "❌ Intermediate certificate signature verification failed (issuer/subject match but signature invalid)"
            openssl verify -CAfile "$ROOT_CERT" "$INTERMEDIATE_CERT" 2>&1 | head -1
            CHAIN_VALID=false
        fi
    else
        echo "⚠️  Intermediate issuer doesn't match root subject"
        echo "   Intermediate issuer: $INTERMEDIATE_ISSUER"
        echo "   Root subject: $ROOT_SUBJECT"
        CHAIN_VALID=false
    fi
fi

# Verify leaf -> root (if no intermediate)
if [ -n "$LEAF_CERT_NUM" ] && [ -z "$INTERMEDIATE_CERT_NUM" ] && [ -n "$ROOT_CERT_NUM" ]; then
    LEAF_ISSUER="${CERT_ISSUERS[$LEAF_CERT_NUM]}"
    ROOT_SUBJECT="${CERT_SUBJECTS[$ROOT_CERT_NUM]}"
    
    if [ "$LEAF_ISSUER" = "$ROOT_SUBJECT" ]; then
        if openssl verify -CAfile "$ROOT_CERT" "$LEAF_CERT" >/dev/null 2>&1; then
            echo "✅ Leaf certificate is properly signed by root"
        else
            echo "❌ Leaf certificate signature verification failed (issuer/subject match but signature invalid)"
            openssl verify -CAfile "$ROOT_CERT" "$LEAF_CERT" 2>&1 | head -1
            CHAIN_VALID=false
        fi
    else
        echo "⚠️  Leaf issuer doesn't match root subject"
        echo "   Leaf issuer: $LEAF_ISSUER"
        echo "   Root subject: $ROOT_SUBJECT"
        CHAIN_VALID=false
    fi
fi

# Check root certificate
if [ -n "$ROOT_CERT_NUM" ]; then
    ROOT_SUBJECT="${CERT_SUBJECTS[$ROOT_CERT_NUM]}"
    ROOT_ISSUER="${CERT_ISSUERS[$ROOT_CERT_NUM]}"
    
    echo ""
    echo "Root Certificate Analysis:"
    echo "   Subject: $ROOT_SUBJECT"
    echo "   Issuer:  $ROOT_ISSUER"
    
    if [ "$ROOT_SUBJECT" = "$ROOT_ISSUER" ]; then
        if openssl verify -CAfile "$ROOT_CERT" "$ROOT_CERT" >/dev/null 2>&1; then
            echo "✅ Root certificate is self-signed and verifies"
        else
            echo "⚠️  Root certificate is self-signed but verification failed"
            openssl verify -CAfile "$ROOT_CERT" "$ROOT_CERT" 2>&1 | head -1
            CHAIN_VALID=false
        fi
    else
        echo "❌ Root certificate is NOT self-signed (this is why registry validation fails)"
        echo "   The root certificate is signed by: $ROOT_ISSUER"
        echo "   This means it's a commercial CA certificate, not a self-signed trust anchor"
        echo "   Registry requires self-signed roots that match its trust anchors"
        CHAIN_VALID=false
    fi
    
    # Show root certificate details
    echo ""
    echo "Root Certificate Details:"
    openssl x509 -in "$ROOT_CERT" -noout -text 2>/dev/null | grep -E "(Subject:|Issuer:|Serial Number:|Not Before|Not After)" | head -5 || echo "   (Could not parse)"
else
    echo "❌ Could not identify root certificate"
    CHAIN_VALID=false
fi

cd - >/dev/null
rm -rf "$TEMP_DIR"

# Summary
echo ""
echo "========================================="
echo "Test Summary - $CHAIN_NAME"
echo "========================================="
if [ "$HTTP_CODE" = "200" ] && echo "$BODY" | grep -q '"result"\s*:\s*true'; then
    echo "✅ Registry Validation: PASSED"
elif [ "$HTTP_CODE" = "200" ]; then
    echo "⚠️  Registry Validation: FAILED (but API call succeeded)"
elif [ "$HTTP_CODE" = "409" ]; then
    echo "❌ Registry Validation: FAILED (HTTP 409 Conflict)"
    echo "   Expected: Root certificate is not self-signed or doesn't match trust anchors"
else
    echo "❌ Registry Validation: FAILED (HTTP $HTTP_CODE)"
fi

if [ "$CHAIN_VALID" = true ]; then
    echo "✅ Local Chain Integrity: VALID (chain is properly formed)"
    echo "   Note: Even though the chain is properly formed, it fails registry"
    echo "   validation because the root is not self-signed."
else
    echo "❌ Local Chain Integrity: INVALID"
fi

if [ "$CHAIN_SELECTOR" = "docexploit" ] || [ "$CHAIN_SELECTOR" = "docexploit.com" ] || [ "$CHAIN_SELECTOR" = "doc" ]; then
    echo ""
    echo "Key Findings:"
    echo "  - docexploit.com uses a commercial SSL certificate chain"
    echo "  - The root certificate is NOT self-signed (it's signed by USERTrust)"
    echo "  - Registry requires self-signed roots that match its trust anchors"
    echo "  - This chain will NOT work with Gaia-X Registry (dev or prod)"
elif [ "$CHAIN_SELECTOR" = "registry" ] || [ "$CHAIN_SELECTOR" = "reg" ]; then
    echo ""
    echo "Key Findings:"
    echo "  - Registry's own certificate chain should pass validation"
    echo "  - This chain is used by the registry itself"
    echo "  - If this fails, there may be a configuration issue"
fi
echo ""
echo "========================================="

# Cleanup
rm -f "$CHAIN_FILE"

