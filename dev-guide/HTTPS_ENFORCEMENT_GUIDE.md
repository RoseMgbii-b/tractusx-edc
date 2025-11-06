# 🔒 HTTPS/TLS Enforcement Guide for Tractus-X EDC

## 📋 Overview

This guide shows you how to enforce HTTPS/TLS for all API calls in the Tractus-X connector at multiple levels:
1. **Request Filter** (JAX-RS) - Reject HTTP requests, enforce HTTPS
2. **Configuration** - Update endpoints to use HTTPS
3. **Infrastructure** - TLS termination at ingress/load balancer

---

## 🎯 Method 1: JAX-RS Filter to Enforce HTTPS (Recommended)

### 📁 Create HTTPS Enforcement Filter

**Location:** `edc-extensions/oauth2-hot-reload/src/main/java/org/eclipse/tractusx/edc/oauth2/hotreload/HttpsEnforcementFilter.java`

```java
/********************************************************************************
 * Copyright (c) 2025 Your Company Name
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

package org.eclipse.tractusx.edc.oauth2.hotreload;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import org.eclipse.edc.spi.monitor.Monitor;

import java.io.IOException;
import java.net.URI;

/**
 * Filter that enforces HTTPS/TLS for all incoming requests.
 * 
 * Rejects HTTP requests and optionally redirects to HTTPS.
 */
public class HttpsEnforcementFilter implements ContainerRequestFilter {

    private final Monitor monitor;
    private final boolean allowRedirect;
    private final int httpsPort;

    /**
     * @param monitor Monitor for logging
     * @param allowRedirect If true, redirects HTTP to HTTPS (301/308). If false, returns 403.
     * @param httpsPort The HTTPS port to redirect to (only used if allowRedirect=true)
     */
    public HttpsEnforcementFilter(Monitor monitor, boolean allowRedirect, int httpsPort) {
        this.monitor = monitor;
        this.allowRedirect = allowRedirect;
        this.httpsPort = httpsPort;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        URI requestUri = requestContext.getUriInfo().getRequestUri();
        String scheme = requestUri.getScheme();
        boolean isSecure = requestContext.getSecurityContext().isSecure();

        monitor.debug("HTTPS Check - Scheme: " + scheme + ", isSecure: " + isSecure + ", URI: " + requestUri);

        // Check if request is HTTP (not HTTPS)
        if (!"https".equalsIgnoreCase(scheme) && !isSecure) {
            String path = requestUri.getPath();
            String query = requestUri.getQuery();

            if (allowRedirect) {
                // OPTION 1: Redirect HTTP to HTTPS (301 Moved Permanently)
                String httpsUrl = buildHttpsUrl(requestUri, query);
                monitor.warning("Redirecting HTTP request to HTTPS: " + requestUri + " -> " + httpsUrl);

                requestContext.abortWith(
                    Response.status(Response.Status.MOVED_PERMANENTLY)
                            .location(URI.create(httpsUrl))
                            .header("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
                            .entity("{\"message\":\"HTTPS required\",\"redirect\":\"" + httpsUrl + "\"}")
                            .build()
                );
            } else {
                // OPTION 2: Reject HTTP requests with 403 Forbidden
                monitor.severe("Rejected HTTP request - HTTPS required: " + requestUri);

                requestContext.abortWith(
                    Response.status(Response.Status.FORBIDDEN)
                            .header("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
                            .entity("{\"message\":\"HTTPS required\",\"type\":\"Forbidden\",\"scheme\":\"" + scheme + "\"}")
                            .build()
                );
            }
        }
    }

    /**
     * Build HTTPS URL from HTTP request URI
     */
    private String buildHttpsUrl(URI requestUri, String query) {
        String host = requestUri.getHost();
        String path = requestUri.getPath();
        
        // Use configured HTTPS port, or default to 443, or keep original port if different
        int port = httpsPort > 0 ? httpsPort : (requestUri.getPort() == -1 ? 443 : requestUri.getPort());
        
        StringBuilder httpsUrl = new StringBuilder("https://");
        httpsUrl.append(host);
        
        if (port != 443) {
            httpsUrl.append(":").append(port);
        }
        
        httpsUrl.append(path);
        
        if (query != null && !query.isEmpty()) {
            httpsUrl.append("?").append(query);
        }
        
        return httpsUrl.toString();
    }
}
```

---

### 📁 Register Filter in Extension

**Update:** `edc-extensions/oauth2-hot-reload/src/main/java/org/eclipse/tractusx/edc/oauth2/hotreload/OauthHotReloadExtension.java`

Add this to your `initialize()` method:

