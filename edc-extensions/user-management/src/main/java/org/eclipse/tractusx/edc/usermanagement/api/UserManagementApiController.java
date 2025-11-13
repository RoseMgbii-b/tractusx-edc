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

package org.eclipse.tractusx.edc.usermanagement.api;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.tractusx.edc.usermanagement.KeycloakUserService;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import java.util.List;
import java.util.stream.Collectors;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Path("/v3/users")
public class UserManagementApiController {

    private final KeycloakUserService userService;
    private final Monitor monitor;

    public UserManagementApiController(KeycloakUserService userService, Monitor monitor) {
        this.userService = userService;
        this.monitor = monitor;
    }

    /**
     * Create a new user with temporary password
     */
    @POST
    public Response createUser(CreateUserRequest request) {
        if (request == null || request.getUsername() == null || request.getTemporaryPassword() == null) {
            return Response.status(BAD_REQUEST)
                    .entity("{\"message\":\"Username and temporaryPassword are required\"}")
                    .build();
        }

        Result<UserRepresentation> result = userService.createUser(
                request.getUsername(),
                request.getEmail(),
                request.getFirstName(),
                request.getLastName(),
                request.getTemporaryPassword(),
                request.getRoles()
        );

        if (result.succeeded()) {
            UserRepresentation user = result.getContent();
            UserDto dto = toDto(user);
            return Response.status(Response.Status.CREATED)
                    .entity(dto)
                    .build();
        } else {
            return Response.status(BAD_REQUEST)
                    .entity("{\"message\":\"" + result.getFailureDetail() + "\"}")
                    .build();
        }
    }

    /**
     * Update user information
     */
    @PUT
    @Path("/{id}")
    public Response updateUser(@PathParam("id") String id, UpdateUserRequest request) {
        if (request == null) {
            return Response.status(BAD_REQUEST)
                    .entity("{\"message\":\"Request body is required\"}")
                    .build();
        }

        Result<UserRepresentation> result = userService.updateUser(
                id,
                request.getEmail(),
                request.getFirstName(),
                request.getLastName(),
                request.getEnabled(),
                request.getRoles()
        );

        if (result.succeeded()) {
            UserRepresentation user = result.getContent();
            UserDto dto = toDto(user);
            return Response.ok(dto).build();
        } else {
            if (result.getFailureDetail().contains("not found")) {
                return Response.status(NOT_FOUND)
                        .entity("{\"message\":\"" + result.getFailureDetail() + "\"}")
                        .build();
            }
            return Response.status(BAD_REQUEST)
                    .entity("{\"message\":\"" + result.getFailureDetail() + "\"}")
                    .build();
        }
    }

    /**
     * Disable a user
     */
    @PUT
    @Path("/{id}/disable")
    public Response disableUser(@PathParam("id") String id) {
        Result<Void> result = userService.disableUser(id);

        if (result.succeeded()) {
            return Response.ok("{\"message\":\"User disabled successfully\"}").build();
        } else {
            if (result.getFailureDetail().contains("not found")) {
                return Response.status(NOT_FOUND)
                        .entity("{\"message\":\"" + result.getFailureDetail() + "\"}")
                        .build();
            }
            return Response.status(BAD_REQUEST)
                    .entity("{\"message\":\"" + result.getFailureDetail() + "\"}")
                    .build();
        }
    }

    /**
     * Enable a user
     */
    @PUT
    @Path("/{id}/enable")
    public Response enableUser(@PathParam("id") String id) {
        Result<Void> result = userService.enableUser(id);

        if (result.succeeded()) {
            return Response.ok("{\"message\":\"User enabled successfully\"}").build();
        } else {
            if (result.getFailureDetail().contains("not found")) {
                return Response.status(NOT_FOUND)
                        .entity("{\"message\":\"" + result.getFailureDetail() + "\"}")
                        .build();
            }
            return Response.status(BAD_REQUEST)
                    .entity("{\"message\":\"" + result.getFailureDetail() + "\"}")
                    .build();
        }
    }

    /**
     * Get all users
     */
    @GET
    public Response getAllUsers() {
        Result<List<UserRepresentation>> result = userService.getAllUsers();

        if (result.succeeded()) {
            List<UserDto> users = result.getContent().stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());
            return Response.ok(users).build();
        } else {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"message\":\"" + result.getFailureDetail() + "\"}")
                    .build();
        }
    }

    /**
     * Get user by ID
     */
    @GET
    @Path("/{id}")
    public Response getUserById(@PathParam("id") String id) {
        Result<UserRepresentation> result = userService.getUserById(id);

        if (result.succeeded()) {
            UserRepresentation user = result.getContent();
            UserDto dto = toDto(user);
            return Response.ok(dto).build();
        } else {
            if (result.getFailureDetail().contains("not found")) {
                return Response.status(NOT_FOUND)
                        .entity("{\"message\":\"" + result.getFailureDetail() + "\"}")
                        .build();
            }
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"message\":\"" + result.getFailureDetail() + "\"}")
                    .build();
        }
    }

    /**
     * Get all available realm roles
     */
    @GET
    @Path("/roles")
    public Response getRealmRoles() {
        Result<List<RoleRepresentation>> result = userService.getRealmRoles();

        if (result.succeeded()) {
            List<RoleDto> roles = result.getContent().stream()
                    .map(this::toRoleDto)
                    .collect(Collectors.toList());
            return Response.ok(roles).build();
        } else {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"message\":\"" + result.getFailureDetail() + "\"}")
                    .build();
        }
    }

    /**
     * Convert UserRepresentation to UserDto
     */
    private UserDto toDto(UserRepresentation user) {
        // Extract roles - UserRepresentation.getRealmRoles() returns List<String>
        List<String> roles = user.getRealmRoles() != null ? user.getRealmRoles() : List.of();
        
        return new UserDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.isEnabled(),
                user.isEmailVerified(),
                roles
        );
    }

    /**
     * Convert RoleRepresentation to RoleDto
     */
    private RoleDto toRoleDto(RoleRepresentation role) {
        return new RoleDto(
                role.getId(),
                role.getName(),
                role.getDescription(),
                role.isComposite()
        );
    }
}

