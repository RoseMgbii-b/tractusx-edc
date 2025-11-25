import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.ApiContext;
import org.eclipse.tractusx.gateway.GatewayExtension;
import org.eclipse.tractusx.gateway.GatewayFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.mockito.Mockito.*;

@ExtendWith(DependencyInjectionExtension.class)
public class GatewayExtensionTest {

    private final WebService webService = mock();

    @BeforeEach
    void setUp(ServiceExtensionContext context) {
        context.registerService(WebService.class, webService);
    }

    @Test
    void initialize_shouldRegisterFilterForAllContexts(GatewayExtension extension,
                                                       ServiceExtensionContext context) {
        extension.initialize(context);

        verify(webService, times(1)).registerResource(eq(ApiContext.PROTOCOL), any(GatewayFilter.class));
        verify(webService, times(1)).registerResource(eq(ApiContext.MANAGEMENT), any(GatewayFilter.class));
        verify(webService, times(1)).registerResource(eq(ApiContext.CONTROL), any(GatewayFilter.class));
        verify(webService, times(1)).registerResource(eq(ApiContext.PUBLIC), any(GatewayFilter.class));
    }
}
