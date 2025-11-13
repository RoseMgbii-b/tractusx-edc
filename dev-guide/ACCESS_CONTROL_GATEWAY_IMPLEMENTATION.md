# Access Control Gateway Implementation Guide

## 🎯 Overview

This guide explains how to implement an **Access Control Gateway** extension in the Tractus-X EDC connector. The gateway acts as a protection and limitation layer that controls all external access to the connector, providing rate limiting, IP filtering, request validation, and other security controls.

---

## ❓ Why a New Extension is Needed

**Yes, a new extension is required** to add comprehensive access control capabilities to the EDC connector. Here's why:

### Current State
- The EDC connector has **basic authentication** (JWT tokens, API keys)
- There's **no built-in rate limiting** or request throttling
- **No IP-based access control** (whitelisting/blacklisting)
- **No request size limits** or validation beyond basic auth
- **No centralized access control** across all endpoints

### What the Extension Provides
1. **Centralized Access Control** - Single point of control for all external requests
2. **Rate Limiting** - Prevents abuse and DoS attacks
3. **IP Filtering** - Whitelist/blacklist based on IP addresses
4. **Request Validation** - Size limits, path validation, header checks
5. **Request Logging** - Audit trail for all access attempts
6. **Protection Layer** - Acts as a security boundary before requests reach business logic

### How It Works

```
┌─────────────────────────────────────────────────────────────────┐
│  External Client (Browser, API Client, etc.)                    │
│  Makes HTTP request to EDC connector                            │
└─────────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────────┐
│  EDC Connector - Access Control Gateway Extension              │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  AccessControlFilter (ContainerRequestFilter)            │  │
│  │  • Intercepts ALL incoming requests                      │  │
│  │  • Checks IP whitelist/blacklist                          │  │
│  │  • Validates rate limits                                  │  │
│  │  • Checks request size                                    │  │
│  │  • Validates path/headers                                 │  │
│  │  • Logs request                                           │  │
│  │  • Allows or blocks request                               │  │
│  └──────────────────────────────────────────────────────────┘  │
│                          ↓                                       │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  AccessControlService                                    │  │
│  │  • Rate limit tracking (per IP/user)                    │  │
│  │  • IP validation logic                                   │  │
│  │  • Request validation rules                             │  │
│  │  • Configuration management                              │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────────┐
│  EDC Business Logic (Controllers, Services)                     │
│  • Only receives requests that passed access control           │
│  • Protected from abuse and unauthorized access                │
└─────────────────────────────────────────────────────────────────┘
```

### Key Points

1. **Gateway = Filter Layer**: The extension acts as a filter that intercepts requests **before** they reach controllers
2. **No Separate Service**: It runs **inside** the connector process, not as a separate service
3. **All Endpoints Protected**: The filter can protect all API contexts (PROTOCOL, MANAGEMENT, CONTROL, etc.)
4. **Configurable**: Access control rules are configurable via properties file
5. **Non-Blocking by Default**: Requests that pass validation continue normally

### Benefits of This Approach

✅ **Single Point of Control**: All access control logic in one place  
✅ **Performance**: Filter runs early, blocking bad requests before expensive processing  
✅ **Security**: Multiple layers of protection (IP, rate, size, validation)  
✅ **Observability**: Centralized logging of all access attempts  
✅ **Flexibility**: Easy to add new access control rules  
✅ **Follows EDC Patterns**: Uses same extension structure as other EDC filters  

---

