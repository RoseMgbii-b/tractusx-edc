package org.eclipse.tractusx.edc.truststore.reload.reloaderwatcher;

import org.eclipse.tractusx.edc.truststore.reload.reloadabletrustmanager.ReloadableX509TrustManager;

import java.nio.file.Path;
import org.eclipse.tractusx.edc.truststore.reload.tmf.TrustManagerFactoryHelper;
import org.eclipse.edc.spi.monitor.Monitor;

import javax.net.ssl.X509TrustManager;

/**
 * The TrustStore Reloader class
 * Reads the truststore from disk and swaps/updates the delegate in a ReloadableX509TrustManager
 */
public class TrustStoreReloader {
    public enum StoreType { JKS, PKCS12, PEM_DIR }

    private final Monitor monitor;
    private final ReloadableX509TrustManager reloadableX509TrustManager;
    private final Path path;
    private final char[] password;
    private final StoreType storeType;

    // Constructor
    public TrustStoreReloader(Monitor monitor, ReloadableX509TrustManager reloadableX509TrustManager, Path path, char[] password, StoreType storeType) {
        this.monitor = monitor;
        this.reloadableX509TrustManager = reloadableX509TrustManager;
        this.path = path;
        this.password = password;
        this.storeType = storeType;
    }

    public void reload() {
        monitor.info("[TrustStoreReloader] Reloading truststore from: " + path);
        try {
            X509TrustManager trustManagerCandidate;
            switch (storeType) {
                case JKS:
                    trustManagerCandidate = TrustManagerFactoryHelper.createFromJksFile(path, password, monitor);
                    break;
                case PKCS12:
                    trustManagerCandidate = TrustManagerFactoryHelper.createFromPkcs12File(path, password, monitor);
                    break;
                case PEM_DIR:
                    trustManagerCandidate = TrustManagerFactoryHelper.createFromPemDirectory(path, monitor);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported store type: " + storeType);
            }

            // validate the new trust manager candidate beforw swapping
            if (isValidTrustManager(trustManagerCandidate)) {
                reloadableX509TrustManager.replaceDelegate(trustManagerCandidate);
                monitor.info("[TrustStoreReloader] Successfully reloaded and applied truststore.");
            } else {
                monitor.warning("[TrustStoreReloader] Validation failed — keeping previous truststore.");
            }

        } catch (Exception e) {
            monitor.severe("[TrustStoreReloader] Failed to reload truststore: " + e.getMessage(), e);
        }
    }

    /**
     * Validate TrustManager
     * Basic validation to ensure trustmanager candidate has at least one accepted issuer
     *
     * @param trustManagerCandidate  the new trustmanager (the trustmanager candidate)
     * @return              true/false
     */
    private boolean isValidTrustManager(X509TrustManager trustManagerCandidate) {
        // conditions to ensure the new trustmanage is not null or empty,
        // and ensure only a new trustmanager with at least one CA issuer configured is accepted and swapped in
        return trustManagerCandidate != null &&
                trustManagerCandidate.getAcceptedIssuers() != null &&
                trustManagerCandidate.getAcceptedIssuers().length > 0;
    }
}
