import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.configuration.Config;
import org.eclipse.tractusx.gateway.service.GatewayService;
import org.eclipse.tractusx.gateway.rate.limit.RateLimitStore;
import org.eclipse.tractusx.gateway.model.RequestInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GatewayServiceTest {

    @Mock
    private RateLimitStore store;
    @Mock private Monitor monitor;
    @Mock private Config config;

    private GatewayService service;

    @BeforeEach
    void setUp() {
        when(config.getBoolean("edc.gateway.enabled", true)).thenReturn(true);
        when(config.getString("edc.gateway.ip.whitelist", "")).thenReturn("");
        when(config.getString("edc.gateway.ip.blacklist", "")).thenReturn("");
        when(config.getLong("edc.gateway.rate.limit.per.minute", 100L)).thenReturn(2L);
        when(config.getLong("edc.gateway.rate.limit.per.hour", 1000L)).thenReturn(5L);
        when(config.getLong("edc.gateway.request.max.size.bytes", 10 * 1024 * 1024L)).thenReturn(1024L);

        service = new GatewayService(store, monitor, config);
    }

    @Test
    void evaluateRequest_whenRateLimitExceeded_shouldDeny() {
        when(store.checkRateLimit("10.0.0.5", 2L, 60L)).thenReturn(false);

        var result = service.evaluateRequest(request("10.0.0.5"));

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getReason()).contains("Rate limit");
        verify(store).checkRateLimit("10.0.0.5", 2L, 60L);
        verify(store, never()).recordRequest(anyString());
    }

    private RequestInfo request(String ip) {
        return new RequestInfo(ip, "/api/assets", "GET", Map.of(), Instant.now(), null);
    }
}