## 📊 Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                    EDC Connector                                 │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  HTTP Request (All Endpoints)                             │  │
│  │  /api/*, /protocol/*, /control/*, /management/*           │  │
│  └──────────────────────────────────────────────────────────┘  │
│                          ↓                                       │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  AccessControlGatewayExtension                            │  │
│  │  • Registers filters for all API contexts                │  │
│  │  • Configures AccessControlService                       │  │
│  └──────────────────────────────────────────────────────────┘  │
│                          ↓                                       │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  AccessControlFilter (ContainerRequestFilter)             │  │
│  │  • filter(ContainerRequestContext)                        │  │
│  │  • Extracts: IP, path, headers, user info                 │  │
│  │  • Calls AccessControlService                             │  │
│  │  • Returns 429 (rate limit) or 403 (blocked) if rejected   │  │
│  └──────────────────────────────────────────────────────────┘  │
│                          ↓                                       │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  AccessControlService                                    │  │
│  │  • isIpAllowed(String ip)                                │  │
│  │  • checkRateLimit(String identifier)                     │  │
│  │  • validateRequest(RequestInfo)                          │  │
│  │  • logAccess(RequestInfo, boolean allowed)               │  │
│  └──────────────────────────────────────────────────────────┘  │
│                          ↓                                       │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  RateLimitStore (In-Memory or Redis)                     │  │
│  │  • Tracks request counts per IP/user                      │  │
│  │  • Sliding window or fixed window algorithm               │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

**Key Components:**
1. **Extension** - Registers the filter with WebService
2. **Filter** - Intercepts all HTTP requests
3. **Service** - Contains access control business logic
4. **Rate Limit Store** - Tracks request rates (in-memory or external)

---

## 🏗️ Implementation Structure

### Module Structure

```
edc-extensions/
└── access-control-gateway/
    ├── build.gradle.kts
    ├── src/
    │   ├── main/
    │   │   ├── java/
    │   │   │   └── org/eclipse/tractusx/edc/gateway/
    │   │   │       ├── AccessControlGatewayExtension.java
    │   │   │       ├── AccessControlFilter.java
    │   │   │       ├── AccessControlService.java
    │   │   │       ├── RateLimitStore.java
    │   │   │       ├── InMemoryRateLimitStore.java
    │   │   │       └── RequestInfo.java
    │   │   └── resources/
    │   │       └── META-INF/
    │   │           └── services/
    │   │               └── org.eclipse.edc.spi.system.ServiceExtension
    │   └── test/
    │       └── java/
    │           └── org/eclipse/tractusx/edc/gateway/
    │               ├── AccessControlFilterTest.java
    │               ├── AccessControlServiceTest.java
    │               └── AccessControlGatewayExtensionTest.java
    └── README.md
```

---

## 📝 Step-by-Step Implementation

### Step 1: Create the Module Structure

Create the directory structure:

```bash
mkdir -p edc-extensions/access-control-gateway/src/main/java/org/eclipse/tractusx/edc/gateway
mkdir -p edc-extensions/access-control-gateway/src/main/resources/META-INF/services
mkdir -p edc-extensions/access-control-gateway/src/test/java/org/eclipse/tractusx/edc/gateway
```

### Step 2: Create build.gradle.kts

```kotlin
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

plugins {
    `java-library`
}

dependencies {
    implementation(libs.edc.spi.web)
    implementation(libs.edc.core.controlplane)
    testImplementation(libs.edc.junit)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit)
}

edcBuild {
    publish.set(true)
}
```

### Step 3: Create RequestInfo (Data Class)

```java
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

package org.eclipse.tractusx.edc.gateway;

import java.time.Instant;
import java.util.Map;

/**
 * Information about an incoming HTTP request for access control evaluation.
 */
public class RequestInfo {
    private final String clientIp;
    private final String path;
    private final String method;
    private final Map<String, String> headers;
    private final Instant timestamp;
    private final String userIdentifier; // From JWT token if available

    public RequestInfo(String clientIp, String path, String method, 
                      Map<String, String> headers, String userIdentifier) {
        this.clientIp = clientIp;
        this.path = path;
        this.method = method;
        this.headers = headers;
        this.timestamp = Instant.now();
        this.userIdentifier = userIdentifier;
    }

    public String getClientIp() {
        return clientIp;
    }

    public String getPath() {
        return path;
    }

    public String getMethod() {
        return method;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getUserIdentifier() {
        return userIdentifier;
    }
}
```

### Step 4: Create RateLimitStore Interface

```java
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

package org.eclipse.tractusx.edc.gateway;

/**
 * Stores and tracks rate limit information for clients.
 */
public interface RateLimitStore {
    
    /**
     * Checks if a request should be allowed based on rate limits.
     * 
     * @param identifier Client identifier (IP address or user ID)
     * @param maxRequests Maximum requests allowed in the time window
     * @param windowSeconds Time window in seconds
     * @return true if request is allowed, false if rate limit exceeded
     */
    boolean checkRateLimit(String identifier, int maxRequests, int windowSeconds);
    
    /**
     * Records a request for rate limiting purposes.
     * 
     * @param identifier Client identifier
     */
    void recordRequest(String identifier);
    
