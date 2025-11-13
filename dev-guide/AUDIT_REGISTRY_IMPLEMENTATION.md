# Centralized Audit Registry Implementation Guide

## 🎯 Overview

This guide explains how to implement a **Centralized Audit Registry** extension in the Tractus-X EDC connector. The audit registry logs all transactions (contract negotiations, data transfers, asset operations, API calls, etc.) in a persistent database, providing a complete audit trail for compliance, security, and troubleshooting purposes.

---

## ❓ Why a New Extension is Needed

**Yes, a new extension is required** to provide comprehensive audit logging capabilities. Here's why:

### Current State
- The EDC connector has **event system** that publishes domain events
- There's an **event-subscriber** extension that sends events to OpenTelemetry
- **No persistent audit log** - events are sent to external systems but not stored locally
- **No query API** - cannot retrieve historical audit records
- **No centralized registry** - audit information is scattered across different systems

### What the Extension Provides
1. **Persistent Audit Storage** - All transactions stored in PostgreSQL database
2. **Structured Audit Records** - Consistent format for all audit entries
3. **Query API** - REST endpoints to search and retrieve audit logs
4. **Complete Transaction History** - Captures all business events and operations
5. **Compliance Support** - Immutable audit trail for regulatory requirements
6. **Troubleshooting** - Historical records for debugging and analysis

### How It Works

```
┌─────────────────────────────────────────────────────────────────┐
│  EDC Connector - Business Operations                            │
│                                                                  │
│  • Contract Negotiations                                        │
│  • Data Transfers                                               │
│  • Asset Operations                                             │
│  • Policy Operations                                            │
│  • API Calls                                                    │
└─────────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────────┐
│  EventRouter (EDC Core)                                         │
│  • Publishes domain events                                      │
│  • Event types: ContractNegotiationFinalized,                   │
│    TransferProcessStarted, AssetCreated, etc.                   │
└─────────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────────┐
│  AuditRegistryExtension                                         │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  AuditEventSubscriber (EventSubscriber)                  │  │
│  │  • Listens to ALL events                                 │  │
│  │  • Transforms events to AuditRecord                      │  │
│  │  • Stores in AuditRegistryStore                          │  │
│  └──────────────────────────────────────────────────────────┘  │
│                          ↓                                       │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  AuditRegistryStore (SQL Store)                          │  │
│  │  • Persists audit records to PostgreSQL                  │  │
│  │  • Provides query capabilities                           │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────────┐
│  PostgreSQL Database                                            │
│  • audit_records table                                          │
│  • Indexed for fast queries                                     │
│  • Immutable audit trail                                        │
└─────────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────────┐
│  AuditRegistryApiExtension                                      │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  AuditRegistryController (REST API)                      │  │
│  │  • GET /api/management/v3/audit                          │  │
│  │  • Query by date, event type, participant, etc.          │  │
│  │  • Export audit logs                                      │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### Key Points

1. **Event-Driven**: Uses EDC's existing event system - no need to modify business logic
2. **Non-Intrusive**: Subscribes to events without affecting existing functionality
3. **Persistent**: All audit records stored in database (not just forwarded to external systems)
4. **Queryable**: REST API to search and retrieve audit logs
5. **Comprehensive**: Captures all domain events automatically

### Benefits of This Approach

✅ **Complete Audit Trail**: All transactions logged automatically  
✅ **Compliance Ready**: Immutable records for regulatory requirements  
✅ **Queryable**: Search and filter audit logs via REST API  
✅ **Performance**: Asynchronous event processing doesn't block business operations  
✅ **Scalable**: Database-backed storage handles large volumes  
✅ **Follows EDC Patterns**: Uses existing event system and SQL store patterns  

---

## 📊 Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                    EDC Connector                                 │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  Domain Events (EventRouter)                              │  │
│  │  • ContractNegotiationFinalized                           │  │
│  │  • TransferProcessStarted                                 │  │
│  │  • AssetCreated                                           │  │
│  │  • PolicyCreated                                          │  │
│  │  • ContractAgreementSigned                                │  │
│  └──────────────────────────────────────────────────────────┘  │
│                          ↓                                       │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  AuditRegistryExtension                                   │  │
│  │  • Registers AuditEventSubscriber                         │  │
│  │  • Configures AuditRegistryStore                          │  │
│  └──────────────────────────────────────────────────────────┘  │
│                          ↓                                       │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  AuditEventSubscriber                                     │  │
│  │  • on(EventEnvelope<E> event)                            │  │
│  │  • Transforms event to AuditRecord                       │  │
│  │  • Extracts: timestamp, event type, participant, data    │  │
│  └──────────────────────────────────────────────────────────┘  │
│                          ↓                                       │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  AuditRegistryStore (SQL)                                 │  │
│  │  • save(AuditRecord)                                      │  │
│  │  • query(QuerySpec)                                       │  │
│  │  • findById(String)                                       │  │
│  └──────────────────────────────────────────────────────────┘  │
│                          ↓                                       │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  AuditRegistryApiExtension                                │  │
│  │  • AuditRegistryController                                │  │
│  │  • GET /api/management/v3/audit                           │  │
│  │  • GET /api/management/v3/audit/{id}                      │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────────┐
│  PostgreSQL Database                                            │
│                                                                  │
│  CREATE TABLE edc_audit_record (                                │
│    id VARCHAR PRIMARY KEY,                                      │
│    timestamp BIGINT,                                            │
│    event_type VARCHAR,                                          │
│    participant_id VARCHAR,                                      │
│    event_data JSONB,                                            │
│    correlation_id VARCHAR,                                      │
│    ...                                                          │
│  );                                                             │
└─────────────────────────────────────────────────────────────────┘
```

