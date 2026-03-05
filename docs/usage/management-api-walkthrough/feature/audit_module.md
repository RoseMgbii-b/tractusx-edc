# Audit Module

## Overview

The Audit Module provides comprehensive auditing capabilities for Tractus-X EDC connectors, enabling systematic tracking and logging of all significant events within the connector ecosystem. This module captures detailed audit trails for contract negotiations, asset management, transfer processes, policy operations, and security events, ensuring compliance, transparency, and traceability across the dataspace.

The module follows an event-driven architecture where it subscribes to EDC events, transforms them into standardized audit events, and persists them to multiple configurable sinks including PostgreSQL, Elasticsearch, and Redis. This multi-sink approach provides flexibility for different deployment scenarios and audit requirements.

## Architecture

The audit module consists of several interconnected components that work together to provide comprehensive audit functionality:

### Core Components

#### 1. AuditExtension
The main EDC service extension that orchestrates the entire audit system. It:
- Initializes and configures all audit sinks based on configuration
- Registers the AuditService as an EDC service
- Sets up the event subscriber to listen for EDC events
- Manages the lifecycle of all audit components
- Handles configuration validation and error reporting

#### 2. AuditService
The central service interface for audit operations:
- **Interface**: `org.eclipse.tractusx.edc.spi.audit.service.AuditService`
- **Implementation**: `org.eclipse.tractusx.edc.audit.service.AuditServiceImpl`
- **Responsibility**: Publishes audit events to all configured sinks
- **Error Handling**: Continues processing even if individual sinks fail

#### 3. AuditEventSubscriber
The event-driven component that bridges EDC events to audit events:
- Implements EDC's `EventSubscriber` interface
- Receives all EDC events through the `EventRouter`
- Uses the `AuditEventMapperRegistry` to transform events
- Handles mapping errors gracefully without disrupting event flow

#### 4. AuditEventMapperRegistry
The intelligent mapping engine that converts EDC events to audit events:
- Maintains a registry of event-to-audit mappings
- Uses reflection-based property extraction for flexible event handling
- Supports hierarchical event type resolution
- Provides extensible mapping registration for custom events

#### 5. Audit Sinks
Pluggable persistence mechanisms for audit events:

##### PostgreSQL Sink
- **Class**: `org.eclipse.tractusx.edc.audit.sql.sinks.postgres.PostgresqlAuditSink`
- **Purpose**: Persistent storage in PostgreSQL database
- **Features**: Transactional integrity, SQL-based queries, enterprise-grade reliability
- **Schema**: Uses `audit_events` table created via Flyway migration

##### Elasticsearch Sink
- **Class**: `org.eclipse.tractusx.edc.audit.sql.sinks.elasticsearch.ElasticsearchAuditSink`
- **Purpose**: Full-text search and analytics capabilities
- **Features**: Real-time indexing, complex queries, aggregation support
- **Use Case**: Compliance reporting, audit trail analysis, monitoring dashboards

##### Redis Sink
- **Class**: `org.eclipse.tractusx.edc.audit.sql.sinks.redis.RedisAuditSink`
- **Purpose**: High-performance caching and recent event storage
- **Features**: In-memory storage, configurable TTL, LRU eviction
- **Use Case**: Real-time monitoring, recent activity feeds, performance-critical scenarios

## Data Model

### AuditEvent
The canonical data model representing an audit event:

```java
public class AuditEvent {
    private String id;                           // UUID - Unique identifier
    private Instant timestamp;                   // When the event occurred
    private AuditEventCategory category;         // High-level event classification
    private AuditEventName eventName;            // Specific event type
    private String description;                  // Human-readable description
    private AuditOutcome outcome;                // SUCCESS, FAILURE, WARNING
    private String actorId;                      // Who performed the action
    private String subjectId;                    // What was acted upon
}
```

### Event Categories

#### AuditEventCategory
High-level classifications for organizing audit events:
- `CONTRACT_REQUEST`: Contract request operations
- `CONTRACT_NEGOTIATION`: Contract negotiation lifecycle
- `CONTRACT_VALIDATION`: Contract validation processes
- `CONTRACT_DEFINITION`: Contract definition management
- `TRANSFER_PROCESS`: Data transfer operations
- `ASSET`: Asset lifecycle management
- `POLICY`: Policy definition and management

