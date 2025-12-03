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

package org.eclipse.tractusx.gateway.service;

import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.configuration.Config;
import org.eclipse.tractusx.gateway.model.AccessControlResult;
import org.eclipse.tractusx.gateway.model.RequestInfo;
import org.eclipse.tractusx.gateway.rate.limit.RateLimitStore;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GatewayService {

    private final RateLimitStore rateLimitStore;
    private final Monitor monitor;
    private final Set<String> ipWhitelist;
    private final Set<String> ipBlacklist;
    private final Long maxRequestsPerMinute;
    private final Long maxRequestsPerHour;
    private final Long maxRequestSizeBytes;
    private final boolean enabled;

    public GatewayService(RateLimitStore rateLimitStore, Monitor monitor, Config config) {
        this.rateLimitStore = rateLimitStore;
        this.monitor = monitor;
        this.enabled = config.getBoolean("edc.gateway.enabled", true);

        // IP filtering
        this.ipWhitelist = parseIpList(config.getString("edc.gateway.ip.whitelist", ""));
        this.ipBlacklist = parseIpList(config.getString("edc.gateway.ip.blacklist", ""));

        // Rate limiting
        this.maxRequestsPerMinute = config.getLong("edc.gateway.rate.limit.per.minute", 100L);
        this.maxRequestsPerHour = config.getLong("edc.gateway.rate.limit.per.hour", 1000L);

        // Request size
        this.maxRequestSizeBytes = config.getLong("edc.gateway.request.max.size.bytes", 10 * 1024 * 1024L); // 10MB default
    }

    /**
     * Evaluates if a request should be allowed.
     *
     * @param requestInfo Information about the request
     * @return AccessControlResult with allow/deny decision and reason
     */
    public AccessControlResult evaluateRequest(RequestInfo requestInfo) {
        if (!enabled) {
            return AccessControlResult.allowed("Gateway disabled");
        }

        // Check IP blacklist
        if (!ipBlacklist.isEmpty() && ipBlacklist.contains(requestInfo.getClientIp())) {
            logAccess(requestInfo, false, "IP blacklisted");
            return AccessControlResult.denied("IP address is blacklisted");
        }

        // Check IP whitelist (if configured)
        if (!ipWhitelist.isEmpty() && !ipWhitelist.contains(requestInfo.getClientIp())) {
            logAccess(requestInfo, false, "IP not whitelisted");
            return AccessControlResult.denied("IP address not in whitelist");
        }

        // Check rate limits
        String identifier = requestInfo.getUserIdentifier() != null
                ? requestInfo.getUserIdentifier()
                : requestInfo.getClientIp();

        // Per-minute limit
        if (!rateLimitStore.checkRateLimit(identifier, maxRequestsPerMinute, 60L)) {
            logAccess(requestInfo, false, "Rate limit exceeded (per minute)");
            return AccessControlResult.denied("Rate limit exceeded: too many requests per minute");
        }

        // Per-hour limit
        if (!rateLimitStore.checkRateLimit(identifier, maxRequestsPerHour, 3600L)) {
            logAccess(requestInfo, false, "Rate limit exceeded (per hour)");
            return AccessControlResult.denied("Rate limit exceeded: too many requests per hour");
        }

        // Record the request
        rateLimitStore.recordRequest(identifier);

        logAccess(requestInfo, true, "Request allowed");
        return AccessControlResult.allowed("Request passed access control");
    }

    private Set<String> parseIpList(String ipList) {
        if (ipList == null || ipList.trim().isEmpty()) {
            return Set.of();
        }
        return Stream.of(ipList.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private void logAccess(RequestInfo requestInfo, boolean allowed, String reason) {
        if (allowed) {
            monitor.debug(String.format("Access allowed: %s %s from %s - %s",
                    requestInfo.getMethod(),
                    requestInfo.getPath(),
                    requestInfo.getClientIp(),
                    reason));
        } else {
            monitor.warning(String.format("Access denied: %s %s from %s - %s",
                    requestInfo.getMethod(),
                    requestInfo.getPath(),
                    requestInfo.getClientIp(),
                    reason));
        }
    }
}
