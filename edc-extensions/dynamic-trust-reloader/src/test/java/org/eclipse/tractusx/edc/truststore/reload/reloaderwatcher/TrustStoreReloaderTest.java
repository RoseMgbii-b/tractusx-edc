package org.eclipse.tractusx.edc.truststore.reload.reloaderwatcher;

import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.tractusx.edc.truststore.reload.reloadabletrustmanager.ReloadableX509TrustManager;
import org.junit.jupiter.api.Test;
import javax.net.ssl.X509TrustManager;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import static org.mockito.Mockito.*;
import org.eclipse.tractusx.edc.truststore.reload.tmf.TrustManagerFactoryHelper;
import org.mockito.MockedStatic;

class TrustStoreReloaderTest {

    @Test
    void reload_shouldReplaceDelegateWhenValid() {
        Monitor monitor = mock(Monitor.class);
        ReloadableX509TrustManager reloadable = mock(ReloadableX509TrustManager.class);

        // mock certificate
        X509Certificate cert = mock(X509Certificate.class);
        X509TrustManager validManager = mock(X509TrustManager.class);
        when(validManager.getAcceptedIssuers()).thenReturn(new X509Certificate[]{cert});

        TrustStoreReloader reloader = new TrustStoreReloader(
                monitor, reloadable, Path.of("dummy"), "p".toCharArray(), TrustStoreReloader.StoreType.JKS);

        try (MockedStatic<TrustManagerFactoryHelper> mocked = mockStatic(TrustManagerFactoryHelper.class)) {
            mocked.when(() -> TrustManagerFactoryHelper.createFromJksFile(any(), any(), any()))
                    .thenReturn(validManager);

            reloader.reload();

            verify(reloadable).replaceDelegate(validManager);
            verify(monitor).info(contains("Successfully reloaded"));
        }
    }

    @Test
    void reload_shouldNotReplaceDelegateWhenInvalid() {
        Monitor monitor = mock(Monitor.class);
        ReloadableX509TrustManager reloadable = mock(ReloadableX509TrustManager.class);

        X509TrustManager invalidManager = mock(X509TrustManager.class);
        when(invalidManager.getAcceptedIssuers()).thenReturn(new X509Certificate[0]); // empty array = invalid

        TrustStoreReloader reloader = new TrustStoreReloader(
                monitor, reloadable, Path.of("dummy"), "p".toCharArray(), TrustStoreReloader.StoreType.JKS);

        try (MockedStatic<TrustManagerFactoryHelper> mocked = mockStatic(TrustManagerFactoryHelper.class)) {
            mocked.when(() -> TrustManagerFactoryHelper.createFromJksFile(any(), any(), any()))
                    .thenReturn(invalidManager);

            reloader.reload();

            verify(reloadable, never()).replaceDelegate(any());
            verify(monitor).warning(contains("Validation failed"));
        }
    }
}

