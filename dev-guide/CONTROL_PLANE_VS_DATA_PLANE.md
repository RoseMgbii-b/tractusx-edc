# Control Plane vs Data Plane: What You're Running

## What is the Control Plane Runtime?

When you run `:edc-controlplane:edc-runtime-memory:run`, you're actually running **BOTH** the Control Plane AND Data Plane together in a single application!

Looking at the build configuration:

```kotlin
dependencies {
    runtimeOnly(project(":edc-controlplane:edc-controlplane-base"))  // ← Control Plane
    runtimeOnly(project(":edc-dataplane:edc-dataplane-base"))        // ← Data Plane (also included!)
    ...
}
```

So the "memory runtime" is actually a **combined runtime** that includes both planes.

---

## Control Plane Responsibilities

The **Control Plane** is the "brain" of the EDC connector. It handles:

### 1. **Resource Management (CRUD Operations)**
   - **Assets**: Define what data you want to share
   - **Policies**: Define access rules and conditions
   - **Contract Definitions**: Link assets + policies for offerings
   - **Business Partner Groups**: Manage BPNs and participants
   - **EDRs (Endpoint Data References)**: Track active data transfers

### 2. **Contract Negotiation**
   - **Contract Offers**: Create and manage contract offers
   - **Contract Negotiation**: Handle the negotiation process between providers and consumers
   - **Contract Agreements**: Finalize and store agreed contracts

### 3. **Transfer Management**
   - **Transfer Processes**: Initiate and monitor data transfers
   - **Transfer Coordination**: Coordinate with Data Plane instances
   - **Transfer State Management**: Track transfer status and lifecycle

### 4. **Management APIs**
   - **Management API** (`/api/management/*`): Human-facing REST API
   - **Protocol API** (`/protocol/*`): Connector-to-connector communication (DSP)
   - **Control API** (`/control/*`): Internal control endpoints

---

## Data Plane Responsibilities

The **Data Plane** does the "heavy lifting" of actual data transfer:

### 1. **Data Transfer Execution**
   - Receives transfer requests from Control Plane
   - Establishes actual data connections (HTTP, S3, Azure Blob, etc.)
   - Streams data from source to destination
   - Handles data encryption/decryption during transfer

### 2. **Data Plane APIs**
   - **Public API** (`/api/public/*`): Receives data from external sources
   - **Control API** (`/control/*`): Receives commands from Control Plane

### 3. **Transfer Protocols**
   - HTTP/HTTPS data transfer
   - HttpProxy (for pushing data)
   - Streaming and chunking large files

---

## What APIs Are Available in Your Current Setup?

When you run the memory runtime, you get access to:

### Control Plane APIs (Management & Coordination)

| Endpoint | Purpose | Example |
|----------|---------|---------|
| `GET /api/management/v3/assets` | List assets | View all data assets |
| `POST /api/management/v3/assets` | Create asset | Define new data asset |
| `GET /api/management/v3/policydefinitions` | List policies | View access policies |
| `POST /api/management/v3/policydefinitions` | Create policy | Define access rules |
| `GET /api/management/v3/contractdefinitions` | List contract definitions | View contract offerings |
| `POST /api/management/v3/contractdefinitions` | Create contract definition | Create new offering |
| `GET /api/management/v3/transferprocesses` | List transfers | Monitor data transfers |
| `POST /api/management/v3/transferprocesses` | Initiate transfer | Start data transfer |
| `GET /api/management/v3/business-partner-groups` | List BPNs | View business partners |

### Protocol APIs (Connector-to-Connector)

| Endpoint | Purpose |
|----------|---------|
| `POST /protocol/v2/catalog/request` | Request catalog from another connector |
| `POST /protocol/v2/contractnegotiations/{id}` | Initiate contract negotiation |
| `GET /protocol/v2/contractnegotiations/{id}` | Get negotiation status |

### Data Plane APIs (Actual Data Transfer)

