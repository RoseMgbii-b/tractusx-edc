#!/bin/bash
# Create a valid certificate chain for Gaia-X Registry
# This script helps you create a certificate chain that chains to a registry trust anchor
#
# Usage:
#   ./create-cert-chain.sh                    # Interactive mode
#   ./create-cert-chain.sh --use-registry-ta  # Use registry's trust anchor (for testing)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REGISTRY_URL="${REGISTRY_URL:-https://registry.lab.gaia-x.eu/development}"
OUTPUT_DIR="${OUTPUT_DIR:-./cert-chain-output}"

echo "========================================="
echo "Create Valid Certificate Chain"
echo "========================================="
echo ""

# Function to extract trust anchor from registry
extract_registry_trust_anchor() {
    echo "Step 1: Extracting trust anchor from registry..."
    echo "----------------------------------------"
    
    # Download registry's certificate chain
    REGISTRY_CHAIN="$OUTPUT_DIR/registry-chain.pem"
    mkdir -p "$OUTPUT_DIR"
    
    if curl -s "${REGISTRY_URL}/.well-known/x509CertificateChain.pem" > "$REGISTRY_CHAIN"; then
        echo "✅ Downloaded registry's certificate chain"
        
        # Extract the root (last) certificate which should be the trust anchor
        CERT_COUNT=$(grep -c "BEGIN CERTIFICATE" "$REGISTRY_CHAIN" || echo "0")
        if [ "$CERT_COUNT" -gt 0 ]; then
            # Extract last certificate
            awk '/BEGIN CERTIFICATE/{i++}{if(i=='"$CERT_COUNT"')print}' "$REGISTRY_CHAIN" > "$OUTPUT_DIR/trust-anchor.pem"
            
            echo "✅ Extracted trust anchor (root certificate)"
            echo "   Certificate count in registry chain: $CERT_COUNT"
            echo "   Trust anchor subject:"
            openssl x509 -in "$OUTPUT_DIR/trust-anchor.pem" -noout -subject 2>/dev/null | sed 's/subject=//' || echo "   (Could not parse)"
            echo ""
            return 0
        else
            echo "❌ No certificates found in registry chain"
            return 1
        fi
    else
        echo "❌ Failed to download registry chain"
        return 1
    fi
}

