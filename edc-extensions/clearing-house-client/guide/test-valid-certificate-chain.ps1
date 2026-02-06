# PowerShell script for testing certificate chain with Gaia-X Registry
# Usage:
#   .\test-valid-certificate-chain.ps1                    # Test docexploit.com (default)
#   .\test-valid-certificate-chain.ps1 -Chain "registry"  # Test registry's own chain
#   .\test-valid-certificate-chain.ps1 -Chain "docexploit" # Test docexploit.com chain
#   .\test-valid-certificate-chain.ps1 -ChainUri "https://example.com/.well-known/x509CertificateChain.pem"  # Custom chain

param(
    [string]$RegistryBaseUrl = "https://registry.lab.gaia-x.eu/development",
    [string]$Chain = "docexploit",
    [string]$ChainUri = ""
)

$ErrorActionPreference = "Stop"

# Determine which chain to test
if ([string]::IsNullOrEmpty($ChainUri)) {
    switch ($Chain.ToLower()) {
        "registry" { 
            $ChainUri = "$RegistryBaseUrl/.well-known/x509CertificateChain.pem"
            $ChainName = "Registry's Own Certificate Chain"
            $ExpectedResult = "VALID"
        }
        "reg" { 
            $ChainUri = "$RegistryBaseUrl/.well-known/x509CertificateChain.pem"
            $ChainName = "Registry's Own Certificate Chain"
            $ExpectedResult = "VALID"
        }
        "docexploit" { 
            $ChainUri = "https://docexploit.com/.well-known/x509CertificateChain.pem"
            $ChainName = "docexploit.com Certificate Chain"
            $ExpectedResult = "INVALID (expected failure)"
        }
        "docexploit.com" { 
            $ChainUri = "https://docexploit.com/.well-known/x509CertificateChain.pem"
            $ChainName = "docexploit.com Certificate Chain"
            $ExpectedResult = "INVALID (expected failure)"
        }
        "doc" { 
            $ChainUri = "https://docexploit.com/.well-known/x509CertificateChain.pem"
            $ChainName = "docexploit.com Certificate Chain"
            $ExpectedResult = "INVALID (expected failure)"
        }
        default {
            # Treat as custom URI
            $ChainUri = $Chain
            $ChainName = "Custom Certificate Chain"
            $ExpectedResult = "UNKNOWN"
        }
    }
} else {
    $ChainName = "Custom Certificate Chain"
    $ExpectedResult = "UNKNOWN"
}

$ApiEndpoint = "$RegistryBaseUrl/api/trustAnchor/chain/file"

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "Testing: $ChainName" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "Registry Base URL: $RegistryBaseUrl"
Write-Host "Certificate Chain URI: $ChainUri"
Write-Host "API Endpoint: $ApiEndpoint"
Write-Host "Expected Result: $ExpectedResult"
Write-Host ""

# Step 1: Get registry trust anchors
Write-Host "Step 1: Fetching registry trust anchors..." -ForegroundColor Yellow
Write-Host "----------------------------------------" -ForegroundColor Gray
$trustAnchorsUrl = "$RegistryBaseUrl/api/trustAnchor"
Write-Host "   URL: $trustAnchorsUrl"
try {
    # Note: This endpoint returns XML (TrustServiceStatusList), not JSON
    $trustAnchorsResponse = Invoke-RestMethod -Uri $trustAnchorsUrl -Method Get -ErrorAction SilentlyContinue
    Write-Host "✅ Trust anchors retrieved" -ForegroundColor Green
    # Show first few properties to confirm it's working
    Write-Host "   Response type: XML (TrustServiceStatusList)" -ForegroundColor Gray
    Write-Host "   (XML response received - showing structure)" -ForegroundColor Gray
    Write-Host ""
} catch {
    Write-Host "⚠️  Could not fetch trust anchors (API may be unavailable)" -ForegroundColor Yellow
    Write-Host "   Try manually: Invoke-RestMethod -Uri '$trustAnchorsUrl' -Method Get" -ForegroundColor Gray
    Write-Host ""
}

# Step 2: Download certificate chain
Write-Host "Step 2: Downloading certificate chain..." -ForegroundColor Yellow
Write-Host "----------------------------------------" -ForegroundColor Gray
$chainFile = [System.IO.Path]::GetTempFileName()

try {
    Invoke-WebRequest -Uri $ChainUri -OutFile $chainFile -ErrorAction Stop
    Write-Host "✅ Downloaded certificate chain from URI" -ForegroundColor Green
    
    $chainContent = Get-Content $chainFile -Raw
    $certCount = ([regex]::Matches($chainContent, "-----BEGIN CERTIFICATE-----")).Count
    Write-Host "   Chain contains $certCount certificate(s)"
    Write-Host ""
} catch {
    Write-Host "❌ Failed to download certificate chain: $_" -ForegroundColor Red
    Remove-Item $chainFile -ErrorAction SilentlyContinue
    exit 1
}

