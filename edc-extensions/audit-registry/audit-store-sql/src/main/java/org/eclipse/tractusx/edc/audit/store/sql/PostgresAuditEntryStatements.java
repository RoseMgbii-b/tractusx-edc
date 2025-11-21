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

/**
 * PostgreSQL-specific SQL statements for audit entries.
 */
public class PostgresAuditEntryStatements implements AuditEntryStatements {

    @Override
    public String getInsertTemplate() {
        return String.format(
                "INSERT INTO %s (%s, %s, %s, %s, %s, %s, %s, %s) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?)",
                getAuditEntryTable(),
                getIdColumn(),
                getTimestampColumn(),
                getEventTypeColumn(),
                getEventNameColumn(),
                getDescriptionColumn(),
                getResultColumn(),
                getMetadataColumn(),
                getCriticalColumn()
        );
    }

    @Override
    public String getFindByIdTemplate() {
        return String.format(
                "SELECT * FROM %s WHERE %s = ?",
                getAuditEntryTable(),
                getIdColumn()
        );
    }

    @Override
    public String getQueryTemplate() {
        return String.format(
                "SELECT * FROM %s WHERE 1=1",
                getAuditEntryTable()
        );
    }
}
