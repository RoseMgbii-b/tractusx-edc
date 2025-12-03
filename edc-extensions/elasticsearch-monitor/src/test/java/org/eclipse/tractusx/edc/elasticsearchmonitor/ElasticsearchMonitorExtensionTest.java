package org.eclipse.tractusx.edc.elasticsearchmonitor;

import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.Config;
import org.eclipse.edc.spi.types.TypeManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;


public class ElasticsearchMonitorExtensionTest {
    private ElasticsearchMonitorExtension extension;
    private Monitor fallback;
    private EdcHttpClient httpClient;
    private TypeManager typeManager;
    private ServiceExtensionContext context;

    private static void injectField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void setup() {
        fallback = mock(Monitor.class);
        httpClient = mock(EdcHttpClient.class);
        typeManager = mock(TypeManager.class);
        context = mock(ServiceExtensionContext.class);

        extension = new ElasticsearchMonitorExtension();
        // simulating dependency injection
        injectField(extension, "fallbackMonitor", fallback);
        injectField(extension, "httpClient", httpClient);
        injectField(extension, "typeManager", typeManager);
    }

    @Test
    void initialize_disabled_config_shouldNotRegisterMonitor() {
        extension.setEnabled(false);

        extension.initialize(context);

        verify(fallback).debug(contains("disabled"));
        verify(context, never()).registerService(eq(Monitor.class), any());
    }

    @Test
    void initialize_missingIndexUrl_shouldRefuseEnable() {
       extension.setEnabled(true);

        // mock Config not ServiceExtensionContext
        Config config = mock(Config.class);
        when(context.getConfig()).thenReturn(config);

        // return null index URL
        when(config.getString(anyString(), isNull())).thenReturn(null);
        extension.initialize(context);

        verify(fallback).warning(startsWith("[ElasticsearchMonitorExtension] No index URL provided. "));
        verify(context, never()).registerService(eq(Monitor.class), any());
    }

    @Test
    void initialize_validConfig_shouldRegisterMonitor() {
        extension.setEnabled(true);

        // mock Config not ServiceExtensionContext
        Config config = mock(Config.class);
        when(context.getConfig()).thenReturn(config);

        when(config.getString(anyString(), isNull())).thenReturn("http://my-es-index");

        extension.initialize(context);

        // Should log the configuration
        verify(fallback).info(contains("index URL"));

        // Should install custom monitor
        verify(context).registerService(eq(Monitor.class), any(ElasticsearchMonitor.class));
    }

}
