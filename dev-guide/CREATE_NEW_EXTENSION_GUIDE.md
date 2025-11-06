# 🚀 Creating a New Extension Module - Complete Guide

## 📋 Overview: What Gets Created

```
edc-extensions/
└── my-new-extension/                    ← New folder
    ├── build.gradle.kts                ← Build file (dependencies)
    ├── src/
    │   └── main/
    │       ├── java/
    │       │   └── org/eclipse/tractusx/edc/mynewextension/
    │       │       └── MyNewExtension.java    ← Your extension class
    │       └── resources/
    │           └── META-INF/
    │               └── services/
    │                   └── org.eclipse.edc.spi.system.ServiceExtension  ← Registration file
    └── README.md                        ← Optional documentation
```

---

## 🎯 Method 1: Using IntelliJ IDEA UI (Recommended for Beginners)

### Step 1: Create Module Folder Structure

**Option A: Right-Click in IntelliJ**
1. In IntelliJ, navigate to `edc-extensions/` in Project view
2. Right-click on `edc-extensions/` folder
3. Select: **New → Module**
4. Choose: **Java → Gradle → Java Library**
5. Module name: `my-new-extension`
6. Location: `/Users/princeangellos/Documents/Projects/tractusx-edc/edc-extensions/my-new-extension`
7. Click **Finish**

**⚠️ Note:** IntelliJ will create a basic structure, but you'll need to manually adjust files.

**Option B: Manual Creation (More Control)**
```bash
mkdir -p edc-extensions/my-new-extension/src/main/java/org/eclipse/tractusx/edc/mynewextension
mkdir -p edc-extensions/my-new-extension/src/main/resources/META-INF/services
mkdir -p edc-extensions/my-new-extension/src/test/java
```

---

### Step 2: Create `build.gradle.kts`

**Location:** `edc-extensions/my-new-extension/build.gradle.kts`

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
    // Core EDC dependencies
    implementation(project(":core:core-utils"))
    implementation(libs.edc.spi.boot)
    implementation(libs.edc.spi.core)
    
    // Add more dependencies as needed:
    // implementation(libs.edc.spi.web)           // For WebService
    // implementation(libs.jakarta.rsApi)         // For JAX-RS
    // implementation(libs.nimbus.jwt)            // For JWT
    
    // Test dependencies
    testImplementation(libs.edc.junit)
}
```

**📌 Key Points:**
- Always include `:core:core-utils` (common utilities)
- Include `libs.edc.spi.boot` and `libs.edc.spi.core` (minimum for extensions)
- Add specific dependencies based on what you need

---

### Step 3: Create Extension Java Class

**Location:** `edc-extensions/my-new-extension/src/main/java/org/eclipse/tractusx/edc/mynewextension/MyNewExtension.java`

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

package org.eclipse.tractusx.edc.mynewextension;

import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

/**
 * My New Extension
 * 
 * PURPOSE:
 * Brief description of what this extension does.
 */
@Extension(value = "My New Extension", categories = { "custom" })
public class MyNewExtension implements ServiceExtension {

    @Inject
    private Monitor monitor;

    @Override
    public String name() {
        return "My New Extension";
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        monitor.info("=== My New Extension Starting ===");
        
        // Add your initialization logic here
        // - Register services
        // - Load configuration
        // - Register REST endpoints
        // - Set up scheduled tasks
        
        monitor.info("My New Extension initialized successfully");
    }

    @Override
    public void shutdown() {
        monitor.info("Shutting down My New Extension");
        // Add cleanup logic here
    }
}
```

**📌 Important:**
- Package name: `org.eclipse.tractusx.edc.mynewextension` (lowercase, no dashes)
- Class name: `MyNewExtension` (PascalCase, no abbreviations with >1 capital)
- Must implement `ServiceExtension`
- Use `@Inject` for dependency injection

---

### Step 4: Create Service Registration File

