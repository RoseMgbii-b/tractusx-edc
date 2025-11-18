package org.eclipse.tractusx.edc.truststore.reload.reloadabletrustmanager;


import javax.net.ssl.X509TrustManager;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;


/**
 * ReloadableX509TrustManager

 * Acts as wrapper around the current TrustManager and holds reference to current TrustManager instance
 * Ensures new connections immediately use the updated truststore

 * The class does not directly validate certificates itself, TLS certificate validation calls are delegated.
 * Used for runtime hot swap because Java SSLContext cannot replace TrustManager instances.
 */
public class ReloadableX509TrustManager implements X509TrustManager {

    // Using AtomicReference to ensure thread-safe replacement of the TrustManager delegates
    private final AtomicReference<X509TrustManager> delegateRef =  new AtomicReference<>();

    /**
     * The ReloadableX509TrustManager constructor
     *
     * @param initialManager   the initial TrustManager delegate
     */
    public ReloadableX509TrustManager (X509TrustManager initialManager) {
        // TLS handshakes must always have a TrustManager available
        Objects.requireNonNull(initialManager, "Initial TrustManager cannot be null.");
        delegateRef.set(initialManager);
    }

    /**
     * Replace Delegate
     * It swaps or replaces the delegate managers at runtime
     * It's called by the TrustStoreReloader when the trust store changes.
     *
     * @param newManager   the new manager
     */
    public void replaceDelegate(X509TrustManager newManager) {
        Objects.requireNonNull(newManager, "New TrustManager cannot be null");
        delegateRef.set(newManager);
    }

    /**
     * Get the current delegate X509TrustManager
     */
    public X509TrustManager getCurrentDelegate() {
        return delegateRef.get();
    }

    /**
     * Require delegate
     * Ensures the delegate used during runtime is not null
     * @return  X509TrustManager
     */
    private X509TrustManager requireDelegate() {
        X509TrustManager trustManager = delegateRef.get();
        if (trustManager == null) {
            throw new IllegalStateException("No delegate X509TrustManager is configured");
        }
        return trustManager;
    }

    /**
     * Check client Trusted
     *
     * @param chain                     the peer certificate chain
     * @param authType                  the authentication type based on the client certificate
     * @throws CertificateException     the CertificateException
     */
    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        requireDelegate().checkClientTrusted(chain, authType);
    }

    /**
     * Check Server Trusted
     *
     * @param chain                     the peer certificate chain
     * @param authType                  the key exchange algorithm used
     * @throws CertificateException     the CertificateException
     */
    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        requireDelegate().checkServerTrusted(chain, authType);
    }

    /**
     *
     * @return  Returns a non-null (possibly empty) array of acceptable CA issuer certificates
     */
    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return requireDelegate().getAcceptedIssuers();
    }
}
