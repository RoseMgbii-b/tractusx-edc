# Extension Development Templates & Tips

## 📋 Quick Reference

### What Happened With Your Build

✅ **BUILD SUCCESSFUL** - All 770 tasks completed
✅ **Runtime Started** - 166 service extensions loaded
⚠️ **Extension Not Loading** - Your extension might not be included in the runtime

**Why extension might not load:**
1. Extension not included in runtime's `build.gradle.kts`
2. Service file not found or class name mismatch
3. Extension disabled or commented out

## 🔧 Check If Extension Is Loaded

Look for this log on startup:
```
INFO ... OAuth Hot Reload Extension Starting
```

If you don't see it, the extension isn't loaded. Check:
```bash
# Verify the service file exists and is correct
cat edc-extensions/oauth2-hot-reload/src/main/resources/META-INF/services/org.eclipse.edc.spi.system.ServiceExtension

# Verify class file exists
ls -la edc-extensions/oauth2-hot-reload/src/main/java/org/eclipse/tractusx/edc/oauth2/hotreload/OauthHotReloadExtension.java
```

---

## 📝 Template 1: Basic Service Extension

### File: `src/main/java/org/eclipse/tractusx/edc/yourmodule/YourExtension.java`

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

package org.eclipse.tractusx.edc.yourmodule;

import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

/**
 * Your Extension Description
 * 
 * PURPOSE:
 * Brief description of what this extension does.
 */
@Extension(value = "Your Extension Name", categories = { "category1", "category2" })
public class YourExtension implements ServiceExtension {

    @Inject
    private Monitor monitor;

    @Override
    public String name() {
        return "Your Extension Name";
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        monitor.info("=== Your Extension Starting ===");
        
        // Add your initialization logic here
        
        monitor.info("Your Extension initialized successfully");
    }

    @Override
    public void shutdown() {
        monitor.info("Shutting down Your Extension");
        // Add cleanup logic here
    }
}
```

### Service Registration File: `src/main/resources/META-INF/services/org.eclipse.edc.spi.system.ServiceExtension`

```
#################################################################################
#  Copyright (c) 2025 Your Company Name
#
#  See the NOTICE file(s) distributed with this work for additional
#  information regarding copyright ownership.
#
#  This program and the accompanying materials are made available under the
#  terms of the Apache License, Version 2.0 which is available at
#  https://www.apache.org/licenses/LICENSE-2.0.
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
#  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
#  License for the specific language governing permissions and limitations
#  under the License.
#
#  SPDX-License-Identifier: Apache-2.0
#################################################################################

org.eclipse.tractusx.edc.yourmodule.YourExtension
```

### Build File: `build.gradle.kts`

```kotlin
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

plugins {
    `maven-publish`
    `java-library`
}

dependencies {
    implementation(project(":core:core-utils"))
    implementation(libs.edc.spi.boot)
    implementation(libs.edc.spi.core)
    
    // Add your dependencies here
    
    testImplementation(libs.edc.junit)
}
```

---

## 📝 Template 2: JAX-RS Filter/Interceptor

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

package org.eclipse.tractusx.edc.yourmodule;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import org.eclipse.edc.spi.monitor.Monitor;

import java.io.IOException;

public class YourRequestFilter implements ContainerRequestFilter {

    private final Monitor monitor;

    public YourRequestFilter(Monitor monitor) {
        this.monitor = monitor;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String path = requestContext.getUriInfo().getPath();
        String method = requestContext.getMethod();
        
        monitor.debug("Filter called for: " + method + " " + path);
        
        // Add your filter logic here
        
        // To block a request:
        // requestContext.abortWith(
        //     Response.status(Response.Status.FORBIDDEN)
        //             .entity("{\"message\":\"Access denied\"}")
        //             .build()
        // );
    }
}
```

**Register the filter in your extension:**
```java
@Inject
private WebService webService;

@Override
public void initialize(ServiceExtensionContext context) {
    YourRequestFilter filter = new YourRequestFilter(context.getMonitor());
    webService.registerResource(ApiContext.MANAGEMENT, filter);
}
```

---

## 📝 Template 3: JAX-RS REST Controller

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

package org.eclipse.tractusx.edc.yourmodule;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.edc.spi.monitor.Monitor;

@Path("/your-api")
@Consumes({MediaType.APPLICATION_JSON})
@Produces({MediaType.APPLICATION_JSON})
public class YourApiController {

    private final Monitor monitor;

    public YourApiController(Monitor monitor) {
        this.monitor = monitor;
    }