    /**
     * Gets the number of requests in the current window for an identifier.
     * 
     * @param identifier Client identifier
     * @param windowSeconds Time window in seconds
     * @return Number of requests in the window
     */
    int getRequestCount(String identifier, int windowSeconds);
}
```

### Step 5: Create InMemoryRateLimitStore Implementation

```java
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

package org.eclipse.tractusx.edc.gateway;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * In-memory implementation of RateLimitStore using sliding window algorithm.
 */
public class InMemoryRateLimitStore implements RateLimitStore {
    
    private final Map<String, RequestWindow> requestWindows = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor = Executors.newScheduledThreadPool(1);
    
    public InMemoryRateLimitStore() {
        // Clean up old entries every minute
        cleanupExecutor.scheduleAtFixedRate(this::cleanup, 1, 1, TimeUnit.MINUTES);
    }
    
    @Override
    public boolean checkRateLimit(String identifier, int maxRequests, int windowSeconds) {
        var window = requestWindows.computeIfAbsent(identifier, 
            k -> new RequestWindow(windowSeconds));
        
        window.removeOldEntries(windowSeconds);
        
        return window.getRequestCount() < maxRequests;
    }
    
    @Override
    public void recordRequest(String identifier) {
        var window = requestWindows.computeIfAbsent(identifier, 
            k -> new RequestWindow(60)); // Default 60 second window
        
        window.addRequest(Instant.now());
    }
    
    @Override
    public int getRequestCount(String identifier, int windowSeconds) {
        var window = requestWindows.get(identifier);
        if (window == null) {
            return 0;
        }
        window.removeOldEntries(windowSeconds);
        return window.getRequestCount();
    }
    
    private void cleanup() {
        var now = Instant.now();
        requestWindows.entrySet().removeIf(entry -> {
            entry.getValue().removeOldEntries(300); // 5 minute cleanup window
            return entry.getValue().getRequestCount() == 0;
        });
    }
    
    private static class RequestWindow {
        private final java.util.List<Instant> requests = new java.util.concurrent.CopyOnWriteArrayList<>();
        private final int defaultWindowSeconds;
        
        RequestWindow(int defaultWindowSeconds) {
            this.defaultWindowSeconds = defaultWindowSeconds;
        }
        
        void addRequest(Instant timestamp) {
            requests.add(timestamp);
        }
        
        void removeOldEntries(int windowSeconds) {
            var cutoff = Instant.now().minusSeconds(windowSeconds);
            requests.removeIf(timestamp -> timestamp.isBefore(cutoff));
        }
        
        int getRequestCount() {
            return requests.size();
        }
    }
}
```

### Step 6: Create AccessControlService

```java
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

package org.eclipse.tractusx.edc.gateway;

import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.configuration.Config;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service that evaluates access control rules for incoming requests.
 */
public class AccessControlService {
    
    private final RateLimitStore rateLimitStore;
    private final Monitor monitor;
    private final Set<String> ipWhitelist;
    private final Set<String> ipBlacklist;
    private final int maxRequestsPerMinute;
    private final int maxRequestsPerHour;
    private final long maxRequestSizeBytes;
    private final boolean enabled;
    
    public AccessControlService(RateLimitStore rateLimitStore, Monitor monitor, Config config) {
        this.rateLimitStore = rateLimitStore;
        this.monitor = monitor;
        this.enabled = config.getBoolean("edc.gateway.enabled", true);
        
        // IP filtering
        this.ipWhitelist = parseIpList(config.getString("edc.gateway.ip.whitelist", ""));
        this.ipBlacklist = parseIpList(config.getString("edc.gateway.ip.blacklist", ""));
        
        // Rate limiting
        this.maxRequestsPerMinute = config.getInt("edc.gateway.rate.limit.per.minute", 100);
        this.maxRequestsPerHour = config.getInt("edc.gateway.rate.limit.per.hour", 1000);
        
        // Request size
        this.maxRequestSizeBytes = config.getLong("edc.gateway.request.max.size.bytes", 10 * 1024 * 1024); // 10MB default
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
        if (!rateLimitStore.checkRateLimit(identifier, maxRequestsPerMinute, 60)) {
            logAccess(requestInfo, false, "Rate limit exceeded (per minute)");
            return AccessControlResult.denied("Rate limit exceeded: too many requests per minute");
        }
        
        // Per-hour limit
        if (!rateLimitStore.checkRateLimit(identifier, maxRequestsPerHour, 3600)) {
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
            monitor.debug("Access allowed: %s %s from %s - %s", 
                requestInfo.getMethod(), 
                requestInfo.getPath(), 
                requestInfo.getClientIp(),
                reason);
        } else {
            monitor.warning("Access denied: %s %s from %s - %s", 
                requestInfo.getMethod(), 
                requestInfo.getPath(), 
                requestInfo.getClientIp(),
                reason);
        }
    }
    
