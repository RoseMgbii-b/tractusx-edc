# KeycloakAdminApiService - Complete Implementation

## 🎯 Overview

This document provides a **production-ready, complete implementation** of the `KeycloakAdminApiService` class with all CRUD operations, proper error handling, token management, and thread safety.

---

## 📋 Complete Implementation

### File: `KeycloakAdminApiService.java`

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

package org.eclipse.tractusx.edc.usermanagement.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.tractusx.edc.usermanagement.model.CreateUserRequest;
import org.eclipse.tractusx.edc.usermanagement.model.UpdateUserRequest;
import org.eclipse.tractusx.edc.usermanagement.model.UserDto;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Service for interacting with Keycloak Admin REST API.
 * Handles authentication, token management, and all user CRUD operations.
 */
public class KeycloakAdminApiService {

    private static final String TOKEN_ENDPOINT = "/protocol/openid-connect/token";
    private static final String USERS_ENDPOINT = "/admin/realms/%s/users";
    private static final String USER_BY_ID_ENDPOINT = "/admin/realms/%s/users/%s";
    private static final String USER_PASSWORD_ENDPOINT = "/admin/realms/%s/users/%s/reset-password";
    private static final String USER_ROLES_ENDPOINT = "/admin/realms/%s/users/%s/role-mappings/realm";
    
    private final String keycloakUrl;
    private final String realm;
    private final String clientId;
    private final String clientSecret;
    private final HttpClient httpClient;
    private final Monitor monitor;
    private final ObjectMapper objectMapper;
    
    // Token management (thread-safe)
    private final ReentrantLock tokenLock = new ReentrantLock();
    private volatile String accessToken;
    private volatile Instant tokenExpiresAt;
    private static final long TOKEN_REFRESH_BUFFER_SECONDS = 60; // Refresh 60s before expiry

    public KeycloakAdminApiService(
            String keycloakUrl,
            String realm,
            String clientId,
            String clientSecret,
            HttpClient httpClient,
            Monitor monitor) {
        this.keycloakUrl = keycloakUrl.endsWith("/") ? keycloakUrl.substring(0, keycloakUrl.length() - 1) : keycloakUrl;
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.httpClient = httpClient;
        this.monitor = monitor;
        this.objectMapper = new ObjectMapper();
    }

    // ============================================================================
    // PUBLIC API METHODS
    // ============================================================================

    /**
     * Creates a new user in Keycloak.
     */
    public Result<UserDto> createUser(CreateUserRequest request) {
        try {
            // Validate input
            var validationResult = validateCreateRequest(request);
            if (validationResult.failed()) {
                return validationResult;
            }

            // Ensure valid token
            var tokenResult = ensureValidToken();
            if (tokenResult.failed()) {
                return Result.failure("Failed to authenticate with Keycloak: " + tokenResult.getFailureDetail());
            }

            // Convert to Keycloak format
            JsonObject keycloakUserJson = convertToKeycloakFormat(request);

            // Build HTTP request
            String endpoint = String.format(USERS_ENDPOINT, realm);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(keycloakUrl + endpoint))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(keycloakUserJson.toString()))
                    .build();

            monitor.debug("Creating user in Keycloak: " + request.getUsername());