    @GET
    @Path("/status")
    public Response getStatus() {
        return Response.ok()
                .entity("{\"status\":\"ok\"}")
                .build();
    }

    @POST
    @Path("/do-something")
    public Response doSomething(String requestBody) {
        monitor.info("Received request: " + requestBody);
        // Add your logic here
        return Response.ok()
                .entity("{\"result\":\"success\"}")
                .build();
    }
}
```

**Register the controller:**
```java
@Override
public void initialize(ServiceExtensionContext context) {
    YourApiController controller = new YourApiController(context.getMonitor());
    webService.registerResource(ApiContext.MANAGEMENT, controller);
}
```

---

## 🔍 Code Quality Checks

### 1. Check Indentation Automatically

```bash
# Run checkstyle to check indentation and formatting
./gradlew :edc-extensions:oauth2-hot-reload:checkstyleMain

# Run for all files in your extension
./gradlew :edc-extensions:oauth2-hot-reload:check
```

### 2. Check Headers Automatically

Checkstyle automatically validates headers based on `resources/java.header` and `resources/hashtag.header`.

**For Java files:** Must match `java.header` format
**For service files:** Must match `hashtag.header` format

### 3. Auto-Format Code

```bash
# If your IDE supports it, or use:
./gradlew :edc-extensions:oauth2-hot-reload:compileJava
```

**IntelliJ IDEA:**
- `Cmd+Option+L` (Mac) or `Ctrl+Alt+L` (Windows/Linux) to format
- Settings → Editor → Code Style → Import from `.editorconfig` (project has `tx-codestyle.editorconfig`)

---

## ✅ Checklist for New Extensions

- [ ] **Java file header** matches template (Apache 2.0 format)
- [ ] **Service file header** matches template (hash format)
- [ ] **Build file header** matches template
- [ ] **Class name** follows naming conventions (no abbreviations > 1 capital letter)
- [ ] **Indentation** is correct (4 spaces, checkstyle will catch this)
- [ ] **Service registration** file points to correct class name
- [ ] **Extension included** in runtime's `build.gradle.kts`
- [ ] **Dependencies** added to `build.gradle.kts`
- [ ] **Logs** confirm extension loads on startup

---

## 🚨 Common Issues & Fixes

### Issue: Extension Not Loading

**Check:**
1. Service file exists at: `src/main/resources/META-INF/services/org.eclipse.edc.spi.system.ServiceExtension`
2. Class name in service file matches actual class name exactly
3. Extension is included in runtime's dependencies

**Fix:**
```kotlin
// In runtime's build.gradle.kts (e.g., edc-controlplane/edc-controlplane-base/build.gradle.kts)
dependencies {
    implementation(project(":edc-extensions:oauth2-hot-reload"))
}
```

### Issue: Checkstyle Errors

**Check:**
```bash
./gradlew :edc-extensions:oauth2-hot-reload:checkstyleMain
```

**Common fixes:**
- **Indentation:** Use 4 spaces, not tabs
- **Headers:** Copy from template exactly
- **Variable names:** `signedJwt` not `signedJWT`, `oauthUrl` not `oauthURL`

### Issue: Build Fails with Missing Dependencies

**Check:**
```bash
./gradlew :edc-extensions:oauth2-hot-reload:dependencies
```

**Fix:** Add missing dependencies to `build.gradle.kts`:
```kotlin
dependencies {
    implementation(libs.jakarta.rsApi)  // For JAX-RS
    implementation(libs.nimbus.jwt)     // For JWT parsing
    // etc.
}
```

---

## 📚 Useful Commands

```bash
# Build only your extension
./gradlew :edc-extensions:oauth2-hot-reload:build

# Run checkstyle
./gradlew :edc-extensions:oauth2-hot-reload:checkstyleMain

# Run tests
./gradlew :edc-extensions:oauth2-hot-reload:test

# Clean and rebuild
./gradlew :edc-extensions:oauth2-hot-reload:clean :edc-extensions:oauth2-hot-reload:build

# Check if extension is included in runtime
grep -r "oauth2-hot-reload" edc-controlplane/
```

---

## 🎯 Best Practices

1. **Always check headers first** - Copy from template to avoid checkstyle errors
2. **Run checkstyle before committing** - Catch issues early
3. **Use Monitor for logging** - Don't use `System.out.println`
4. **Inject dependencies** - Use `@Inject` annotation
5. **Test locally first** - Run `./gradlew :edc-extensions:your-extension:build` before full build
6. **Check logs on startup** - Verify extension loads with `monitor.info()` at startup