    /**
     * Result of access control evaluation.
     */
    public static class AccessControlResult {
        private final boolean allowed;
        private final String reason;
        
        private AccessControlResult(boolean allowed, String reason) {
            this.allowed = allowed;
            this.reason = reason;
        }
        
        public static AccessControlResult allowed(String reason) {
            return new AccessControlResult(true, reason);
        }
        
        public static AccessControlResult denied(String reason) {
            return new AccessControlResult(false, reason);
        }
        
        public boolean isAllowed() {
            return allowed;
        }
        
        public String getReason() {
            return reason;
        }
    }
}
```

### Step 7: Create AccessControlFilter

```java
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

package org.eclipse.tractusx.edc.gateway;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.eclipse.edc.spi.monitor.Monitor;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * JAX-RS filter that intercepts all HTTP requests and applies access control.
 */
public class AccessControlFilter implements ContainerRequestFilter {
    
    private final AccessControlService accessControlService;
    private final Monitor monitor;
    
    public AccessControlFilter(AccessControlService accessControlService, Monitor monitor) {
        this.accessControlService = accessControlService;
        this.monitor = monitor;
    }
    
    @Override
    public void filter(ContainerRequestContext requestContext) {
        try {
            // Extract request information
            String clientIp = extractClientIp(requestContext);
            String path = requestContext.getUriInfo().getRequestUri().getPath();
            String method = requestContext.getMethod();
            Map<String, String> headers = extractHeaders(requestContext);
            String userIdentifier = extractUserIdentifier(requestContext);
            
            // Create request info
            RequestInfo requestInfo = new RequestInfo(
                clientIp, 
                path, 
                method, 
                headers, 
                userIdentifier
            );
            
            // Evaluate access control
            AccessControlService.AccessControlResult result = 
                accessControlService.evaluateRequest(requestInfo);
            
            // Block request if denied
            if (!result.isAllowed()) {
                monitor.warning("Access denied: %s - %s", requestInfo.getPath(), result.getReason());
                requestContext.abortWith(
                    Response.status(Response.Status.FORBIDDEN)
                        .entity(Map.of("error", result.getReason()))
                        .build()
                );
                return;
            }
            
            // Request allowed, continue processing
            monitor.debug("Access allowed: %s %s from %s", method, path, clientIp);
            
        } catch (Exception e) {
            monitor.severe("Error in access control filter", e);
            // On error, allow request (fail open) - you may want to change this to fail closed
            monitor.warning("Access control error, allowing request: %s", e.getMessage());
        }
    }
    
    private String extractClientIp(ContainerRequestContext requestContext) {
        // Check X-Forwarded-For header (for proxies/load balancers)
        String xForwardedFor = requestContext.getHeaderString("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // Take the first IP (original client)
            return xForwardedFor.split(",")[0].trim();
        }
        
        // Check X-Real-IP header
        String xRealIp = requestContext.getHeaderString("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        // Fallback to remote address
        return requestContext.getSecurityContext().getUserPrincipal() != null
            ? requestContext.getSecurityContext().getUserPrincipal().getName()
            : "unknown";
    }
    
    private Map<String, String> extractHeaders(ContainerRequestContext requestContext) {
        Map<String, String> headers = new HashMap<>();
        requestContext.getHeaders().forEach((key, values) -> {
            if (values != null && !values.isEmpty()) {
                headers.put(key, values.get(0));
            }
        });
        return headers;
    }
    
    private String extractUserIdentifier(ContainerRequestContext requestContext) {
        // Try to extract user ID from JWT token if available
        String authHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // In a real implementation, you might decode the JWT to get user ID
            // For now, we'll use a simple approach
            return extractUserIdFromToken(authHeader);
        }
        return null;
    }
    
