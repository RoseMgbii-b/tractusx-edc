#!/bin/bash
# Extract and view trust anchors from Gaia-X Registry
# Usage: ./extract-trust-anchors.sh

set -e

REGISTRY_URL="${REGISTRY_URL:-https://registry.lab.gaia-x.eu/development/api/trustAnchor}"
OUTPUT_DIR="${OUTPUT_DIR:-trust-anchors}"

echo "========================================="
echo "Extracting Trust Anchors from Registry"
echo "========================================="
echo "Registry URL: $REGISTRY_URL"
echo "Output Directory: $OUTPUT_DIR"
echo ""

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Fetch trust anchors XML
echo "Step 1: Fetching trust anchors from registry..."
echo "----------------------------------------"
if curl -s "$REGISTRY_URL" > "$OUTPUT_DIR/trust-anchors.xml"; then
    echo "✅ Downloaded trust anchors XML"
    FILE_SIZE=$(wc -c < "$OUTPUT_DIR/trust-anchors.xml")
    echo "   File size: $FILE_SIZE bytes"
    echo ""
else
    echo "❌ Failed to fetch trust anchors"
    exit 1
fi

# Extract base64 certificates
echo "Step 2: Extracting certificates from XML..."
echo "----------------------------------------"
grep -oP '(?<=<X509Certificate>)[^<]+' "$OUTPUT_DIR/trust-anchors.xml" > "$OUTPUT_DIR/certs-base64.txt" || true

CERT_COUNT=$(wc -l < "$OUTPUT_DIR/certs-base64.txt" 2>/dev/null || echo "0")
if [ "$CERT_COUNT" -eq 0 ]; then
    echo "⚠️  No certificates found in XML"
    echo "   Trying alternative extraction method..."
    # Alternative: look for base64 content between X509Certificate tags
    sed -n 's/.*<X509Certificate>\([^<]*\)<\/X509Certificate>.*/\1/p' "$OUTPUT_DIR/trust-anchors.xml" > "$OUTPUT_DIR/certs-base64.txt"
    CERT_COUNT=$(wc -l < "$OUTPUT_DIR/certs-base64.txt" 2>/dev/null || echo "0")
fi

if [ "$CERT_COUNT" -eq 0 ]; then
    echo "❌ Could not extract certificates from XML"
    echo "   XML structure may be different than expected"
    echo "   Viewing first 100 lines of XML:"
    head -100 "$OUTPUT_DIR/trust-anchors.xml"
    exit 1
fi

echo "✅ Found $CERT_COUNT certificate(s)"
echo ""

# Convert each base64 certificate to PEM format
echo "Step 3: Converting certificates to PEM format..."
echo "----------------------------------------"
i=1
while IFS= read -r cert || [ -n "$cert" ]; do
    # Skip empty lines
    [ -z "$cert" ] && continue
    
    PEM_FILE="$OUTPUT_DIR/trust-anchor-$i.pem"
    
    # Convert base64 to PEM
    echo "-----BEGIN CERTIFICATE-----" > "$PEM_FILE"
    echo "$cert" | fold -w 64 >> "$PEM_FILE"
    echo "-----END CERTIFICATE-----" >> "$PEM_FILE"
    
    # Verify and display certificate info
    if openssl x509 -in "$PEM_FILE" -noout -text >/dev/null 2>&1; then
        echo "✅ Trust Anchor $i:"
        echo "   Subject: $(openssl x509 -in "$PEM_FILE" -noout -subject 2>/dev/null | sed 's/subject=//')"
        echo "   Issuer:  $(openssl x509 -in "$PEM_FILE" -noout -issuer 2>/dev/null | sed 's/issuer=//')"
        
        # Check if self-signed
        SUBJECT=$(openssl x509 -in "$PEM_FILE" -noout -subject 2>/dev/null | sed 's/subject=//')
        ISSUER=$(openssl x509 -in "$PEM_FILE" -noout -issuer 2>/dev/null | sed 's/issuer=//')
        if [ "$SUBJECT" = "$ISSUER" ]; then
            echo "   Status:  ✅ Self-signed (valid trust anchor)"
        else
            echo "   Status:  ⚠️  Not self-signed"
        fi
        
        echo "   Valid:   $(openssl x509 -in "$PEM_FILE" -noout -dates 2>/dev/null | grep notBefore | sed 's/notBefore=//') to $(openssl x509 -in "$PEM_FILE" -noout -dates 2>/dev/null | grep notAfter | sed 's/notAfter=//')"
        echo ""
    else
        echo "⚠️  Trust Anchor $i: Invalid certificate format"
        echo ""
    fi
    
    i=$((i+1))
done < "$OUTPUT_DIR/certs-base64.txt"

TOTAL_ANCHORS=$((i-1))

echo "========================================="
echo "Summary"
echo "========================================="
echo "✅ Extracted $TOTAL_ANCHORS trust anchor(s)"
echo "📁 Certificates saved to: $OUTPUT_DIR/"
echo ""
echo "To view a specific trust anchor:"
echo "  openssl x509 -in $OUTPUT_DIR/trust-anchor-1.pem -text -noout"
echo ""
echo "To use a trust anchor in a certificate chain:"
echo "  # Your chain must end with one of these trust anchors"
echo "  cat your-leaf.pem your-intermediate.pem $OUTPUT_DIR/trust-anchor-1.pem > chain.pem"
echo ""
echo "========================================="