#### AuditEventName
Specific event types within each category:
- **Contract Definition**: `CONTRACT_DEFINITION_CREATED`, `CONTRACT_DEFINITION_UPDATED`
- **Contract Negotiation**: `CONTRACT_NEGOTIATION_INITIATED`, `CONTRACT_NEGOTIATION_REQUESTED`, `CONTRACT_NEGOTIATION_ACCEPTED`, `CONTRACT_NEGOTIATION_AGREED`, `CONTRACT_NEGOTIATION_TERMINATED`
- **Transfer Process**: `TRANSFER_PROCESS_STARTED`, `TRANSFER_PROCESS_REQUESTED`, `TRANSFER_PROCESS_TERMINATED`, `TRANSFER_PROCESS_SUSPENDED`
- **Asset**: `ASSET_CREATED`, `ASSET_UPDATED`, `ASSET_DELETED`
- **Policy**: `POLICY_DEFINITION_CREATED`, `POLICY_DEFINITION_UPDATED`, `POLICY_DEFINITION_DELETED`
- **Security**: `AUTHENTICATION_SUCCESS`, `AUTHENTICATION_FAILURE`, `SYSTEM_ERROR`, `SYSTEM_STARTUP`, `SYSTEM_SHUTDOWN`

#### AuditOutcome
Possible outcomes for audit events:
- `SUCCESS`: Operation completed successfully
- `FAILURE`: Operation failed with error
- `WARNING`: Operation completed with warnings

## Configuration

The audit module is configured through EDC configuration properties. All sinks are optional - the module will initialize only the sinks that are properly configured.

### PostgreSQL Sink Configuration

| Property | Description | Example | Default |
|----------|-------------|---------|---------|
| `edc.sql.store.audit.datasource` | DataSource name for audit storage | `audit` | `default` |

**Example Configuration**:
```properties
edc.sql.store.audit.datasource=audit
```

### Redis Sink Configuration

| Property | Description | Example | Default |
|----------|-------------|---------|---------|
| `edc.audit.redis.enabled` | Enable Redis sink | `true` | `false` |
| `edc.audit.redis.host` | Redis server host | `redis.example.com` | `localhost` |
| `edc.audit.redis.port` | Redis server port | `6379` | `6379` |
| `edc.audit.redis.maxEntries` | Maximum entries in cache | `5000` | `1000` |

**Example Configuration**:
```properties
edc.audit.redis.enabled=true
edc.audit.redis.host=redis.example.com
edc.audit.redis.port=6379
edc.audit.redis.maxEntries=5000
```

### Elasticsearch Sink Configuration

| Property | Description | Example | Default |
|----------|-------------|---------|---------|
| `edc.audit.elasticsearch.index.url` | Elasticsearch index URL | `http://elasticsearch:9200/audit-events/_doc` | null (disabled) |

**Example Configuration**:
```properties
edc.audit.elasticsearch.index.url=http://elasticsearch:9200/audit-events/_doc
```

### Complete Configuration Example

```properties
# PostgreSQL Sink
edc.sql.store.audit.datasource=audit

# Redis Sink
edc.audit.redis.enabled=true
edc.audit.redis.host=redis.example.com
edc.audit.redis.port=6379
edc.audit.redis.maxEntries=2000

# Elasticsearch Sink
edc.audit.elasticsearch.index.url=http://elasticsearch:9200/audit-events/_doc
```

## Event Flow

### 1. Event Generation
EDC components generate events for significant operations:
- Contract negotiations create negotiation events
- Asset operations generate asset lifecycle events
- Transfer processes emit transfer state change events
- Policy operations trigger policy management events

### 2. Event Subscription
The `AuditEventSubscriber` receives all EDC events through the `EventRouter`:
```java
eventRouter.registerSync(Event.class, new AuditEventSubscriber(auditService, monitor, mapperRegistry));
```

### 3. Event Mapping
The `AuditEventMapperRegistry` transforms EDC events to audit events:
- Extracts relevant properties using reflection
- Maps event types to audit categories and names
- Determines appropriate outcomes based on event context
- Builds standardized `AuditEvent` objects

### 4. Event Publication
The `AuditService` publishes audit events to all configured sinks:
- Iterates through all active sinks
- Handles sink failures gracefully
- Continues processing even if some sinks are unavailable

