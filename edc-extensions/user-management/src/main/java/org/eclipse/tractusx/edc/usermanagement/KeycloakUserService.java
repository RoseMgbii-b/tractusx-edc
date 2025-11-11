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

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.eclipse.edc.spi.result.Result;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.eclipse.edc.spi.monitor.Monitor;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing users in Keycloak via Admin API
 */
public class KeycloakUserService {

    private final Keycloak keycloak;
    private final String realmName;
    private final Monitor monitor;

    public KeycloakUserService(String serverUrl, String realmName, String clientId, 
                               String clientSecret, Monitor monitor) {
        this.realmName = realmName;
        this.monitor = monitor;
        
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
            
            // Try to get realm roles list first (for validation)
            List<RoleRepresentation> rolesToAssign;
            try {
                List<RoleRepresentation> realmRoles = realm.roles().list();
                // Filter roles that exist in Keycloak
                rolesToAssign = realmRoles.stream()
                        .filter(role -> roles.contains(role.getName()))
                        .collect(Collectors.toList());
                
                // Log warning for roles that don't exist
                List<String> assignedRoleNames = rolesToAssign.stream()
                        .map(RoleRepresentation::getName)
                        .collect(Collectors.toList());
                List<String> missingRoles = roles.stream()
                        .filter(role -> !assignedRoleNames.contains(role))
                        .collect(Collectors.toList());
                
                if (!missingRoles.isEmpty()) {
                    monitor.warning("Roles not found in Keycloak (will be ignored): " + String.join(", ", missingRoles));
                }
            } catch (Exception e) {
                // If we can't list roles (403 Forbidden), try to assign directly
                String errorMsg = e.getMessage();
                if (errorMsg != null && (errorMsg.contains("403") || errorMsg.contains("Forbidden"))) {
                    monitor.debug("Cannot list roles (403 Forbidden), attempting direct role assignment");
                    // Try to assign roles directly without validation
                    rolesToAssign = roles.stream()
                            .map(roleName -> {
                                RoleRepresentation role = new RoleRepresentation();
                                role.setName(roleName);
                                return role;
                            })
                            .collect(Collectors.toList());
                } else {
                    throw e; // Re-throw if it's a different error
                }
            }
            
            // Assign roles to user
            if (!rolesToAssign.isEmpty()) {
                userResource.roles().realmLevel().add(rolesToAssign);
                monitor.debug("Assigned roles to user: " + rolesToAssign.stream()
                        .map(RoleRepresentation::getName)
                        .collect(Collectors.joining(", ")));
            }
            
        } catch (Exception e) {
            monitor.warning("Failed to assign roles to user: " + e.getMessage(), e);
            // Don't fail user creation/update if role assignment fails
        }
    }

    /**
     * Get all available realm roles
     * 
     * Note: This requires the Keycloak admin client to have "view-realm-roles" permission.
     * If you get a 403 Forbidden error, you need to:
     * 1. Go to Keycloak Admin Console
     * 2. Navigate to Clients -> [your-admin-client] -> Service Account Roles
     * 3. Assign the "realm-management" client role "view-realm-roles" or "realm-admin"
     * 
     * As a fallback, if direct role listing fails, this method will attempt to extract
     * unique roles from all users' assigned roles.
     */
    public Result<List<RoleRepresentation>> getRealmRoles() {
        try {
            RealmResource realm = keycloak.realm(realmName);
            List<RoleRepresentation> roles = realm.roles().list();
            
            monitor.debug("Retrieved " + roles.size() + " realm roles from Keycloak");
            return Result.success(roles);

        } catch (Exception e) {
            String errorMsg = e.getMessage();
            // Check if this is a 403 Forbidden error (permission denied)
            if (errorMsg != null && (errorMsg.contains("403") || errorMsg.contains("Forbidden"))) {
                monitor.warning("Direct role listing failed with 403 Forbidden. Attempting fallback method to extract roles from users.");
                return getRealmRolesFromUsers();
            }
            
            monitor.severe("Failed to get realm roles: " + errorMsg, e);
            return Result.failure("Failed to get realm roles: " + errorMsg + 
                    ". Ensure the Keycloak admin client has 'view-realm-roles' permission.");
        }
    }

    /**
     * Fallback method: Extract unique realm roles from all users' assigned roles.
     * This works even if the client doesn't have direct permission to list roles,
     * as long as it can view users and their role assignments.
     */
    private Result<List<RoleRepresentation>> getRealmRolesFromUsers() {
        try {
            RealmResource realm = keycloak.realm(realmName);
            UsersResource usersResource = realm.users();
            
            // Get all users
            List<UserRepresentation> users = usersResource.list(0, 100); // Limit to first 100 users
            
            // Collect unique role names from all users
            java.util.Set<String> uniqueRoleNames = new java.util.HashSet<>();
            
            for (UserRepresentation user : users) {
                try {
                    UserResource userResource = usersResource.get(user.getId());
                    List<RoleRepresentation> userRoles = userResource.roles().realmLevel().listAll();
                    userRoles.forEach(role -> uniqueRoleNames.add(role.getName()));
                } catch (Exception e) {
                    monitor.debug("Could not fetch roles for user " + user.getId() + ": " + e.getMessage());
                    // Continue with other users
                }
            }
            
            // Convert role names to RoleRepresentation objects
            // Note: We only have names, not full role details, but this is better than nothing
            List<RoleRepresentation> roles = uniqueRoleNames.stream()
                    .map(roleName -> {
                        RoleRepresentation role = new RoleRepresentation();
                        role.setName(roleName);
                        // Try to get full role details if possible
                        try {
                            RoleRepresentation fullRole = realm.roles().get(roleName).toRepresentation();
                            return fullRole;
                        } catch (Exception e) {
                            // If we can't get full details, return basic role with just name
                            return role;
                        }
                    })
                    .collect(Collectors.toList());
            
            monitor.info("Retrieved " + roles.size() + " realm roles using fallback method (extracted from users)");
            return Result.success(roles);
            
        } catch (Exception e) {
            monitor.severe("Fallback method also failed to get realm roles: " + e.getMessage(), e);
            return Result.failure("Failed to get realm roles. Direct listing requires 'view-realm-roles' permission. " +
                    "Fallback method also failed: " + e.getMessage());
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

