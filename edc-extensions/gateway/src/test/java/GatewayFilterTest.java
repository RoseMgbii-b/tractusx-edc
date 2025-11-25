import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.tractusx.gateway.AccessControlResult;
import org.eclipse.tractusx.gateway.GatewayFilter;
import org.eclipse.tractusx.gateway.GatewayService;
import org.eclipse.tractusx.gateway.RequestInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GatewayFilterTest {

    @Mock
    private GatewayService gatewayService;
    @Mock
    private Monitor monitor;
    @Mock
    private ContainerRequestContext requestContext;
    @Mock
    private UriInfo uriInfo;

    private GatewayFilter filter;

    @BeforeEach
    void setUp() {
        filter = new GatewayFilter(gatewayService, monitor);
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getRequestUri()).thenReturn(URI.create("http://example.com/api/test"));
        when(requestContext.getMethod()).thenReturn("GET");
        when(requestContext.getHeaderString("X-Forwarded-For")).thenReturn("10.0.0.5");
        when(requestContext.getHeaders()).thenReturn(new MultivaluedHashMap<>());
        when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer token");
    }

    @Test
    void filter_whenAccessAllowed_shouldContinue() throws Exception {
        when(gatewayService.evaluateRequest(any(RequestInfo.class))).thenReturn(AccessControlResult.allowed("ok"));

        filter.filter(requestContext);

        verify(requestContext, never()).abortWith(any());
        verify(monitor).debug(contains("Access allowed"));
    }

    @Test
    void filter_whenAccessDenied_shouldAbortWithForbidden() throws Exception {
        when(gatewayService.evaluateRequest(any(RequestInfo.class))).thenReturn(AccessControlResult.denied("blocked"));

        filter.filter(requestContext);

        verify(requestContext).abortWith(argThat(response -> {
            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(response.getEntity()).isInstanceOf(Map.class);
            @SuppressWarnings("unchecked")
            var entity = (Map<String, String>) response.getEntity();
            assertThat(entity).containsEntry("error", "blocked");
            return true;
        }));
        verify(monitor).warning(contains("Access denied"));
    }

    @Test
    void filter_whenGatewayThrows_shouldFailOpen() throws Exception {
        when(gatewayService.evaluateRequest(any(RequestInfo.class))).thenThrow(new RuntimeException("boom"));

        filter.filter(requestContext);

        verify(requestContext, never()).abortWith(any());
        verify(monitor).warning(contains("Access control error"));
    }
}
