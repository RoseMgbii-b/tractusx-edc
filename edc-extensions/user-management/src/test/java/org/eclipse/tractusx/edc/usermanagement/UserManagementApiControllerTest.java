package org.eclipse.tractusx.edc.usermanagement;

import jakarta.ws.rs.core.Response;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.tractusx.edc.usermanagement.dto.UserDto;
import org.eclipse.tractusx.edc.usermanagement.request.CreateUserRequest;
import org.eclipse.tractusx.edc.usermanagement.request.UpdateUserRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserManagementApiControllerTest {

    @Mock
    private KeycloakUserService userService;
    @Mock
    private Monitor monitor;

    private UserManagementApiController controller;

    @BeforeEach
    void setUp() {
        controller = new UserManagementApiController(userService, monitor);
    }

    @Test
    void createUser_whenSuccess_shouldReturnCreatedDto() {
        var request = new CreateUserRequest("alice", "alice@example.com", "Alice", "Doe", "tempPass", List.of("ADMIN"));
        var userRepresentation = createUser("1", "alice");
        when(userService.createUser(any(), any(), any(), any(), any(), any())).thenReturn(Result.success(userRepresentation));

        Response response = controller.createUser(request);

        assertThat(response.getStatus()).isEqualTo(Response.Status.CREATED.getStatusCode());
        assertThat(response.getEntity()).isInstanceOf(UserDto.class);
        var dto = (UserDto) response.getEntity();
        assertThat(dto.getUsername()).isEqualTo("alice");
        assertThat(dto.getRoles()).containsExactly("ADMIN");
    }

    @Test
    void createUser_whenRequestInvalid_shouldReturnBadRequest() {
        Response response = controller.createUser(null);

        assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
    }

    @Test
    void updateUser_whenNotFound_shouldReturn404() {
        when(userService.updateUser(eq("missing"), any(), any(), any(), any(), any()))
                .thenReturn(Result.failure("User not found"));

        Response response = controller.updateUser("missing", new UpdateUserRequest(null, null, null, null, null));

        assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
    }

    @Test
    void updateUser_whenSuccess_shouldReturnUpdatedUser() {
        var updated = createUser("2", "bob");
        when(userService.updateUser(eq("2"), any(), any(), any(), any(), any()))
                .thenReturn(Result.success(updated));

        Response response = controller.updateUser("2", new UpdateUserRequest("bob@example.com", "Bob", "Builder", true, List.of("VIEWER")));

        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        var dto = (UserDto) response.getEntity();
        assertThat(dto.getUsername()).isEqualTo("bob");
    }

    @Test
    void disableUser_whenFailure_shouldPropagateStatus() {
        when(userService.disableUser("abc")).thenReturn(Result.failure("User abc not found"));

        Response response = controller.disableUser("abc");

        assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
    }

    @Test
    void getAllUsers_whenSuccess_shouldReturnDtos() {
        when(userService.getAllUsers()).thenReturn(Result.success(List.of(createUser("1", "alice"))));

        Response response = controller.getAllUsers();

        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        @SuppressWarnings("unchecked")
        var users = (List<UserDto>) response.getEntity();
        assertThat(users).hasSize(1);
        assertThat(users.get(0).getUsername()).isEqualTo("alice");
    }

    @Test
    void getRealmRoles_whenFailure_shouldReturnServerError() {
        when(userService.getRealmRoles()).thenReturn(Result.failure("Keycloak unreachable"));

        Response response = controller.getRealmRoles();

        assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
    }

    private static UserRepresentation createUser(String id, String username) {
        var user = new UserRepresentation();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setFirstName(username);
        user.setLastName("Test");
        user.setEnabled(true);
        user.setEmailVerified(false);
        user.setRealmRoles(List.of("ADMIN"));
        return user;
    }

}