# Function to create a certificate chain using registry's trust anchor
create_chain_with_registry_ta() {
    echo "Step 2: Creating certificate chain..."
    echo "----------------------------------------"
    
    if [ ! -f "$OUTPUT_DIR/trust-anchor.pem" ]; then
        echo "❌ Trust anchor not found. Run extract_registry_trust_anchor first."
        return 1
    fi
    
    # Create directory for keys and certs
    mkdir -p "$OUTPUT_DIR/keys"
    
    # Generate intermediate CA key
    echo "Generating intermediate CA key..."
    openssl genrsa -out "$OUTPUT_DIR/keys/intermediate-ca.key" 4096 2>/dev/null
    echo "✅ Created intermediate CA key"
    
    # Create intermediate CA certificate
    echo "Creating intermediate CA certificate..."
    # Note: We can't actually sign with the trust anchor's private key (we don't have it)
    # So we'll create a self-signed intermediate for demonstration
    # In reality, you'd need the trust anchor's private key to sign this
    
    openssl req -new -x509 -key "$OUTPUT_DIR/keys/intermediate-ca.key" \
        -out "$OUTPUT_DIR/intermediate-ca.pem" \
        -days 365 \
        -subj "/CN=Test Intermediate CA/O=Test Organization/C=US" \
        2>/dev/null
    
    echo "✅ Created intermediate CA certificate (self-signed for demo)"
    echo "   ⚠️  Note: In production, this must be signed by the trust anchor's private key"
    
    # Generate leaf certificate key
    echo "Generating leaf certificate key..."
    openssl genrsa -out "$OUTPUT_DIR/keys/leaf.key" 2048 2>/dev/null
    echo "✅ Created leaf certificate key"
    
    # Create leaf certificate signed by intermediate CA
    echo "Creating leaf certificate..."
    openssl req -new -key "$OUTPUT_DIR/keys/leaf.key" \
        -out "$OUTPUT_DIR/leaf.csr" \
        -subj "/CN=test-participant.example.com/O=Test Organization/C=US" \
        2>/dev/null
    
    openssl x509 -req -in "$OUTPUT_DIR/leaf.csr" \
        -CA "$OUTPUT_DIR/intermediate-ca.pem" \
        -CAkey "$OUTPUT_DIR/keys/intermediate-ca.key" \
        -CAcreateserial \
        -out "$OUTPUT_DIR/leaf.pem" \
        -days 365 \
        2>/dev/null
    
    echo "✅ Created leaf certificate"
    
    # Create certificate chain file
    echo "Creating certificate chain file..."
    cat "$OUTPUT_DIR/leaf.pem" "$OUTPUT_DIR/intermediate-ca.pem" "$OUTPUT_DIR/trust-anchor.pem" > "$OUTPUT_DIR/cert-chain.pem"
    echo "✅ Created certificate chain: $OUTPUT_DIR/cert-chain.pem"
    echo ""
    
    # Display chain info
    echo "Certificate Chain Contents:"
    echo "----------------------------------------"
    CERT_COUNT=$(grep -c "BEGIN CERTIFICATE" "$OUTPUT_DIR/cert-chain.pem")
    echo "Total certificates: $CERT_COUNT"
    echo ""
    echo "1. Leaf certificate:"
    openssl x509 -in "$OUTPUT_DIR/leaf.pem" -noout -subject -issuer 2>/dev/null | sed 's/^/   /'
    echo ""
    echo "2. Intermediate CA:"
    openssl x509 -in "$OUTPUT_DIR/intermediate-ca.pem" -noout -subject -issuer 2>/dev/null | sed 's/^/   /'
    echo ""
    echo "3. Trust Anchor (root):"
    openssl x509 -in "$OUTPUT_DIR/trust-anchor.pem" -noout -subject -issuer 2>/dev/null | sed 's/^/   /'
    echo ""
    
    return 0
}

# Function to verify chain locally
verify_chain_locally() {
    echo "Step 3: Verifying chain locally..."
    echo "----------------------------------------"
    
    if [ ! -f "$OUTPUT_DIR/cert-chain.pem" ]; then
        echo "❌ Certificate chain not found"
        return 1
    fi
    
    # Extract certificates
    TEMP_DIR=$(mktemp -d)
    awk '/BEGIN CERTIFICATE/{i++}{print > "'"$TEMP_DIR"'/cert"i".pem"}' "$OUTPUT_DIR/cert-chain.pem"
    
    CERT_COUNT=$(ls -1 "$TEMP_DIR"/cert*.pem 2>/dev/null | wc -l)
    
    if [ "$CERT_COUNT" -lt 2 ]; then
        echo "⚠️  Chain has less than 2 certificates, cannot verify chain"
        rm -rf "$TEMP_DIR"
        return 1
    fi
    
    # Verify leaf -> intermediate
    if [ "$CERT_COUNT" -ge 2 ]; then
        LEAF_ISSUER=$(openssl x509 -in "$TEMP_DIR/cert1.pem" -noout -issuer 2>/dev/null | sed 's/issuer=//')
        INTERMEDIATE_SUBJECT=$(openssl x509 -in "$TEMP_DIR/cert2.pem" -noout -subject 2>/dev/null | sed 's/subject=//')
        
        if [ "$LEAF_ISSUER" = "$INTERMEDIATE_SUBJECT" ]; then
            if openssl verify -CAfile "$TEMP_DIR/cert2.pem" "$TEMP_DIR/cert1.pem" >/dev/null 2>&1; then
                echo "✅ Leaf certificate is properly signed by intermediate"
            else
                echo "❌ Leaf certificate signature verification failed"
            fi
        else
            echo "⚠️  Leaf issuer doesn't match intermediate subject"
        fi
    fi
    
    # Verify intermediate -> root (if exists)
    if [ "$CERT_COUNT" -ge 3 ]; then
        INTERMEDIATE_ISSUER=$(openssl x509 -in "$TEMP_DIR/cert2.pem" -noout -issuer 2>/dev/null | sed 's/issuer=//')
        ROOT_SUBJECT=$(openssl x509 -in "$TEMP_DIR/cert3.pem" -noout -subject 2>/dev/null | sed 's/subject=//')
        
        if [ "$INTERMEDIATE_ISSUER" = "$ROOT_SUBJECT" ]; then
            if openssl verify -CAfile "$TEMP_DIR/cert3.pem" "$TEMP_DIR/cert2.pem" >/dev/null 2>&1; then
                echo "✅ Intermediate certificate is properly signed by root"
            else
                echo "⚠️  Intermediate certificate signature verification failed"
                echo "   (This is expected if intermediate is self-signed, not signed by trust anchor)"
            fi
        else
            echo "⚠️  Intermediate issuer doesn't match root subject"
            echo "   (This is expected - intermediate is self-signed, not signed by trust anchor)"
        fi
    fi
    
    # Check root
    ROOT_SUBJECT=$(openssl x509 -in "$OUTPUT_DIR/trust-anchor.pem" -noout -subject 2>/dev/null | sed 's/subject=//')
    ROOT_ISSUER=$(openssl x509 -in "$OUTPUT_DIR/trust-anchor.pem" -noout -issuer 2>/dev/null | sed 's/issuer=//')
    
    if [ "$ROOT_SUBJECT" = "$ROOT_ISSUER" ]; then
        echo "✅ Root certificate is self-signed (valid trust anchor)"
    else
        echo "❌ Root certificate is NOT self-signed"
    fi
    
    rm -rf "$TEMP_DIR"
    echo ""
}