    private String extractUserIdFromToken(String authHeader) {
        // Simplified - in production, decode JWT and extract 'sub' or 'preferred_username'
        // This is a placeholder - implement proper JWT parsing if needed
        return null;
    }
}
```

### Step 8: Create AccessControlGatewayExtension

```java
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

package org.eclipse.tractusx.edc.gateway;

import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.Config;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.ApiContext;

/**
 * Extension that provides access control gateway functionality.
 */
@Extension("Access Control Gateway")
public class AccessControlGatewayExtension implements ServiceExtension {
    
    @Inject
    private WebService webService;
    
    @Inject
    private Monitor monitor;
    
    @Setting(value = "Enable access control gateway", defaultValue = "true")
    public static final String GATEWAY_ENABLED = "edc.gateway.enabled";
    
    @Setting(value = "IP whitelist (comma-separated)", defaultValue = "")
    public static final String IP_WHITELIST = "edc.gateway.ip.whitelist";
    
    @Setting(value = "IP blacklist (comma-separated)", defaultValue = "")
    public static final String IP_BLACKLIST = "edc.gateway.ip.blacklist";
    
    @Setting(value = "Max requests per minute per client", defaultValue = "100")
    public static final String RATE_LIMIT_PER_MINUTE = "edc.gateway.rate.limit.per.minute";
    
    @Setting(value = "Max requests per hour per client", defaultValue = "1000")
    public static final String RATE_LIMIT_PER_HOUR = "edc.gateway.rate.limit.per.hour";
    
    @Override
    public void initialize(ServiceExtensionContext context) {
        Config config = context.getConfig();
        Monitor monitor = context.getMonitor();
        
        // Create rate limit store
        RateLimitStore rateLimitStore = new InMemoryRateLimitStore();
        
        // Create access control service
        AccessControlService accessControlService = new AccessControlService(
            rateLimitStore, 
            monitor, 
            config
        );
        
        // Create and register filter for all API contexts
        AccessControlFilter filter = new AccessControlFilter(accessControlService, monitor);
        
        // Register filter for all API contexts
        registerFilterForContext(ApiContext.PROTOCOL, filter);
        registerFilterForContext(ApiContext.MANAGEMENT, filter);
        registerFilterForContext(ApiContext.CONTROL, filter);
        registerFilterForContext(ApiContext.PUBLIC, filter);
        
        monitor.info("Access Control Gateway initialized and active");
    }
    
    private void registerFilterForContext(String apiContext, AccessControlFilter filter) {
        try {
            webService.registerResource(apiContext, filter);
            monitor.debug("Access control filter registered for context: %s", apiContext);
        } catch (Exception e) {
            monitor.warning("Failed to register access control filter for context %s: %s", 
                apiContext, e.getMessage());
        }
    }
}
```

### Step 9: Create Service Extension Registration File

Create `src/main/resources/META-INF/services/org.eclipse.edc.spi.system.ServiceExtension`:

```
org.eclipse.tractusx.edc.gateway.AccessControlGatewayExtension
```

### Step 10: Add Extension to Control Plane

Edit `edc-controlplane/edc-controlplane-base/build.gradle.kts`:

```kotlin
dependencies {
    // ... existing dependencies ...
    implementation(project(":edc-extensions:access-control-gateway"))
}
```

---

## ⚙️ Configuration

### Configuration Properties

Add these properties to your `configuration.properties` file:

```properties
# Enable/disable access control gateway
edc.gateway.enabled=true

# IP filtering (comma-separated list)
edc.gateway.ip.whitelist=192.168.1.0/24,10.0.0.0/8
edc.gateway.ip.blacklist=192.168.1.100,10.0.0.50

# Rate limiting
edc.gateway.rate.limit.per.minute=100
edc.gateway.rate.limit.per.hour=1000

# Request size limits (in bytes)
edc.gateway.request.max.size.bytes=10485760  # 10MB
```

### Environment Variables

You can also configure via environment variables (converted automatically):

```bash
export EDC_GATEWAY_ENABLED=true
export EDC_GATEWAY_IP_WHITELIST=192.168.1.0/24,10.0.0.0/8
export EDC_GATEWAY_RATE_LIMIT_PER_MINUTE=100
```

---

## 🧪 Testing

### Unit Tests

Create test files following the pattern:

```java
@ExtendWith(MockitoExtension.class)
class AccessControlFilterTest {
    
