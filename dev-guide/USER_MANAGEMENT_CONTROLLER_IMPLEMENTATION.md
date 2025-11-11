# User Management Controller Implementation Guide

## 🎯 Overview

This guide explains how to implement a **User Management Controller** in the Tractus-X EDC connector that allows administrators to create, update, and disable users via REST API endpoints. The controller integrates with Keycloak's Admin API to perform user management operations.

---

## ❓ Why a New Extension is Needed

**Yes, a new extension is required** to add user management capabilities to the EDC connector. Here's why:

### Current State
- The EDC connector currently **does not have** user management REST endpoints
- User management is handled **directly in Keycloak** (via Keycloak Admin Console or Keycloak Admin API)
- There's no programmatic way for admins to manage users through the EDC Management API

### What the Extension Provides
1. **REST API Endpoints** in EDC connector (`/api/management/v3/users`)
2. **Admin Interface** - Allows admins to manage users via EDC API instead of going to Keycloak
3. **Integration Layer** - Bridges EDC Management API with Keycloak Admin API
4. **Consistent API** - Follows EDC's API patterns and conventions

### How It Works

```
┌─────────────────────────────────────────────────────────────────┐
│  Admin User (with ADMIN role)                                   │
│  Makes HTTP request to EDC connector                            │
└─────────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────────┐
│  EDC Connector - NEW Extension                                  │
│                                                                  │
│  POST /api/management/v3/users                                  │
│  Authorization: Bearer <admin-jwt-token>                        │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  UserManagementApiV3Controller                           │  │
│  │  • Validates admin JWT token                             │  │
│  │  • Validates request data                                │  │
│  │  • Calls KeycloakAdminApiService                         │  │
│  └──────────────────────────────────────────────────────────┘  │
│                          ↓                                       │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  KeycloakAdminApiService (Interface/Service)             │  │
│  │  • Authenticates with Keycloak (service account)         │  │
│  │  • Makes HTTP POST to Keycloak Admin API                 │  │
│  │  • POST https://keycloak.../admin/realms/{realm}/users  │  │
│  │  • Handles Keycloak responses                            │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────────┐
│  Keycloak Admin API (External Service)                          │
│                                                                  │
│  • Receives HTTP request from EDC connector                    │
│  • Validates service account token                             │
│  • Creates/updates/disables user in Keycloak database          │
│  • Returns user data or error                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Key Points

1. **EDC Extension = API Gateway**: The extension acts as a gateway that exposes user management endpoints in the EDC connector
2. **KeycloakAdminApiService = HTTP Client**: This service makes HTTP calls to Keycloak's Admin REST API (it's an interface/adapter to Keycloak)
3. **Keycloak = Source of Truth**: Keycloak is where users are actually stored and managed
4. **No Local Storage**: The EDC connector does NOT store user data - it only proxies requests to Keycloak

### Benefits of This Approach

✅ **Unified API**: Admins can manage users through EDC API instead of switching to Keycloak  
✅ **Consistent Authentication**: Uses same JWT tokens as other EDC Management API endpoints  
✅ **Role-Based Access**: Leverages existing `RoleBasedAccessFilter` for admin-only access  
✅ **No Data Duplication**: Users remain in Keycloak (single source of truth)  
✅ **Follows EDC Patterns**: Uses same extension structure as other EDC APIs  

---

## 📊 Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                    EDC Connector                                 │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  Management API                                          │  │
│  │  /api/management/v3/users                                │  │
│  │                                                           │  │
│  │  ┌────────────────────────────────────────────────────┐  │  │
│  │  │  UserManagementApiV3Controller                     │  │  │
│  │  │  • POST   /users          (Create user)            │  │  │
│  │  │  • PUT    /users/{id}     (Update user)            │  │  │
│  │  │  • PUT    /users/{id}/disable (Disable user)       │  │  │
│  │  │  • GET    /users/{id}     (Get user)               │  │  │
│  │  │  • GET    /users          (List users)             │  │  │
│  │  └────────────────────────────────────────────────────┘  │  │
│  │           │                                               │  │
│  │           │ Uses                                          │  │
│  │           ↓                                               │  │
│  │  ┌────────────────────────────────────────────────────┐  │  │
│  │  │  KeycloakAdminApiService                           │  │  │
│  │  │  • createUser()                                    │  │  │
│  │  │  • updateUser()                                    │  │  │
│  │  │  • disableUser()                                   │  │  │
│  │  │  • getUser()                                       │  │  │
│  │  │  • listUsers()                                     │  │  │
│  │  └────────────────────────────────────────────────────┘  │  │
│  └──────────────────────────────────────────────────────────┘  │
│           │                                                     │
│           │ HTTP Requests                                       │
│           ↓                                                     │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  RoleBasedAccessFilter                                   │  │
│  │  • Validates JWT token                                   │  │
│  │  • Checks for ADMIN role                                 │  │
│  │  • Blocks non-admin requests                             │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
           │
           │ HTTPS
           ↓
┌─────────────────────────────────────────────────────────────────┐
│  Keycloak Admin API                                             │
│  https://keycloak.../admin/realms/{realm}/users                │
│                                                                  │
│  • Requires admin credentials or service account                │
│  • Performs actual user CRUD operations                         │
│  • Returns user data                                            │
└─────────────────────────────────────────────────────────────────┘
```

