package org.eclipse.tractusx.edc.usermanagement;

import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.ApiContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserManagementExtensionTest {

    @Mock
    private WebService webService;
    @Mock
    private Monitor monitor;
    @Mock
    private ServiceExtensionContext context;

    private UserManagementExtension extension;

    @BeforeEach
    void setUp() throws Exception {
        extension = new UserManagementExtension();
        setField("webService", webService);
        setField("monitor", monitor);
    }

    @Test
    void initialize_shouldRegisterController_whenConfigProvided() throws Exception {
        mockConfigValues();

        try (MockedConstruction<KeycloakUserService> ignored = mockConstruction(KeycloakUserService.class)) {
            extension.initialize(context);

            verify(webService).registerResource(eq(ApiContext.MANAGEMENT), isA(UserManagementApiController.class));
            verify(monitor).info(contains("User Management Extension started successfully"));
        }
    }

    @Test
    void initialize_shouldFallbackToIssuer_whenServerAndRealmMissing() throws Exception {
        when(context.getSetting(eq(UserManagementExtension.KEYCLOAK_SERVER_URL), nullable(String.class))).thenReturn(null);
        when(context.getSetting(eq(UserManagementExtension.KEYCLOAK_REALM), nullable(String.class))).thenReturn(null);
        when(context.getSetting(eq("edc.oauth.provider.issuer"), nullable(String.class)))
                .thenReturn("https://kc.example.com/realms/demo");
        when(context.getSetting(eq(UserManagementExtension.KEYCLOAK_CLIENT_ID), nullable(String.class))).thenReturn("client");
        when(context.getSetting(eq(UserManagementExtension.KEYCLOAK_CLIENT_SECRET), nullable(String.class))).thenReturn("secret");

        try (MockedConstruction<KeycloakUserService> ignored = mockConstruction(KeycloakUserService.class)) {
            extension.initialize(context);

            verify(webService).registerResource(eq(ApiContext.MANAGEMENT), isA(UserManagementApiController.class));
        }
    }

    @Test
    void initialize_whenClientSecretMissing_shouldThrow() {
        when(context.getSetting(eq(UserManagementExtension.KEYCLOAK_SERVER_URL), nullable(String.class))).thenReturn("https://kc");
        when(context.getSetting(eq(UserManagementExtension.KEYCLOAK_REALM), nullable(String.class))).thenReturn("realm");
        when(context.getSetting(eq(UserManagementExtension.KEYCLOAK_CLIENT_ID), nullable(String.class))).thenReturn("client");
        when(context.getSetting(eq(UserManagementExtension.KEYCLOAK_CLIENT_SECRET), nullable(String.class))).thenReturn(null);

        assertThatThrownBy(() -> extension.initialize(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("client secret");
    }

    private void mockConfigValues() {
        when(context.getSetting(eq(UserManagementExtension.KEYCLOAK_SERVER_URL), nullable(String.class)))
                .thenReturn("https://kc");
        when(context.getSetting(eq(UserManagementExtension.KEYCLOAK_REALM), nullable(String.class)))
                .thenReturn("realm");
        when(context.getSetting(eq(UserManagementExtension.KEYCLOAK_CLIENT_ID), nullable(String.class)))
                .thenReturn("client");
        when(context.getSetting(eq(UserManagementExtension.KEYCLOAK_CLIENT_SECRET), nullable(String.class)))
                .thenReturn("secret");
        when(context.getSetting(eq("edc.oauth.provider.issuer"), nullable(String.class)))
                .thenReturn(null);
    }

    private void setField(String name, Object value) throws Exception {
        var field = UserManagementExtension.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(extension, value);
    }
}

