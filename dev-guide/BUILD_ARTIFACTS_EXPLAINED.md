# Build Artifacts Explained: Gradle → Binary → Docker → Kubernetes

## What Happens When You Run `gradlew run`

When you run `:edc-controlplane:edc-runtime-memory:run` from the Gradle panel:

```
1. Gradle compiles all Java source files → .class files
2. Gradle resolves all dependencies (JARs from Maven repositories)
3. Gradle creates a classpath with all dependencies
4. Gradle executes: java -cp [classpath] org.eclipse.edc.boot.system.runtime.BaseRuntime
5. Application runs directly in your JVM (development mode)
```

**This is NOT creating a binary** - it's running the application directly from compiled classes and dependencies.

---

## Build Artifacts: What Gets Created

### 1. **Regular JAR** (Module-only)
```
build/libs/edc-runtime-memory-0.12.0-SNAPSHOT.jar
```
- Contains only the compiled classes from `edc-runtime-memory` module
- **NOT executable standalone** - requires all dependencies to be on classpath
- Used as a library dependency for other modules

### 2. **Shadow JAR** (Fat JAR - **THIS IS THE BINARY**)
```
build/libs/edc-runtime-memory.jar
```
- Contains **ALL dependencies merged into a single JAR file**
- **This is a standalone executable binary**
- Can be run with: `java -jar edc-runtime-memory.jar`
- Created by the `shadow` plugin (Gradle Shadow Plugin)
- Size: Usually 50-200MB (includes all dependencies)

**How to build it:**
```bash
./gradlew :edc-controlplane:edc-runtime-memory:shadowJar
```

### 3. **Distribution Archives** (ZIP/TAR)
```
build/distributions/edc-runtime-memory-0.12.0-SNAPSHOT.zip
build/distributions/edc-runtime-memory-0.12.0-SNAPSHOT.tar
```
- Contains the Shadow JAR + startup scripts (Unix `.sh` and Windows `.bat`)
- Scripts set up the JVM args and classpath
- Can be extracted and run on any machine with Java installed

**How to build:**
```bash
./gradlew :edc-controlplane:edc-runtime-memory:distZip
# or
./gradlew :edc-controlplane:edc-runtime-memory:distTar
```

### 4. **Docker Image** (Container)
```
edc-runtime-memory:latest
edc-runtime-memory:0.12.0-SNAPSHOT
```
- Contains the Shadow JAR + OpenTelemetry agent + legal docs
- Based on `eclipse-temurin:24-jre-alpine` (Alpine Linux + JRE)
- Can be run with: `docker run edc-runtime-memory:latest`

**How to build:**
```bash
./gradlew :edc-controlplane:edc-runtime-memory:dockerize
```

**What's inside the Docker image:**
```
/app/
  ├── edc-runtime.jar          (Shadow JAR - the binary)
  ├── opentelemetry-javaagent.jar
  ├── SECURITY.md
  ├── NOTICE.md
  ├── DEPENDENCIES
  └── LICENSE
```

---

## Build Process Flow

```
Source Code (.java)
    ↓
[gradlew build]
    ↓
Compiled Classes (.class)
    ↓
[gradlew shadowJar]
    ↓
Shadow JAR (Fat JAR) ← THIS IS THE BINARY
    ↓
[gradlew dockerize]
    ↓
Docker Image (Container)
    ↓
[Helm/Kubernetes Deployment]
    ↓
Running Pod in Kubernetes
```

---

## Answering Your Questions

### Q: "Is this build binary?"
**A:** When you run `gradlew run`, **NO** - it runs directly from compiled classes.

When you run `gradlew shadowJar`, **YES** - it creates `edc-runtime-memory.jar`, which is a standalone executable binary.

### Q: "I know it's not deployed with Helm/Kubernetes/Docker"
**A:** Correct for development! But here's how it works:

1. **Development (what you're doing):**
   - `gradlew run` → Runs directly in JVM, no binary needed

2. **Production Deployment:**
   - `gradlew shadowJar` → Creates binary JAR
   - `gradlew dockerize` → Creates Docker image (contains the binary)
   - Helm chart → Deploys Docker image to Kubernetes

---

## How to Build the Binary

### Option 1: Build Shadow JAR Only
```bash
./gradlew :edc-controlplane:edc-runtime-memory:shadowJar
```
**Output:** `edc-controlplane/edc-runtime-memory/build/libs/edc-runtime-memory.jar`

**Run it:**
```bash
java -jar edc-controlplane/edc-runtime-memory/build/libs/edc-runtime-memory.jar \
  -Dedc.fs.config=/path/to/configuration.properties
```

### Option 2: Build Distribution (JAR + Scripts)
```bash
./gradlew :edc-controlplane:edc-runtime-memory:distZip
```
**Output:** `edc-controlplane/edc-runtime-memory/build/distributions/edc-runtime-memory-0.12.0-SNAPSHOT.zip`

**Extract and run:**
```bash
unzip edc-controlplane/edc-runtime-memory/build/distributions/edc-runtime-memory-0.12.0-SNAPSHOT.zip
cd edc-runtime-memory-0.12.0-SNAPSHOT
./bin/edc-runtime-memory
```

### Option 3: Build Docker Image
```bash
./gradlew :edc-controlplane:edc-runtime-memory:dockerize
```
**Output:** Docker image `edc-runtime-memory:latest`

**Run it:**
```bash
docker run -p 28080:28080 \
  -v /path/to/config.properties:/app/configuration.properties \
  edc-runtime-memory:latest
```

---

## What's in the Shadow JAR?

The Shadow JAR (fat JAR) contains:

1. **All your compiled classes** from `edc-runtime-memory`
2. **All classes from dependencies** (merged):
   - EDC Control Plane modules
   - EDC Data Plane modules
   - Your custom extensions (e.g., `oauth2-hot-reload`)
   - All third-party libraries (Jackson, Jetty, Nimbus JWT, etc.)
3. **META-INF/services files** (merged) - for ServiceLoader discovery
4. **Log4j2 plugins cache** (merged)

**Why "Shadow"?**
- The Gradle Shadow Plugin "shadows" (includes) all transitive dependencies
- Creates a single, self-contained JAR file
- No need for a separate `lib/` directory with JARs

---

## Summary

| Task | Creates | Type | Standalone? |
|------|---------|------|-------------|
| `gradlew run` | Nothing (runs in-process) | Development | No |
| `gradlew build` | Regular JAR | Library | No |
| `gradlew shadowJar` | **Fat JAR** | **Binary** | **Yes** ✅ |
| `gradlew distZip` | ZIP with JAR + scripts | Distribution | Yes |
| `gradlew dockerize` | Docker image (contains Shadow JAR) | Container | Yes |

**The Shadow JAR (`edc-runtime-memory.jar`) is the binary you're looking for!**