| Endpoint | Purpose |
|----------|---------|
| `POST /api/public/{path}` | Receive data (push mode) |
| `GET /api/public/{path}` | Serve data (pull mode) |
| `POST /control/token` | Validate transfer tokens |

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│              Your Memory Runtime (Single Process)            │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │           CONTROL PLANE                              │  │
│  │                                                      │  │
│  │  • Management API (port 28080)                      │  │
│  │    - /api/management/v3/assets                      │  │
│  │    - /api/management/v3/policies                    │  │
│  │    - /api/management/v3/contractdefinitions         │  │
│  │    - /api/management/v3/transferprocesses           │  │
│  │                                                      │  │
│  │  • Protocol API (port 28081)                        │  │
│  │    - /protocol/v2/catalog/request                   │  │
│  │    - /protocol/v2/contractnegotiations              │  │
│  │                                                      │  │
│  │  • Control API (port 28082)                         │  │
│  │    - /control/token                                 │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │           DATA PLANE (included)                      │  │
│  │                                                      │  │
│  │  • Public API (usually separate port)                │  │
│  │    - /api/public/*                                   │  │
│  │                                                      │  │
│  │  • Control API (shared with Control Plane)           │  │
│  │    - Receives transfer commands                      │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │           IN-MEMORY STORES                           │  │
│  │                                                      │  │
│  │  • Assets (in-memory)                                │  │
│  │  • Policies (in-memory)                              │  │
│  │  • Contracts (in-memory)                             │  │
│  │  • Vault (in-memory)                                 │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

---

## What You Can Test with Just the Memory Runtime

✅ **You CAN test:**
- Creating/reading/updating/deleting assets
- Creating/reading/updating/deleting policies
- Creating/reading contract definitions
- Initiating transfer processes
- Contract negotiations (with another connector)
- Business partner management
- OAuth2 authentication (Management API)
- RBAC (Role-Based Access Control)

❌ **You CANNOT fully test:**
- **Actual data transfer** (requires a separate Data Plane instance or external data source)
- **Multi-instance scenarios** (Control Plane and Data Plane on different machines)
- **Production-like data streaming** (large files, high throughput)

---

## Production Architecture (Separate Planes)

In production, Control Plane and Data Plane are typically **separate applications**:

```
┌─────────────────────────────────┐      ┌─────────────────────────────────┐
│      CONTROL PLANE              │      │      DATA PLANE                 │
│   (Separate Process/Pod)        │      │   (Separate Process/Pod)        │
│                                  │      │                                  │
│  • Management API                │──────▶│  • Public API                   │
│  • Protocol API                  │      │  • Control API                  │
│  • Contract Negotiation          │      │  • Actual Data Transfer         │
│  • Transfer Coordination         │      │                                  │
│                                  │      │                                  │
│  Database: PostgreSQL            │      │  Vault: HashiCorp Vault         │
│  Vault: HashiCorp Vault          │      │                                  │
└─────────────────────────────────┘      └─────────────────────────────────┘
```

**Why separate?**
- **Scalability**: Scale Data Plane independently (multiple instances)
- **Security**: Data Plane can be isolated from control logic
- **Performance**: Data Plane doesn't need to know about contracts/policies

---

## Summary

**What you're running:**
- A combined Control Plane + Data Plane runtime
- All APIs available in a single process
- In-memory storage (data lost on restart)
- Perfect for development and API testing

**What you're testing:**
- Management operations (CRUD on assets, policies, contracts)
- Contract negotiation workflows
- Transfer process initiation
- OAuth2/RBAC authentication

**What you're NOT testing:**
- Actual large-scale data transfers
- Production-like multi-instance deployments
- Persistent storage (PostgreSQL)

---

## Quick Reference: APIs You're Using

```bash
# Management API (Human-facing)
curl -X GET http://localhost:28080/api/management/v3/assets \
  -H "Authorization: Bearer <JWT_TOKEN>"

# Protocol API (Connector-to-connector)
curl -X POST http://localhost:28081/protocol/v2/catalog/request \
  -H "Content-Type: application/json" \
  -d '{"counterPartyAddress": "http://other-connector:28081/protocol"}'

# Control API (Internal)
curl -X POST http://localhost:28082/control/token \
  -H "Content-Type: application/json" \
  -d '{"token": "..."}'
```

---

**Bottom Line:** You're running a **combined Control Plane + Data Plane** runtime, which gives you full API access for testing management operations, but data is stored in-memory and will be lost on restart.

