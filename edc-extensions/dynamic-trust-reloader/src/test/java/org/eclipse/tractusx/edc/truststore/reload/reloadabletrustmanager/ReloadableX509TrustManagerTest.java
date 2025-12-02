package org.eclipse.tractusx.edc.truststore.reload.reloadabletrustmanager;


import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

import static org.assertj.core.api.Assertions.*;

class ReloadableX509TrustManagerTest {

    @Test
    void constructor_shouldSetInitialDelegate() {
        var initial = Mockito.mock(X509TrustManager.class);

        var manager = new ReloadableX509TrustManager(initial);

        assertThat(manager.getCurrentDelegate()).isEqualTo(initial);
    }

    @Test
    void replaceDelegate_shouldSwapDelegate() {
        var initial = Mockito.mock(X509TrustManager.class);
        var next = Mockito.mock(X509TrustManager.class);

        var manager = new ReloadableX509TrustManager(initial);
        manager.replaceDelegate(next);

        assertThat(manager.getCurrentDelegate()).isEqualTo(next);
    }

    @Test
    void checkServerTrusted_shouldDelegate() throws Exception {
        var initial = Mockito.mock(X509TrustManager.class);
        var manager = new ReloadableX509TrustManager(initial);

        X509Certificate[] chain = new X509Certificate[0];
        manager.checkServerTrusted(chain, "RSA");

        Mockito.verify(initial).checkServerTrusted(chain, "RSA");
    }

    @Test
    void getAcceptedIssuers_shouldDelegate() {
        var initial = Mockito.mock(X509TrustManager.class);
        var manager = new ReloadableX509TrustManager(initial);

        manager.getAcceptedIssuers();
        Mockito.verify(initial).getAcceptedIssuers();
    }
}