**Key Components:**
1. **Controller** - REST endpoints for user management operations
2. **Service** - Business logic and Keycloak Admin API integration
3. **Filter** - Role-based access control (admin-only)
4. **Keycloak Admin API** - Actual user management backend

---

## 🏗️ Implementation Structure

### Module Structure

```
edc-extensions/
└── user-management-api/
    ├── build.gradle.kts
    ├── src/
    │   ├── main/
    │   │   ├── java/
    │   │   │   └── org/eclipse/tractusx/edc/usermanagement/
    │   │   │       ├── UserManagementExtension.java
    │   │   │       ├── api/
    │   │   │       │   ├── v3/
    │   │   │       │   │   ├── UserManagementApiV3Controller.java
    │   │   │       │   │   └── UserManagementApiV3.java
    │   │   │       │   └── base/
    │   │   │       │       └── BaseUserManagementApiController.java
    │   │   │       ├── service/
    │   │   │       │   └── KeycloakAdminApiService.java
    │   │   │       └── model/
    │   │   │           ├── UserDto.java
    │   │   │           └── CreateUserRequest.java
    │   │   └── resources/
    │   │       └── META-INF/
    │   │           └── services/
    │   │               └── org.eclipse.edc.spi.system.ServiceExtension
    │   └── test/
    │       └── java/
    │           └── org/eclipse/tractusx/edc/usermanagement/
    │               └── UserManagementApiV3ControllerTest.java
```

---

## 📝 Step-by-Step Implementation

### Step 1: Create Extension Module

Follow the guide in `CREATE_NEW_EXTENSION_GUIDE.md` to create a new extension module named `user-management-api`.

**Key Points:**
- Module name: `user-management-api`
- Package: `org.eclipse.tractusx.edc.usermanagement`
- Register in `settings.gradle.kts`
- Add to runtime dependencies in `edc-controlplane-base/build.gradle.kts`

---

### Step 2: Configure Dependencies

**File:** `edc-extensions/user-management-api/build.gradle.kts`

```kotlin
dependencies {
    // Core EDC dependencies
    implementation(project(":core:core-utils"))
    implementation(libs.edc.spi.boot)
    implementation(libs.edc.spi.core)
    implementation(libs.edc.spi.web)
    
    // JAX-RS for REST endpoints
    implementation(libs.jakarta.rsApi)
    
    // JSON processing
    implementation(libs.jakarta.jsonApi)
    
    // HTTP client for Keycloak Admin API
    implementation(libs.okhttp3)  // or use Java 11+ HttpClient
    
    // JWT handling (if needed for service account auth)
    implementation(libs.nimbus.jwt)
    
    // Test dependencies
    testImplementation(libs.edc.junit)
    testImplementation(libs.mockito.core)
}
```

---

### Step 3: Create Data Transfer Objects (DTOs)

**File:** `src/main/java/org/eclipse/tractusx/edc/usermanagement/model/UserDto.java`

```java
package org.eclipse.tractusx.edc.usermanagement.model;

import jakarta.json.JsonObject;

/**
 * Data Transfer Object for user information
 */
public class UserDto {
    private String id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private boolean enabled;
    private boolean emailVerified;
    private java.util.List<String> roles;
    
    // Constructors, getters, setters
    // toJson() method to convert to JsonObject
    // fromJson() static method to parse from JsonObject
}
```