**Key Components:**
1. **EventSubscriber** - Listens to all domain events
2. **AuditRecord** - Data model for audit entries
3. **SQL Store** - Persists audit records to database
4. **REST API** - Query and retrieve audit logs
5. **Database** - PostgreSQL table for storage

---

## 🏗️ Implementation Structure

### Module Structure

```
edc-extensions/
└── audit-registry/
    ├── audit-registry-spi/
    │   ├── build.gradle.kts
    │   └── src/main/java/
    │       └── org/eclipse/tractusx/edc/audit/
    │           ├── spi/
    │           │   ├── AuditRegistryStore.java
    │           │   └── types/
    │           │       └── AuditRecord.java
    │           └── events/
    │               └── AuditEvent.java
    ├── audit-registry-core/
    │   ├── build.gradle.kts
    │   └── src/main/java/
    │       └── org/eclipse/tractusx/edc/audit/
    │           ├── AuditEventSubscriber.java
    │           └── AuditRegistryService.java
    ├── audit-registry-store-sql/
    │   ├── build.gradle.kts
    │   └── src/main/java/
    │       └── org/eclipse/tractusx/edc/audit/
    │           ├── store/
    │           │   ├── sql/
    │           │   │   ├── SqlAuditRegistryStore.java
    │           │   │   └── SqlAuditRegistryStatements.java
    │           │   └── migration/
    │           │       └── V1__CreateAuditRecordTable.java
    └── audit-registry-api/
        ├── build.gradle.kts
        └── src/main/java/
            └── org/eclipse/tractusx/edc/audit/
                ├── api/
                │   ├── AuditRegistryApiExtension.java
                │   └── controller/
                │       └── AuditRegistryController.java
                └── dto/
                    └── AuditRecordDto.java
```

---

## 📝 Step-by-Step Implementation

### Step 1: Create SPI Module (audit-registry-spi)

#### 1.1 Create build.gradle.kts

```kotlin
/********************************************************************************
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
 ********************************************************************************/

plugins {
    `java-library`
}

dependencies {
    implementation(libs.edc.spi.core)
    implementation(libs.edc.spi.event)
}

edcBuild {
    publish.set(true)
}
```

#### 1.2 Create AuditRecord Type