### 5. Event Persistence
Each sink handles event persistence according to its implementation:
- **PostgreSQL**: Inserts into `audit_events` table with transactional integrity
- **Elasticsearch**: Indexes documents for search and analytics
- **Redis**: Stores in memory with configurable eviction policies

## Database Schema

### PostgreSQL Schema

The PostgreSQL sink uses the following table structure (created via Flyway migration):

```sql
CREATE TABLE audit_events (
    id VARCHAR(36) PRIMARY KEY,
    timestamp TIMESTAMPTZ NOT NULL,
    category VARCHAR(50) NOT NULL,
    event_name VARCHAR(100) NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    subject_id VARCHAR(255),
    actor_id VARCHAR(255),
    description TEXT
);
```

**Index Recommendations**:
```sql
-- Performance indexes for common queries
CREATE INDEX idx_audit_events_timestamp ON audit_events(timestamp);
CREATE INDEX idx_audit_events_category ON audit_events(category);
CREATE INDEX idx_audit_events_subject_id ON audit_events(subject_id);
CREATE INDEX idx_audit_events_actor_id ON audit_events(actor_id);
CREATE INDEX idx_audit_events_outcome ON audit_events(outcome);

-- Composite index for time-based filtering
CREATE INDEX idx_audit_events_timestamp_category ON audit_events(timestamp, category);
```

## Usage Scenarios

### 1. Compliance Auditing
Track all data access and transfer operations for regulatory compliance:

```sql
-- Query all data transfers in the last 30 days
SELECT * FROM audit_events 
WHERE category = 'TRANSFER_PROCESS' 
  AND timestamp >= NOW() - INTERVAL '30 days'
ORDER BY timestamp DESC;
```

### 2. Security Monitoring
Monitor authentication failures and suspicious activities:

```sql
-- Find failed authentication attempts
SELECT * FROM audit_events 
WHERE event_name = 'AUTHENTICATION_FAILURE' 
  AND timestamp >= NOW() - INTERVAL '24 hours'
ORDER BY timestamp DESC;
```

### 3. Performance Analysis
Analyze transfer process patterns and bottlenecks:

```sql
-- Transfer process statistics by outcome
SELECT outcome, COUNT(*) as count
FROM audit_events 
WHERE category = 'TRANSFER_PROCESS'
  AND timestamp >= NOW() - INTERVAL '7 days'
GROUP BY outcome;
```

### 4. Contract Negotiation Tracking
Monitor contract negotiation lifecycle:

```sql
-- Contract negotiation flow analysis
SELECT event_name, COUNT(*) as count
FROM audit_events 
WHERE category = 'CONTRACT_NEGOTIATION'
  AND subject_id = 'contract-123'
GROUP BY event_name
ORDER BY timestamp;
```

## Implementation Details

### Thread Safety
The audit module is designed for thread-safe operation:
- `AuditServiceImpl` uses thread-safe iteration over sinks
- Each sink implementation handles its own thread safety
- Event mapping is stateless and thread-safe
- Error handling prevents cascading failures

### Error Handling
Comprehensive error handling ensures system reliability:
- Sink failures don't prevent other sinks from processing
- Mapping errors are logged but don't stop event processing
- Configuration errors are detected during initialization
- Database connection issues are handled gracefully

### Performance Considerations
- **Asynchronous Processing**: Event publishing is non-blocking
- **Batch Operations**: Sinks can implement batch writing for efficiency
- **Memory Management**: Redis sink uses LRU eviction to prevent memory leaks
- **Connection Pooling**: Database connections are managed efficiently
- **Minimal Overhead**: Event processing adds minimal latency to operations

### Monitoring and Logging
The module provides detailed operational visibility:
- Extension initialization and sink configuration status
- Event processing statistics and error rates
- Sink-specific performance metrics
- Configuration validation warnings

## Integration with EDC

### Service Registration
The module registers the following services:
- `AuditService`: Central audit event publishing service
- Event subscribers for automatic event capture

### Extension Dependencies
The audit module depends on:
- `EventRouter`: For receiving EDC events
- `DataSourceRegistry`: For database access (PostgreSQL sink)
- `TransactionContext`: For transactional database operations
- `EdcHttpClient`: For Elasticsearch communication
- `TypeManager`: For JSON serialization
- `Monitor`: For logging and monitoring

### Lifecycle Management
- **Initialize**: Configuration validation, sink setup, event subscriber registration
- **Shutdown**: Graceful cleanup of resources and connections