**File:** `src/main/java/org/eclipse/tractusx/edc/usermanagement/model/CreateUserRequest.java`

```java
package org.eclipse.tractusx.edc.usermanagement.model;

import jakarta.json.JsonObject;

/**
 * Request DTO for creating a new user
 */
public class CreateUserRequest {
    private String username;
    private String email;
    private String password;
    private String firstName;
    private String lastName;
    private boolean enabled;
    private boolean emailVerified;
    private java.util.List<String> roles;
    
    // Constructors, getters, setters
    // fromJson() static method to parse from JsonObject
    // validate() method for input validation
}
```

---

### Step 4: Create Keycloak Admin API Service

**File:** `src/main/java/org/eclipse/tractusx/edc/usermanagement/service/KeycloakAdminApiService.java`

**Purpose:** This is an **HTTP client interface** that makes REST API calls to Keycloak's Admin API. It acts as an adapter/bridge between EDC and Keycloak.

**What It Does:**
- Makes HTTP requests to Keycloak Admin REST API endpoints
- Handles authentication with Keycloak (service account)
- Converts EDC domain objects to Keycloak API format
- Converts Keycloak responses back to EDC domain objects
- Handles errors and maps them to domain exceptions

**Key Responsibilities:**
- Authenticate with Keycloak (using service account or admin credentials)
- Create users via Keycloak Admin API HTTP calls
- Update user information via HTTP PUT requests
- Enable/disable users via HTTP PUT requests
- Retrieve user information via HTTP GET requests
- List users via HTTP GET requests
- Handle HTTP errors and map to domain exceptions

**Implementation Approach:**

1. **Authentication with Keycloak:**
   - Use service account (client credentials OAuth2 flow) to get admin access token
   - Make HTTP POST to: `POST /realms/{realm}/protocol/openid-connect/token`
   - Store token with expiration time
   - Refresh token when expired (before making API calls)

2. **Keycloak Admin API HTTP Endpoints (that this service calls):**
   ```
   POST   /admin/realms/{realm}/users              - Create user
   GET    /admin/realms/{realm}/users              - List users
   GET    /admin/realms/{realm}/users/{id}         - Get user
   PUT    /admin/realms/{realm}/users/{id}         - Update user
   PUT    /admin/realms/{realm}/users/{id}/reset-password - Reset password
   ```

3. **Complete Implementation:**
   
   **📖 See `KEYCLOAK_ADMIN_API_SERVICE_IMPLEMENTATION.md` for the full production-ready implementation** with:
   - Complete CRUD operations (create, update, disable, get, list)
   - Thread-safe token management
   - Comprehensive error handling
   - Input validation
   - Role management
   - All helper methods implemented
   
   **Quick Overview:**
   ```java
   public class KeycloakAdminApiService {
       private final String keycloakUrl;
       private final String realm;
       private final HttpClient httpClient;
       private final ReentrantLock tokenLock; // Thread-safe token management
       private volatile String accessToken;
       private volatile Instant tokenExpiresAt;
       
       public Result<UserDto> createUser(CreateUserRequest request) {
           // 1. Validate input
           // 2. Ensure valid token (thread-safe)
           // 3. Convert to Keycloak format
           // 4. Make HTTP POST to Keycloak
           // 5. Handle response and convert to UserDto
       }
       
       // Additional methods: updateUser, disableUser, getUser, listUsers, assignRoles
       // Token management: ensureValidToken(), refreshToken()
       // Helpers: parseUserResponse(), handleErrorResponse(), etc.
   }
   ```

4. **Error Handling:**
   - Map Keycloak HTTP errors to domain exceptions
   - Handle 409 (Conflict) for duplicate users → `ObjectConflictException`
   - Handle 404 (Not Found) for missing users → `ObjectNotFoundException`
   - Handle 401/403 for authentication/authorization errors → `AuthenticationException`

**Configuration Properties:**
```properties
# Keycloak Admin API Configuration
edc.usermanagement.keycloak.admin.url=https://keycloak.example.com
edc.usermanagement.keycloak.realm=your-realm
edc.usermanagement.keycloak.admin.client.id=admin-cli
edc.usermanagement.keycloak.admin.client.secret=your-secret
```