    @Mock
    private AccessControlService accessControlService;
    
    @Mock
    private Monitor monitor;
    
    @Mock
    private ContainerRequestContext requestContext;
    
    private AccessControlFilter filter;
    
    @BeforeEach
    void setUp() {
        filter = new AccessControlFilter(accessControlService, monitor);
    }
    
    @Test
    void filter_shouldAllowRequest_whenAccessControlAllows() {
        // Test implementation
    }
    
    @Test
    void filter_shouldBlockRequest_whenAccessControlDenies() {
        // Test implementation
    }
}
```

### Integration Testing

Test the extension in an end-to-end test:

```java
@EndToEndTest
class AccessControlGatewayIntegrationTest {
    
    @Test
    void shouldBlockRequest_whenRateLimitExceeded() {
        // Make many requests quickly
        // Verify 429 response
    }
    
    @Test
    void shouldBlockRequest_whenIpBlacklisted() {
        // Configure blacklist
        // Make request from blacklisted IP
        // Verify 403 response
    }
}
```

---

## 🚀 Deployment

### Build the Extension

```bash
./gradlew :edc-extensions:access-control-gateway:build
```

### Run with Extension

The extension will be automatically loaded when you run the control plane:

```bash
./gradlew :edc-controlplane:edc-controlplane-base:run
```

### Docker Deployment

The extension is included in the Docker image automatically when you build:

```bash
./gradlew dockerize
```

---

## 📋 Checklist

- [ ] Create module structure
- [ ] Implement `RequestInfo` class
- [ ] Implement `RateLimitStore` interface and `InMemoryRateLimitStore`
- [ ] Implement `AccessControlService`
- [ ] Implement `AccessControlFilter`
- [ ] Implement `AccessControlGatewayExtension`
- [ ] Create service extension registration file
- [ ] Add extension to control plane dependencies
- [ ] Write unit tests
- [ ] Write integration tests
- [ ] Configure properties
- [ ] Test in development environment
- [ ] Document configuration options
- [ ] Update README

---

## 🔧 Advanced Features (Optional)

### Redis-Based Rate Limiting

For distributed deployments, implement a Redis-based `RateLimitStore`:

```java
public class RedisRateLimitStore implements RateLimitStore {
    private final RedisClient redisClient;
    // Implementation using Redis for distributed rate limiting
}
```

### Custom Access Rules

Add support for path-based rules:

```properties
edc.gateway.rules.path./api/management/v3/users.rate.limit=50
edc.gateway.rules.path./protocol/2024/1/catalog/request.rate.limit=200
```

### Metrics Integration

Add metrics for monitoring:

```java
// Track metrics: requests allowed/denied, rate limit hits, etc.
monitor.metric("gateway.requests.allowed", 1);
monitor.metric("gateway.requests.denied", 1);
```

---

## 📚 References

- [EDC Web Service SPI](https://github.com/eclipse-edc/Connector)
- [JAX-RS ContainerRequestFilter](https://jakarta.ee/specifications/restful-ws/3.0/apidocs/jakarta/ws/rs/jakarta/ws/rs/container/containerrequestfilter)
- [Token Interceptor Extension](../edc-extensions/token-interceptor) - Reference implementation
- [User Management Guide](./USER_MANAGEMENT_CONTROLLER_IMPLEMENTATION.md) - Similar extension pattern

---

## ❓ FAQ

**Q: Does this replace authentication?**  
A: No, this is a **complementary** layer. Authentication (JWT tokens, API keys) still happens. The gateway adds rate limiting, IP filtering, and request validation.

**Q: Can I disable it for certain endpoints?**  
A: Yes, you can modify the filter to skip certain paths or add configuration for excluded paths.

**Q: How does it work with load balancers?**  
A: The filter checks `X-Forwarded-For` and `X-Real-IP` headers to get the original client IP.

**Q: Is the in-memory rate limit store suitable for production?**  
A: For single-instance deployments, yes. For distributed deployments, consider implementing a Redis-based store.

**Q: Can I add custom access control rules?**  
A: Yes, extend `AccessControlService` to add custom validation logic.

---

## 📝 License

SPDX-License-Identifier: Apache-2.0

