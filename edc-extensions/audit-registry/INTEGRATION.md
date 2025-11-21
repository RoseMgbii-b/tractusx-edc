# Audit Registry Integration Guide

This guide explains how to integrate the Audit Registry into your Tractus-X EDC deployment.

## Quick Start

### 1. Add Dependencies

Add the audit registry modules to your runtime's `build.gradle.kts`:

```kotlin
dependencies {
    // Audit Registry modules
    implementation(project(":edc-extensions:audit-registry:audit-store-sql"))
    implementation(project(":edc-extensions:audit-registry:audit-registry-core"))
    implementation(project(":edc-extensions:audit-registry:audit-event-subscriber"))
    
    // Your other dependencies...
}
```

### 2. Database Setup

#### Create Database (if using dedicated audit database)

```sql
CREATE DATABASE edc_audit;
```

#### Run Migration

The audit table will be created automatically on first startup. The schema includes:

```sql
CREATE TABLE edc_audit_entry (
    id VARCHAR(255) PRIMARY KEY,
    timestamp BIGINT NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    event_name VARCHAR(255) NOT NULL,
    description TEXT,
    result VARCHAR(255),
    metadata JSONB,
    critical BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 3. Configure Properties

Create or update your configuration file (e.g., `config.properties`):

```properties
# Enable audit
tx.edc.audit.enabled=true

# PostgreSQL connection
edc.datasource.default.url=jdbc:postgresql://localhost:5432/edc
edc.datasource.default.user=edc_user
edc.datasource.default.password=edc_password

# Redis cache (optional)
tx.edc.audit.redis.enabled=true
tx.edc.audit.redis.host=localhost
tx.edc.audit.redis.port=6379
```

See [example-config.properties](example-config.properties) for complete configuration options.

### 4. Deploy Redis (Optional but Recommended)

Using Docker:

```bash
docker run -d \
  --name edc-audit-redis \
  -p 6379:6379 \
  redis:7-alpine
```

Using Docker Compose:

```yaml
services:
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    command: redis-server --appendonly yes

volumes:
  redis-data:
```

### 5. Start Your EDC Runtime

```bash
./gradlew :edc-controlplane:edc-runtime-memory:run
# or your specific runtime
```

## Verification

### Check Logs

On startup, you should see:

```
INFO: Audit Registry Core initialized
INFO: SQL Audit Store initialized with datasource: default
INFO: Redis cache enabled for audit entries
INFO: Audit event subscriber registered successfully
```

### Verify Database

Check that the audit table was created:

```sql
SELECT * FROM edc_audit_entry LIMIT 5;
```

### Verify Event Capture

Trigger a contract negotiation or data transfer and verify events are being captured:

```sql
SELECT 
    event_type, 
    event_name, 
    result, 
    timestamp 
FROM edc_audit_entry 
ORDER BY timestamp DESC 
LIMIT 10;
```

## Advanced Configuration

### Separate Audit Database

For production deployments, use a dedicated database:

```properties
# Dedicated audit datasource
edc.sql.store.audit.datasource=audit
edc.datasource.audit.url=jdbc:postgresql://audit-db:5432/edc_audit
edc.datasource.audit.user=audit_user
edc.datasource.audit.password=audit_password
```

### Configure Immutability

After deployment, enforce immutability on the audit table:

```sql
-- Enable row-level security
ALTER TABLE edc_audit_entry ENABLE ROW LEVEL SECURITY;

-- Prevent updates
CREATE POLICY audit_immutable ON edc_audit_entry 
FOR UPDATE USING (false);

-- Prevent deletes (except for retention policy)
CREATE POLICY audit_no_delete ON edc_audit_entry 
FOR DELETE USING (false);

-- Grant INSERT permission to audit writer
GRANT INSERT ON edc_audit_entry TO audit_writer;
GRANT SELECT ON edc_audit_entry TO audit_reader;
```

### Enable Encryption

#### Option 1: SSL/TLS for Connections

```properties
edc.datasource.audit.url=jdbc:postgresql://audit-db:5432/edc_audit?ssl=true&sslmode=require
```

#### Option 2: Column-level Encryption

```sql
-- Enable pgcrypto extension
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Encrypt sensitive columns (requires application changes)
ALTER TABLE edc_audit_entry ADD COLUMN metadata_encrypted BYTEA;
```

#### Option 3: Transparent Data Encryption (TDE)

Configure at PostgreSQL level. Refer to your PostgreSQL distribution documentation.

### Configure Retention Policy

Create a scheduled job to enforce retention:

```sql
-- Install pg_cron extension
CREATE EXTENSION IF NOT EXISTS pg_cron;