```java
@Override
public void initialize(ServiceExtensionContext context) {
    // ... existing code ...
    
    // Register HTTPS enforcement filter for Management API
    boolean allowRedirect = context.getSetting("web.http.management.https.redirect", false);
    int httpsPort = context.getSetting("web.http.management.https.port", 8443);
    
    HttpsEnforcementFilter httpsFilter = new HttpsEnforcementFilter(
        context.getMonitor(),
        allowRedirect,  // Set to true to redirect, false to reject
        httpsPort
    );
    
    // Register for all API contexts you want to protect
    webService.registerResource(ApiContext.MANAGEMENT, httpsFilter);
    // webService.registerResource(ApiContext.PROTOCOL, httpsFilter);  // Uncomment to protect protocol API
    // webService.registerResource(ApiContext.PUBLIC, httpsFilter);    // Uncomment to protect public API
    // webService.registerResource(ApiContext.CONTROL, httpsFilter);   // Uncomment to protect control API
    
    monitor.info("HTTPS enforcement filter registered for Management API");
}
```

---

## 🔧 Method 2: Check HTTPS in Existing RBAC Filter

If you want to add HTTPS checking to your existing `RoleBasedAccessFilter`, add this at the beginning of the `filter()` method:

**Location:** `edc-extensions/oauth2-hot-reload/src/main/java/org/eclipse/tractusx/edc/oauth2/hotreload/RoleBasedAccessFilter.java`

```java
@Override
public void filter(ContainerRequestContext requestContext) throws IOException {
    // HTTPS Enforcement Check (add at the top)
    URI requestUri = requestContext.getUriInfo().getRequestUri();
    String scheme = requestUri.getScheme();
    boolean isSecure = requestContext.getSecurityContext().isSecure();
    
    if (!"https".equalsIgnoreCase(scheme) && !isSecure) {
        monitor.severe("Rejected HTTP request - HTTPS required: " + requestUri);
        requestContext.abortWith(
            Response.status(Response.Status.FORBIDDEN)
                    .entity("{\"message\":\"HTTPS required\",\"type\":\"Forbidden\"}")
                    .build()
        );
        return; // Stop processing
    }
    
    // ... rest of your RBAC logic ...
    String path = requestContext.getUriInfo().getPath();
    // ... existing code ...
}
```

**Don't forget to add the import:**
```java
import java.net.URI;
```

---

## ⚙️ Method 3: Update Configuration Properties

**Update:** `configuration/config.properties`

Change all `http://` URLs to `https://`:

```properties
# BEFORE (HTTP - NOT SECURE)
edc.dataplane.token.validation.endpoint=http://localhost:28082/control/token
edc.dsp.callback.address=http://localhost:28081/protocol

# AFTER (HTTPS - SECURE)
edc.dataplane.token.validation.endpoint=https://localhost:8443/control/token
edc.dsp.callback.address=https://localhost:8444/protocol

# Or in production, use actual hostnames:
edc.dataplane.token.validation.endpoint=https://connector.example.com/control/token
edc.dsp.callback.address=https://connector.example.com/protocol
```

---

## 🌐 Method 4: Infrastructure-Level TLS (Production)

### Option A: Kubernetes Ingress with TLS

In production, use a Kubernetes Ingress controller with TLS termination:

**Example Ingress Config:**
```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: tractusx-connector-ingress
  annotations:
    cert-manager.io/cluster-issuer: letsencrypt-prod
    nginx.ingress.kubernetes.io/ssl-redirect: "true"  # Auto-redirect HTTP to HTTPS
    nginx.ingress.kubernetes.io/force-ssl-redirect: "true"
spec:
  tls:
    - hosts:
        - connector.example.com
      secretName: connector-tls-secret
  rules:
    - host: connector.example.com
      http:
        paths:
          - path: /api/management
            pathType: Prefix
            backend:
              service:
                name: connector-service
                port:
                  number: 8181
```

### Option B: Load Balancer with TLS Termination

Configure your cloud load balancer (AWS ALB, Azure LB, GCP LB) to:
1. Terminate TLS at the load balancer
2. Forward to backend on HTTP (or HTTPS for end-to-end encryption)
3. Set `X-Forwarded-Proto: https` header

---

## 🔍 Method 5: Detect HTTPS Behind Proxy/Load Balancer

If your connector runs behind a reverse proxy (nginx, load balancer) that terminates TLS, use forwarded headers:

**Update `HttpsEnforcementFilter.java`:**

```java
@Override
public void filter(ContainerRequestContext requestContext) throws IOException {
    // Check actual scheme
    URI requestUri = requestContext.getUriInfo().getRequestUri();
    String scheme = requestUri.getScheme();
    
    // Check X-Forwarded-Proto header (set by reverse proxy)
    String forwardedProto = requestContext.getHeaderString("X-Forwarded-Proto");
    String forwardedSsl = requestContext.getHeaderString("X-Forwarded-Ssl");
    
    boolean isHttps = "https".equalsIgnoreCase(scheme) 
                   || "https".equalsIgnoreCase(forwardedProto)
                   || "on".equalsIgnoreCase(forwardedSsl)
                   || requestContext.getSecurityContext().isSecure();
    
    if (!isHttps) {
        monitor.severe("Rejected HTTP request - HTTPS required: " + requestUri);
        requestContext.abortWith(
            Response.status(Response.Status.FORBIDDEN)
                    .entity("{\"message\":\"HTTPS required\",\"type\":\"Forbidden\"}")
                    .build()
        );
    }
}
```