```java
/********************************************************************************
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
 ********************************************************************************/

package org.eclipse.tractusx.edc.audit.spi.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.eclipse.edc.spi.entity.Entity;

import java.util.Map;

/**
 * Represents an audit record for a transaction/event in the EDC connector.
 */
@JsonDeserialize(builder = AuditRecord.Builder.class)
public class AuditRecord extends Entity {
    
    private String eventType;
    private String eventId;
    private String participantId;
    private String correlationId;
    private Map<String, Object> eventData;
    private String eventPayload; // JSON string of the full event
    private String source; // Where the event originated (e.g., "contract-negotiation", "transfer-process")
    
    private AuditRecord() {
    }
    
    @JsonProperty("eventType")
    public String getEventType() {
        return eventType;
    }
    
    @JsonProperty("eventId")
    public String getEventId() {
        return eventId;
    }
    
    @JsonProperty("participantId")
    public String getParticipantId() {
        return participantId;
    }
    
    @JsonProperty("correlationId")
    public String getCorrelationId() {
        return correlationId;
    }
    
    @JsonProperty("eventData")
    public Map<String, Object> getEventData() {
        return eventData;
    }
    
    @JsonProperty("eventPayload")
    public String getEventPayload() {
        return eventPayload;
    }
    
    @JsonProperty("source")
    public String getSource() {
        return source;
    }
    
    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder extends Entity.Builder<AuditRecord, Builder> {
        
        private Builder(AuditRecord record) {
            super(record);
        }
        
        @JsonCreator
        public static Builder newInstance() {
            return new Builder(new AuditRecord());
        }
        
        public Builder eventType(String eventType) {
            entity.eventType = eventType;
            return this;
        }
        
        public Builder eventId(String eventId) {
            entity.eventId = eventId;
            return this;
        }
        
        public Builder participantId(String participantId) {
            entity.participantId = participantId;
            return this;
        }
        
        public Builder correlationId(String correlationId) {
            entity.correlationId = correlationId;
            return this;
        }
        
        public Builder eventData(Map<String, Object> eventData) {
            entity.eventData = eventData;
            return this;
        }
        
        public Builder eventPayload(String eventPayload) {
            entity.eventPayload = eventPayload;
            return this;
        }
        
        public Builder source(String source) {
            entity.source = source;
            return this;
        }
        
        @Override
        public Builder self() {
            return this;
        }
        
        @Override
        public AuditRecord build() {
            return entity;
        }
    }
}
```

#### 1.3 Create AuditRegistryStore Interface

```java
/********************************************************************************
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
 ********************************************************************************/

package org.eclipse.tractusx.edc.audit.spi;

import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.tractusx.edc.audit.spi.types.AuditRecord;

import java.util.stream.Stream;

/**
 * Store for audit records.
 */
public interface AuditRegistryStore {
    
    /**
     * Saves an audit record.
     * 
     * @param record The audit record to save
     * @return StoreResult indicating success or failure
     */
    StoreResult<Void> save(AuditRecord record);
    
    /**
     * Finds an audit record by ID.
     * 
     * @param id The audit record ID
     * @return The audit record, or null if not found
     */
    AuditRecord findById(String id);
    
    /**
     * Queries audit records based on QuerySpec.
     * 
     * @param querySpec The query specification
     * @return Stream of matching audit records
     */
    Stream<AuditRecord> query(QuerySpec querySpec);
    
    /**
     * Deletes audit records older than the specified timestamp.
     * Used for data retention policies.
     * 
     * @param timestampMillis Timestamp in milliseconds
     * @return Number of records deleted
     */
    int deleteOlderThan(long timestampMillis);
}
```

### Step 2: Create Core Module (audit-registry-core)

#### 2.1 Create build.gradle.kts

```kotlin
/********************************************************************************
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
 ********************************************************************************/

plugins {
    `java-library`
}

dependencies {
    implementation(project(":edc-extensions:audit-registry:audit-registry-spi"))
    implementation(libs.edc.spi.core)
    implementation(libs.edc.spi.event)
    testImplementation(libs.edc.junit)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.mockito.core)
}

edcBuild {
    publish.set(true)
}
```

#### 2.2 Create AuditEventSubscriber

