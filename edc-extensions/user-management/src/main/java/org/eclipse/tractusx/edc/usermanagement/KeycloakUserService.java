/********************************************************************************
 * Copyright (c) 2025 Contributors
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

package org.eclipse.tractusx.edc.usermanagement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.eclipse.tractusx.edc.usermanagement.dto.TokenResponse;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing users in Keycloak via Admin API
 */
public class KeycloakUserService {

    private final Keycloak keycloak;
    private final String serverUrl;
    private final String realmName;
    private final String clientId;
    private final String clientSecret;
    private final Monitor monitor;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public KeycloakUserService(String serverUrl, String realmName, String clientId, 
                               String clientSecret, Monitor monitor) {
        this.serverUrl = serverUrl;
        this.realmName = realmName;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.monitor = monitor;
        this.httpClient = new OkHttpClient();
        this.objectMapper = new ObjectMapper();
        
        this.keycloak = KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm(realmName)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .grantType("client_credentials")
                .build();
        
        monitor.info("KeycloakUserService initialized for realm: " + realmName);
    }

    /**
     * Create a new user with temporary password
     */
    public Result<UserRepresentation> createUser(String username, String email, String firstName, 
                                                  String lastName, String temporaryPassword, List<String> roles) {
        try {
            RealmResource realm = keycloak.realm(realmName);
            UsersResource usersResource = realm.users();

            // Check if user already exists
            List<UserRepresentation> existingUsers = usersResource.search(username, true);
            if (!existingUsers.isEmpty()) {
                return Result.failure("User with username '" + username + "' already exists");
            }

            // Create user representation
            UserRepresentation user = new UserRepresentation();
            user.setUsername(username);
            user.setEmail(email);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setEnabled(true);
            user.setEmailVerified(false);

            // Create user
            Response response = usersResource.create(user);
            
            if (response.getStatus() != 201) {
                String errorMessage = "Failed to create user. Status: " + response.getStatus();
                monitor.warning(errorMessage);
                return Result.failure(errorMessage);
            }

            // Get created user ID from location header
            String userId = getUserIdFromLocation(response.getLocation().getPath());
            UserResource userResource = usersResource.get(userId);

            // Set temporary password
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(temporaryPassword);
            credential.setTemporary(true); // User must change password on first login
            userResource.resetPassword(credential);

            // Assign roles if provided
            if (roles != null && !roles.isEmpty()) {
                assignRoles(userResource, roles);
            }

            // Get updated user representation
            UserRepresentation createdUser = userResource.toRepresentation();
            
            monitor.info("User created successfully: " + username + " (ID: " + userId + ")");
            return Result.success(createdUser);

        } catch (Exception e) {
            monitor.severe("Failed to create user: " + e.getMessage(), e);
            return Result.failure("Failed to create user: " + e.getMessage());
        }
    }

    /**
     * Update user information
     */
    public Result<UserRepresentation> updateUser(String userId, String email, String firstName, 
                                                  String lastName, Boolean enabled, List<String> roles) {
        try {
            RealmResource realm = keycloak.realm(realmName);
            UserResource userResource = realm.users().get(userId);

            if (userResource == null) {
                return Result.failure("User with ID '" + userId + "' not found");
            }

            UserRepresentation user = userResource.toRepresentation();
            
            if (email != null) {
                user.setEmail(email);
            }
            if (firstName != null) {
                user.setFirstName(firstName);
            }
            if (lastName != null) {
                user.setLastName(lastName);
            }
            if (enabled != null) {
                user.setEnabled(enabled);
            }

            if (roles != null && !roles.isEmpty()) {
                assignRoles(userResource, roles);
            }

            userResource.update(user);
            
            UserRepresentation updatedUser = userResource.toRepresentation();
            monitor.info("User updated successfully: " + userId);
            return Result.success(updatedUser);

        } catch (NotFoundException e) {
            return Result.failure("User with ID '" + userId + "' not found");
        } catch (Exception e) {
            monitor.severe("Failed to update user: " + e.getMessage(), e);
            return Result.failure("Failed to update user: " + e.getMessage());
        }
    }

    /**
     * Disable a user (set enabled=false)
     */
    public Result<Void> disableUser(String userId) {
        try {
            RealmResource realm = keycloak.realm(realmName);
            UserResource userResource = realm.users().get(userId);

            if (userResource == null) {
                return Result.failure("User with ID '" + userId + "' not found");
            }

            UserRepresentation user = userResource.toRepresentation();
            user.setEnabled(false);
            userResource.update(user);

            monitor.info("User disabled successfully: " + userId);
            return Result.success();

        } catch (NotFoundException e) {
            return Result.failure("User with ID '" + userId + "' not found");
        } catch (Exception e) {
            monitor.severe("Failed to disable user: " + e.getMessage(), e);
            return Result.failure("Failed to disable user: " + e.getMessage());
        }
    }

