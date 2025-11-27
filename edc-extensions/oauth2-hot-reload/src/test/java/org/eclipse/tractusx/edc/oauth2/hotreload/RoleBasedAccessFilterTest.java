package org.eclipse.tractusx.edc.oauth2.hotreload;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.edc.spi.monitor.Monitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleBasedAccessFilterTest {

    @Mock
    private Monitor monitor;
    @Mock
    private ContainerRequestContext requestContext;
    @Mock
    private UriInfo uriInfo;

    private RoleBasedAccessFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RoleBasedAccessFilter(monitor);
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getRequestUri()).thenReturn(URI.create("http://localhost/api/v1/business-partner-groups"));
    }

    @Test
    void filter_whenGetRequest_shouldSkipRbac() throws Exception {
        when(requestContext.getMethod()).thenReturn("GET");
        when(uriInfo.getPath()).thenReturn("v1/business-partner-groups");

        filter.filter(requestContext);

        verify(requestContext, never()).abortWith(any());
    }

    @Test
    void filter_whenNoRoles_shouldAbortForbidden() throws Exception {
        when(requestContext.getMethod()).thenReturn("POST");
        when(uriInfo.getPath()).thenReturn("v1/business-partner-groups");
        when(requestContext.getProperty("edc.jwt.claims")).thenReturn(Map.of());

        filter.filter(requestContext);

        verify(requestContext).abortWith(argThat(response -> {
            assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
            return true;
        }));
        verify(monitor).warning(contains("No roles found"));
    }

    @Test
    void filter_whenUserHasRequiredRole_shouldAllow() throws Exception {
        when(requestContext.getMethod()).thenReturn("POST");
        when(uriInfo.getPath()).thenReturn("v1/business-partner-groups");
        when(requestContext.getProperty("edc.jwt.claims")).thenReturn(
                Map.of("realm_access", Map.of("roles", List.of("ADMIN")))
        );

        filter.filter(requestContext);

        verify(requestContext, never()).abortWith(any());
        verify(monitor).debug(contains("Access granted"));
    }

    @Test
    void filter_whenPathNotConfigured_shouldAllowByDefault() throws Exception {
        when(requestContext.getMethod()).thenReturn("POST");
        when(uriInfo.getPath()).thenReturn("v1/other");
        when(requestContext.getProperty("edc.jwt.claims")).thenReturn(
                Map.of("realm_access", Map.of("roles", List.of("ANY_ROLE")))
        );

        filter.filter(requestContext);

        verify(requestContext, never()).abortWith(any());
        verify(monitor).debug(contains("No RBAC rule"));
    }
}

