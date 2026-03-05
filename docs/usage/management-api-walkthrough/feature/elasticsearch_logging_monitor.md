# Elasticsearch Monitor Extension

## Overview

The Elasticsearch Monitor Extension provides comprehensive logging capabilities for Tractus-X EDC connectors by forwarding all monitor logs to Elasticsearch while preserving the existing console output. This extension enables centralized log aggregation, advanced search capabilities, and real-time monitoring of connector operations in distributed environments.

The extension operates as a drop-in replacement for the default EDC monitor, implementing the same `Monitor` interface while adding Elasticsearch persistence. It maintains backward compatibility by ensuring all existing console logging continues to work unchanged, while simultaneously sending structured log entries to Elasticsearch for centralized analysis and monitoring.

## Architecture

The extension consists of three main components that work together to provide Elasticsearch-based logging:

### Core Components

#### 1. ElasticsearchMonitorExtension
The main EDC service extension that orchestrates the entire Elasticsearch logging system. It:
- Initializes and configures the Elasticsearch logging based on configuration settings
- Registers the custom `ElasticsearchMonitor` as the primary EDC monitor service
- Handles configuration validation and graceful error handling
- Manages the extension lifecycle and dependency injection
- Provides fallback behavior when Elasticsearch is unavailable or misconfigured

**Configuration Settings:**
- `edc.monitor.elasticsearch.enabled` (boolean, default: false) - Enables/disables the extension
- `edc.monitor.elasticsearch.index.url` (string, required) - Elasticsearch index URL for log ingestion
- `edc.monitor.elasticsearch.maxStacktraceLength` (integer, default: 32000) - Maximum stacktrace character limit

#### 2. ElasticsearchMonitor
A custom monitor implementation that extends EDC's logging capabilities:
- **Interface Implementation**: Fully implements EDC's `Monitor` interface
- **Dual Output Strategy**: Simultaneously writes to both fallback monitor and Elasticsearch
- **Log Level Support**: Handles all standard log levels (INFO, WARNING, ERROR, DEBUG)
- **Error Resilience**: Continues console logging even if Elasticsearch writes fail
- **Message Sanitization**: Ensures log message integrity and handles null/empty values
- **Supplier Support**: Efficient lazy evaluation of log messages using Java suppliers

**Log Processing Flow:**
1. Receives log call from EDC components
2. Delegates to fallback monitor for console output
3. Sanitizes and validates log message content
4. Forwards to ElasticsearchLogSink for persistence
5. Handles any Elasticsearch failures gracefully

#### 3. ElasticsearchLogSink
The asynchronous Elasticsearch integration component:
- **Asynchronous Processing**: Uses `CompletableFuture` for non-blocking log writes
- **Structured Logging**: Creates JSON documents with comprehensive metadata
- **HTTP Client Integration**: Leverages EDC's `EdcHttpClient` for HTTP communications
- **Error Isolation**: Prevents Elasticsearch failures from affecting connector operations
- **Configurable Stacktrace Handling**: Truncates excessive stacktraces to prevent storage bloat

**Log Document Structure:**
```json
{
  "timestamp": "2024-01-01T12:00:00.000Z",
  "level": "INFO",
  "message": "Connector operation completed successfully",
  "thread": "main",
  "instanceId": "connector-instance-1",
  "service": "edc-connector",
  "exception": "java.lang.RuntimeException",
  "stacktrace": "java.lang.RuntimeException: Error occurred\n\tat com.example..."
}
```

## Key Features

### 1. Zero-Downtime Logging
- **Non-blocking Architecture**: All Elasticsearch operations are asynchronous
- **Fallback Guarantee**: Console logging always works regardless of Elasticsearch status
- **Graceful Degradation**: Connector continues operating even if Elasticsearch is unavailable

### 2. Comprehensive Log Enrichment
- **Automatic Timestamping**: ISO 8601 timestamps for precise event ordering
- **Thread Context**: Includes thread names for concurrent operation tracking
- **Instance Identification**: Uses HOSTNAME environment variable for multi-instance deployments
- **Service Classification**: Tags all logs with "edc-connector" service identifier

### 3. Intelligent Exception Handling
- **Structured Exception Data**: Captures exception class names and formatted stacktraces
- **Configurable Truncation**: Prevents excessive stacktrace storage with configurable limits
- **Error Context**: Preserves original error information while managing storage efficiency

### 4. Configuration Flexibility
- **Feature Toggle**: Enable/disable without code changes
- **Dynamic URL Configuration**: Support for any Elasticsearch endpoint
- **Performance Tuning**: Adjustable stacktrace limits for storage optimization

## Configuration

### Required Configuration

```properties
# Enable Elasticsearch monitoring
edc.monitor.elasticsearch.enabled=true

# Elasticsearch index URL (required)
edc.monitor.elasticsearch.index.url=http://localhost:9200/edc-logs/_doc
```

### Optional Configuration

```properties
# Maximum stacktrace length in characters (default: 32000)
edc.monitor.elasticsearch.maxStacktraceLength=64000
```

### Environment Variables

The extension automatically uses the following environment variable:
- `HOSTNAME`: Used to identify the connector instance in log entries (defaults to "unknown" if not set)

## Integration Patterns

### 1. Standalone Elasticsearch Deployment
For simple deployments with a dedicated Elasticsearch cluster:
```properties
edc.monitor.elasticsearch.enabled=true
edc.monitor.elasticsearch.index.url=http://elasticsearch:9200/connector-logs/_doc
```

