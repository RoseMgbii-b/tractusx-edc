# Audit Registry Extension

## Overview

The Audit Registry extension provides a centralized audit logging system for Tractus-X EDC, designed to meet security and compliance requirements for traceability of all critical transactions and events.

## Architecture

The audit system consists of three main components:

1. **PostgreSQL Store** (`audit-store-sql`): Stores critical audit data with encryption and immutability support
2. **Redis Cache** (`audit-registry-core`): Provides fast access to recent critical audit entries
3. **Elasticsearch Store** (future): For detailed logs and extended audit information

### Data Flow

```
EDC Events → Audit Event Subscriber → Audit Registry → 
  ├─ Critical Events → PostgreSQL + Redis Cache
  └─ Detailed Logs → Elasticsearch (optional)
```

## Features

### Core Capabilities

- **Event Capture**: Automatically captures contract negotiation and data transfer events
- **Critical vs. Normal Classification**: Routes events based on criticality
- **PostgreSQL Storage**: Persistent storage with encryption support
- **Redis Caching**: Fast access to recent critical audit entries
- **Async Processing**: Non-blocking audit recording
- **Configurable Retention**: Support for custom retention policies

### Audit Entry Structure

Each audit entry contains:
- `id`: Unique identifier
- `timestamp`: Event timestamp (milliseconds since epoch)
- `eventType`: Type of event (TRANSACTION, CONTRACT_NEGOTIATION, etc.)
- `eventName`: Specific event name
- `description`: Human-readable description
- `result`: Event result (SUCCESS, FAILED, PENDING, etc.)
- `metadata`: Additional context as JSON
- `critical`: Whether the event is critical

## Configuration

### PostgreSQL Configuration

```properties
# Datasource for audit entries (uses default datasource if not specified)
edc.sql.store.audit.datasource=default

# PostgreSQL connection (if using separate datasource)
edc.datasource.audit.url=jdbc:postgresql://localhost:5432/edc_audit
edc.datasource.audit.user=audit_user
edc.datasource.audit.password=secure_password
```

### Redis Cache Configuration

```properties
# Enable Redis caching
tx.edc.audit.redis.enabled=true

# Redis connection
tx.edc.audit.redis.host=localhost
tx.edc.audit.redis.port=6379
tx.edc.audit.redis.password=redis_password

# Cache TTL (time to live in seconds)
tx.edc.audit.redis.ttl=3600
```

### Audit Event Subscriber Configuration

```properties
# Enable/disable audit event capture
tx.edc.audit.enabled=true

# Thread pool size for async processing
tx.edc.audit.thread.pool.size=2
```

## Database Schema

The PostgreSQL table is created automatically on first run:

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

### Indexes

- `idx_audit_timestamp`: For time-based queries
- `idx_audit_event_type`: For filtering by event type
- `idx_audit_critical`: For filtering critical events
- `idx_audit_event_type_critical`: Composite index for common queries

## Security Features

### Encryption

**PostgreSQL Level Encryption**: Configure encryption at the database level:

1. **Transparent Data Encryption (TDE)**: Encrypt entire database
2. **Column-level Encryption**: Encrypt sensitive columns using `pgcrypto`
3. **SSL/TLS**: Encrypt data in transit

Example column-level encryption:
```sql
-- Enable pgcrypto extension
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Encrypt metadata column
UPDATE edc_audit_entry 
SET metadata = pgp_sym_encrypt(metadata::text, 'encryption_key')::jsonb;
```

### Immutability

**PostgreSQL Immutability Options**:

1. **Row-level Security (RLS)**: Prevent updates/deletes
```sql
ALTER TABLE edc_audit_entry ENABLE ROW LEVEL SECURITY;
CREATE POLICY audit_immutable ON edc_audit_entry FOR UPDATE USING (false);
CREATE POLICY audit_no_delete ON edc_audit_entry FOR DELETE USING (false);
```

2. **Triggers**: Prevent modifications
```sql
CREATE OR REPLACE FUNCTION prevent_audit_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Audit entries are immutable';
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_immutable_trigger
BEFORE UPDATE OR DELETE ON edc_audit_entry
FOR EACH ROW EXECUTE FUNCTION prevent_audit_modification();
```