## Testing

### Test Coverage
The audit module includes comprehensive test coverage:
- Unit tests for all core components
- Integration tests with different sink configurations
- Event mapping validation tests
- Error handling and resilience tests
- Performance and load tests

### Test Utilities
- Mock event generators for testing different event types
- Test sink implementations for validation
- Database test fixtures for PostgreSQL testing
- Redis and Elasticsearch test containers

## Best Practices

### Production Deployment
1. **Configure Multiple Sinks**: Use PostgreSQL for persistence and Redis for performance
2. **Monitor Sink Health**: Implement health checks for all sink connections
3. **Set Appropriate Retention**: Configure data retention policies based on compliance requirements
4. **Index Optimization**: Create appropriate database indexes for query performance
5. **Error Monitoring**: Set up alerts for audit processing failures

### Security Considerations
1. **Secure Audit Data**: Protect audit logs with appropriate access controls
2. **Tamper Protection**: Use write-once storage or blockchain for critical audit trails
3. **Data Privacy**: Ensure audit logs don't contain sensitive personal data
4. **Access Logging**: Log all access to audit data
5. **Backup Procedures**: Implement regular backup of audit data

### Performance Optimization
1. **Batch Processing**: Use batch inserts for high-volume scenarios
2. **Async Processing**: Implement asynchronous event processing for better performance
3. **Connection Pooling**: Optimize database connection pool settings
4. **Index Strategy**: Create indexes based on common query patterns
5. **Data Partitioning**: Consider partitioning large audit tables by time

## Troubleshooting

### Common Issues

#### Extension Not Starting
**Symptoms**: No audit events being generated
**Solutions**:
- Check extension is included in classpath
- Verify configuration properties are correct
- Review initialization logs for errors

#### Sink Connection Failures
**Symptoms**: Logs showing sink connection errors
**Solutions**:
- Verify network connectivity to sink services
- Check authentication credentials
- Validate sink service configuration
- Monitor resource utilization

#### Event Mapping Failures
**Symptoms**: Events not being mapped to audit events
**Solutions**:
- Check event type compatibility
- Verify reflection-based property access
- Review mapper registry configuration
- Enable debug logging for detailed tracing

#### Performance Issues
**Symptoms**: Slow event processing or high latency
**Solutions**:
- Optimize database indexes
- Increase connection pool sizes
- Consider batch processing
- Monitor resource utilization

### Debug Logging
Enable debug logging for detailed troubleshooting:
```properties
edc.logger.level=DEBUG
```

This provides detailed information about:
- Event processing flow
- Sink operation status
- Mapping transformation details
- Error stack traces

## Migration Guide

### From Manual Auditing
To migrate from manual to automated auditing:
1. **Identify Audit Points**: Map manual audit processes to EDC events
2. **Configure Sinks**: Set up appropriate persistence mechanisms
3. **Validate Event Coverage**: Ensure all required events are captured
4. **Test Data Quality**: Verify audit data completeness and accuracy
5. **Update Procedures**: Modify operational procedures to use automated audit

### Sink Migration
To migrate between audit sinks:
1. **Export Existing Data**: Extract current audit data
2. **Configure New Sink**: Set up new sink configuration
3. **Validate Data Import**: Ensure data integrity in new sink
4. **Update Applications**: Modify any applications using old sink
5. **Decommission Old Sink**: Remove old sink after validation

## Future Enhancements

Potential areas for future development:
- **Additional Sink Types**: Support for more storage systems (MongoDB, Cassandra)
- **Event Filtering**: Configurable event filtering to reduce noise
- **Audit Analytics**: Built-in analytics and reporting capabilities
- **Event Enrichment**: Automatic enrichment of audit events with additional context
- **Compression**: Data compression for long-term storage
- **Streaming**: Real-time audit event streaming to external systems
- **API Endpoints**: REST API for querying audit data
- **Retention Policies**: Automated data retention and archiving

## License

This module is licensed under the Apache-2.0 license, consistent with the Tractus-X EDC project.

## Contributing

When contributing to the Audit Module:
- Follow the existing code style and patterns
- Add comprehensive tests for new functionality
- Update documentation for configuration changes
- Consider backward compatibility for event mappings
- Test with all supported sink types
- Ensure thread safety for concurrent operations
- Validate performance impact of changes
