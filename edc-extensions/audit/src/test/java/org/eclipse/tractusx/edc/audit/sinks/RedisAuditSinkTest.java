package org.eclipse.tractusx.edc.audit.sinks;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.types.TypeManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.tractusx.edc.spi.audit.types.AuditEvent;
import org.eclipse.tractusx.edc.spi.audit.types.AuditEventCategory;
import org.eclipse.tractusx.edc.spi.audit.types.AuditEventName;
import org.eclipse.tractusx.edc.spi.audit.types.AuditOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.eclipse.tractusx.edc.audit.sql.sinks.redis.RedisAuditSink;
import org.mockito.ArgumentCaptor;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisException;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class RedisAuditSinkTest {

    private Monitor monitor;
    private TypeManager typeManager;
    private JedisPool jedisPool;
    private Jedis jedis;
    private RedisAuditSink sink;

    @BeforeEach
    void setup() {
        monitor = mock(Monitor.class);

        typeManager = mock(TypeManager.class);
        when(typeManager.getMapper()).thenReturn(new ObjectMapper());

        jedisPool = mock(JedisPool.class);
        jedis = mock(Jedis.class);

        when(jedisPool.getResource()).thenReturn(jedis);

        sink = new RedisAuditSink(jedisPool, monitor, 5, typeManager);
    }

    @Test
    void write_shouldPushTransformedEventToRedis() throws JsonProcessingException {
        // Arrange
        var event = AuditEvent.Builder.newInstance()
                .id("id-123")
                .timestamp(Instant.parse("2025-12-02T08:00:00Z"))
                .category(AuditEventCategory.CONTRACT_NEGOTIATION)
                .eventName(AuditEventName.CONTRACT_NEGOTIATION_INITIATED)
                .outcome(AuditOutcome.SUCCESS)
                .actorId("actor-1")
                .subjectId("subject-1")
                .build();

        // Act
        sink.write(event);

        // Assert - verify correct Redis key
        var expectedKey = "recent:audit:CONTRACT_NEGOTIATION:CONTRACT_NEGOTIATION_INITIATED";

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);

        verify(jedis).lpush(eq(expectedKey), jsonCaptor.capture());
        verify(jedis).ltrim(expectedKey, 0, 4);
        verify(jedis).close();

        // JSON verification
        String json = jsonCaptor.getValue();
        Map<?, ?> parsed = typeManager.getMapper().readValue(json, Map.class);

        assertThat(parsed.get("id")).isEqualTo("id-123");
        assertThat(parsed.get("category")).isEqualTo("CONTRACT_NEGOTIATION");
        assertThat(parsed.get("eventName")).isEqualTo("CONTRACT_NEGOTIATION_INITIATED");
        assertThat(parsed.get("outcome")).isEqualTo("SUCCESS");
    }

    @Test
    void write_shouldHandleJedisExceptionGracefully() {
        // Arrange
        when(jedisPool.getResource()).thenThrow(new JedisException("redis down"));

        var event = AuditEvent.Builder.newInstance()
                .id("id-err")
                .timestamp(Instant.now())
                .category(AuditEventCategory.POLICY)
                .eventName(AuditEventName.POLICY_DEFINITION_DELETED)
                .outcome(AuditOutcome.WARNING)
                .build();

        // Act
        sink.write(event);

        // Assert — no exception should escape
        verify(monitor).warning(startsWith("[RedisAuditSink]: Failed to write event"), any());
    }

    @Test
    void write_shouldHandleSerializationFailureGracefully() throws Exception {
        // Arrange
        var badMapper = mock(ObjectMapper.class);
        when(badMapper.writeValueAsString(any())).thenThrow(new RuntimeException("bad json"));
        when(typeManager.getMapper()).thenReturn(badMapper);

        var sink = new RedisAuditSink(jedisPool, monitor, 5, typeManager);

        var event = AuditEvent.Builder.newInstance()
                .id("123")
                .timestamp(Instant.now())
                .category(AuditEventCategory.ASSET)
                .eventName(AuditEventName.ASSET_CREATED)
                .outcome(AuditOutcome.SUCCESS)
                .build();

        // Act
        sink.write(event);

        // Assert
        verify(monitor).warning(startsWith("[RedisAuditSink]: Failed to serialize AuditEvent to JSON: bad json"));
    }

    @Test
    void write_logs_warning_on_jedis_failure() {
        // given exception, ensure log is written
        when(jedisPool.getResource()).thenThrow(new JedisException("fail"));

        var sink = new RedisAuditSink(jedisPool, monitor, 100, typeManager);

        var event = mock(AuditEvent.class);
        when(event.getId()).thenReturn("evt-redis");

        // act
        sink.write(event);

        verify(monitor).warning(contains
                ("[RedisAuditSink]: Failed to write event: evt-redis"), any());
    }

}
