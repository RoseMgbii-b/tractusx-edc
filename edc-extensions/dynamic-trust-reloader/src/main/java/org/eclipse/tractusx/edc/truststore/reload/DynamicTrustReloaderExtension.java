package org.eclipse.tractusx.edc.truststore.reload;

import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import java.util.concurrent.ScheduledExecutorService;
import javax.net.ssl.X509TrustManager;
import java.nio.file.Path;

import org.eclipse.tractusx.edc.truststore.reload.reloaderwatcher.TrustStoreFileWatcher;
import org.eclipse.tractusx.edc.truststore.reload.reloaderwatcher.TrustStoreReloader;
import org.eclipse.tractusx.edc.truststore.reload.reloadabletrustmanager.ReloadableX509TrustManager;
import org.eclipse.tractusx.edc.truststore.reload.tmf.TrustManagerFactoryHelper;

import static org.eclipse.tractusx.edc.truststore.reload.DynamicTrustReloaderExtension.NAME;

@Extension (value = NAME)
public class DynamicTrustReloaderExtension implements ServiceExtension {
    public static final String NAME = "Dynamic Trust Store Reloader Extension";

    private Monitor monitor;
    private ReloadableX509TrustManager reloadableX509TrustManager;
    private ScheduledExecutorService executor;
    private TrustStoreReloader reloader;
    private TrustStoreFileWatcher watcher;


    @Override
    public void initialize (ServiceExtensionContext context){
        this.monitor = context.getMonitor();

        var contextConfig = context.getConfig();
        String path = contextConfig.getString("dynamic.truststore.path", null);

        if (path == null || path.isBlank()) {
            monitor.info("[DynamicTrustReloaderExtension] dynamic.truststore.path not set, skipping dynamic reload.");
            return;
        }

        Path trustPath = Path.of(path);
        String trustType = contextConfig.getString("dynamic.truststore.type", "JKS");
        char[] password = contextConfig.getString("dynamic.truststore.password", "").toCharArray();

        monitor.info("[DynamicTrustReloaderExtension] Watching path: " + trustPath.toAbsolutePath());


        TrustStoreReloader.StoreType storeType;
        try {
            storeType = TrustStoreReloader.StoreType.valueOf(trustType);
        } catch (IllegalArgumentException e) {
            monitor.severe("[DynamicTrustReloaderExtension] Invalid dynamic.truststore.type: "
                    + trustType + ". Supported types are: JKS, PKCS12, PEM_DIR");
            return;
        }

        try {
            // initial lload
            X509TrustManager initial =
                    switch (storeType) {
                        case JKS ->
                            TrustManagerFactoryHelper.createFromJksFile(trustPath, password, monitor);
                        case PKCS12 ->
                            TrustManagerFactoryHelper.createFromPkcs12File(trustPath, password, monitor);
                        case PEM_DIR ->
                            TrustManagerFactoryHelper.createFromPemDirectory(trustPath, monitor);
                    };
            reloadableX509TrustManager = new ReloadableX509TrustManager((initial));
            reloader = new TrustStoreReloader(monitor, reloadableX509TrustManager, trustPath, password, storeType);

            // START FILE WATCHER
            watcher = new TrustStoreFileWatcher(
                    trustPath,
                    () -> reloader.reload(),     // callback when file changes
                    monitor
            );

            watcher.start();
            context.registerService(TrustStoreFileWatcher.class, watcher);
            monitor.info("[DynamicTrustReloaderExtension] File watcher started on " + trustPath);


            // register for injection
            context.registerService(X509TrustManager.class, reloadableX509TrustManager);
        } catch (Exception e) {
            monitor.severe
                    ("[DynamicTrustReloaderExtension] Failed to load initial truststore, " +
                            "(failed to initialize dynamic trust store reloader): " +
                            e.getMessage(), e
                    );
            throw new IllegalStateException(e);
        }
    }

    public void shutdown() {
        if (executor != null) {
            executor.shutdownNow();
        }

        if (watcher != null) {
            watcher.stop();
        }

        monitor.info("[DynamicTrustReloaderExtension] Dynamic Trust Store Reloader shut down.");
    }

}