---

### Step 5: Create Base Controller

**File:** `src/main/java/org/eclipse/tractusx/edc/usermanagement/api/base/BaseUserManagementApiController.java`

**Purpose:** Contains shared business logic that can be reused across API versions.

**Key Methods:**
- `createUser(JsonObject request)` - Validates request and calls service
- `updateUser(String userId, JsonObject request)` - Validates and updates user
- `disableUser(String userId)` - Disables a user account
- `getUser(String userId)` - Retrieves user information
- `listUsers()` - Lists all users (with pagination support)

**Error Handling:**
- Use `Result<T>` pattern for error handling
- Map service exceptions to appropriate HTTP responses
- Validate input data before processing

---

### Step 6: Create Versioned Controller

**File:** `src/main/java/org/eclipse/tractusx/edc/usermanagement/api/v3/UserManagementApiV3Controller.java`

**Purpose:** Implements the v3 API contract with JAX-RS annotations.

**Routes:**
```java
@Path("/v3/users")
@Consumes({ MediaType.APPLICATION_JSON })
@Produces({ MediaType.APPLICATION_JSON })
public class UserManagementApiV3Controller extends BaseUserManagementApiController {
    
    @POST
    public Response createUser(JsonObject request) {
        // Create user logic
    }
    
    @PUT
    @Path("/{userId}")
    public Response updateUser(@PathParam("userId") String userId, JsonObject request) {
        // Update user logic
    }
    
    @PUT
    @Path("/{userId}/disable")
    public Response disableUser(@PathParam("userId") String userId) {
        // Disable user logic
    }
    
    @GET
    @Path("/{userId}")
    public Response getUser(@PathParam("userId") String userId) {
        // Get user logic
    }
    
    @GET
    public Response listUsers(@QueryParam("first") Integer first, 
                              @QueryParam("max") Integer max) {
        // List users logic
    }
}
```

**Response Format:**
- Success: HTTP 200/201 with JSON body
- Error: HTTP 4xx/5xx with error JSON
- Use consistent error response format matching EDC patterns

---

### Step 7: Create API Interface (Optional)

**File:** `src/main/java/org/eclipse/tractusx/edc/usermanagement/api/v3/UserManagementApiV3.java`

**Purpose:** Defines the API contract (useful for OpenAPI/Swagger documentation).

```java
public interface UserManagementApiV3 {
    JsonObject createUser(JsonObject request);
    JsonObject updateUser(String userId, JsonObject request);
    void disableUser(String userId);
    JsonObject getUser(String userId);
    JsonObject listUsers(Integer first, Integer max);
}
```

---

### Step 8: Register Controller in Extension

**File:** `src/main/java/org/eclipse/tractusx/edc/usermanagement/UserManagementExtension.java`

**Key Steps:**
1. Inject required services (WebService, Monitor, Configuration)
2. Create KeycloakAdminApiService instance
3. Create controller instance
4. Register controller with WebService

```java
@Extension(value = "User Management API", categories = { "api", "management" })
public class UserManagementExtension implements ServiceExtension {
    
    @Inject
    private WebService webService;
    
    @Inject
    private Monitor monitor;
    
    @Inject
    private ServiceExtensionContext context;
    
    @Override
    public void initialize(ServiceExtensionContext context) {
        // Read configuration
        String keycloakUrl = context.getSetting("edc.usermanagement.keycloak.admin.url", null);
        String realm = context.getSetting("edc.usermanagement.keycloak.realm", null);
        // ... other config
        
        // Create service
        KeycloakAdminApiService keycloakService = new KeycloakAdminApiService(
            keycloakUrl, realm, /* other params */
            monitor
        );
        
        // Create controller
        BaseUserManagementApiController controller = 
            new UserManagementApiV3Controller(keycloakService, monitor);
        
        // Register with WebService
        webService.registerResource("management", controller);
        
        monitor.info("User Management API v3 registered at /api/management/v3/users");
    }
}
```

---

### Step 9: Configure Role-Based Access Control

**File:** `edc-extensions/oauth2-hot-reload/src/main/java/org/eclipse/tractusx/edc/oauth2/hotreload/RoleBasedAccessFilter.java`

