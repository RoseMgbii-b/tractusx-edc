#!/bin/bash
# Verification script for Certificate Validator Extension

echo "=== Certificate Validator Extension Verification ==="
echo ""

# Step 1: Build
echo "Step 1: Building extension..."
./gradlew :edc-extensions:certificate-validator:build
if [ $? -ne 0 ]; then
    echo "❌ Build failed!"
    exit 1
fi
echo "✅ Build successful"
echo ""

# Step 2: Check META-INF exists
echo "Step 2: Checking META-INF registration..."
if [ -f "edc-extensions/certificate-validator/src/main/resources/META-INF/services/org.eclipse.edc.spi.system.ServiceExtension" ]; then
    echo "✅ META-INF/services file exists"
    echo "Content:"
    cat edc-extensions/certificate-validator/src/main/resources/META-INF/services/org.eclipse.edc.spi.system.ServiceExtension
else
    echo "❌ META-INF/services file not found!"
    exit 1
fi
echo ""

# Step 3: Run tests
echo "Step 3: Running unit tests..."
./gradlew :edc-extensions:certificate-validator:test
if [ $? -ne 0 ]; then
    echo "❌ Tests failed!"
    exit 1
fi
echo "✅ All tests passed"
echo ""

# Step 4: Check JAR contains META-INF
echo "Step 4: Verifying JAR contains META-INF..."
JAR_FILE=$(find edc-extensions/certificate-validator/build/libs -name "*.jar" -not -name "*-sources.jar" -not -name "*-javadoc.jar" | head -1)
if [ -z "$JAR_FILE" ]; then
    echo "❌ JAR file not found!"
    exit 1
fi

if unzip -l "$JAR_FILE" | grep -q "META-INF/services/org.eclipse.edc.spi.system.ServiceExtension"; then
    echo "✅ META-INF/services found in JAR"
else
    echo "❌ META-INF/services not found in JAR!"
    exit 1
fi
echo ""

echo "=== Verification Complete ==="
echo "✅ Extension is ready to use!"