            // Send request
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            // Handle response
            if (response.statusCode() == 201) {
                // User created successfully
                String locationHeader = response.headers().firstValue("Location").orElse(null);
                String userId = extractUserIdFromLocation(locationHeader);
                
                if (userId == null) {
                    return Result.failure("User created but could not extract user ID from response");
                }

                // Fetch the created user to return full details
                return getUser(userId);
            } else {
                return handleErrorResponse(response, "create user");
            }

        } catch (IOException | InterruptedException e) {
            monitor.severe("Error creating user in Keycloak", e);
            return Result.failure("Error communicating with Keycloak: " + e.getMessage());
        }
    }

    /**
     * Updates an existing user in Keycloak.
     */
    public Result<UserDto> updateUser(String userId, UpdateUserRequest request) {
        try {
            // Validate input
            if (userId == null || userId.isBlank()) {
                return Result.failure("User ID cannot be null or empty");
            }

            // Ensure valid token
            var tokenResult = ensureValidToken();
            if (tokenResult.failed()) {
                return Result.failure("Failed to authenticate with Keycloak: " + tokenResult.getFailureDetail());
            }

            // Convert to Keycloak format
            JsonObject keycloakUserJson = convertUpdateToKeycloakFormat(request);

            // Build HTTP request
            String endpoint = String.format(USER_BY_ID_ENDPOINT, realm, userId);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(keycloakUrl + endpoint))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(keycloakUserJson.toString()))
                    .build();

            monitor.debug("Updating user in Keycloak: " + userId);

            // Send request
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            // Handle response
            if (response.statusCode() == 204) {
                // User updated successfully, fetch updated user
                return getUser(userId);
            } else {
                return handleErrorResponse(response, "update user");
            }

        } catch (IOException | InterruptedException e) {
            monitor.severe("Error updating user in Keycloak", e);
            return Result.failure("Error communicating with Keycloak: " + e.getMessage());
        }
    }

    /**
     * Disables a user account in Keycloak.
     */
    public Result<UserDto> disableUser(String userId) {
        try {
            // Validate input
            if (userId == null || userId.isBlank()) {
                return Result.failure("User ID cannot be null or empty");
            }

            // Ensure valid token
            var tokenResult = ensureValidToken();
            if (tokenResult.failed()) {
                return Result.failure("Failed to authenticate with Keycloak: " + tokenResult.getFailureDetail());
            }

            // Build update request to disable user
            JsonObject disableRequest = Json.createObjectBuilder()
                    .add("enabled", false)
                    .build();

            // Build HTTP request
            String endpoint = String.format(USER_BY_ID_ENDPOINT, realm, userId);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(keycloakUrl + endpoint))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(disableRequest.toString()))
                    .build();

            monitor.debug("Disabling user in Keycloak: " + userId);

            // Send request
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            // Handle response
            if (response.statusCode() == 204) {
                // User disabled successfully, fetch updated user
                return getUser(userId);
            } else {
                return handleErrorResponse(response, "disable user");
            }

        } catch (IOException | InterruptedException e) {
            monitor.severe("Error disabling user in Keycloak", e);
            return Result.failure("Error communicating with Keycloak: " + e.getMessage());
        }
    }

    /**
     * Retrieves a user by ID from Keycloak.
     */
    public Result<UserDto> getUser(String userId) {
        try {
            // Validate input
            if (userId == null || userId.isBlank()) {
                return Result.failure("User ID cannot be null or empty");
            }

            // Ensure valid token
            var tokenResult = ensureValidToken();
            if (tokenResult.failed()) {
                return Result.failure("Failed to authenticate with Keycloak: " + tokenResult.getFailureDetail());
            }

            // Build HTTP request
            String endpoint = String.format(USER_BY_ID_ENDPOINT, realm, userId);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(keycloakUrl + endpoint))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            monitor.debug("Fetching user from Keycloak: " + userId);

            // Send request
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            // Handle response
            if (response.statusCode() == 200) {
                return parseUserResponse(response.body());
            } else if (response.statusCode() == 404) {
                return Result.failure("User not found: " + userId);
            } else {
                return handleErrorResponse(response, "get user");
            }

        } catch (IOException | InterruptedException e) {
            monitor.severe("Error fetching user from Keycloak", e);
            return Result.failure("Error communicating with Keycloak: " + e.getMessage());
        }
    }

    /**
     * Lists users from Keycloak with pagination.
     */
    public Result<List<UserDto>> listUsers(Integer first, Integer max, String search) {
        try {
            // Ensure valid token
            var tokenResult = ensureValidToken();
            if (tokenResult.failed()) {
                return Result.failure("Failed to authenticate with Keycloak: " + tokenResult.getFailureDetail());
            }

            // Build query parameters
            StringBuilder queryParams = new StringBuilder();
            if (first != null && first > 0) {
                queryParams.append("first=").append(first);
            }
            if (max != null && max > 0) {
                if (queryParams.length() > 0) queryParams.append("&");
                queryParams.append("max=").append(Math.min(max, 100)); // Limit to 100
            }
            if (search != null && !search.isBlank()) {
                if (queryParams.length() > 0) queryParams.append("&");
                queryParams.append("search=").append(URLEncoder.encode(search, StandardCharsets.UTF_8));
            }

            // Build HTTP request
            String endpoint = String.format(USERS_ENDPOINT, realm);
            String uri = keycloakUrl + endpoint;
            if (queryParams.length() > 0) {
                uri += "?" + queryParams;
            }

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(uri))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            monitor.debug("Listing users from Keycloak");

            // Send request
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            // Handle response
            if (response.statusCode() == 200) {
                return parseUsersListResponse(response.body());
            } else {
                return Result.failure("Failed to list users: HTTP " + response.statusCode());
            }

        } catch (IOException | InterruptedException e) {
            monitor.severe("Error listing users from Keycloak", e);
            return Result.failure("Error communicating with Keycloak: " + e.getMessage());
        }
    }

    /**
     * Assigns roles to a user.
     */
    public Result<Void> assignRoles(String userId, List<String> roleNames) {
        try {
            // Validate input
            if (userId == null || userId.isBlank()) {
                return Result.failure("User ID cannot be null or empty");
            }
            if (roleNames == null || roleNames.isEmpty()) {
                return Result.failure("Role names cannot be null or empty");
            }

            // Ensure valid token
            var tokenResult = ensureValidToken();
            if (tokenResult.failed()) {
                return Result.failure("Failed to authenticate with Keycloak: " + tokenResult.getFailureDetail());
            }

            // First, get realm roles to find role IDs
            var rolesResult = getRealmRoles(roleNames);
            if (rolesResult.failed()) {
                return Result.failure("Failed to get realm roles: " + rolesResult.getFailureDetail());
            }

            JsonArray rolesArray = rolesResult.getContent();

            // Build HTTP request
            String endpoint = String.format(USER_ROLES_ENDPOINT, realm, userId);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(keycloakUrl + endpoint))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(rolesArray.toString()))
                    .build();

            monitor.debug("Assigning roles to user: " + userId);

            // Send request
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            // Handle response
            if (response.statusCode() == 204 || response.statusCode() == 200) {
                return Result.success();
            } else {
                return handleErrorResponse(response, "assign roles");
            }

        } catch (IOException | InterruptedException e) {
            monitor.severe("Error assigning roles to user in Keycloak", e);
            return Result.failure("Error communicating with Keycloak: " + e.getMessage());
        }
    }

    // ============================================================================
    // TOKEN MANAGEMENT (Thread-safe)
    // ============================================================================

    /**
     * Ensures we have a valid access token. Refreshes if needed.
     * Thread-safe implementation.
     */
    private Result<Void> ensureValidToken() {
        tokenLock.lock();
        try {
            // Check if token is valid and not expired (with buffer)
            if (accessToken != null && tokenExpiresAt != null) {
                Instant refreshTime = tokenExpiresAt.minus(TOKEN_REFRESH_BUFFER_SECONDS, ChronoUnit.SECONDS);
                if (Instant.now().isBefore(refreshTime)) {
                    // Token is still valid
                    return Result.success();
                }
            }

            // Need to get a new token
            monitor.debug("Refreshing Keycloak admin token");
            return refreshToken();

        } finally {
            tokenLock.unlock();
        }
    }

    /**
     * Refreshes the access token using client credentials flow.
     */
    private Result<Void> refreshToken() {
        try {
            // Build token request
            String tokenUrl = keycloakUrl + "/realms/" + realm + TOKEN_ENDPOINT;
            String requestBody = "grant_type=client_credentials" +
                    "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8) +
                    "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8);

            HttpRequest tokenRequest = HttpRequest.newBuilder()
                    .uri(URI.create(tokenUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            // Send request
            HttpResponse<String> response = httpClient.send(tokenRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                // Parse token response
                JsonObject tokenResponse = Json.createReader(new java.io.StringReader(response.body())).readObject();
                accessToken = tokenResponse.getString("access_token");
                
                // Calculate expiration time (default to 60 seconds if not provided)
                int expiresIn = tokenResponse.getInt("expires_in", 60);
                tokenExpiresAt = Instant.now().plus(expiresIn, ChronoUnit.SECONDS);

                monitor.debug("Successfully obtained Keycloak admin token (expires in " + expiresIn + "s)");
                return Result.success();
            } else {
                String errorMsg = "Failed to obtain token: HTTP " + response.statusCode() + " - " + response.body();
                monitor.severe(errorMsg);
                return Result.failure(errorMsg);
            }

        } catch (Exception e) {
            String errorMsg = "Error obtaining Keycloak admin token: " + e.getMessage();
            monitor.severe(errorMsg, e);
            return Result.failure(errorMsg);
        }
    }

    // ============================================================================
    // CONVERSION METHODS
    // ============================================================================

    /**
     * Converts CreateUserRequest to Keycloak user JSON format.
     */
    private JsonObject convertToKeycloakFormat(CreateUserRequest request) {
        JsonObjectBuilder builder = Json.createObjectBuilder();

        if (request.getUsername() != null) {
            builder.add("username", request.getUsername());
        }
        if (request.getEmail() != null) {
            builder.add("email", request.getEmail());
        }
        if (request.getFirstName() != null) {
            builder.add("firstName", request.getFirstName());
        }
        if (request.getLastName() != null) {
            builder.add("lastName", request.getLastName());
        }
        builder.add("enabled", request.isEnabled());
        builder.add("emailVerified", request.isEmailVerified());

        // Add credentials if password is provided
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            JsonArrayBuilder credentialsArray = Json.createArrayBuilder();
            JsonObject credential = Json.createObjectBuilder()
                    .add("type", "password")
                    .add("value", request.getPassword())
                    .add("temporary", false)
                    .build();
            credentialsArray.add(credential);
            builder.add("credentials", credentialsArray);
        }

        return builder.build();
    }

    /**
     * Converts UpdateUserRequest to Keycloak user JSON format.
     */
    private JsonObject convertUpdateToKeycloakFormat(UpdateUserRequest request) {
        JsonObjectBuilder builder = Json.createObjectBuilder();

        if (request.getEmail() != null) {
            builder.add("email", request.getEmail());
        }
        if (request.getFirstName() != null) {
            builder.add("firstName", request.getFirstName());
        }
        if (request.getLastName() != null) {
            builder.add("lastName", request.getLastName());
        }
        if (request.getEnabled() != null) {
            builder.add("enabled", request.getEnabled());
        }
        if (request.getEmailVerified() != null) {
            builder.add("emailVerified", request.getEmailVerified());
        }

        return builder.build();
    }

    /**
     * Parses Keycloak user JSON response to UserDto.
     */
    private Result<UserDto> parseUserResponse(String jsonBody) {
        try {
            JsonObject jsonObject = Json.createReader(new java.io.StringReader(jsonBody)).readObject();
            
            UserDto userDto = new UserDto();
            userDto.setId(getStringValue(jsonObject, "id"));
            userDto.setUsername(getStringValue(jsonObject, "username"));
            userDto.setEmail(getStringValue(jsonObject, "email"));
            userDto.setFirstName(getStringValue(jsonObject, "firstName"));
            userDto.setLastName(getStringValue(jsonObject, "lastName"));
            userDto.setEnabled(jsonObject.getBoolean("enabled", false));
            userDto.setEmailVerified(jsonObject.getBoolean("emailVerified", false));

            // Fetch roles separately (Keycloak doesn't include roles in user object)
            String userId = userDto.getId();
            if (userId != null) {
                var rolesResult = getUserRoles(userId);
                if (rolesResult.succeeded()) {
                    userDto.setRoles(rolesResult.getContent());
                }
            }

            return Result.success(userDto);

        } catch (Exception e) {
            monitor.warning("Error parsing user response from Keycloak: " + e.getMessage());
            return Result.failure("Error parsing user response: " + e.getMessage());
        }
    }

    /**
     * Parses Keycloak users list JSON response.
     */
    private Result<List<UserDto>> parseUsersListResponse(String jsonBody) {
        try {
            JsonArray jsonArray = Json.createReader(new java.io.StringReader(jsonBody)).readArray();
            List<UserDto> users = new ArrayList<>();

            for (int i = 0; i < jsonArray.size(); i++) {
                JsonObject userJson = jsonArray.getJsonObject(i);
                UserDto userDto = new UserDto();
                userDto.setId(getStringValue(userJson, "id"));
                userDto.setUsername(getStringValue(userJson, "username"));
                userDto.setEmail(getStringValue(userJson, "email"));
                userDto.setFirstName(getStringValue(userJson, "firstName"));
                userDto.setLastName(getStringValue(userJson, "lastName"));
                userDto.setEnabled(userJson.getBoolean("enabled", false));
                userDto.setEmailVerified(userJson.getBoolean("emailVerified", false));
                
                // Note: Roles are not included in list response for performance
                users.add(userDto);
            }

            return Result.success(users);

        } catch (Exception e) {
            monitor.warning("Error parsing users list response from Keycloak: " + e.getMessage());
            return Result.failure("Error parsing users list response: " + e.getMessage());
        }
    }

    /**
     * Gets roles for a user.
     */
    private Result<List<String>> getUserRoles(String userId) {
        try {
            String endpoint = String.format(USER_ROLES_ENDPOINT, realm, userId);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(keycloakUrl + endpoint))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonArray rolesArray = Json.createReader(new java.io.StringReader(response.body())).readArray();
                List<String> roleNames = new ArrayList<>();
                for (int i = 0; i < rolesArray.size(); i++) {
                    JsonObject role = rolesArray.getJsonObject(i);
                    roleNames.add(getStringValue(role, "name"));
                }
                return Result.success(roleNames);
            } else {
                return Result.success(new ArrayList<>()); // Return empty list if roles can't be fetched
            }

        } catch (Exception e) {
            monitor.warning("Error fetching user roles: " + e.getMessage());
            return Result.success(new ArrayList<>()); // Return empty list on error
        }
    }

    /**
     * Gets realm roles by name.
     */
    private Result<JsonArray> getRealmRoles(List<String> roleNames) {
        try {
            // Get all realm roles first
            String endpoint = "/admin/realms/" + realm + "/roles";
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(keycloakUrl + endpoint))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonArray allRoles = Json.createReader(new java.io.StringReader(response.body())).readArray();
                JsonArrayBuilder matchingRoles = Json.createArrayBuilder();
                
                for (int i = 0; i < allRoles.size(); i++) {
                    JsonObject role = allRoles.getJsonObject(i);
                    String roleName = getStringValue(role, "name");
                    if (roleNames.contains(roleName)) {
                        matchingRoles.add(role);
                    }
                }
                
                return Result.success(matchingRoles.build());
            } else {
                return Result.failure("Failed to get realm roles: HTTP " + response.statusCode());
            }

        } catch (Exception e) {
            return Result.failure("Error getting realm roles: " + e.getMessage());
        }
    }

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    /**
     * Handles error responses from Keycloak API.
     */
    private <T> Result<T> handleErrorResponse(HttpResponse<String> response, String operation) {
        int statusCode = response.statusCode();
        String body = response.body();

        String errorMessage = switch (statusCode) {
            case 400 -> "Bad request: Invalid input data";
            case 401 -> "Unauthorized: Invalid or expired token";
            case 403 -> "Forbidden: Insufficient permissions";
            case 404 -> "Not found: Resource does not exist";
            case 409 -> "Conflict: Resource already exists";
            case 500, 502, 503, 504 -> "Keycloak server error";
            default -> "Unexpected error: HTTP " + statusCode;
        };

        // Try to extract error message from response body
        if (body != null && !body.isBlank()) {
            try {
                JsonObject errorJson = Json.createReader(new java.io.StringReader(body)).readObject();
                if (errorJson.containsKey("errorMessage")) {
                    errorMessage = errorJson.getString("errorMessage");
                } else if (errorJson.containsKey("error")) {
                    errorMessage = errorJson.getString("error");
                }
            } catch (Exception e) {
                // Ignore parsing errors, use default message
            }
        }

        monitor.warning("Keycloak API error during " + operation + ": " + errorMessage + " (HTTP " + statusCode + ")");
        return Result.failure(errorMessage);
    }

    /**
     * Extracts user ID from Location header.
     */
    private String extractUserIdFromLocation(String location) {
        if (location == null || location.isBlank()) {
            return null;
        }
        // Location format: https://keycloak.../admin/realms/{realm}/users/{userId}
        String[] parts = location.split("/users/");
        if (parts.length > 1) {
            return parts[1];
        }
        return null;
    }

    /**
     * Validates CreateUserRequest.
     */
    private Result<Void> validateCreateRequest(CreateUserRequest request) {
        if (request == null) {
            return Result.failure("Request cannot be null");
        }
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            return Result.failure("Username is required");
        }
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            return Result.failure("Email is required");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            return Result.failure("Password is required");
        }
        // Basic email validation
        if (!request.getEmail().contains("@")) {
            return Result.failure("Invalid email format");
        }
        return Result.success();
    }

    /**
     * Safely gets string value from JsonObject.
     */
    private String getStringValue(JsonObject jsonObject, String key) {
        try {
            if (jsonObject.containsKey(key) && !jsonObject.isNull(key)) {
                return jsonObject.getString(key);
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }
}
```

---

## 📋 Supporting Classes

### File: `UpdateUserRequest.java`

```java
package org.eclipse.tractusx.edc.usermanagement.model;

/**
 * Request DTO for updating a user
 */
public class UpdateUserRequest {
    private String email;
    private String firstName;
    private String lastName;
    private Boolean enabled;
    private Boolean emailVerified;
    private List<String> roles;

    // Constructors
    public UpdateUserRequest() {
    }

    // Getters and setters
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(Boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }
}
```

---

## 🔑 Key Improvements

### 1. **Thread-Safe Token Management**
- Uses `ReentrantLock` to ensure thread-safe token refresh
- Prevents multiple threads from refreshing token simultaneously
- Token refresh buffer (60 seconds before expiry)

### 2. **Complete CRUD Operations**
- ✅ Create user
- ✅ Update user
- ✅ Disable user
- ✅ Get user
- ✅ List users (with pagination and search)
- ✅ Assign roles

### 3. **Robust Error Handling**
- Maps HTTP status codes to meaningful error messages
- Extracts error details from Keycloak response body
- Returns `Result<T>` pattern for all operations

### 4. **Input Validation**
- Validates required fields
- Email format validation
- Null/empty checks

### 5. **Proper HTTP Client Usage**
- Uses Java 11+ `HttpClient` (no external dependencies)
- Proper header management
- URL encoding for query parameters

### 6. **Logging**
- Debug logs for operations
- Warning logs for errors
- Severe logs for exceptions

### 7. **Token Refresh Logic**
- Automatic token refresh before expiry
- Client credentials OAuth2 flow
- Handles token expiration gracefully

### 8. **Role Management**
- Fetches user roles separately (Keycloak limitation)
- Assigns roles to users
- Gets realm roles for assignment

---

## 🚀 Usage Example

```java
// Create service
HttpClient httpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .build();

KeycloakAdminApiService service = new KeycloakAdminApiService(
    "https://keycloak.example.com",
    "my-realm",
    "admin-cli",
    "client-secret",
    httpClient,
    monitor
);

// Create user
CreateUserRequest request = new CreateUserRequest();
request.setUsername("john.doe");
request.setEmail("john.doe@example.com");
request.setPassword("SecurePassword123!");
request.setFirstName("John");
request.setLastName("Doe");
request.setEnabled(true);
request.setEmailVerified(false);

Result<UserDto> result = service.createUser(request);
if (result.succeeded()) {
    UserDto user = result.getContent();
    System.out.println("User created: " + user.getId());
} else {
    System.out.println("Error: " + result.getFailureDetail());
}
```

---

## ✅ Summary

This implementation provides:
- ✅ **Production-ready code** with full error handling
- ✅ **Thread-safe token management**
- ✅ **Complete CRUD operations**
- ✅ **Proper validation and error messages**
- ✅ **Follows EDC Result pattern**
- ✅ **Comprehensive logging**
- ✅ **No external HTTP client dependencies** (uses Java 11+ HttpClient)