# Step 3: Verify the chain with registry API
Write-Host "Step 3: Verifying certificate chain with registry API..." -ForegroundColor Yellow
Write-Host "----------------------------------------" -ForegroundColor Gray

$requestBody = @{
    uri = $ChainUri
} | ConvertTo-Json

Write-Host "Request:"
Write-Host ($requestBody | ConvertFrom-Json | ConvertTo-Json)
Write-Host ""

try {
    $response = Invoke-RestMethod -Uri $ApiEndpoint -Method Post -Body $requestBody -ContentType "application/json" -ErrorAction Stop
    $httpCode = 200
    
    Write-Host "Response (HTTP $httpCode):" -ForegroundColor Green
    $response | ConvertTo-Json -Depth 10
    Write-Host ""
    
    if ($response.result -eq $true -or $response.valid -eq $true) {
        Write-Host "✅ SUCCESS: Certificate chain is VALID" -ForegroundColor Green
        Write-Host ""
        Write-Host "This certificate chain passes registry validation!" -ForegroundColor Green
    } else {
        Write-Host "⚠️  Certificate chain validation returned false" -ForegroundColor Yellow
    }
} catch {
    $httpCode = $_.Exception.Response.StatusCode.value__
    $errorBody = $_.ErrorDetails.Message
    
    Write-Host "Response (HTTP $httpCode):" -ForegroundColor Red
    if ($errorBody) {
        try {
            $errorJson = $errorBody | ConvertFrom-Json
            $errorJson | ConvertTo-Json -Depth 10
        } catch {
            Write-Host $errorBody
        }
    } else {
        Write-Host $_.Exception.Message
    }
    Write-Host ""
    
    if ($httpCode -eq 409) {
        Write-Host "❌ CONFLICT: Certificate chain validation failed" -ForegroundColor Red
        if ($Chain -eq "docexploit" -or $Chain -eq "docexploit.com" -or $Chain -eq "doc") {
            Write-Host "   (EXPECTED for docexploit.com)" -ForegroundColor Yellow
        }
        Write-Host "   This usually means:" -ForegroundColor Yellow
        Write-Host "   - Root certificate is not self-signed"
        Write-Host "   - Root certificate doesn't match registry trust anchors"
        Write-Host ""
        if ($Chain -eq "docexploit" -or $Chain -eq "docexploit.com" -or $Chain -eq "doc") {
            Write-Host "   This is expected because:" -ForegroundColor Yellow
            Write-Host "   - docexploit.com uses a commercial SSL certificate (USERTrust root)"
            Write-Host "   - The root is NOT self-signed (it's signed by USERTrust)"
            Write-Host "   - USERTrust is not in the registry's trust anchors"
        }
    } elseif ($httpCode -eq 400) {
        Write-Host "❌ BAD REQUEST: Could not load certificate chain from URI" -ForegroundColor Red
        Write-Host "   This usually means the registry cannot access the URI" -ForegroundColor Yellow
    } else {
        Write-Host "⚠️  Unexpected response code: $httpCode" -ForegroundColor Yellow
    }
}

# Summary
Write-Host ""
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "Test Summary - $ChainName" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

if ($httpCode -eq 200 -and ($response.result -eq $true -or $response.valid -eq $true)) {
    Write-Host "✅ Registry Validation: PASSED" -ForegroundColor Green
} elseif ($httpCode -eq 200) {
    Write-Host "⚠️  Registry Validation: FAILED (but API call succeeded)" -ForegroundColor Yellow
} elseif ($httpCode -eq 409) {
    Write-Host "❌ Registry Validation: FAILED (HTTP 409 Conflict)" -ForegroundColor Red
    Write-Host "   Expected: Root certificate is not self-signed or doesn't match trust anchors" -ForegroundColor Yellow
} else {
    Write-Host "❌ Registry Validation: FAILED (HTTP $httpCode)" -ForegroundColor Red
}

if ($Chain -eq "docexploit" -or $Chain -eq "docexploit.com" -or $Chain -eq "doc") {
    Write-Host ""
    Write-Host "Key Findings:" -ForegroundColor Yellow
    Write-Host "  - docexploit.com uses a commercial SSL certificate chain"
    Write-Host "  - The root certificate is NOT self-signed (it's signed by USERTrust)"
    Write-Host "  - Registry requires self-signed roots that match its trust anchors"
    Write-Host "  - This chain will NOT work with Gaia-X Registry (dev or prod)"
} elseif ($Chain -eq "registry" -or $Chain -eq "reg") {
    Write-Host ""
    Write-Host "Key Findings:" -ForegroundColor Yellow
    Write-Host "  - Registry's own certificate chain should pass validation"
    Write-Host "  - This chain is used by the registry itself"
    Write-Host "  - If this fails, there may be a configuration issue"
}
Write-Host ""
Write-Host "=========================================" -ForegroundColor Cyan

# Cleanup
Remove-Item $chainFile -ErrorAction SilentlyContinue


