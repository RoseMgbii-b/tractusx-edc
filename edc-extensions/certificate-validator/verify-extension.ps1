# PowerShell Verification script for Certificate Validator Extension

Write-Host "=== Certificate Validator Extension Verification ===" -ForegroundColor Cyan
Write-Host ""

# Step 1: Build
Write-Host "Step 1: Building extension..." -ForegroundColor Yellow
& .\gradlew.bat :edc-extensions:certificate-validator:build
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Build failed!" -ForegroundColor Red
    exit 1
}
Write-Host "✅ Build successful" -ForegroundColor Green
Write-Host ""

# Step 2: Check META-INF exists
Write-Host "Step 2: Checking META-INF registration..." -ForegroundColor Yellow
$metaInfPath = "edc-extensions\certificate-validator\src\main\resources\META-INF\services\org.eclipse.edc.spi.system.ServiceExtension"
if (Test-Path $metaInfPath) {
    Write-Host "✅ META-INF/services file exists" -ForegroundColor Green
    Write-Host "Content:"
    Get-Content $metaInfPath
} else {
    Write-Host "❌ META-INF/services file not found!" -ForegroundColor Red
    exit 1
}
Write-Host ""

# Step 3: Run tests
Write-Host "Step 3: Running unit tests..." -ForegroundColor Yellow
& .\gradlew.bat :edc-extensions:certificate-validator:test
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Tests failed!" -ForegroundColor Red
    exit 1
}
Write-Host "✅ All tests passed" -ForegroundColor Green
Write-Host ""

# Step 4: Check JAR contains META-INF
Write-Host "Step 4: Verifying JAR contains META-INF..." -ForegroundColor Yellow
$jarFiles = Get-ChildItem -Path "edc-extensions\certificate-validator\build\libs" -Filter "*.jar" | Where-Object { $_.Name -notlike "*-sources.jar" -and $_.Name -notlike "*-javadoc.jar" }
if ($jarFiles.Count -eq 0) {
    Write-Host "❌ JAR file not found!" -ForegroundColor Red
    exit 1
}

$jarFile = $jarFiles[0].FullName
$jarContent = jar -tf $jarFile
if ($jarContent -match "META-INF/services/org.eclipse.edc.spi.system.ServiceExtension") {
    Write-Host "✅ META-INF/services found in JAR" -ForegroundColor Green
} else {
    Write-Host "❌ META-INF/services not found in JAR!" -ForegroundColor Red
    exit 1
}
Write-Host ""

Write-Host "=== Verification Complete ===" -ForegroundColor Cyan
Write-Host "✅ Extension is ready to use!" -ForegroundColor Green