**Add to PATH_ROLE_MAP:**
```java
private static final Map<String, Set<String>> PATH_ROLE_MAP = Map.of(
    // ... existing paths ...
    "v3/users", Set.of("ADMIN")  // ← ADD THIS LINE
);
```

**Security Considerations:**
- Only users with `ADMIN` role can access user management endpoints
- GET requests might be allowed for `USER` role (read-only access)
- All write operations (POST, PUT, DELETE) require `ADMIN` role

---

## 🔒 Security Considerations

### 1. Authentication & Authorization

**Admin-Only Access:**
- All user management endpoints must require `ADMIN` role
- Configure `RoleBasedAccessFilter` to enforce this
- Validate JWT token on every request

**Service Account for Keycloak:**
- Use a dedicated service account (client credentials) to call Keycloak Admin API
- Store credentials securely (environment variables, secrets manager)
- Never expose service account credentials in code or logs

### 2. Input Validation

**Validate All Inputs:**
- Email format validation
- Username format validation (alphanumeric, no special chars)
- Password strength requirements
- Role names validation (prevent privilege escalation)

**Sanitize Inputs:**
- Prevent SQL injection (if using database)
- Prevent XSS attacks in user data
- Validate JSON structure before processing

### 3. Error Handling

**Don't Leak Information:**
- Don't expose Keycloak internal errors to API consumers
- Don't reveal whether a user exists (security through obscurity)
- Return generic error messages for authentication failures

**Logging:**
- Log all user management operations (audit trail)
- Include admin user ID in logs
- Don't log sensitive data (passwords, tokens)

### 4. Rate Limiting

**Prevent Abuse:**
- Implement rate limiting on user creation endpoints
- Prevent brute force attacks
- Limit concurrent requests per admin user

---

## 📋 API Endpoints Specification

### 1. Create User

**Endpoint:** `POST /api/management/v3/users`

**Request Body:**
```json
{
  "username": "john.doe",
  "email": "john.doe@example.com",
  "password": "SecurePassword123!",
  "firstName": "John",
  "lastName": "Doe",
  "enabled": true,
  "emailVerified": false,
  "roles": ["USER"]
}
```

**Response (201 Created):**
```json
{
  "id": "user-uuid-123",
  "username": "john.doe",
  "email": "john.doe@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "enabled": true,
  "emailVerified": false,
  "roles": ["USER"],
  "createdAt": "2025-01-15T10:30:00Z"
}
```

**Error Responses:**
- `400 Bad Request` - Invalid input data
- `409 Conflict` - User already exists
- `403 Forbidden` - Insufficient permissions
- `500 Internal Server Error` - Keycloak error

---

### 2. Update User

**Endpoint:** `PUT /api/management/v3/users/{userId}`

**Request Body:**
```json
{
  "email": "newemail@example.com",
  "firstName": "John",
  "lastName": "Smith",
  "roles": ["USER", "ASSET_MANAGER"]
}
```

**Response (200 OK):**
```json
{
  "id": "user-uuid-123",
  "username": "john.doe",
  "email": "newemail@example.com",
  "firstName": "John",
  "lastName": "Smith",
  "enabled": true,
  "emailVerified": false,
  "roles": ["USER", "ASSET_MANAGER"],
  "updatedAt": "2025-01-15T11:00:00Z"
}
```

**Error Responses:**
- `400 Bad Request` - Invalid input data
- `404 Not Found` - User not found
- `403 Forbidden` - Insufficient permissions
- `500 Internal Server Error` - Keycloak error

---

### 3. Disable User

**Endpoint:** `PUT /api/management/v3/users/{userId}/disable`

**Request Body:** (empty or optional reason)
```json
{
  "reason": "Security violation"
}
```

**Response (200 OK):**
```json
{
  "id": "user-uuid-123",
  "username": "john.doe",
  "enabled": false,
  "disabledAt": "2025-01-15T12:00:00Z",
  "disabledBy": "admin-user-id"
}
```

**Error Responses:**
- `404 Not Found` - User not found
- `403 Forbidden` - Insufficient permissions
- `500 Internal Server Error` - Keycloak error

---

### 4. Get User

**Endpoint:** `GET /api/management/v3/users/{userId}`

