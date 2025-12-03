# GitLab SSH Key Setup Guide

This guide explains how to add and use your SSH key with GitLab.

## Step 1: Add Public Key to GitLab

1. **Copy your public key:**
   ```
   ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIKfWKOlQ8ziUAACjaPcZhBqrjKqe7Zy14eSF7m8Gd5DZ
   ```

2. **Add to GitLab:**
   - Log in to your GitLab account
   - Go to **User Settings** → **SSH Keys** (or navigate to: `https://gitlab.com/-/profile/keys`)
   - Click **Add new key**
   - **Title:** Enter a descriptive name (e.g., "Laptop - Tractus-X EDC")
   - **Key:** Paste the public key above
   - **Expiration date:** (Optional) Set if needed
   - Click **Add key**

## Step 2: Save Your Private Key Locally

### Option A: Save to Default SSH Directory (Recommended)

1. **Create the SSH directory if it doesn't exist:**
   ```bash
   mkdir -p ~/.ssh
   chmod 700 ~/.ssh
   ```

2. **Save the private key:**
   ```bash
   # On Windows (PowerShell or Git Bash)
   # Save the private key content to: C:\Users\prince.kofi\.ssh\id_ed25519_gitlab
   
   # On Linux/Mac
   nano ~/.ssh/id_ed25519_gitlab
   # Paste the private key content, save and exit
   ```

3. **Set proper permissions:**
   ```bash
   # On Linux/Mac
   chmod 600 ~/.ssh/id_ed25519_gitlab
   
   # On Windows (Git Bash)
   chmod 600 ~/.ssh/id_ed25519_gitlab
   ```

### Option B: Use Git Bash or PowerShell

**Windows PowerShell:**
```powershell
# Create .ssh directory if it doesn't exist
New-Item -ItemType Directory -Force -Path "$env:USERPROFILE\.ssh"

# Save the private key
@"
-----BEGIN OPENSSH PRIVATE KEY-----
b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAMwAAAAtzc2gtZW
QyNTUxOQAAACCn1ijpUPM4lAAAo2j3GYQaq4yqnu2cteHkhe5vBneQ2QAAAKDvNvG37zbx
twAAAAtzc2gtZWQyNTUxOQAAACCn1ijpUPM4lAAAo2j3GYQaq4yqnu2cteHkhe5vBneQ2Q
AAAEB3F5BBd3gBybCemX5Wd06WlAPmSIl0pa7SboAPz1PmEKfWKOlQ8ziUAACjaPcZhBqr
jKqe7Zy14eSF7m8Gd5DZAAAAHWpvbi5yb2RyaWd1ZXpATDIzMDIwMDdNLmxvY2Fs
-----END OPENSSH PRIVATE KEY-----
"@ | Out-File -FilePath "$env:USERPROFILE\.ssh\id_ed25519_gitlab" -Encoding utf8 -NoNewline
```

## Step 3: Configure SSH Config

Create or edit `~/.ssh/config` (or `C:\Users\prince.kofi\.ssh\config` on Windows):

```bash
# GitLab
Host gitlab.com
    HostName gitlab.com
    User git
    IdentityFile ~/.ssh/id_ed25519_gitlab
    IdentitiesOnly yes
```

**Windows path example:**
```
Host gitlab.com
    HostName gitlab.com
    User git
    IdentityFile C:\Users\prince.kofi\.ssh\id_ed25519_gitlab
    IdentitiesOnly yes
```

## Step 4: Test SSH Connection

Test your SSH connection to GitLab:

```bash
ssh -T git@gitlab.com
```

You should see a message like:
```
Welcome to GitLab, @username!
```

If you see a permission denied error, check:
- The private key file permissions (should be 600)
- The public key is correctly added to GitLab
- The SSH config file is correct

## Step 5: Clone/Use GitLab Repositories

Once configured, you can clone repositories using SSH:

```bash
# Clone using SSH URL
git clone git@gitlab.com:username/repository.git

# Or if you already have a repository, update the remote:
git remote set-url origin git@gitlab.com:username/repository.git
```

## Troubleshooting

### Permission Denied Error

1. **Check key permissions:**
   ```bash
   # Linux/Mac
   ls -la ~/.ssh/id_ed25519_gitlab
   # Should show: -rw------- (600)
   
   # Fix if needed:
   chmod 600 ~/.ssh/id_ed25519_gitlab
   ```

2. **Verify key format:**
   ```bash
   # Test if SSH can read the key
   ssh-keygen -l -f ~/.ssh/id_ed25519_gitlab
   ```

3. **Check SSH agent (optional):**
   ```bash
   # Start SSH agent
   eval "$(ssh-agent -s)"
   
   # Add key to agent
   ssh-add ~/.ssh/id_ed25519_gitlab
   ```

### Multiple SSH Keys

If you have multiple GitLab accounts or need different keys:

**SSH Config example for multiple accounts:**
```
# Personal GitLab
Host gitlab.com-personal
    HostName gitlab.com
    User git
    IdentityFile ~/.ssh/id_ed25519_gitlab_personal
    IdentitiesOnly yes

# Work GitLab
Host gitlab.com-work
    HostName gitlab.com
    User git
    IdentityFile ~/.ssh/id_ed25519_gitlab_work
    IdentitiesOnly yes
```

Then use:
```bash
git clone git@gitlab.com-personal:username/repo.git
# or
git clone git@gitlab.com-work:username/repo.git
```

## Security Best Practices

1. **Never share your private key** - Only the public key goes to GitLab
2. **Use strong passphrases** - Consider adding a passphrase to your key:
   ```bash
   ssh-keygen -p -f ~/.ssh/id_ed25519_gitlab
   ```
3. **Set proper file permissions** - Private keys should be 600 (read/write for owner only)
4. **Use different keys for different services** - Don't reuse the same key everywhere
5. **Rotate keys periodically** - Update keys every 6-12 months

## Additional Resources

- [GitLab SSH Documentation](https://docs.gitlab.com/ee/user/ssh.html)
- [GitLab SSH Key Troubleshooting](https://docs.gitlab.com/ee/user/ssh.html#troubleshooting)