**Location:** `edc-extensions/my-new-extension/src/main/resources/META-INF/services/org.eclipse.edc.spi.system.ServiceExtension`

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

org.eclipse.tractusx.edc.mynewextension.MyNewExtension
```

**📌 Important:**
- File name must be exactly: `org.eclipse.edc.spi.system.ServiceExtension`
- Last line: Full class name (package + class)
- No blank lines after the class name

---

### Step 5: Register Module in `settings.gradle.kts`

**Location:** `settings.gradle.kts`

Find the section with other extension includes (around line 56-99) and add:

```kotlin
include(":edc-extensions:token-interceptor")
include(":edc-extensions:oauth2-hot-reload")
include(":edc-extensions:my-new-extension")  // ← ADD THIS LINE
```

**📌 Format:**
- `include(":edc-extensions:your-module-name")`
- Use colons `:` to separate path segments
- Module name should match folder name

---

### Step 6: Add to Runtime Dependencies

**Location:** `edc-controlplane/edc-controlplane-base/build.gradle.kts`

Find the `dependencies` block (around line 34-75) and add:

```kotlin
dependencies {
    // ... existing dependencies ...
    implementation(project(":edc-extensions:token-interceptor"))
    implementation(project(":edc-extensions:oauth2-hot-reload"))
    implementation(project(":edc-extensions:my-new-extension"))  // ← ADD THIS LINE
    
    // ... rest of dependencies ...
}
```

**📌 Why This Matters:**
- Without this, your extension won't be loaded at runtime
- The runtime's classpath needs to include your extension JAR
- Only extensions listed here will be available when you run the connector

---

### Step 7: Refresh Gradle Project

**In IntelliJ IDEA:**
1. Click the **Gradle** tool window (usually on the right side)
2. Click the **🔄 Refresh** icon (or press `Cmd+Shift+O` / `Ctrl+Shift+O`)
3. Wait for Gradle sync to complete

**Or via Terminal:**
```bash
./gradlew --refresh-dependencies
```

**📌 What Happens:**
- Gradle discovers your new module from `settings.gradle.kts`
- Builds the module structure
- Makes it available to other modules

---

### Step 8: Verify & Test

**1. Build your extension:**
```bash
./gradlew :edc-extensions:my-new-extension:build
```

**2. Check for errors:**
```bash
./gradlew :edc-extensions:my-new-extension:checkstyleMain
```

**3. Run the runtime and check logs:**
```bash
./gradlew :edc-controlplane:edc-runtime-memory:run
```

**Look for this in the logs:**
```
INFO ... My New Extension Starting
INFO ... My New Extension initialized successfully
```

**4. Verify extension count:**
```
INFO ... 167 service extensions started  ← Should be +1 from before
```

---

## 📊 Visual Flow: What Happens When You Create an Extension

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. Create Files & Folder Structure                              │
│    └─> my-new-extension/                                        │
│        ├─ build.gradle.kts                                      │
│        └─ src/main/...                                          │
└─────────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────────┐
│ 2. Register in settings.gradle.kts                              │
│    └─> include(":edc-extensions:my-new-extension")              │
│    └─> Gradle now knows about your module                       │
└─────────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────────┐
│ 3. Add to Runtime Dependencies                                  │
│    └─> edc-controlplane-base/build.gradle.kts                  │
│    └─> implementation(project(":edc-extensions:my-new-extension"))│
└─────────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────────┐
│ 4. Build & Compile                                              │
│    └─> ./gradlew :edc-extensions:my-new-extension:build        │
│    └─> Creates JAR file in build/libs/                          │
└─────────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────────┐
│ 5. Runtime Loads Extension                                      │
│    └─> Reads META-INF/services/org.eclipse.edc.spi...          │
│    └─> Finds class: MyNewExtension                              │
│    └─> Calls: initialize()                                     │
│    └─> Extension is now active! ✅                              │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🔍 Quick Reference: File Locations

| File | Location | Purpose |
|------|----------|---------|
| **Extension Class** | `src/main/java/org/eclipse/tractusx/edc/mynewextension/MyNewExtension.java` | Your main extension logic |
| **Service Registration** | `src/main/resources/META-INF/services/org.eclipse.edc.spi.system.ServiceExtension` | Tells EDC to load your extension |
| **Build File** | `build.gradle.kts` | Defines dependencies |
| **Module Registration** | `settings.gradle.kts` | Makes Gradle aware of your module |
| **Runtime Inclusion** | `edc-controlplane/edc-controlplane-base/build.gradle.kts` | Makes extension available at runtime |

---

## ✅ Checklist

Before building, ensure:

- [ ] ✅ Folder structure created: `edc-extensions/my-new-extension/`
- [ ] ✅ `build.gradle.kts` created with proper header and dependencies
- [ ] ✅ Extension class created with proper header and implements `ServiceExtension`
- [ ] ✅ Service registration file created with full class name
- [ ] ✅ Added to `settings.gradle.kts`: `include(":edc-extensions:my-new-extension")`
- [ ] ✅ Added to runtime: `implementation(project(":edc-extensions:my-new-extension"))`
- [ ] ✅ Gradle project refreshed
- [ ] ✅ Build successful: `./gradlew :edc-extensions:my-new-extension:build`
- [ ] ✅ Checkstyle passes: `./gradlew :edc-extensions:my-new-extension:checkstyleMain`
- [ ] ✅ Extension loads: Check logs for startup message

---

## 🎨 IntelliJ IDEA Quick Actions

### Auto-Generate Package Structure

1. Right-click on `src/main/java`
2. Select: **New → Package**
3. Enter: `org.eclipse.tractusx.edc.mynewextension`
4. Press Enter

### Auto-Generate Class

1. Right-click on the package you created
2. Select: **New → Java Class**
3. Name: `MyNewExtension`
4. Select: **Interface** → Type: `ServiceExtension`
5. IntelliJ will auto-import and create stub

### Copy Header Template

1. Copy header from any existing extension (like `OauthHotReloadExtension.java`)
2. Paste at top of your file
3. Update copyright year/company if needed

---

## 🚨 Common Mistakes

### ❌ Wrong: Class name with abbreviations
```java
public class OAuthExtension  // ❌ OAuth has 2 consecutive capitals
```

### ✅ Correct:
```java
public class OauthExtension  // ✅ Only 1 capital per abbreviation
```

### ❌ Wrong: Package name with dashes
```java
package org.eclipse.tractusx.edc.my-new-extension;  // ❌ Dashes not allowed
```

### ✅ Correct:
```java
package org.eclipse.tractusx.edc.mynewextension;  // ✅ No dashes
```

### ❌ Wrong: Service file missing class name
```
org.eclipse.tractusx.edc.mynewextension  // ❌ Missing class name
```

### ✅ Correct:
```
org.eclipse.tractusx.edc.mynewextension.MyNewExtension  // ✅ Full class name
```

---

## 🎯 Example: Complete "Hello World" Extension

See `TEMPLATES.md` in this directory for complete working examples of:
- Basic extension
- JAX-RS filter
- REST controller
- Full build configurations

---

## 🆘 Troubleshooting

**Extension not loading?**
1. Check service file exists and has correct class name
2. Verify module is in `settings.gradle.kts`
3. Verify runtime dependency is added
4. Check logs for class loading errors
5. Rebuild: `./gradlew clean :edc-extensions:my-new-extension:build`

**Build fails?**
1. Check `build.gradle.kts` syntax
2. Verify dependencies exist in `libs.versions.toml`
3. Run: `./gradlew :edc-extensions:my-new-extension:dependencies`

**Checkstyle errors?**
1. Copy headers exactly from templates
2. Fix indentation (4 spaces)
3. Run: `./gradlew :edc-extensions:my-new-extension:checkstyleMain`


