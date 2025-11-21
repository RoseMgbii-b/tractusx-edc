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

package org.eclipse.tractusx.edc.audit.store.sql;

import org.eclipse.edc.sql.statement.SqlStatements;

/**
 * SQL statements for audit entry operations.
 */
public interface AuditEntryStatements extends SqlStatements {

    default String getAuditEntryTable() {
        return "edc_audit_entry";
    }

    default String getIdColumn() {
        return "id";
    }

    default String getTimestampColumn() {
        return "timestamp";
    }

    default String getEventTypeColumn() {
        return "event_type";
    }

    default String getEventNameColumn() {
        return "event_name";
    }

    default String getDescriptionColumn() {
        return "description";
    }

    default String getResultColumn() {
        return "result";
    }

    default String getMetadataColumn() {
        return "metadata";
    }

    default String getCriticalColumn() {
        return "critical";
    }

    String getInsertTemplate();

    String getFindByIdTemplate();

    String getQueryTemplate();
}