---

## 📝 Configuration Properties Reference

Add these to your `configuration/config.properties`:

```properties
# HTTPS Enforcement Configuration
web.http.management.https.redirect=false  # true = redirect HTTP to HTTPS, false = reject
web.http.management.https.port=8443       # HTTPS port for redirects (if redirect=true)

# Update all endpoints to HTTPS
edc.dataplane.token.validation.endpoint=https://localhost:8443/control/token
edc.dsp.callback.address=https://localhost:8444/protocol
edc.hostname=connector.example.com  # Production hostname

# Strict Transport Security (HSTS) - handled by filter
# Max age: 1 year (31536000 seconds)
```

---

## ✅ Testing

### Test 1: HTTP Request Should Be Rejected

```bash
# This should return 403 Forbidden
curl -v http://localhost:8181/api/management/v3/assets
```

**Expected Response:**
```json
{
  "message": "HTTPS required",
  "type": "Forbidden",
  "scheme": "http"
}
```

### Test 2: HTTPS Request Should Work

```bash
# This should work (if TLS is configured)
curl -v https://localhost:8443/api/management/v3/assets \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

### Test 3: Check Logs

Look for these log messages:
```
DEBUG ... HTTPS Check - Scheme: https, isSecure: true, URI: https://...
INFO  ... HTTPS enforcement filter registered for Management API
```

---

## 🎯 Implementation Strategy

### For Local Development:
1. ✅ Use Method 2 (add to RBAC filter) - Quick and simple
2. ✅ Or disable HTTPS enforcement in dev (add config flag)

### For Production:
1. ✅ Method 4 (Infrastructure TLS) - Primary approach
2. ✅ Method 1 (JAX-RS Filter) - Additional security layer
3. ✅ Method 3 (Update Config) - Ensure all endpoints use HTTPS

---

## 🔐 Security Best Practices

1. **Always Use TLS 1.2+**: Configure your web server/Jetty to only accept TLS 1.2 or higher
2. **HSTS Header**: Your filter already adds `Strict-Transport-Security` header
3. **Certificate Management**: Use proper certificates (Let's Encrypt, internal CA)
4. **Port Separation**: Use different ports for HTTP (for redirects) and HTTPS
5. **End-to-End Encryption**: Consider mTLS for internal service-to-service communication

---

## 📚 Code Locations Reference

| Component | Location |
|-----------|----------|
| **Filter Class** | `edc-extensions/oauth2-hot-reload/src/main/java/.../HttpsEnforcementFilter.java` |
| **Filter Registration** | `edc-extensions/oauth2-hot-reload/src/main/java/.../OauthHotReloadExtension.java` |
| **Config File** | `configuration/config.properties` |
| **Kubernetes Ingress** | `charts/tractusx-connector/templates/ingress-controlplane.yaml` |

---

## ⚠️ Important Notes

1. **Behind Proxy**: If behind a proxy/load balancer, the connector may see HTTP even if the client uses HTTPS. Use `X-Forwarded-Proto` header checking.

2. **Local Development**: You may want to disable HTTPS enforcement for local dev. Add a config flag:
   ```java
   boolean enforceHttps = context.getSetting("web.http.https.enforce", true);
   if (enforceHttps) {
       webService.registerResource(ApiContext.MANAGEMENT, httpsFilter);
   }
   ```

3. **Health Checks**: Consider excluding health check endpoints from HTTPS enforcement:
   ```java
   String path = requestUri.getPath();
   if (path.startsWith("/health") || path.startsWith("/metrics")) {
       return; // Skip HTTPS check for health endpoints
   }
   ```

4. **Performance**: HTTPS enforcement is lightweight, but if you have high traffic, consider doing it at the infrastructure level first.

---

## 🚀 Quick Start: Add to Your Existing Extension

**Just copy this code block into `OauthHotReloadExtension.initialize()`:**

```java
// HTTPS Enforcement
boolean enforceHttps = context.getSetting("web.http.https.enforce", true);
if (enforceHttps) {
    HttpsEnforcementFilter httpsFilter = new HttpsEnforcementFilter(
        monitor,
        false,  // Don't redirect, just reject
        0       // Port not needed if redirect=false
    );
    webService.registerResource(ApiContext.MANAGEMENT, httpsFilter);
    monitor.info("HTTPS enforcement enabled for Management API");
}
```

And create the `HttpsEnforcementFilter.java` file as shown in Method 1 above.

---

That's it! Your connector will now reject all HTTP requests and enforce HTTPS/TLS. 🔒✅