# Function to test with registry API
test_with_registry() {
    echo "Step 4: Testing with registry API..."
    echo "----------------------------------------"
    echo "⚠️  Note: To test with registry API, you need to:"
    echo "   1. Host the certificate chain at a publicly accessible URI"
    echo "   2. The chain must be properly signed (intermediate signed by trust anchor)"
    echo ""
    echo "For now, you can test with the registry's own chain:"
    echo ""
    echo "   curl -X POST ${REGISTRY_URL}/api/trustAnchor/chain/file \\"
    echo "     -H 'Content-Type: application/json' \\"
    echo "     -d '{\"uri\": \"${REGISTRY_URL}/.well-known/x509CertificateChain.pem\"}'"
    echo ""
}

# Main execution
main() {
    mkdir -p "$OUTPUT_DIR"
    
    # Extract trust anchor
    if ! extract_registry_trust_anchor; then
        echo "❌ Failed to extract trust anchor. Cannot proceed."
        exit 1
    fi
    
    # Create chain
    if ! create_chain_with_registry_ta; then
        echo "❌ Failed to create certificate chain."
        exit 1
    fi
    
    # Verify locally
    verify_chain_locally
    
    # Test info
    test_with_registry
    
    echo "========================================="
    echo "Summary"
    echo "========================================="
    echo "✅ Certificate chain created: $OUTPUT_DIR/cert-chain.pem"
    echo "✅ Trust anchor extracted: $OUTPUT_DIR/trust-anchor.pem"
    echo "✅ Intermediate CA: $OUTPUT_DIR/intermediate-ca.pem"
    echo "✅ Leaf certificate: $OUTPUT_DIR/leaf.pem"
    echo ""
    echo "⚠️  Important Notes:"
    echo "   - The intermediate CA is self-signed (for demonstration)"
    echo "   - In production, the intermediate must be signed by the trust anchor's private key"
    echo "   - You need access to the trust anchor's private key to create a valid chain"
    echo "   - For testing, use the registry's own certificate chain instead"
    echo ""
    echo "To use registry's chain for testing:"
    echo "   curl -X POST ${REGISTRY_URL}/api/trustAnchor/chain/file \\"
    echo "     -H 'Content-Type: application/json' \\"
    echo "     -d '{\"uri\": \"${REGISTRY_URL}/.well-known/x509CertificateChain.pem\"}'"
    echo ""
}

# Run main function
main


