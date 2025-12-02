package org.eclipse.tractusx.edc.audit.sql.sinks.redis.transform;

import org.eclipse.tractusx.edc.spi.audit.types.AuditEvent;

import java.util.HashMap;
import java.util.Map;

/**
 *  Redis Audit Event Transformer
 *  Takes only the needed fields from AuditEvent model
 */
public class RedisAuditEventTransformer {
    /**
     * Extracts the needed fields for redis from audit event
     *
     * @param event     The Audit Event
     * @return          Map with key/value pairs of field name/value
     */
    public Map<String, Object> transform(AuditEvent event) {
        Map<String, Object> map = new HashMap<>();

        map.put("id", event.getId());
        map.put("timestamp", event.getTimestamp().toEpochMilli());
        map.put("category", event.getCategory().name());
        map.put("eventName", event.getEventName().name());
        map.put("outcome", event.getOutcome().name());
        map.put("actorId", event.getActorId());
        map.put("subjectId", event.getSubjectId());

        return map;
    }
}

