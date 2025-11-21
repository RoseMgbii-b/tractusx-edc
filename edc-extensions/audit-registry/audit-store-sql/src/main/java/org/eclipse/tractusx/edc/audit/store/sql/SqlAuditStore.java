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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.spi.persistence.EdcPersistenceException;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.eclipse.tractusx.edc.spi.audit.model.AuditEntry;
import org.eclipse.tractusx.edc.spi.audit.model.AuditEventType;
import org.eclipse.tractusx.edc.spi.audit.store.AuditQuery;
import org.eclipse.tractusx.edc.spi.audit.store.AuditStore;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * SQL-based implementation of the AuditStore interface.
 */
public class SqlAuditStore implements AuditStore {

    private final DataSourceRegistry dataSourceRegistry;
    private final String dataSourceName;
    private final TransactionContext transactionContext;
    private final ObjectMapper objectMapper;
    private final QueryExecutor queryExecutor;
    private final AuditEntryStatements statements;

    public SqlAuditStore(DataSourceRegistry dataSourceRegistry,
                         String dataSourceName,
                         TransactionContext transactionContext,
                         ObjectMapper objectMapper,
                         QueryExecutor queryExecutor,
                         AuditEntryStatements statements) {
        this.dataSourceRegistry = dataSourceRegistry;
        this.dataSourceName = dataSourceName;
        this.transactionContext = transactionContext;
        this.objectMapper = objectMapper;
        this.queryExecutor = queryExecutor;
        this.statements = statements;
    }

    @Override
    public StoreResult<Void> save(AuditEntry entry) {
        Objects.requireNonNull(entry, "entry");
        return transactionContext.execute(() -> {
            try (var connection = getConnection()) {
                var sql = statements.getInsertTemplate();
                queryExecutor.execute(connection, sql,
                        entry.getId(),
                        entry.getTimestamp(),
                        entry.getEventType().name(),
                        entry.getEventName(),
                        entry.getDescription(),
                        entry.getResult(),
                        serializeMetadata(entry.getMetadata()),
                        entry.isCritical()
                );
                return StoreResult.success();
            } catch (Exception e) {
                throw new EdcPersistenceException(e.getMessage(), e);
            }
        });
    }

    @Override
    public AuditEntry findById(String id) {
        Objects.requireNonNull(id, "id");
        return transactionContext.execute(() -> {
            try (var connection = getConnection()) {
                var sql = statements.getFindByIdTemplate();
                return queryExecutor.single(connection, true, this::mapResultSet, sql, id);
            } catch (Exception e) {
                throw new EdcPersistenceException(e.getMessage(), e);
            }
        });
    }

    @Override
    public List<AuditEntry> query(AuditQuery query) {
        Objects.requireNonNull(query, "query");
        return transactionContext.execute(() -> {
            try (var connection = getConnection()) {
                var sql = buildQuerySql(query);
                var params = buildQueryParams(query);
                var stream = queryExecutor.query(connection, true, this::mapResultSet, sql, params.toArray());
                return stream.toList();
            } catch (Exception e) {
                throw new EdcPersistenceException(e.getMessage(), e);
            }
        });
    }

    private AuditEntry mapResultSet(ResultSet rs) throws SQLException {
        return AuditEntry.builder()
                .id(rs.getString(statements.getIdColumn()))
                .timestamp(rs.getLong(statements.getTimestampColumn()))
                .eventType(AuditEventType.valueOf(rs.getString(statements.getEventTypeColumn())))
                .eventName(rs.getString(statements.getEventNameColumn()))
                .description(rs.getString(statements.getDescriptionColumn()))
                .result(rs.getString(statements.getResultColumn()))
                .metadata(deserializeMetadata(rs.getString(statements.getMetadataColumn())))
                .critical(rs.getBoolean(statements.getCriticalColumn()))
                .build();
    }

    private String buildQuerySql(AuditQuery query) {
        var sql = new StringBuilder(statements.getQueryTemplate());
        
        if (query.getEventType() != null) {
            sql.append(String.format(" AND %s = ?", statements.getEventTypeColumn()));
        }
        if (query.getFromTimestamp() != null) {
            sql.append(String.format(" AND %s >= ?", statements.getTimestampColumn()));
        }
        if (query.getToTimestamp() != null) {
            sql.append(String.format(" AND %s <= ?", statements.getTimestampColumn()));
        }
        if (query.getCritical() != null) {
            sql.append(String.format(" AND %s = ?", statements.getCriticalColumn()));
        }
        
        sql.append(String.format(" ORDER BY %s DESC", statements.getTimestampColumn()));
        sql.append(" LIMIT ? OFFSET ?");
        
        return sql.toString();
    }

    private List<Object> buildQueryParams(AuditQuery query) {
        var params = new ArrayList<Object>();
        
        if (query.getEventType() != null) {
            params.add(query.getEventType().name());
        }
        if (query.getFromTimestamp() != null) {
            params.add(query.getFromTimestamp());
        }
        if (query.getToTimestamp() != null) {
            params.add(query.getToTimestamp());
        }
        if (query.getCritical() != null) {
            params.add(query.getCritical());
        }
        
        params.add(query.getLimit());
        params.add(query.getOffset());
        
        return params;
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new EdcPersistenceException("Failed to serialize metadata", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deserializeMetadata(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            throw new EdcPersistenceException("Failed to deserialize metadata", e);
        }
    }

    private Connection getConnection() throws SQLException {
        return dataSourceRegistry.resolve(dataSourceName).getConnection();
    }
}