### Retention Policy

Configure retention using PostgreSQL partitioning or scheduled jobs:

```sql
-- Example: Delete entries older than 7 years (compliance requirement)
DELETE FROM edc_audit_entry 
WHERE timestamp < EXTRACT(EPOCH FROM NOW() - INTERVAL '7 years') * 1000;
```

For automated retention, use `pg_cron` extension or external schedulers.

## Captured Events

### Contract Negotiation Events

- `ContractNegotiationInitiated`: Negotiation started
- `ContractNegotiationRequested`: Request received
- `ContractNegotiationVerified`: Verification complete
- `ContractNegotiationAccepted`: Offer accepted
- `ContractNegotiationAgreed`: Agreement reached
- `ContractNegotiationFinalized`: Contract finalized
- `ContractNegotiationFailed`: Negotiation failed
- `ContractNegotiationTerminated`: Negotiation terminated

### Transfer Process Events

- `TransferProcessInitiated`: Transfer initiated
- `TransferProcessRequested`: Transfer requested
- `TransferProcessProvisioned`: Resources provisioned
- `TransferProcessStarted`: Transfer started
- `TransferProcessCompleted`: Transfer completed
- `TransferProcessFailed`: Transfer failed
- `TransferProcessDeprovisioned`: Resources cleaned up
- `TransferProcessTerminated`: Transfer terminated

## Usage

### Programmatic Access

To record custom audit entries:

```java
@Inject
private AuditRegistry auditRegistry;

public void recordCustomEvent() {
    var entry = AuditEntry.builder()
        .id(UUID.randomUUID().toString())
        .timestamp(System.currentTimeMillis())
        .eventType(AuditEventType.VALIDATION)
        .eventName("CustomPolicyValidation")
        .description("Policy validation performed")
        .result("SUCCESS")
        .metadata(Map.of("policyId", "policy-123", "validatorId", "validator-1"))
        .critical(true)
        .build();
    
    auditRegistry.record(entry);
}
```

### Querying Audit Logs

```java
@Inject
private AuditStore auditStore;

public List<AuditEntry> queryCriticalEvents() {
    var query = AuditQuery.builder()
        .eventType(AuditEventType.CONTRACT_NEGOTIATION)
        .critical(true)
        .fromTimestamp(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7))
        .limit(100)
        .build();
    
    return auditStore.query(query);
}
```

## Extension Dependencies

Add these modules to your EDC runtime:

```gradle
dependencies {
    implementation(project(":edc-extensions:audit-registry:audit-store-sql"))
    implementation(project(":edc-extensions:audit-registry:audit-registry-core"))
    implementation(project(":edc-extensions:audit-registry:audit-event-subscriber"))
}
```

## Monitoring and Maintenance

### Health Checks

Monitor the audit system:
- Check PostgreSQL connection and query performance
- Monitor Redis cache hit ratio
- Track async processing queue depth
- Alert on failed audit recordings

### Performance Considerations

- **Async Processing**: Audit recording is non-blocking
- **Batch Processing**: Consider batching for high-volume scenarios
- **Cache Tuning**: Adjust Redis TTL based on query patterns
- **Index Optimization**: Add custom indexes for specific query patterns

### Troubleshooting

**Issue**: Audit entries not appearing in database
- Check `tx.edc.audit.enabled=true`
- Verify database connection and credentials
- Check application logs for errors

**Issue**: Redis connection failures
- Verify Redis host and port configuration
- Check Redis authentication settings
- System works without Redis (just slower queries)

**Issue**: Performance degradation
- Increase thread pool size: `tx.edc.audit.thread.pool.size`
- Optimize database indexes
- Consider archiving old audit entries

## Future Enhancements

- Elasticsearch integration for detailed logs
- REST API for audit log queries
- Dashboard for audit visualization
- Advanced filtering and search capabilities
- Integration with SIEM systems
- Audit log export functionality

## Compliance

This audit system is designed to help meet compliance requirements including:
- **GDPR**: Data processing traceability
- **SOC 2**: Activity logging and monitoring
- **ISO 27001**: Information security event logging
- **Industry-specific**: Sector-specific audit requirements

## License

Apache License 2.0 - See LICENSE file for details.