### 2. Elasticsearch with Authentication
For secured Elasticsearch deployments:
```properties
edc.monitor.elasticsearch.enabled=true
edc.monitor.elasticsearch.index.url=https://user:password@elasticsearch:9200/connector-logs/_doc
```

### 3. Multi-Instance Logging
When running multiple connector instances:
```properties
# Set unique hostname for each instance
HOSTNAME=connector-instance-1

edc.monitor.elasticsearch.enabled=true
edc.monitor.elasticsearch.index.url=http://elasticsearch:9200/connector-logs/_doc
```

## Performance Considerations

### 1. Asynchronous Processing
- All Elasticsearch writes are non-blocking
- Uses `CompletableFuture` for fire-and-forget semantics
- No impact on connector response times

### 2. Memory Management
- Stacktrace truncation prevents memory exhaustion
- Failed async operations are discarded to prevent memory leaks
- Lazy evaluation of log messages reduces object allocation

### 3. Network Resilience
- HTTP timeouts are handled by the underlying `EdcHttpClient`
- Connection failures don't affect connector operations
- Retry logic should be implemented at the infrastructure level

## Error Handling and Resilience

### 1. Initialization Failures
- Missing URL configuration: Extension logs warning and remains disabled
- Network connectivity issues: Extension logs error but doesn't fail startup
- Dependency injection failures: Handled gracefully by EDC framework

### 2. Runtime Failures
- Elasticsearch unavailability: Logs are discarded, console logging continues
- JSON serialization errors: Logged to fallback monitor, connector operation
- HTTP request failures: Silently ignored to prevent log spam

### 3. Configuration Errors
- Invalid URLs: Detected during initialization, extension remains disabled
- Malformed configuration: Uses defaults where possible, logs warnings for issues

## Monitoring and Operations

### 1. Log Querying Examples

**Find all errors for a specific instance:**
```json
{
  "query": {
    "bool": {
      "must": [
        {"term": {"level": "ERROR"}},
        {"term": {"instanceId": "connector-instance-1"}}
      ]
    }
  }
}
```

**Monitor connector startup:**
```json
{
  "query": {
    "bool": {
      "must": [
        {"term": {"service": "edc-connector"}},
        {"range": {"timestamp": {"gte": "now-1h"}}}
      ]
    }
  },
  "sort": [{"timestamp": {"order": "asc"}}]
}
```

### 2. Operational Metrics
- Monitor Elasticsearch index size and growth rates
- Track async failure rates through connector logs
- Monitor HTTP response times and error rates
- Set up alerts for high error rates or log volume spikes

## Testing and Validation

### 1. Unit Test Coverage
The extension includes comprehensive unit tests:
- Configuration validation scenarios
- Monitor behavior verification
- Error handling validation
- Async operation testing

### 2. Integration Testing
- Elasticsearch connectivity validation
- End-to-end log flow testing
- Performance impact assessment
- Failover scenario testing

### 3. Test Utilities
- Mock Elasticsearch server for isolated testing
- Configuration fixture for various scenarios
- Async operation verification utilities

## Best Practices

### 1. Deployment
- Enable the extension in production environments for centralized monitoring
- Configure appropriate Elasticsearch index lifecycle management
- Set up monitoring for Elasticsearch cluster health
- Use environment-specific configurations for different deployment stages

### 2. Performance Optimization
- Adjust stacktrace limits based on storage constraints
- Monitor Elasticsearch indexing performance
- Consider index sharding strategies for high-volume deployments
- Implement appropriate retention policies for log data

### 3. Security Considerations
- Use HTTPS for Elasticsearch communication in production
- Implement proper authentication and authorization
- Consider log data encryption for sensitive information
- Regularly rotate Elasticsearch credentials

### 4. Troubleshooting
- Monitor extension initialization logs for configuration issues
- Check Elasticsearch cluster health for connectivity problems
- Verify network connectivity between connector and Elasticsearch
- Use fallback monitor logs to diagnose extension issues

## Dependencies and Requirements

### Runtime Dependencies
- EDC Core Framework (spi.boot, spi.core, spi.controlplane)
- EDC HTTP Client (edc.spi.http)
- Audit SPI (spi.audit-spi)
- OkHttp Client (for HTTP communications)
- Jackson JSON Processor (via TypeManager)

### Test Dependencies
- JUnit 5 (testing framework)
- Mockito (mocking framework)
- Testcontainers (integration testing)
- Netty Mock Server (HTTP mocking)

### Infrastructure Requirements
- Elasticsearch cluster (version compatible with connector)
- Network connectivity between connector and Elasticsearch
- Appropriate Elasticsearch index mappings and templates
- Sufficient storage capacity for log retention requirements

## Migration and Compatibility

### Version Compatibility
- Compatible with EDC 0.14.1 and later versions
- Maintains backward compatibility with existing monitor configurations
- No breaking changes to existing logging APIs

### Migration from Default Monitor
- No code changes required in existing connector implementations
- Configuration-only migration process
- Gradual rollout possible through feature toggle
- Fallback to default monitor if configuration is incomplete

## Future Enhancements

### Potential Improvements
- Structured logging with custom fields support
- Log sampling for high-volume scenarios
- Integration with log aggregation platforms (ELK stack, Splunk)
- Custom Elasticsearch document templates
- Bulk indexing support for improved performance
- Log filtering and routing capabilities

### Extension Points
- Custom log enrichment strategies
- Pluggable Elasticsearch client configurations
- Custom error handling and retry policies
- Integration with monitoring and alerting systems
