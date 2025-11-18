package org.eclipse.tractusx.edc.truststore.reload.tmf;

import org.eclipse.edc.spi.monitor.Monitor;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class TrustManagerFactoryHelper {

    private TrustManagerFactoryHelper() { /*utility */ }

    // Backward compatibility method, accepts InputStream and explicit keystore type (JKS, PKCS12, etc.)
    public static X509TrustManager createTrustManagerFromStream(InputStream trustStoreInputStream, char[] password, String type, Monitor monitor) {
        try (InputStream input = trustStoreInputStream) {
            KeyStore trustStore = KeyStore.getInstance(type);
            trustStore.load(input, password);
            return createTrustManagerFromKeyStore(trustStore);
        } catch (Exception e) {
            if (monitor != null) {
                monitor.severe("Failed to create X509TrustManager from keystore input stream: " + e.getMessage(), e);
            }
            throw new RuntimeException("Failed to create X509TrustManager", e);
        }
    }

    //Load a JKS file from a Path
    public static X509TrustManager createFromJksFile(Path trustStorePath, char[] password, Monitor monitor) {
        return createFromKeyStoreFile(trustStorePath, password, "JKS", monitor);
    }

    // Load a PKCS12 file from a Path
    public static X509TrustManager createFromPkcs12File(Path trustStorePath, char[] password, Monitor monitor) {
        return createFromKeyStoreFile(trustStorePath, password, "PKCS12", monitor);
    }

    // Load all PEM files from a directory into an in-memory truststore and then create X509TrustManager
    public static X509TrustManager createFromPemDirectory(Path pemDirectoryPath, Monitor monitor) {
        try {
            KeyStore truststore = KeyStore.getInstance(KeyStore.getDefaultType());
            truststore.load(null, null); // Initialize empty keystore

            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            AtomicInteger index = new AtomicInteger(0);

            try (Stream<Path> files = Files.list(pemDirectoryPath)) {
                files.filter(Files::isRegularFile)
                        .forEach(childPath -> {
                            try (InputStream input = Files.newInputStream(childPath)) {
                                Collection< ? extends Certificate> certs = certificateFactory.generateCertificates(input);
                                for (Certificate cert : certs) {
                                    truststore.setCertificateEntry("pem-cert-" + index.getAndIncrement(), cert);
                                }
                            } catch (Exception e) {
                                throw new RuntimeException("Failed to load certificate from " + childPath + ":" + e.getMessage(), e);
                            }
                        });
            }

            return createTrustManagerFromKeyStore(truststore);
        } catch (Exception e) {
            if (monitor != null) {
                monitor.severe("Failed to create X509TrustManager from PEM directory: " + e.getMessage(), e);
            }
            throw new RuntimeException("Failed to create X509TrustManager", e);
        }
    }

    // A helper method to load JKS/PKCS12 file-based trust stores
    private static X509TrustManager createFromKeyStoreFile(Path trustStorePath, char[] password, String trustStoreType, Monitor monitor) {
        try (InputStream input = Files.newInputStream(trustStorePath)) {
            KeyStore trustStore = KeyStore.getInstance(trustStoreType);
            trustStore.load(input, password);
            return createTrustManagerFromKeyStore(trustStore);
        } catch (Exception e) {
            if (monitor != null) {
                monitor.severe("Failed to create X509TrustManager from keystore file: " + e.getMessage(), e);
            }
            throw new RuntimeException("Failed to create X509TrustManager", e);
        }
    }

    // Convert a populated keystore (truststore in this case) into an X509TrustManager
    private static X509TrustManager createTrustManagerFromKeyStore(KeyStore truststore) throws Exception {
        String algorithm = TrustManagerFactory.getDefaultAlgorithm();
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(algorithm);
        trustManagerFactory.init(truststore);

        for (TrustManager trustManager : trustManagerFactory.getTrustManagers()) {
            if (trustManager instanceof X509TrustManager x509TrustManager) {
                return x509TrustManager;
            }
        }
        throw new IllegalStateException("No X509TrustManager found inside the TrustManagerFactory");
    }
}
