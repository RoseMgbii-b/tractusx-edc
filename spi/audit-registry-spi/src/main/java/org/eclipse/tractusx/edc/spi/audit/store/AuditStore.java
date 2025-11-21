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

import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.tractusx.edc.spi.audit.model.AuditEntry;

import java.util.List;

/**
 * Store interface for persisting and retrieving audit entries.
 * Implementations may store data in different backends (PostgreSQL, Elasticsearch, etc.)
 */
public interface AuditStore {

    /**
     * Stores an audit entry.
     *
     * @param entry the audit entry to store
     * @return a StoreResult indicating success or failure
     */
    StoreResult<Void> save(AuditEntry entry);

    /**
     * Retrieves an audit entry by its ID.
     *
     * @param id the entry ID
     * @return the audit entry if found, null otherwise
     */
    AuditEntry findById(String id);

    /**
     * Queries audit entries based on criteria.
     *
     * @param query the query criteria
     * @return a list of matching audit entries
     */
    List<AuditEntry> query(AuditQuery query);
}
