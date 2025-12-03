# GitLab SSH Key Setup Script for Windows
# This script helps set up your SSH key for GitLab

$ErrorActionPreference = "Stop"

Write-Host "GitLab SSH Key Setup" -ForegroundColor Green
Write-Host "====================" -ForegroundColor Green
Write-Host ""

# Get user profile path
$sshDir = Join-Path $env:USERPROFILE ".ssh"
$privateKeyPath = Join-Path $sshDir "id_ed25519_gitlab"
$configPath = Join-Path $sshDir "config"

# Create .ssh directory if it doesn't exist
if (-not (Test-Path $sshDir)) {
    Write-Host "Creating .ssh directory..." -ForegroundColor Yellow
    New-Item -ItemType Directory -Path $sshDir -Force | Out-Null
    Write-Host "✓ Created .ssh directory" -ForegroundColor Green
} else {
    Write-Host "✓ .ssh directory exists" -ForegroundColor Green
}

# Check if private key already exists
if (Test-Path $privateKeyPath) {
    $overwrite = Read-Host "Private key already exists. Overwrite? (y/N)"
    if ($overwrite -ne "y" -and $overwrite -ne "Y") {
        Write-Host "Skipping private key creation." -ForegroundColor Yellow
    } else {
        Write-Host "Saving private key..." -ForegroundColor Yellow
        $privateKey = @"
-----BEGIN OPENSSH PRIVATE KEY-----
b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAMwAAAAtzc2gtZW
QyNTUxOQAAACCn1ijpUPM4lAAAo2j3GYQaq4yqnu2cteHkhe5vBneQ2QAAAKDvNvG37zbx
twAAAAtzc2gtZWQyNTUxOQAAACCn1ijpUPM4lAAAo2j3GYQaq4yqnu2cteHkhe5vBneQ2Q
AAAEB3F5BBd3gBybCemX5Wd06WlAPmSIl0pa7SboAPz1PmEKfWKOlQ8ziUAACjaPcZhBqr
jKqe7Zy14eSF7m8Gd5DZAAAAHWpvbi5yb2RyaWd1ZXpATDIzMDIwMDdNLmxvY2Fs
-----END OPENSSH PRIVATE KEY-----
"@
        $privateKey | Out-File -FilePath $privateKeyPath -Encoding utf8 -NoNewline
        Write-Host "✓ Private key saved to: $privateKeyPath" -ForegroundColor Green
    }
} else {
    Write-Host "Saving private key..." -ForegroundColor Yellow
    $privateKey = @"
-----BEGIN OPENSSH PRIVATE KEY-----
b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAMwAAAAtzc2gtZW
QyNTUxOQAAACCn1ijpUPM4lAAAo2j3GYQaq4yqnu2cteHkhe5vBneQ2QAAAKDvNvG37zbx
twAAAAtzc2gtZWQyNTUxOQAAACCn1ijpUPM4lAAAo2j3GYQaq4yqnu2cteHkhe5vBneQ2Q
AAAEB3F5BBd3gBybCemX5Wd06WlAPmSIl0pa7SboAPz1PmEKfWKOlQ8ziUAACjaPcZhBqr
jKqe7Zy14eSF7m8Gd5DZAAAAHWpvbi5yb2RyaWd1ZXpATDIzMDIwMDdNLmxvY2Fs
-----END OPENSSH PRIVATE KEY-----
"@
    $privateKey | Out-File -FilePath $privateKeyPath -Encoding utf8 -NoNewline
    Write-Host "✓ Private key saved to: $privateKeyPath" -ForegroundColor Green
}

# Set file permissions (Windows)
Write-Host "Setting file permissions..." -ForegroundColor Yellow
icacls $privateKeyPath /inheritance:r /grant:r "$env:USERNAME`:F" | Out-Null
Write-Host "✓ Permissions set" -ForegroundColor Green

# Configure SSH config
Write-Host "Configuring SSH config..." -ForegroundColor Yellow
$sshConfig = @"
# GitLab
Host gitlab.com
    HostName gitlab.com
    User git
    IdentityFile $privateKeyPath
    IdentitiesOnly yes
"@

if (Test-Path $configPath) {
    $existingConfig = Get-Content $configPath -Raw
    if ($existingConfig -notmatch "Host gitlab.com") {
        Add-Content -Path $configPath -Value "`n$sshConfig"
        Write-Host "✓ Added GitLab configuration to existing SSH config" -ForegroundColor Green
    } else {
        Write-Host "✓ GitLab configuration already exists in SSH config" -ForegroundColor Green
    }
} else {
    $sshConfig | Out-File -FilePath $configPath -Encoding utf8
    Write-Host "✓ Created SSH config file" -ForegroundColor Green
}

Write-Host ""
Write-Host "Setup Complete!" -ForegroundColor Green
Write-Host "==============" -ForegroundColor Green
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Yellow
Write-Host "1. Add your public key to GitLab:" -ForegroundColor White
Write-Host "   ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIKfWKOlQ8ziUAACjaPcZhBqrjKqe7Zy14eSF7m8Gd5DZ" -ForegroundColor Cyan
Write-Host ""
Write-Host "2. Go to: https://gitlab.com/-/profile/keys" -ForegroundColor White
Write-Host ""
Write-Host "3. Test your connection:" -ForegroundColor White
Write-Host "   ssh -T git@gitlab.com" -ForegroundColor Cyan
Write-Host ""