```java
/********************************************************************************
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
 ********************************************************************************/

package org.eclipse.tractusx.edc.audit;

import org.eclipse.edc.spi.event.Event;
import org.eclipse.edc.spi.event.EventEnvelope;
import org.eclipse.edc.spi.event.EventSubscriber;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.tractusx.edc.audit.spi.AuditRegistryStore;
import org.eclipse.tractusx.edc.audit.spi.types.AuditRecord;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Event subscriber that captures all domain events and stores them as audit records.
 */
public class AuditEventSubscriber implements EventSubscriber {
    
    private final AuditRegistryStore auditStore;
    private final TypeManager typeManager;
    private final Monitor monitor;
    private final Clock clock;
    
    public AuditEventSubscriber(AuditRegistryStore auditStore, TypeManager typeManager, 
                                Monitor monitor, Clock clock) {
        this.auditStore = auditStore;
        this.typeManager = typeManager;
        this.monitor = monitor;
        this.clock = clock;
    }
    
    @Override
    public <E extends Event> void on(EventEnvelope<E> eventEnvelope) {
        try {
            E event = eventEnvelope.getPayload();
            
            // Transform event to audit record
            AuditRecord auditRecord = createAuditRecord(eventEnvelope, event);
            
            // Save to audit store
            var result = auditStore.save(auditRecord);
            if (result.failed()) {
                monitor.warning("Failed to save audit record for event %s: %s", 
                    eventEnvelope.getId(), result.getFailureDetail());
            } else {
                monitor.debug("Audit record saved: %s (%s)", 
                    auditRecord.getId(), auditRecord.getEventType());
            }
        } catch (Exception e) {
            monitor.severe("Error processing audit event: " + eventEnvelope.getId(), e);
            // Don't throw - we don't want audit failures to break business logic
        }
    }
    
    private <E extends Event> AuditRecord createAuditRecord(EventEnvelope<E> envelope, E event) {
        // Serialize event payload
        String eventPayload = typeManager.writeValueAsString(event);
        
        // Extract event metadata
        String eventType = event.getClass().getName();
        String source = extractSource(eventType);
        String participantId = extractParticipantId(event);
        String correlationId = extractCorrelationId(event);
        
        // Extract key data from event
        Map<String, Object> eventData = extractEventData(event);
        
        // Create audit record
        return AuditRecord.Builder.newInstance()
            .id(UUID.randomUUID().toString())
            .eventId(envelope.getId())
            .eventType(eventType)
            .source(source)
            .participantId(participantId)
            .correlationId(correlationId)
            .eventData(eventData)
            .eventPayload(eventPayload)
            .createdAt(clock.millis())
            .build();
    }
    
    private String extractSource(String eventType) {
        // Extract source from event type name
        // e.g., "ContractNegotiationFinalized" -> "contract-negotiation"
        if (eventType.contains("ContractNegotiation")) {
            return "contract-negotiation";
        } else if (eventType.contains("TransferProcess")) {
            return "transfer-process";
        } else if (eventType.contains("Asset")) {
            return "asset";
        } else if (eventType.contains("Policy")) {
            return "policy";
        } else if (eventType.contains("Agreement")) {
            return "agreement";
        }
        return "unknown";
    }
    
    private String extractParticipantId(Event event) {
        // Try to extract participant ID from event
        // This depends on the event structure - may need reflection or event-specific logic
        try {
            // Example: if event has getConnectorId() or getParticipantId() method
            // Use reflection or event-specific extraction
            return null; // Implement based on actual event structure
        } catch (Exception e) {
            return null;
        }
    }
    
    private String extractCorrelationId(Event event) {
        // Extract correlation ID if available
        // Many events have a correlation ID for tracking related operations
        try {
            // Implement based on event structure
            return null;
        } catch (Exception e) {
            return null;
        }
    }
    
    private Map<String, Object> extractEventData(Event event) {
        // Extract key fields from event for easier querying
        Map<String, Object> data = new HashMap<>();
        
        try {
            // Use reflection or type-specific logic to extract important fields
            // Example: contract ID, asset ID, transfer process ID, etc.
            
            // For now, return empty map - can be enhanced based on specific event types
            return data;
        } catch (Exception e) {
            monitor.warning("Error extracting event data", e);
            return data;
        }
    }
}
```

#### 2.3 Create AuditRegistryServiceExtension

