# How to Update TrustedIssuerRegistry & Why settings.gradle.kts is Needed

## 🔄 How to Update the TrustedIssuerRegistry

### Implementation Summary

The `reloadTrustedIssuerRegistry()` method now:

1. **Gets the TrustedIssuerRegistry service** from `ServiceExtensionContext`
2. **Registers each issuer** using `registry.register(Issuer, credentialType)`
3. **Handles credential types** (converts `*` to `WILDCARD`)

### Code Implementation

```java
private void reloadTrustedIssuerRegistry(ServiceExtensionContext context, List<TrustedIssuerConfig> issuers) {
    if (trustedIssuerRegistry == null) {
        monitor.severe("TrustedIssuerRegistry is not available. Cannot reload issuers.");
        return;
    }

    // Add or update issuers
    for (TrustedIssuerConfig issuerConfig : issuers) {
        Issuer issuer = new Issuer(issuerConfig.id, Map.of());
        
        // Convert supportedTypes Set to List
        List<String> credentialTypes = new ArrayList<>(issuerConfig.supportedTypes);
        
        // If types contain "*", use WILDCARD
        if (credentialTypes.contains("*") || credentialTypes.isEmpty()) {
            credentialTypes = List.of(WILDCARD);
        }
        
        // Register each credential type
        for (String credentialType : credentialTypes) {
            trustedIssuerRegistry.register(issuer, credentialType);
        }
        
        registeredIssuerIds.add(issuerConfig.id);
        monitor.info("Registered trusted issuer: " + issuerConfig.id + " with types: " + credentialTypes);
    }
}
```

### Key Components

- **`TrustedIssuerRegistry`**: Service that manages trusted issuers
- **`Issuer`**: Represents an issuer with DID and properties
- **`WILDCARD`**: Constant for accepting all credential types
- **`register()`**: Method to add an issuer with specific credential types

---

## 📋 Why All Extensions Need to be in settings.gradle.kts

### What is settings.gradle.kts?

`settings.gradle.kts` is Gradle's **build configuration file** that tells Gradle:
- **Which modules exist** in your project
- **Where to find them** (directory structure)
- **How modules relate to each other** (project hierarchy)

### Why It's Required

#### 1. **Gradle Needs to Know About All Modules**

Gradle is a **multi-module build system**. When you run `./gradlew build`, Gradle needs to know:
- What modules to build
- What order to build them
- Which modules depend on which

**Without `settings.gradle.kts`:**
```
./gradlew :edc-extensions:trusted-issuers-hot-reload:build
> FAILURE: Project 'edc-extensions:trusted-issuers-hot-reload' not found
```

**With `settings.gradle.kts`:**
```kotlin
include(":edc-extensions:trusted-issuers-hot-reload")
```
```
./gradlew :edc-extensions:trusted-issuers-hot-reload:build
> SUCCESS: Build complete
```

#### 2. **Module Discovery**

When you add a new module folder (e.g., `edc-extensions/trusted-issuers-hot-reload/`), Gradle doesn't automatically discover it. You must explicitly tell Gradle about it:

```kotlin
// settings.gradle.kts
include(":edc-extensions:trusted-issuers-hot-reload")
//           ↑
// This tells Gradle: "There's a module at edc-extensions/trusted-issuers-hot-reload/"
```

#### 3. **Dependency Resolution**

When other modules depend on your extension:

```kotlin
// edc-controlplane/edc-controlplane-base/build.gradle.kts
dependencies {
    implementation(project(":edc-extensions:trusted-issuers-hot-reload"))
    //                                   ↑
    // This reference only works if the module is in settings.gradle.kts
}
```

**If not in `settings.gradle.kts`:**
```
> Could not resolve project :edc-extensions:trusted-issuers-hot-reload
```

#### 4. **Build Order**

Gradle determines **build order** based on dependencies and module structure. Without `settings.gradle.kts`, Gradle can't determine the correct build sequence.

---

## 📊 Visual Flow: How It All Works

```
┌─────────────────────────────────────────────────────────────┐
│  1. You create a new module folder                          │
│     edc-extensions/trusted-issuers-hot-reload/              │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  2. Add to settings.gradle.kts                              │
│     include(":edc-extensions:trusted-issuers-hot-reload")   │
│     ↑                                                       │
│     Gradle now knows about this module                     │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  3. Add to runtime dependencies                             │
│     edc-controlplane-base/build.gradle.kts:                 │
│     implementation(project(":edc-extensions:..."))          │
│     ↑                                                       │
│     Runtime will include this extension                    │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  4. Gradle builds everything                                │
│     • Compiles your extension                               │
│     • Includes it in runtime JAR                            │
│     • Makes it available at runtime                         │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  5. ServiceLoader discovers your extension                  │
│     • Reads META-INF/services/org.eclipse.edc...            │
│     • Finds your extension class                            │
│     • Calls initialize()                                    │
│     • Extension is now active! ✅                           │
└─────────────────────────────────────────────────────────────┘
```

---

## ✅ Complete Checklist for New Extension

When creating a new extension, you must:

1. **Create module folder structure**
   ```
   edc-extensions/my-extension/
   ├── build.gradle.kts
   └── src/main/...
   ```

2. **Add to `settings.gradle.kts`** ⚠️ **REQUIRED**
   ```kotlin
   include(":edc-extensions:my-extension")
   ```

3. **Add service registration file**
   ```
   src/main/resources/META-INF/services/org.eclipse.edc.spi.system.ServiceExtension
   ```

4. **Add to runtime dependencies** (if you want it loaded)
   ```kotlin
   // edc-controlplane/edc-controlplane-base/build.gradle.kts
   implementation(project(":edc-extensions:my-extension"))
   ```

5. **Refresh Gradle** (in IDE or `./gradlew --refresh-dependencies`)

---

## 🎯 Summary

### Why settings.gradle.kts is Needed

| Reason | Explanation |
|--------|-------------|
| **Module Discovery** | Gradle doesn't auto-discover modules. You must declare them. |
| **Dependency Resolution** | Other modules can't depend on your extension if Gradle doesn't know about it. |
| **Build Order** | Gradle needs to know module structure to determine build sequence. |
| **Classpath** | Gradle needs module info to set up compilation classpath. |

### How Registry Update Works

1. **Get service** from `ServiceExtensionContext`
2. **Create `Issuer` objects** from config
3. **Register each issuer** with `registry.register(issuer, credentialType)`
4. **Handle wildcards** (`*` → `WILDCARD`)
5. **Log changes** for audit

---

**Bottom Line:** `settings.gradle.kts` is like a **table of contents** for your project. Without it, Gradle doesn't know your extension exists, so it can't build it or include it in the runtime! 📚