    /**
     * Enable a user (set enabled=true)
     */
    public Result<Void> enableUser(String userId) {
        try {
            RealmResource realm = keycloak.realm(realmName);
            UserResource userResource = realm.users().get(userId);

            if (userResource == null) {
                return Result.failure("User with ID '" + userId + "' not found");
            }

            UserRepresentation user = userResource.toRepresentation();
            user.setEnabled(true);
            userResource.update(user);

            monitor.info("User enabled successfully: " + userId);
            return Result.success();

        } catch (NotFoundException e) {
            return Result.failure("User with ID '" + userId + "' not found");
        } catch (Exception e) {
            monitor.severe("Failed to enable user: " + e.getMessage(), e);
            return Result.failure("Failed to enable user: " + e.getMessage());
        }
    }

    /**
     * Get all users
     */
    public Result<List<UserRepresentation>> getAllUsers() {
        try {
            RealmResource realm = keycloak.realm(realmName);
            UsersResource usersResource = realm.users();
            
            List<UserRepresentation> users = usersResource.list();
            
            // Fetch roles for each user
            for (UserRepresentation user : users) {
                try {
                    UserResource userResource = usersResource.get(user.getId());
                    List<org.keycloak.representations.idm.RoleRepresentation> realmRoles = userResource.roles().realmLevel().listAll();
                    user.setRealmRoles(realmRoles.stream()
                            .map(org.keycloak.representations.idm.RoleRepresentation::getName)
                            .collect(Collectors.toList()));
                } catch (Exception e) {
                    monitor.warning("Failed to fetch roles for user " + user.getId() + ": " + e.getMessage());
                    user.setRealmRoles(List.of());
                }
            }
            
            monitor.debug("Retrieved " + users.size() + " users from Keycloak");
            return Result.success(users);

        } catch (Exception e) {
            monitor.severe("Failed to get users: " + e.getMessage(), e);
            return Result.failure("Failed to get users: " + e.getMessage());
        }
    }

    /**
     * Get user by ID
     */
    public Result<UserRepresentation> getUserById(String userId) {
        try {
            RealmResource realm = keycloak.realm(realmName);
            UserResource userResource = realm.users().get(userId);

            if (userResource == null) {
                return Result.failure("User with ID '" + userId + "' not found");
            }

            UserRepresentation user = userResource.toRepresentation();
            
            // Fetch realm roles for the user
            List<org.keycloak.representations.idm.RoleRepresentation> realmRoles = userResource.roles().realmLevel().listAll();
            user.setRealmRoles(realmRoles.stream()
                    .map(org.keycloak.representations.idm.RoleRepresentation::getName)
                    .collect(Collectors.toList()));
            
            return Result.success(user);

        } catch (jakarta.ws.rs.NotFoundException e) {
            return Result.failure("User with ID '" + userId + "' not found");
        } catch (Exception e) {
            monitor.severe("Failed to get user: " + e.getMessage(), e);
            return Result.failure("Failed to get user: " + e.getMessage());
        }
    }
    
    /**
     * Get user by username
     */
    public Result<UserRepresentation> getUserByUsername(String username) {
        try {
            RealmResource realm = keycloak.realm(realmName);
            UsersResource usersResource = realm.users();
            
            List<UserRepresentation> users = usersResource.search(username, true);
            
            if (users.isEmpty()) {
                return Result.failure("User with username '" + username + "' not found");
            }
            
            if (users.size() > 1) {
                monitor.warning("Multiple users found with username '" + username + "', returning first match");
            }
            
            return Result.success(users.get(0));

        } catch (Exception e) {
            monitor.severe("Failed to get user by username: " + e.getMessage(), e);
            return Result.failure("Failed to get user by username: " + e.getMessage());
        }
    }

