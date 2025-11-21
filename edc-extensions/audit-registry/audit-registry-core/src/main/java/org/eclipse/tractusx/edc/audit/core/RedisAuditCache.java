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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.tractusx.edc.spi.audit.model.AuditEntry;
import redis.clients.jedis.JedisPool;

import java.io.IOException;

/**
 * Redis-based cache for recent critical audit entries.
 * Provides fast access to recent audit data without querying PostgreSQL.
 */
public class RedisAuditCache {

    private static final String KEY_PREFIX = "audit:";
    
    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper;
    private final int ttlSeconds;
    private final Monitor monitor;

    public RedisAuditCache(JedisPool jedisPool, ObjectMapper objectMapper, int ttlSeconds, Monitor monitor) {
        this.jedisPool = jedisPool;
        this.objectMapper = objectMapper;
        this.ttlSeconds = ttlSeconds;
        this.monitor = monitor;
    }

    /**
     * Cache an audit entry with a TTL.
     *
     * @param entry the audit entry to cache
     */
    public void put(AuditEntry entry) {
        try (var jedis = jedisPool.getResource()) {
            var key = KEY_PREFIX + entry.getId();
            var value = objectMapper.writeValueAsString(entry);
            jedis.setex(key, ttlSeconds, value);
        } catch (JsonProcessingException e) {
            monitor.warning("Failed to serialize audit entry for caching: " + e.getMessage(), e);
        } catch (Exception e) {
            monitor.warning("Failed to cache audit entry in Redis: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieve an audit entry from cache.
     *
     * @param id the entry ID
     * @return the cached entry or null if not found
     */
    public AuditEntry get(String id) {
        try (var jedis = jedisPool.getResource()) {
            var key = KEY_PREFIX + id;
            var value = jedis.get(key);
            if (value != null) {
                return objectMapper.readValue(value, AuditEntry.class);
            }
        } catch (IOException e) {
            monitor.warning("Failed to deserialize cached audit entry: " + e.getMessage(), e);
        } catch (Exception e) {
            monitor.warning("Failed to retrieve audit entry from Redis: " + e.getMessage(), e);
        }
        return null;
    }

    /**
     * Invalidate a cached entry.
     *
     * @param id the entry ID
     */
    public void invalidate(String id) {
        try (var jedis = jedisPool.getResource()) {
            var key = KEY_PREFIX + id;
            jedis.del(key);
        } catch (Exception e) {
            monitor.warning("Failed to invalidate cached audit entry: " + e.getMessage(), e);
        }
    }

    /**
     * Close the Redis connection pool.
     */
    public void close() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
        }
    }
}
