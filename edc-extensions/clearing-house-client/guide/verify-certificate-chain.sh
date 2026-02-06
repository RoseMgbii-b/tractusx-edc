#!/bin/bash
# Certificate Chain Verification Script
# Usage: ./verify-certificate-chain.sh <certificate-chain-url>

set -e

if [ -z "$1" ]; then
    echo "Usage: $0 <certificate-chain-url>"
    echo "Example: $0 https://docexploit.com/.well-known/x509CertificateChain.pem"
    exit 1
fi

CHAIN_URL="$1"
CHAIN_FILE="chain.pem"
TEMP_DIR=$(mktemp -d)

echo "========================================="
echo "Certificate Chain Verification"
echo "========================================="
echo "URL: $CHAIN_URL"
echo ""

# Download certificate chain
echo "1. Downloading certificate chain..."
curl -s -o "$CHAIN_FILE" "$CHAIN_URL" || {
    echo "❌ Failed to download certificate chain from $CHAIN_URL"
    exit 1
}
echo "✅ Downloaded certificate chain"
echo ""

# Count certificates
CERT_COUNT=$(grep -c "BEGIN CERTIFICATE" "$CHAIN_FILE" || echo "0")
echo "2. Certificate chain contains $CERT_COUNT certificate(s)"
echo ""

if [ "$CERT_COUNT" -eq 0 ]; then
    echo "❌ No certificates found in chain"
    exit 1
fi

# Extract certificates
echo "3. Extracting certificates..."
cd "$TEMP_DIR"
awk '/BEGIN CERTIFICATE/{i++}{print > "cert"i".pem"}' "../$CHAIN_FILE"
echo "✅ Extracted $CERT_COUNT certificate(s)"
echo ""

# Verify each certificate
echo "4. Verifying certificate details..."
for i in $(seq 1 $CERT_COUNT); do
    CERT_FILE="cert${i}.pem"
    if [ ! -f "$CERT_FILE" ]; then
        continue
    fi
    
    echo "--- Certificate $i ---"
    SUBJECT=$(openssl x509 -in "$CERT_FILE" -noout -subject 2>/dev/null | sed 's/subject=//')
    ISSUER=$(openssl x509 -in "$CERT_FILE" -noout -issuer 2>/dev/null | sed 's/issuer=//')
    SERIAL=$(openssl x509 -in "$CERT_FILE" -noout -serial 2>/dev/null | sed 's/serial=//')
    NOT_BEFORE=$(openssl x509 -in "$CERT_FILE" -noout -startdate 2>/dev/null | sed 's/notBefore=//')
    NOT_AFTER=$(openssl x509 -in "$CERT_FILE" -noout -enddate 2>/dev/null | sed 's/notAfter=//')
    
    echo "Subject: $SUBJECT"
    echo "Issuer:  $ISSUER"
    echo "Serial:  $SERIAL"
    echo "Valid:   $NOT_BEFORE to $NOT_AFTER"
    
    # Check if self-signed (root certificate)
    if [ "$SUBJECT" = "$ISSUER" ]; then
        echo "✅ Self-signed root certificate detected"
    else
        echo "⚠️  Not self-signed (signed by another CA)"
    fi
    echo ""
done

# Verify chain integrity
echo "5. Verifying chain integrity..."
CHAIN_VALID=true
for i in $(seq 1 $((CERT_COUNT - 1))); do
    CERT_FILE="cert${i}.pem"
    ISSUER_FILE="cert$((i + 1)).pem"
    
    if openssl verify -CAfile "$ISSUER_FILE" "$CERT_FILE" >/dev/null 2>&1; then
        echo "✅ Certificate $i is properly signed by certificate $((i + 1))"
    else
        echo "❌ Certificate $i is NOT properly signed by certificate $((i + 1))"
        CHAIN_VALID=false
    fi
done

# Check root certificate
ROOT_CERT="cert${CERT_COUNT}.pem"
if openssl verify -CAfile "$ROOT_CERT" "$ROOT_CERT" >/dev/null 2>&1; then
    echo "✅ Root certificate (cert $CERT_COUNT) is self-signed"
else
    echo "❌ Root certificate (cert $CERT_COUNT) is NOT self-signed"
    CHAIN_VALID=false
fi
echo ""

# Summary
echo "========================================="
echo "Verification Summary"
echo "========================================="
if [ "$CHAIN_VALID" = true ]; then
    echo "✅ Certificate chain integrity: VALID"
else
    echo "❌ Certificate chain integrity: INVALID"
fi

# Check root certificate
ROOT_SUBJECT=$(openssl x509 -in "$ROOT_CERT" -noout -subject 2>/dev/null | sed 's/subject=//')
ROOT_ISSUER=$(openssl x509 -in "$ROOT_CERT" -noout -issuer 2>/dev/null | sed 's/issuer=//')

if [ "$ROOT_SUBJECT" = "$ROOT_ISSUER" ]; then
    echo "✅ Root certificate is self-signed"
    echo ""
    echo "⚠️  NOTE: Even if self-signed, the root must match one of the"
    echo "   Gaia-X Registry trust anchors to pass validation."
    echo "   Check: https://registry.lab.gaia-x.eu/development/api/trustAnchor"
else
    echo "❌ Root certificate is NOT self-signed"
    echo ""
    echo "⚠️  ERROR: The Gaia-X Registry requires a self-signed root certificate."
    echo "   Current root certificate is signed by: $ROOT_ISSUER"
    echo "   This certificate chain will NOT pass registry validation."
fi

# Cleanup
cd - >/dev/null
rm -rf "$TEMP_DIR"
rm -f "$CHAIN_FILE"

echo ""
echo "========================================="

