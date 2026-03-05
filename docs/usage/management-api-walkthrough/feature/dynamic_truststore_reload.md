# Dynamic Trust Store Reloader Extension

## Overview

The Dynamic Trust Store Reloader Extension provides runtime hot-reloading capabilities for SSL/TLS trust stores in Tractus-X EDC connectors. This extension enables connectors to update their trust certificates without requiring a restart, which is crucial for maintaining high availability and security in production environments.

The extension monitors trust store files or directories for changes and automatically reloads the updated certificates, ensuring that new TLS connections immediately use the refreshed trust store while existing connections continue uninterrupted.

## Architecture

The extension consists of four main components that work together to provide dynamic trust store reloading:

### Core Components

#### 1. DynamicTrustReloaderExtension
The main EDC service extension that orchestrates the entire dynamic reloading process. It:
- Initializes the trust store watcher and reloader based on configuration
- Registers the reloadable trust manager as an EDC service
- Handles extension lifecycle (initialization and shutdown)
- Manages configuration validation and error handling

#### 2. ReloadableX509TrustManager
A thread-safe wrapper around the standard `X509TrustManager` that enables hot-swapping of trust managers. Key features:
- Uses `AtomicReference` for thread-safe delegate replacement
- Implements the `X509TrustManager` interface and delegates all certificate validation calls
- Allows runtime replacement of the underlying trust manager without disrupting ongoing TLS handshakes
- Ensures there's always a valid delegate available for certificate validation

#### 3. TrustStoreFileWatcher
Monitors trust store files or directories for changes using Java's `WatchService`. Features include:
- Real-time file system monitoring for modify, create, and delete events
- Debouncing mechanism (500ms) to prevent multiple reload triggers from rapid file changes
- Modification time comparison to avoid unnecessary reloads
- Configurable to monitor single files or entire directories (for PEM certificates)
- Graceful shutdown and resource cleanup

#### 4. TrustStoreReloader
Handles the actual trust store reloading process. Responsibilities include:
- Loading trust stores from different formats (JKS, PKCS12, PEM directories)
- Creating new `X509TrustManager` instances from updated trust stores
- Validating new trust managers before applying them
- Swapping the delegate in the `ReloadableX509TrustManager`
- Comprehensive error handling and logging

#### 5. TrustManagerFactoryHelper
Utility class that provides factory methods for creating `X509TrustManager` instances from various trust store formats:
- **JKS (Java KeyStore)**: Standard Java keystore format
- **PKCS12**: Portable keystore format widely used in production
- **PEM Directory**: Directory containing individual PEM certificate files

## Configuration

The extension is configured through EDC configuration properties. All configuration is optional - if not provided, the extension will skip initialization.

### Required Configuration

| Property | Description | Example | Default |
|----------|-------------|---------|---------|
| `dynamic.truststore.path` | Absolute path to the trust store file or PEM directory | `/etc/ssl/certs/truststore.jks` | null (extension disabled) |

### Optional Configuration

| Property | Description | Example | Default |
|----------|-------------|---------|---------|
| `dynamic.truststore.type` | Trust store type (JKS, PKCS12, PEM_DIR) | `PKCS12` | `JKS` |
| `dynamic.truststore.password` | Password for encrypted trust stores | `changeit` | empty string |

### Configuration Examples

#### JKS Trust Store
```properties
dynamic.truststore.path=/opt/connector/truststore.jks
dynamic.truststore.type=JKS
dynamic.truststore.password=changeit
```

#### PKCS12 Trust Store
```properties
dynamic.truststore.path=/opt/connector/truststore.p12
dynamic.truststore.type=PKCS12
dynamic.truststore.password=securepassword
```

#### PEM Directory
```properties
dynamic.truststore.path=/opt/connector/certs/
dynamic.truststore.type=PEM_DIR
# No password needed for PEM files
```

## Supported Trust Store Formats

### JKS (Java KeyStore)
- Standard Java keystore format
- Requires password protection
- Single file containing multiple certificates
- Suitable for Java-centric environments

### PKCS12
- Industry-standard keystore format
- Better interoperability than JKS
- Requires password protection
- Recommended for production deployments

### PEM Directory
- Directory containing individual PEM certificate files
- Each `.pem` file is loaded as a separate certificate
- No password required
- Ideal for environments with frequently updated certificate authorities
- Supports hot addition/removal of individual certificates

## Usage Scenarios

### 1. Certificate Authority Updates
When a Certificate Authority (CA) updates its root or intermediate certificates, the extension can automatically reload the updated trust store without connector restart:

```bash
# Update the trust store file
cp new-truststore.jks /opt/connector/truststore.jks
# Extension detects change and reloads automatically
```

### 2. Dynamic Partner Onboarding
When adding new business partners to the dataspace, their certificates can be added to a PEM directory:

```bash
# Add new partner certificate to PEM directory
cp partner-cert.pem /opt/connector/certs/
# Extension detects new file and reloads automatically
```

### 3. Certificate Revocation
Revoked certificates can be removed from the trust store dynamically:

```bash
# Remove revoked certificate from PEM directory
rm /opt/connector/certs/revoked-cert.pem
# Extension detects file deletion and reloads
```

## Implementation Details

