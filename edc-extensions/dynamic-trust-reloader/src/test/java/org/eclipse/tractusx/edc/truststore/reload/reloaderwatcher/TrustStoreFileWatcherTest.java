package org.eclipse.tractusx.edc.truststore.reload.reloaderwatcher;

import org.eclipse.edc.spi.monitor.Monitor;
import org.junit.jupiter.api.*;import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.mockito.Mockito.*;

class TrustStoreFileWatcherTest {

    private Path tempFile;
    private Monitor monitor;

    @BeforeEach
    void setUp() throws Exception {
        tempFile = Files.createTempFile("trust", ".p12");
        monitor = mock(Monitor.class);
    }

    @Test
    void debounceReload_shouldCallListenerOnce() throws Exception {
        var listener = mock(TrustStoreFileWatcher.ChangeListener.class);
        var watcher = new TrustStoreFileWatcher(tempFile, listener, monitor);

        watcher.start();
        Files.writeString(tempFile, "x1");
        Files.writeString(tempFile, "x2");
        Files.writeString(tempFile, "x3");

        TimeUnit.MILLISECONDS.sleep(900);

        verify(listener, times(1)).onTrustStoreChanged();

        watcher.stop();
    }

    @Test
    void deletion_shouldNotTriggerReload() throws Exception {
        var listener = mock(TrustStoreFileWatcher.ChangeListener.class);
        var watcher = new TrustStoreFileWatcher(tempFile, listener, monitor);

        watcher.start();
        Files.delete(tempFile);

        TimeUnit.MILLISECONDS.sleep(500);

        verify(listener, never()).onTrustStoreChanged();

        watcher.stop();
    }
}

