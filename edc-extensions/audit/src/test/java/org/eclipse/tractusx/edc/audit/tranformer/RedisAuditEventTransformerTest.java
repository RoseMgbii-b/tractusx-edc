package org.eclipse.tractusx.edc.audit.tranformer;

import org.eclipse.tractusx.edc.audit.sql.sinks.redis.transform.RedisAuditEventTransformer;
import org.eclipse.tractusx.edc.spi.audit.types.AuditEvent;
import org.eclipse.tractusx.edc.spi.audit.types.AuditEventCategory;
import org.eclipse.tractusx.edc.spi.audit.types.AuditEventName;
import org.eclipse.tractusx.edc.spi.audit.types.AuditOutcome;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class RedisAuditEventTransformerTest {

    @Test
    void transform_shouldExtractExpectedFields() {
        // Arrange
        var timestamp = Instant.parse("2025-12-02T08:00:00Z");

        var event = AuditEvent.Builder.newInstance()
                .id("123")
                .timestamp(timestamp)
                .category(AuditEventCategory.CONTRACT_NEGOTIATION)
                .eventName(AuditEventName.CONTRACT_NEGOTIATION_INITIATED)
                .outcome(AuditOutcome.SUCCESS)
                .actorId("actor-1")
                .subjectId("subject-1")
                .description("ignored in transformer")
                .build();
        var transformer = new RedisAuditEventTransformer();

        // Act
        Map<String, Object> map = transformer.transform(event);

        // Assert
        assertThat(map)
                .containsEntry("id", "123")
                .containsEntry("timestamp", timestamp.toEpochMilli())
                .containsEntry("category", "CONTRACT_NEGOTIATION")
                .containsEntry("eventName", "CONTRACT_NEGOTIATION_INITIATED")
                .containsEntry("outcome", "SUCCESS")
                .containsEntry("actorId", "actor-1")
                .containsEntry("subjectId", "subject-1")
                .hasSize(7); // no extra fields
    }

    @Test
    void transform_shouldHandleNullActorAndSubject() {
        // Arrange
        var timestamp = Instant.now();

        var event = AuditEvent.Builder.newInstance()
                .id("abc")
                .timestamp(timestamp)
                .category(AuditEventCategory.POLICY)
                .eventName(AuditEventName.POLICY_DEFINITION_DELETED)
                .outcome(AuditOutcome.WARNING)
                // actorId + subjectId are null
                .build();

        var transformer = new RedisAuditEventTransformer();

        // Act
        Map<String, Object> map = transformer.transform(event);

        // Assert
        assertThat(map.get("actorId")).isNull();
        assertThat(map.get("subjectId")).isNull();
        assertThat(map.get("category")).isEqualTo("POLICY");
        assertThat(map.get("eventName")).isEqualTo("POLICY_DEFINITION_DELETED");
        assertThat(map.get("outcome")).isEqualTo("WARNING");
    }
}