    /**
     * Assign roles to a user
     */
    private void assignRoles(UserResource userResource, List<String> roles) {
        try {
            RealmResource realm = keycloak.realm(realmName);
            
            // Fetch roles individually by name to avoid requiring view-realm permission
            List<RoleRepresentation> rolesToAssign = new ArrayList<>();
            List<String> missingRoles = new ArrayList<>();
            
            for (String roleName : roles) {
                try {
                    RoleRepresentation role = realm.roles().get(roleName).toRepresentation();
                    rolesToAssign.add(role);
                } catch (NotFoundException e) {
                    missingRoles.add(roleName);
                    monitor.debug("Role not found in Keycloak: " + roleName);
                } catch (Exception e) {
                    monitor.warning("Failed to fetch role '" + roleName + "': " + e.getMessage());
                    missingRoles.add(roleName);
                }
            }
            
            if (!rolesToAssign.isEmpty()) {
                userResource.roles().realmLevel().add(rolesToAssign);
                monitor.debug("Assigned roles to user: " + rolesToAssign.stream()
                        .map(org.keycloak.representations.idm.RoleRepresentation::getName)
                        .collect(Collectors.joining(", ")));
            }
            
            if (!missingRoles.isEmpty()) {
                monitor.warning("Roles not found in Keycloak (will be ignored): " + String.join(", ", missingRoles));
            }
            
        } catch (Exception e) {
            monitor.warning("Failed to assign roles to user: " + e.getMessage(), e);
            // Don't fail user creation if role assignment fails
        }
    }

    /**
     * Get all available realm roles
     */
    public Result<List<RoleRepresentation>> getRealmRoles() {
        try {
            RealmResource realm = keycloak.realm(realmName);
            List<RoleRepresentation> roles = realm.roles().list();
            
            monitor.debug("Retrieved " + roles.size() + " realm roles from Keycloak");
            return Result.success(roles);

        } catch (Exception e) {
            monitor.severe("Failed to get realm roles: " + e.getMessage(), e);
            return Result.failure("Failed to get realm roles: " + e.getMessage());
        }
    }

    /**
     * Generate access token using password credentials grant (Resource Owner Password Credentials)
     * 
     * @param username The username
     * @param password The password
     * @return Result containing TokenResponse with access token, refresh token, etc.
     */
    public Result<TokenResponse> generateToken(String username, String password) {
        try {
            // Build the token endpoint URL
            String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token", 
                    serverUrl, realmName);
            
            // Build form body for password credentials grant
            RequestBody formBody = new FormBody.Builder()
                    .add("grant_type", "password")
                    .add("username", username)
                    .add("password", password)
                    .add("client_id", clientId)
                    .add("client_secret", clientSecret)
                    .build();
            
            // Create HTTP request
            Request request = new Request.Builder()
                    .url(tokenUrl)
                    .post(formBody)
                    .addHeader("Content-Type", "application/x-www-form-urlencoded")
                    .build();
            
            // Execute request
            try (okhttp3.Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "No error details";
                    monitor.warning("Failed to generate token for user '" + username + "'. Status: " + 
                            response.code() + ", Error: " + errorBody);
                    
                    // Try to parse error message from response
                    String errorMessage = "Authentication failed";
                    try {
                        JsonNode errorJson = objectMapper.readTree(errorBody);
                        if (errorJson.has("error_description")) {
                            errorMessage = errorJson.get("error_description").asText();
                        } else if (errorJson.has("error")) {
                            errorMessage = errorJson.get("error").asText();
                        }
                    } catch (Exception e) {
                        // Use default error message
                    }
                    
                    return Result.failure(errorMessage);
                }
                
                // Parse successful response
                String responseBody = response.body().string();
                JsonNode tokenJson = objectMapper.readTree(responseBody);
                
                TokenResponse tokenResponse = new TokenResponse(
                        tokenJson.has("access_token") ? tokenJson.get("access_token").asText() : null,
                        tokenJson.has("refresh_token") ? tokenJson.get("refresh_token").asText() : null,
                        tokenJson.has("token_type") ? tokenJson.get("token_type").asText() : "Bearer",
                        tokenJson.has("expires_in") ? tokenJson.get("expires_in").asLong() : null,
                        tokenJson.has("refresh_expires_in") ? tokenJson.get("refresh_expires_in").asLong() : null
                );
                
                monitor.info("Token generated successfully for user: " + username);
                return Result.success(tokenResponse);
            }
            
        } catch (IOException e) {
            monitor.severe("Failed to generate token: " + e.getMessage(), e);
            return Result.failure("Failed to generate token: " + e.getMessage());
        } catch (Exception e) {
            monitor.severe("Unexpected error during token generation: " + e.getMessage(), e);
            return Result.failure("Unexpected error during token generation: " + e.getMessage());
        }
    }

    /**
     * Extract user ID from Keycloak location header
     * Format: /admin/realms/{realm}/users/{userId}
     */
    private String getUserIdFromLocation(String location) {
        String[] parts = location.split("/");
        return parts[parts.length - 1];
    }
}

