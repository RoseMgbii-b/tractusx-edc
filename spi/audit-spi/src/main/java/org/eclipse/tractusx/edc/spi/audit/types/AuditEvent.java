package org.eclipse.tractusx.edc.spi.audit.types;

import java.time.Instant;
import java.util.Objects;



/**
 * The Audit Event
 * The main data model representing an audit event in the system.
 * Constructed and populated by the AuditEventSubscriber when an auditable action occurs.
 */
public class AuditEvent {
    private String id;                           // UUID
    private Instant timestamp;                   // Time event occurred
    private AuditEventCategory category;         // CONTRACT_NEGOTIATION, TRANSFER_PROCESS, CONTRACT_AGREEMENT, etc.
    private AuditEventName eventName;            // The specific event name eg: TRANSFER_PROCESS_REQUESTED
    private String description;                  // Details about the event
    private AuditOutcome outcome;                // SUCCESS, FAILURE, WARNING
    private String actorId;                      // User, connector Id, or system
    private String subjectId;                    // Domain object ID (contractId, etc)

    private AuditEvent() {}


    public String getId() {
        return id;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public AuditEventCategory getCategory() {
        return category;
    }

    public AuditEventName getEventName() {
        return eventName;
    }

    public String getDescription() {
        return description;
    }

    public AuditOutcome getOutcome() {
        return outcome;
    }

    public String getActorId() {
        return actorId;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public static class Builder {
        private final AuditEvent event;

        private Builder() {
            event = new AuditEvent();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder id(String id) {
            event.id = id;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            event.timestamp = timestamp;
            return this;
        }

        public Builder category(AuditEventCategory category) {
            event.category = category;
            return this;
        }

        public Builder eventName(AuditEventName name) {
            event.eventName = name;
            return this;
        }

        public Builder outcome(AuditOutcome outcome) {
            event.outcome = outcome;
            return this;
        }

        public Builder subjectId(String subjectId) {
            event.subjectId = subjectId;
            return this;
        }

        public Builder actorId(String actorId) {
            event.actorId = actorId;
            return this;
        }

        public Builder description(String description) {
            event.description = description;
            return this;
        }

        public AuditEvent build() {
            Objects.requireNonNull(event.id, " AuditEvent.id is required");
            Objects.requireNonNull(event.timestamp, " AuditEvent.timestamp is required");
            Objects.requireNonNull(event.category, " AuditEvent.category is required");
            Objects.requireNonNull(event.eventName, " AuditEvent.eventName is required");
            Objects.requireNonNull(event.outcome, " AuditEvent.outcome is required");
            return event;
        }
    }
}