### Thread Safety
The extension is designed for thread-safe operation in concurrent environments:
- `AtomicReference` ensures safe trust manager replacement
- File watcher runs in a dedicated daemon thread
- Debouncing prevents race conditions from rapid file changes
- All certificate validation operations are delegated to thread-safe trust managers

### Error Handling
The extension implements comprehensive error handling:
- Invalid trust store configurations are detected during initialization
- Corrupted trust store files don't affect the currently loaded certificates
- Validation failures prevent applying invalid trust managers
- All errors are logged with appropriate severity levels

### Performance Considerations
- Minimal overhead during normal operation (file system monitoring only)
- Trust store reloading occurs asynchronously
- Existing TLS connections are not interrupted during reloads
- Debouncing prevents unnecessary reload operations
- Memory usage is optimized through efficient certificate loading

### Monitoring and Logging
The extension provides detailed logging for operational visibility:
- Initialization and configuration status
- File system events and reload triggers
- Trust store loading success/failure
- Certificate validation results
- Extension shutdown status

## Integration with EDC

### Service Registration
The extension registers the following services with the EDC runtime:
- `X509TrustManager`: The reloadable trust manager for SSL/TLS connections
- `TrustStoreFileWatcher`: The file watcher service for monitoring trust store changes

### Lifecycle Management
The extension integrates with EDC's service extension lifecycle:
- **Initialize**: Configuration validation, component setup, and service registration
- **Shutdown**: Graceful cleanup of file system watchers and thread pools

## Testing

The extension includes comprehensive test coverage:
- Unit tests for all core components
- Integration tests with sample certificates
- File system monitoring tests
- Trust store format validation tests
- Thread safety and concurrency tests

### Test Certificates
Sample certificates are provided in the `local-certs/` directory for testing:
- `CertificateAuthorityCertificate.pem`: Root CA certificate
- `example-ca.pem`: Example intermediate CA
- `example-ca2.pem`: Additional CA for multi-CA scenarios
- `sample-ca.pem`: Sample CA for testing

## Best Practices

### Production Deployment
1. **Use PKCS12 format** for better interoperability and security
2. **Set appropriate file permissions** on trust store files
3. **Monitor logs** for reload events and errors
4. **Test trust store updates** in non-production environments first
5. **Implement backup procedures** for trust store files

### Security Considerations
1. **Protect trust store passwords** using EDC's secure configuration mechanisms
2. **Limit file system access** to trust store directories
3. **Validate certificate sources** before adding to trust stores
4. **Monitor for unauthorized trust store modifications**
5. **Implement certificate revocation checking** alongside this extension

### Operational Guidelines
1. **Plan trust store updates** during maintenance windows when possible
2. **Monitor connector performance** after trust store reloads
3. **Keep backup copies** of previous trust store versions
4. **Document certificate management procedures**
5. **Test failover scenarios** with corrupted trust store files

## Troubleshooting

### Common Issues

#### Extension Not Starting
**Symptoms**: Log shows "dynamic.truststore.path not set, skipping dynamic reload"
**Solution**: Ensure `dynamic.truststore.path` is configured with a valid file or directory path

#### Trust Store Load Failures
**Symptoms**: Log shows "Failed to load initial truststore"
**Solutions**:
- Verify trust store file exists and is readable
- Check trust store password is correct
- Validate trust store format matches configured type
- Ensure trust store file is not corrupted

#### File Watcher Not Detecting Changes
**Symptoms**: Trust store changes don't trigger reloads
**Solutions**:
- Verify file system permissions allow monitoring
- Check if the parent directory exists and is accessible
- Ensure the trust store file path is correct
- Monitor for file system watcher errors in logs

#### Certificate Validation Failures
**Symptoms**: TLS connections fail after trust store reload
**Solutions**:
- Verify new certificates are valid and not expired
- Check certificate chains are complete
- Ensure certificate authorities are trusted
- Review certificate validation policies

### Debug Logging
Enable debug logging for the extension by configuring the EDC monitor:
```properties
edc.logger.level=DEBUG
```

This will provide detailed information about:
- File system events
- Trust store loading operations
- Certificate validation results
- Internal component state changes

## Migration Guide

### From Static Trust Stores
To migrate from static trust stores to dynamic reloading:

1. **Backup existing trust store**
2. **Configure extension properties** with current trust store path
3. **Test extension initialization** in development environment
4. **Deploy to production** with monitoring enabled
5. **Verify dynamic reloading** with test certificate updates

### Trust Store Format Migration
To migrate between trust store formats:

1. **Export certificates** from current format
2. **Import into new format** using Java keytool or equivalent
3. **Update configuration** with new format type
4. **Test with extension** before production deployment
5. **Update documentation** and operational procedures

## Future Enhancements

Potential areas for future development:
- Support for additional trust store formats (BKS, etc.)
- Integration with external certificate management systems
- REST API for manual trust store reload triggers
- Certificate expiration monitoring and alerts
- Trust store change auditing and reporting
- Integration with HashiCorp Vault for certificate management

## License

This extension is licensed under the Apache-2.0 license, consistent with the Tractus-X EDC project.

## Contributing

When contributing to the Dynamic Trust Store Reloader Extension:
- Follow the existing code style and patterns
- Add comprehensive tests for new functionality
- Update documentation for configuration changes
- Consider backward compatibility for configuration properties
- Test with all supported trust store formats
