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

package org.eclipse.tractusx.edc.spi.audit.store;

import org.eclipse.tractusx.edc.spi.audit.model.AuditEventType;

/**
 * Query criteria for retrieving audit entries.
 */
public class AuditQuery {
    private final AuditEventType eventType;
    private final Long fromTimestamp;
    private final Long toTimestamp;
    private final Boolean critical;
    private final int limit;
    private final int offset;

    private AuditQuery(Builder builder) {
        this.eventType = builder.eventType;
        this.fromTimestamp = builder.fromTimestamp;
        this.toTimestamp = builder.toTimestamp;
        this.critical = builder.critical;
        this.limit = builder.limit;
        this.offset = builder.offset;
    }

    public AuditEventType getEventType() {
        return eventType;
    }

    public Long getFromTimestamp() {
        return fromTimestamp;
    }

    public Long getToTimestamp() {
        return toTimestamp;
    }

    public Boolean getCritical() {
        return critical;
    }

    public int getLimit() {
        return limit;
    }

    public int getOffset() {
        return offset;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private AuditEventType eventType;
        private Long fromTimestamp;
        private Long toTimestamp;
        private Boolean critical;
        private int limit = 100;
        private int offset = 0;

        private Builder() {
        }

        public Builder eventType(AuditEventType eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder fromTimestamp(Long fromTimestamp) {
            this.fromTimestamp = fromTimestamp;
            return this;
        }

        public Builder toTimestamp(Long toTimestamp) {
            this.toTimestamp = toTimestamp;
            return this;
        }

        public Builder critical(Boolean critical) {
            this.critical = critical;
            return this;
        }

        public Builder limit(int limit) {
            this.limit = limit;
            return this;
        }

        public Builder offset(int offset) {
            this.offset = offset;
            return this;
        }

        public AuditQuery build() {
            return new AuditQuery(this);
        }
    }
}
