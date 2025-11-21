/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.tractusx.edc.spi.audit.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Objects;

/**
 * Represents an audit entry in the system.
 * Contains essential information about events that need to be tracked for compliance and traceability.
 */
public class AuditEntry {
    private final String id;
    private final long timestamp;
    private final AuditEventType eventType;
    private final String eventName;
    private final String description;
    private final String result;
    private final Map<String, Object> metadata;
    private final boolean critical;

    @JsonCreator
    public AuditEntry(
            @JsonProperty("id") String id,
            @JsonProperty("timestamp") long timestamp,
            @JsonProperty("eventType") AuditEventType eventType,
            @JsonProperty("eventName") String eventName,
            @JsonProperty("description") String description,
            @JsonProperty("result") String result,
            @JsonProperty("metadata") Map<String, Object> metadata,
            @JsonProperty("critical") boolean critical) {
        this.id = id;
        this.timestamp = timestamp;
        this.eventType = eventType;
        this.eventName = eventName;
        this.description = description;
        this.result = result;
        this.metadata = metadata;
        this.critical = critical;
    }

    public String getId() {
        return id;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public AuditEventType getEventType() {
        return eventType;
    }

    public String getEventName() {
        return eventName;
    }

    public String getDescription() {
        return description;
    }

    public String getResult() {
        return result;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public boolean isCritical() {
        return critical;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuditEntry that = (AuditEntry) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private long timestamp;
        private AuditEventType eventType;
        private String eventName;
        private String description;
        private String result;
        private Map<String, Object> metadata;
        private boolean critical;

        private Builder() {
            this.timestamp = System.currentTimeMillis();
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder eventType(AuditEventType eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder eventName(String eventName) {
            this.eventName = eventName;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder result(String result) {
            this.result = result;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder critical(boolean critical) {
            this.critical = critical;
            return this;
        }

        public AuditEntry build() {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(eventType, "eventType");
            Objects.requireNonNull(eventName, "eventName");
            return new AuditEntry(id, timestamp, eventType, eventName, description, result, metadata, critical);
        }
    }
}
