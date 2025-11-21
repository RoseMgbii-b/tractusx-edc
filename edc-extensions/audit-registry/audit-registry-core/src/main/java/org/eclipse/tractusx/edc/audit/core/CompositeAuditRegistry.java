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

package org.eclipse.tractusx.edc.audit.core;

import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.tractusx.edc.spi.audit.AuditRegistry;
import org.eclipse.tractusx.edc.spi.audit.model.AuditEntry;
import org.eclipse.tractusx.edc.spi.audit.store.AuditStore;

import java.util.concurrent.ExecutorService;

/**
 * Composite audit registry that routes audit entries to different stores based on criticality.
 * Critical entries go to PostgreSQL (with optional Redis caching), while non-critical entries
 * can be routed to Elasticsearch for detailed logging.
 */
public class CompositeAuditRegistry implements AuditRegistry {

    private final AuditStore criticalStore;
    private final AuditStore detailedStore;
    private final RedisAuditCache cache;
    private final ExecutorService executorService;
    private final Monitor monitor;

    public CompositeAuditRegistry(AuditStore criticalStore,
                                  AuditStore detailedStore,
                                  RedisAuditCache cache,
                                  ExecutorService executorService,
                                  Monitor monitor) {
        this.criticalStore = criticalStore;
        this.detailedStore = detailedStore;
        this.cache = cache;
        this.executorService = executorService;
        this.monitor = monitor;
    }

    @Override
    public void record(AuditEntry entry) {
        // Use async processing to avoid blocking the main flow
        executorService.submit(() -> {
            try {
                if (entry.isCritical()) {
                    // Store critical entries in PostgreSQL
                    var result = criticalStore.save(entry);
                    if (result.succeeded() && cache != null) {
                        // Cache recent critical entries in Redis
                        cache.put(entry);
                    }
                }

                // Always store in detailed store (Elasticsearch) if configured
                if (detailedStore != null) {
                    detailedStore.save(entry);
                }
            } catch (Exception e) {
                monitor.warning("Failed to record audit entry: " + e.getMessage(), e);
            }
        });
    }
}
