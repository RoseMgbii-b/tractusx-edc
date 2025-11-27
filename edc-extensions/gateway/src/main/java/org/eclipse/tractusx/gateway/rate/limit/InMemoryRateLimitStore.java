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

package org.eclipse.tractusx.gateway.rate.limit;

import org.eclipse.tractusx.gateway.model.RequestWindow;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class InMemoryRateLimitStore implements RateLimitStore {
    private final Map<String, RequestWindow> requestWindows =  new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor = Executors.newScheduledThreadPool(1);

    public InMemoryRateLimitStore() {
        cleanupExecutor.scheduleAtFixedRate(this::cleanup,1,1, TimeUnit.MINUTES);
    }

    @Override
    public boolean checkRateLimit(String identifier, Long maxRequests, Long windowSeconds) {
        var window = requestWindows.computeIfAbsent(identifier,
                k -> new RequestWindow(windowSeconds));

        window.removeOldEntries(windowSeconds);

        return window.getRequestCount() < maxRequests;
    }

    @Override
    public void recordRequest(String identifier) {
        // Default 60-second window
        var window = requestWindows.computeIfAbsent(identifier,
                k -> new RequestWindow(60L));
        window.addRequest(Instant.now());
    }

    @Override
    public int getRequestCount(String identifier, int windowSeconds) {
        var window = requestWindows.get(identifier);
        if (window == null) {
            return 0;
        }
        window.removeOldEntries((long) windowSeconds);
        return window.getRequestCount();
    }

    private void cleanup() {
        var now = Instant.now();
        requestWindows.entrySet().removeIf(entry -> {
            // 5-minute cleanup window
            entry.getValue().removeOldEntries(300L);
            return entry.getValue().getRequestCount() == 0;
        });
    }
}