```java
/********************************************************************************
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
 ********************************************************************************/

package org.eclipse.tractusx.edc.audit;

import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.event.Event;
import org.eclipse.edc.spi.event.EventRouter;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.tractusx.edc.audit.spi.AuditRegistryStore;

import java.time.Clock;

/**
 * Extension that registers the audit event subscriber to capture all domain events.
 */
@Extension("Audit Registry Core")
public class AuditRegistryServiceExtension implements ServiceExtension {
    
    @Setting(value = "Enable audit registry", defaultValue = "true")
    public static final String AUDIT_ENABLED = "edc.audit.registry.enabled";
    
    @Inject
    private EventRouter eventRouter;
    
    @Inject
    private AuditRegistryStore auditStore;
    
    @Inject
    private TypeManager typeManager;
    
    @Override
    public void initialize(ServiceExtensionContext context) {
        var enabled = context.getSetting(AUDIT_ENABLED, true);
        
        if (!enabled) {
            context.getMonitor().info("Audit registry is disabled");
            return;
        }
        
        // Register subscriber for all events
        AuditEventSubscriber subscriber = new AuditEventSubscriber(
            auditStore,
            typeManager,
            context.getMonitor(),
            Clock.systemUTC()
        );
        
        eventRouter.register(Event.class, subscriber);
        
        context.getMonitor().info("Audit registry initialized - all events will be logged");
    }
}
```

### Step 3: Create SQL Store Module (audit-registry-store-sql)

#### 3.1 Create build.gradle.kts

```kotlin
/********************************************************************************
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
 ********************************************************************************/

plugins {
    `java-library`
}

dependencies {
    implementation(project(":edc-extensions:audit-registry:audit-registry-spi"))
    implementation(libs.edc.sql.core)
    implementation(libs.edc.transaction.local)
    testImplementation(libs.edc.junit)
    testImplementation(libs.edc.sql.testfixtures)
}

edcBuild {
    publish.set(true)
}
```

#### 3.2 Create SQL Statements

```java
/********************************************************************************
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
 ********************************************************************************/

package org.eclipse.tractusx.edc.audit.store.sql;

import org.eclipse.edc.sql.statement.SqlStatements;

/**
 * SQL statements for audit registry store.
 */
public interface SqlAuditRegistryStatements extends SqlStatements {
    
    default String getInsertTemplate() {
        return """
            INSERT INTO %s_audit_record (
                id, created_at, event_type, event_id, participant_id, 
                correlation_id, event_data, event_payload, source
            ) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
            """.formatted(getSchema());
    }
    
    default String getSelectByIdTemplate() {
        return """
            SELECT * FROM %s_audit_record WHERE id = ?
            """.formatted(getSchema());
    }
    
    default String getSelectTemplate() {
        return """
            SELECT * FROM %s_audit_record
            """.formatted(getSchema());
    }
    
    default String getDeleteOlderThanTemplate() {
        return """
            DELETE FROM %s_audit_record WHERE created_at < ?
            """.formatted(getSchema());
    }
    
    default String getTableName() {
        return getSchema() + "_audit_record";
    }
    
    String getSchema();
}
```

#### 3.3 Create SQL Store Implementation