**Response (200 OK):**
```json
{
  "id": "user-uuid-123",
  "username": "john.doe",
  "email": "john.doe@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "enabled": true,
  "emailVerified": true,
  "roles": ["USER"],
  "createdAt": "2025-01-15T10:30:00Z"
}
```

**Error Responses:**
- `404 Not Found` - User not found
- `403 Forbidden` - Insufficient permissions

---

### 5. List Users

**Endpoint:** `GET /api/management/v3/users?first=0&max=20`

**Query Parameters:**
- `first` (optional) - First result index (default: 0)
- `max` (optional) - Maximum results (default: 20, max: 100)
- `search` (optional) - Search by username or email

**Response (200 OK):**
```json
{
  "users": [
    {
      "id": "user-uuid-123",
      "username": "john.doe",
      "email": "john.doe@example.com",
      "enabled": true,
      "roles": ["USER"]
    }
  ],
  "total": 150,
  "first": 0,
  "max": 20
}
```

---

## 🧪 Testing Strategy

### Unit Tests

**Test Controller:**
- Mock `KeycloakAdminApiService`
- Test request validation
- Test error handling
- Test response formatting

**Test Service:**
- Mock HTTP client for Keycloak
- Test authentication flow
- Test error mapping
- Test retry logic

### Integration Tests

**Test with Keycloak:**
- Use test Keycloak instance
- Test full user lifecycle (create → update → disable)
- Test error scenarios (duplicate user, invalid data)
- Test authentication/authorization

### Manual Testing

**Test Checklist:**
- [ ] Create user with valid data
- [ ] Create user with duplicate email (should fail)
- [ ] Update user information
- [ ] Disable user account
- [ ] Try to access as non-admin (should fail)
- [ ] List users with pagination
- [ ] Search users

---

## 🔧 Configuration

### Required Configuration Properties

**File:** `configuration/controlplane.properties` (or environment variables)

```properties
# Keycloak Admin API Configuration
edc.usermanagement.keycloak.admin.url=https://keycloak.example.com
edc.usermanagement.keycloak.realm=your-realm
edc.usermanagement.keycloak.admin.client.id=admin-cli
edc.usermanagement.keycloak.admin.client.secret=${KEYCLOAK_ADMIN_SECRET}

# Optional: Service account token cache TTL (seconds)
edc.usermanagement.keycloak.token.cache.ttl=300

# Optional: HTTP client timeouts (milliseconds)
edc.usermanagement.keycloak.http.connect.timeout=5000
edc.usermanagement.keycloak.http.read.timeout=10000
```

### Environment Variables

```bash
export KEYCLOAK_ADMIN_URL=https://keycloak.example.com
export KEYCLOAK_REALM=your-realm
export KEYCLOAK_ADMIN_CLIENT_ID=admin-cli
export KEYCLOAK_ADMIN_CLIENT_SECRET=your-secret
```

---

## 🚀 Deployment Considerations

### 1. Keycloak Setup

**Create Service Account:**
1. Create a new client in Keycloak
2. Enable "Service Accounts Enabled"
3. Assign `realm-admin` role to service account
4. Get client ID and secret

**Realm Configuration:**
- Ensure user registration is configured appropriately
- Set up email verification if needed
- Configure password policies

### 2. Network Configuration

**Firewall Rules:**
- EDC connector must be able to reach Keycloak Admin API
- Use HTTPS for all Keycloak communication
- Consider using internal network for Keycloak access

### 3. Monitoring

**Metrics to Monitor:**
- User creation rate
- Failed authentication attempts
- Keycloak API response times
- Error rates by endpoint

**Logging:**
- Log all user management operations
- Include admin user ID and target user ID
- Log Keycloak API errors

---

## 📚 Related Documentation

- `CREATE_NEW_EXTENSION_GUIDE.md` - How to create a new extension
- `USER_MANAGEMENT_GUIDE.md` - Overview of user management in EDC
- **`KEYCLOAK_ADMIN_API_SERVICE_IMPLEMENTATION.md`** - **Complete production-ready implementation of KeycloakAdminApiService**
- `OAUTH2_HOT_RELOAD_README.md` - OAuth2 and JWT validation
- Keycloak Admin REST API: https://www.keycloak.org/docs-api/latest/rest-api/

---

## ✅ Implementation Checklist

