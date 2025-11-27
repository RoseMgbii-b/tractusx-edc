package org.eclipse.tractusx.edc.oauth2.hotreload;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.edc.spi.monitor.Monitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HttpsEnforcementFilterTest {

    @Mock
    private Monitor monitor;
    @Mock
    private ContainerRequestContext requestContext;
    @Mock
    private UriInfo uriInfo;
    @Mock
    private SecurityContext securityContext;

    @BeforeEach
    void setUp() {
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(requestContext.getSecurityContext()).thenReturn(securityContext);
    }

    @Test
    void filter_whenRequestAlreadySecure_shouldDoNothing() throws Exception {
        when(uriInfo.getRequestUri()).thenReturn(URI.create("https://example.com/api"));
        when(securityContext.isSecure()).thenReturn(true);

        var filter = new HttpsEnforcementFilter(monitor, false, 8443);

        filter.filter(requestContext);

        verify(requestContext, never()).abortWith(any());
    }

    @Test
    void filter_whenHttpAndRedirectEnabled_shouldRedirect() throws Exception {
        when(uriInfo.getRequestUri()).thenReturn(URI.create("http://example.com/api"));
        when(securityContext.isSecure()).thenReturn(false);

        var filter = new HttpsEnforcementFilter(monitor, true, 9443);
        filter.filter(requestContext);

        verify(requestContext).abortWith(argThat(response -> {
            assertThat(response.getStatus()).isEqualTo(Response.Status.MOVED_PERMANENTLY.getStatusCode());
            assertThat(response.getLocation()).isEqualTo(URI.create("https://example.com:9443/api"));
            return true;
        }));
    }

    @Test
    void filter_whenHttpAndRedirectDisabled_shouldReturnForbidden() throws Exception {
        when(uriInfo.getRequestUri()).thenReturn(URI.create("http://example.com/api"));
        when(securityContext.isSecure()).thenReturn(false);

        var filter = new HttpsEnforcementFilter(monitor, false, 0);
        filter.filter(requestContext);

        verify(requestContext).abortWith(argThat(response -> {
            assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
            assertThat(response.getEntity().toString()).contains("HTTPS required");
            return true;
        }));
    }
}