```java
/********************************************************************************
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
 ********************************************************************************/

package org.eclipse.tractusx.edc.audit.store.sql;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.spi.persistence.EdcPersistenceException;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.sql.store.AbstractSqlStore;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.eclipse.tractusx.edc.audit.spi.AuditRegistryStore;
import org.eclipse.tractusx.edc.audit.spi.types.AuditRecord;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.stream.Stream;

public class SqlAuditRegistryStore extends AbstractSqlStore implements AuditRegistryStore {
    
    private final SqlAuditRegistryStatements statements;
    
    public SqlAuditRegistryStore(DataSourceRegistry dataSourceRegistry, String dataSourceName,
                                TransactionContext transactionContext, ObjectMapper objectMapper,
                                QueryExecutor queryExecutor, SqlAuditRegistryStatements statements) {
        super(dataSourceRegistry, dataSourceName, transactionContext, objectMapper, queryExecutor);
        this.statements = statements;
    }
    
    @Override
    public StoreResult<Void> save(AuditRecord record) {
        return transactionContext.execute(() -> {
            try (var connection = getConnection()) {
                queryExecutor.execute(connection, statements.getInsertTemplate(),
                    record.getId(),
                    record.getCreatedAt(),
                    record.getEventType(),
                    record.getEventId(),
                    record.getParticipantId(),
                    record.getCorrelationId(),
                    toJson(record.getEventData()),
                    record.getEventPayload(),
                    record.getSource()
                );
                return StoreResult.success();
            } catch (SQLException e) {
                throw new EdcPersistenceException(e);
            }
        });
    }
    
    @Override
    public AuditRecord findById(String id) {
        return transactionContext.execute(() -> {
            try (var connection = getConnection()) {
                return queryExecutor.single(connection, true, this::mapRecord, statements.getSelectByIdTemplate(), id);
            } catch (SQLException e) {
                throw new EdcPersistenceException(e);
            }
        });
    }
    
    @Override
    public Stream<AuditRecord> query(QuerySpec querySpec) {
        return transactionContext.execute(() -> {
            try (var connection = getConnection()) {
                var statement = statements.createQuery(querySpec);
                return queryExecutor.query(connection, true, this::mapRecord, 
                    statement.getQueryAsString(), statement.getParameters());
            } catch (SQLException e) {
                throw new EdcPersistenceException(e);
            }
        });
    }
    
    @Override
    public int deleteOlderThan(long timestampMillis) {
        return transactionContext.execute(() -> {
            try (var connection = getConnection()) {
                return queryExecutor.execute(connection, statements.getDeleteOlderThanTemplate(), timestampMillis);
            } catch (SQLException e) {
                throw new EdcPersistenceException(e);
            }
        });
    }
    
    private AuditRecord mapRecord(ResultSet resultSet) throws SQLException {
        return AuditRecord.Builder.newInstance()
            .id(resultSet.getString("id"))
            .createdAt(resultSet.getLong("created_at"))
            .eventType(resultSet.getString("event_type"))
            .eventId(resultSet.getString("event_id"))
            .participantId(resultSet.getString("participant_id"))
            .correlationId(resultSet.getString("correlation_id"))
            .eventData(fromJson(resultSet.getString("event_data"), Map.class))
            .eventPayload(resultSet.getString("event_payload"))
            .source(resultSet.getString("source"))
            .build();
    }
}
```

#### 3.4 Create Database Migration

Create migration file: `src/main/resources/migrations/V1__CreateAuditRecordTable.sql`

```sql
CREATE TABLE IF NOT EXISTS edc_audit_record
(
    id                VARCHAR NOT NULL PRIMARY KEY,
    created_at        BIGINT  NOT NULL,
    event_type        VARCHAR NOT NULL,
    event_id          VARCHAR,
    participant_id    VARCHAR,
    correlation_id    VARCHAR,
    event_data        JSONB,
    event_payload     TEXT,
    source            VARCHAR
);

CREATE INDEX IF NOT EXISTS idx_audit_record_created_at ON edc_audit_record (created_at);
CREATE INDEX IF NOT EXISTS idx_audit_record_event_type ON edc_audit_record (event_type);
CREATE INDEX IF NOT EXISTS idx_audit_record_participant_id ON edc_audit_record (participant_id);
CREATE INDEX IF NOT EXISTS idx_audit_record_correlation_id ON edc_audit_record (correlation_id);
CREATE INDEX IF NOT EXISTS idx_audit_record_source ON edc_audit_record (source);
```

### Step 4: Create API Module (audit-registry-api)

#### 4.1 Create REST Controller

```java
/********************************************************************************
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
 ********************************************************************************/

package org.eclipse.tractusx.edc.audit.api.controller;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.tractusx.edc.audit.spi.AuditRegistryStore;
import org.eclipse.tractusx.edc.audit.spi.types.AuditRecord;

import java.util.List;
import java.util.stream.Collectors;

import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;

/**
 * REST API for querying audit records.
 */
@Path("/audit")
public class AuditRegistryController {
    
    private final AuditRegistryStore auditStore;
    
    public AuditRegistryController(AuditRegistryStore auditStore) {
        this.auditStore = auditStore;
    }
    
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response queryAuditRecords(
            @QueryParam("offset") Integer offset,
            @QueryParam("limit") Integer limit,
            @QueryParam("filter") String filter,
            @QueryParam("sort") String sort) {
        
        try {
            var querySpec = QuerySpec.Builder.newInstance()
                .offset(offset != null ? offset : 0)
                .limit(limit != null ? limit : 50)
                .filter(filter)
                .sortField(sort)
                .build();
            
            var records = auditStore.query(querySpec)
                .collect(Collectors.toList());
            
            return Response.ok(records).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Result.failure("Query failed: " + e.getMessage()))
                .build();
        }
    }
    
    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAuditRecord(@PathParam("id") String id) {
        var record = auditStore.findById(id);
        
        if (record == null) {
            return Response.status(NOT_FOUND)
                .entity(Result.failure("Audit record not found: " + id))
                .build();
        }
        
        return Response.ok(record).build();
    }
}
```

