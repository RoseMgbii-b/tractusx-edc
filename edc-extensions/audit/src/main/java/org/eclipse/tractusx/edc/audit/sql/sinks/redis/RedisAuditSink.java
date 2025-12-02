package org.eclipse.tractusx.edc.audit.sql.sinks.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.tractusx.edc.audit.sql.sinks.redis.transform.RedisAuditEventTransformer;
import org.eclipse.tractusx.edc.spi.audit.sink.AuditSink;
import org.eclipse.tractusx.edc.spi.audit.types.AuditEvent;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisException;

public class RedisAuditSink implements AuditSink {

    private final Monitor monitor;
    private final int maxEntries;
    private final ObjectMapper objectMapper;
    private final String redisHost;
    private final int redisPort;
    private final JedisPool jedisPool;
    // get only the required fields from audit events
    private final RedisAuditEventTransformer redisTransformer = new RedisAuditEventTransformer();

    public RedisAuditSink(String redisHost, int redisPort, Monitor monitor, int maxEntries, TypeManager typeManager) {
        this.monitor = monitor;
        this.maxEntries = maxEntries;
        this.objectMapper = typeManager.getMapper();
        this.redisHost = redisHost;
        this.redisPort = redisPort;
        this.jedisPool = new JedisPool(redisHost, redisPort);

    }

    // For testing
    public RedisAuditSink(JedisPool jedisPool, Monitor monitor, int maxEntries, TypeManager typeManager) {
        this.monitor = monitor;
        this.maxEntries = maxEntries;
        this.objectMapper = typeManager.getMapper();
        this.jedisPool = jedisPool;
        this.redisHost = null;
        this.redisPort = 0;
    }

    @Override
    public void write(AuditEvent event) {
        var name = event.getEventName() != null ? event.getEventName().name(): "UNKNOWN_EVENT_NAME";
        var category = event.getCategory() !=null ? event.getCategory().name() : "UNKNOWN_CATEGORY";

        var key = "recent:audit:" + category + ":" + name;

        try (var jedis = jedisPool.getResource()) {
            // transform audit event to contain only accepted fields for redis
            var transformedEvent = redisTransformer.transform(event);
            var json = toJson(transformedEvent);

            jedis.lpush(key, json);
            jedis.ltrim(key, 0, maxEntries - 1);

        } catch (JedisException j) {
            monitor.warning("[RedisAuditSink]: Failed to write event: " + event.getId(), j);
        } catch (Exception e) {
            monitor.warning("[RedisAuditSink]: Unexpected error while writing event: " + event.getId(), e);
        }
    }

    /**
     * Serialize AuditEvent to JSON
     * Converts all objects (including maps) to json
     *
     * @param event     Event to serialize
     * @return          JSON string representation of the AuditEvent
     */
    private String toJson(Object event) {
        try{
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            monitor.warning("[RedisAuditSink]: Failed to serialize AuditEvent to JSON: " + e.getMessage());
        return "{}";
        }
    }

}


