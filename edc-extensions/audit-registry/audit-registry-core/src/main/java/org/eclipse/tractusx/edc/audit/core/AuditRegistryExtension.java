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

import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.system.ExecutorInstrumentation;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.tractusx.edc.spi.audit.AuditRegistry;
import org.eclipse.tractusx.edc.spi.audit.store.AuditStore;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.concurrent.Executors;

/**
 * Extension that provides the composite audit registry with Redis caching support.
 */
@Extension("Audit Registry Core")
public class AuditRegistryExtension implements ServiceExtension {

    @Setting(value = "Enable Redis caching for audit entries", defaultValue = "false")
    public static final String REDIS_ENABLED = "tx.edc.audit.redis.enabled";

    @Setting(value = "Redis host", defaultValue = "localhost")
    public static final String REDIS_HOST = "tx.edc.audit.redis.host";

    @Setting(value = "Redis port", defaultValue = "6379")
    public static final String REDIS_PORT = "tx.edc.audit.redis.port";

    @Setting(value = "Redis cache TTL in seconds", defaultValue = "3600")
    public static final String REDIS_TTL = "tx.edc.audit.redis.ttl";

    @Setting(value = "Redis password (optional)")
    public static final String REDIS_PASSWORD = "tx.edc.audit.redis.password";

    @Setting(value = "Number of threads for async audit processing", defaultValue = "2")
    public static final String THREAD_POOL_SIZE = "tx.edc.audit.thread.pool.size";

    @Inject
    private AuditStore criticalStore;

    @Inject(required = false)
    private AuditStore detailedStore;

    @Inject
    private TypeManager typeManager;

    @Inject
    private ExecutorInstrumentation executorInstrumentation;

    private RedisAuditCache cache;

    @Override
    public String name() {
        return "Audit Registry Core";
    }

    @Provider
    public AuditRegistry auditRegistry(ServiceExtensionContext context) {
        var monitor = context.getMonitor();
        var redisEnabled = context.getSetting(REDIS_ENABLED, false);

        if (redisEnabled) {
            var host = context.getSetting(REDIS_HOST, "localhost");
            var port = context.getSetting(REDIS_PORT, 6379);
            var ttl = context.getSetting(REDIS_TTL, 3600);
            var password = context.getSetting(REDIS_PASSWORD, null);

            var poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(10);
            poolConfig.setMaxIdle(5);
            poolConfig.setMinIdle(1);

            JedisPool jedisPool;
            if (password != null && !password.isEmpty()) {
                jedisPool = new JedisPool(poolConfig, host, port, 2000, password);
            } else {
                jedisPool = new JedisPool(poolConfig, host, port);
            }

            cache = new RedisAuditCache(jedisPool, typeManager.getMapper(), ttl, monitor);
            monitor.info("Redis cache enabled for audit entries");
        }

        var poolSize = context.getSetting(THREAD_POOL_SIZE, 2);
        var executorService = executorInstrumentation.instrument(
                Executors.newFixedThreadPool(poolSize),
                "Audit Registry"
        );

        return new CompositeAuditRegistry(criticalStore, detailedStore, cache, executorService, monitor);
    }

    @Override
    public void shutdown() {
        if (cache != null) {
            cache.close();
        }
    }
}