-- Schedule retention cleanup (7-year retention)
SELECT cron.schedule(
    'audit-retention-cleanup',
    '0 2 * * *', -- Daily at 2 AM
    $$DELETE FROM edc_audit_entry 
      WHERE timestamp < EXTRACT(EPOCH FROM NOW() - INTERVAL '7 years') * 1000$$
);
```

Or use a simple cron job:

```bash
# Add to crontab
0 2 * * * psql -U audit_user -d edc_audit -c "DELETE FROM edc_audit_entry WHERE timestamp < EXTRACT(EPOCH FROM NOW() - INTERVAL '7 years') * 1000"
```

### Redis Security

Enable authentication:

```bash
# Redis configuration
requirepass your_secure_password
```

```properties
# Application configuration
tx.edc.audit.redis.password=your_secure_password
```

## Monitoring

### Database Metrics

Monitor these metrics:

1. **Table size**: `SELECT pg_size_pretty(pg_total_relation_size('edc_audit_entry'));`
2. **Row count**: `SELECT COUNT(*) FROM edc_audit_entry;`
3. **Write rate**: Track `INSERT` operations per second
4. **Query performance**: Use `EXPLAIN ANALYZE` on common queries

### Redis Metrics

Monitor cache performance:

```bash
redis-cli INFO stats
```

Key metrics:
- `keyspace_hits`: Cache hits
- `keyspace_misses`: Cache misses
- `used_memory`: Memory usage
- `evicted_keys`: Number of evicted keys

### Application Metrics

Monitor:
- Audit queue depth
- Failed audit writes
- Average write latency
- Cache hit ratio

## Troubleshooting

### Issue: Audit entries not appearing

**Solutions:**
1. Check that `tx.edc.audit.enabled=true`
2. Verify database connection is working
3. Check application logs for errors
4. Verify the audit table exists and is accessible

### Issue: Performance degradation

**Solutions:**
1. Enable Redis cache if not already enabled
2. Increase `tx.edc.audit.thread.pool.size`
3. Optimize database indexes
4. Consider archiving old audit entries
5. Use a dedicated database for audit data

### Issue: Redis connection failures

**Solutions:**
1. System continues to work without Redis (just slower)
2. Check Redis is running: `redis-cli ping`
3. Verify connection settings
4. Check firewall rules
5. Review Redis logs

### Issue: Database disk space

**Solutions:**
1. Implement retention policy
2. Archive old records to cold storage
3. Compress JSONB data
4. Use table partitioning for large deployments

## Testing

### Unit Testing

Mock the `AuditRegistry` in your tests:

```java
@Mock
private AuditRegistry auditRegistry;

@Test
public void testAuditRecording() {
    // Your test code
    verify(auditRegistry).record(any(AuditEntry.class));
}
```

### Integration Testing

Start test containers:

```java
@Container
static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
    .withDatabaseName("test_audit");

@Container
static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
    .withExposedPorts(6379);
```

### Load Testing

Test audit system under load:

1. Generate high volume of events
2. Monitor database performance
3. Check Redis cache efficiency
4. Verify no events are lost

## Migration from Existing Systems

If migrating from an existing audit system:

1. **Parallel Running**: Run both systems in parallel initially
2. **Data Migration**: Export old audit data and import to new schema
3. **Validation**: Compare audit entries between systems
4. **Cutover**: Switch to new system once validated
5. **Archival**: Archive old system data for compliance

## Best Practices

1. **Separate Database**: Use dedicated database for audit data
2. **Enable Redis**: Always enable Redis in production
3. **Regular Backups**: Backup audit database regularly
4. **Access Control**: Implement strict access controls
5. **Monitoring**: Set up alerting for audit system health
6. **Retention**: Implement appropriate retention policies
7. **Encryption**: Enable encryption at rest and in transit
8. **Immutability**: Enforce immutability at database level
9. **Testing**: Test disaster recovery procedures
10. **Documentation**: Document your audit procedures

## Support

For issues or questions:

1. Check the [README](README.md) for detailed information
2. Review configuration in [example-config.properties](example-config.properties)
3. Open an issue in the GitHub repository
4. Consult the Tractus-X EDC documentation

## License

Apache License 2.0 - See LICENSE file for details.