### Phase 1: Setup
- [ ] Create extension module structure
- [ ] Configure dependencies in `build.gradle.kts`
- [ ] Register module in `settings.gradle.kts`
- [ ] Add to runtime dependencies

### Phase 2: Core Implementation
- [ ] Create DTOs (UserDto, CreateUserRequest)
- [ ] Implement KeycloakAdminApiService
- [ ] Create BaseUserManagementApiController
- [ ] Create UserManagementApiV3Controller
- [ ] Implement UserManagementExtension

### Phase 3: Security
- [ ] Configure RoleBasedAccessFilter for admin-only access
- [ ] Implement input validation
- [ ] Add error handling
- [ ] Configure service account authentication

### Phase 4: Testing
- [ ] Write unit tests for controller
- [ ] Write unit tests for service
- [ ] Write integration tests
- [ ] Manual testing with Keycloak

### Phase 5: Documentation & Deployment
- [ ] Document API endpoints
- [ ] Configure properties
- [ ] Set up Keycloak service account
- [ ] Deploy and verify

---

## 🎯 Summary

This guide provides a complete roadmap for implementing a user management controller in the EDC connector. The implementation:

1. **Follows EDC Patterns:** Uses standard extension structure, JAX-RS controllers, and service layer
2. **Integrates with Keycloak:** Uses Keycloak Admin API for actual user management
3. **Enforces Security:** Admin-only access via RoleBasedAccessFilter
4. **Provides RESTful API:** Standard CRUD operations for user management
5. **Handles Errors:** Proper error handling and validation

**Key Takeaways:**
- **Yes, a new extension is needed** - The EDC connector doesn't currently have user management endpoints
- **The extension writes to Keycloak via HTTP** - It makes REST API calls to Keycloak Admin API
- **KeycloakAdminApiService is the interface** - This service class acts as an HTTP client that calls Keycloak's Admin REST API endpoints
- User management is performed via Keycloak Admin API, not directly in EDC
- All endpoints require ADMIN role
- Service account is used to authenticate with Keycloak
- Follow existing controller patterns in the codebase
- Implement comprehensive error handling and validation

### Direct Answer to Your Question

**Q: Is a new extension needed? So it writes to Keycloak from the APIs with an interface for Keycloak Admin APIs?**

**A: Yes, exactly!**

1. **New Extension Required:** Yes, you need to create a new extension (`user-management-api`) to add user management endpoints to the EDC connector.

2. **Writes to Keycloak via APIs:** Yes, the extension exposes REST endpoints in EDC (`/api/management/v3/users`) that internally make HTTP requests to Keycloak's Admin API to create/update/disable users.

3. **Interface for Keycloak Admin APIs:** Yes, the `KeycloakAdminApiService` class is the interface/service that:
   - Makes HTTP calls to Keycloak Admin REST API endpoints
   - Handles authentication (service account)
   - Converts between EDC format and Keycloak format
   - Acts as an adapter between EDC and Keycloak

**Flow:**
```
Admin → EDC API Endpoint → Controller → KeycloakAdminApiService (HTTP client) → Keycloak Admin API → Keycloak Database
```

---

## 🆘 Troubleshooting

### Issue: Controller not accessible

**Check:**
1. Extension is registered in `settings.gradle.kts`
2. Extension is in runtime dependencies
3. Extension is loaded (check startup logs)
4. Controller is registered with WebService
5. Path is correct: `/api/management/v3/users`

### Issue: 403 Forbidden errors

**Check:**
1. JWT token contains `ADMIN` role
2. RoleBasedAccessFilter is configured correctly
3. Path mapping in PATH_ROLE_MAP is correct
4. Token is valid and not expired

### Issue: Keycloak API errors

**Check:**
1. Service account credentials are correct
2. Service account has `realm-admin` role
3. Keycloak URL and realm are correct
4. Network connectivity to Keycloak
5. Keycloak Admin API is enabled

### Issue: User creation fails

**Check:**
1. Username/email is not already taken
2. Password meets Keycloak requirements
3. Required fields are provided
4. Keycloak realm configuration allows user creation

---

**Note:** This is a guide for implementation. Actual code should follow the patterns and conventions established in the existing codebase. Refer to existing controllers (e.g., `BusinessPartnerGroupApiV3Controller`) for concrete implementation examples.