#### 4.2 Create API Extension

```java
/********************************************************************************
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
 ********************************************************************************/

package org.eclipse.tractusx.edc.audit.api;

import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.ApiContext;
import org.eclipse.tractusx.edc.audit.api.controller.AuditRegistryController;
import org.eclipse.tractusx.edc.audit.spi.AuditRegistryStore;

/**
 * Extension that provides REST API for audit registry.
 */
@Extension("Audit Registry API")
public class AuditRegistryApiExtension implements ServiceExtension {
    
    @Inject
    private WebService webService;
    
    @Inject
    private AuditRegistryStore auditStore;
    
    @Override
    public void initialize(ServiceExtensionContext context) {
        var controller = new AuditRegistryController(auditStore);
        webService.registerResource(ApiContext.MANAGEMENT, controller);
        
        context.getMonitor().info("Audit Registry API initialized at /api/management/v3/audit");
    }
}
```

---

## ⚙️ Configuration

### Configuration Properties

Add to `configuration.properties`:

```properties
# Enable audit registry
edc.audit.registry.enabled=true

# Data source configuration (uses default if not specified)
edc.sql.store.audit.datasource=default

# Data retention (delete records older than X days, 0 = never delete)
edc.audit.registry.retention.days=365
```

---

## 🧪 Testing

### Unit Tests

Test the event subscriber and store:

```java
@ExtendWith(MockitoExtension.class)
class AuditEventSubscriberTest {
    
    @Mock
    private AuditRegistryStore auditStore;
    
    @Mock
    private TypeManager typeManager;
    
    @Mock
    private Monitor monitor;
    
    private AuditEventSubscriber subscriber;
    
    @BeforeEach
    void setUp() {
        subscriber = new AuditEventSubscriber(auditStore, typeManager, monitor, Clock.systemUTC());
    }
    
    @Test
    void shouldSaveAuditRecord_whenEventReceived() {
        // Test implementation
    }
}
```

---

## 📋 Checklist

- [ ] Create SPI module with AuditRecord and AuditRegistryStore
- [ ] Create core module with AuditEventSubscriber
- [ ] Create SQL store module with database implementation
- [ ] Create database migration script
- [ ] Create API module with REST controller
- [ ] Register extensions in control plane
- [ ] Write unit tests
- [ ] Write integration tests
- [ ] Configure properties
- [ ] Test in development environment
- [ ] Document API endpoints
- [ ] Update README

---

## 📚 References

- [Event Subscriber Extension](../edc-extensions/event-subscriber) - Reference for event subscription
- [SQL Store Pattern](../edc-extensions/agreements/retirement-evaluation-store-sql) - Reference for SQL store implementation
- [EDC Event System](https://github.com/eclipse-edc/Connector) - EDC event documentation

---

## ❓ FAQ

**Q: Does this replace the event-subscriber extension?**  
A: No, they can coexist. The event-subscriber sends events to OpenTelemetry, while the audit registry stores them locally in the database.

**Q: What events are captured?**  
A: All events that extend `Event` class are captured automatically.

**Q: Can I filter which events are audited?**  
A: Yes, modify `AuditEventSubscriber` to check event type before saving.

**Q: How do I query audit records?**  
A: Use the REST API: `GET /api/management/v3/audit?filter=...&limit=50`

**Q: What about data retention?**  
A: Use the `deleteOlderThan()` method with a scheduled job to clean up old records.

---

## 📝 License

SPDX-License-Identifier: Apache-2.0

