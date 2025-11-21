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

import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.eclipse.tractusx.edc.spi.audit.store.AuditStore;

/**
 * Extension that provides a SQL-based implementation of the AuditStore.
 */
@Extension("SQL Audit Store")
public class SqlAuditStoreExtension implements ServiceExtension {

    @Setting(value = "The datasource to be used for audit entries", defaultValue = DataSourceRegistry.DEFAULT_DATASOURCE)
    public static final String DATASOURCE_NAME = "edc.sql.store.audit.datasource";

    @Inject
    private DataSourceRegistry dataSourceRegistry;

    @Inject
    private TransactionContext transactionContext;

    @Inject
    private TypeManager typeManager;

    @Inject
    private QueryExecutor queryExecutor;

    @Inject(required = false)
    private AuditEntryStatements statements;

    @Override
    public String name() {
        return "SQL Audit Store";
    }

    @Provider
    public AuditStore auditStore(ServiceExtensionContext context) {
        var dataSourceName = context.getConfig().getString(DATASOURCE_NAME, DataSourceRegistry.DEFAULT_DATASOURCE);
        return new SqlAuditStore(
                dataSourceRegistry,
                dataSourceName,
                transactionContext,
                typeManager.getMapper(),
                queryExecutor,
                getStatements()
        );
    }

    private AuditEntryStatements getStatements() {
        return statements == null ? new PostgresAuditEntryStatements() : statements;
    }
}
