package org.eclipse.tractusx.edc.truststore.reload;

import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.tractusx.edc.truststore.reload.tmf.TrustManagerFactoryHelper;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import javax.net.ssl.X509TrustManager;
import java.nio.file.*;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;


class TrustManagerFactoryHelperTest {

    private Path tempTrustStoreDir;

    @BeforeEach
    void setup() throws Exception {
        tempTrustStoreDir = Files.createTempDirectory("truststore-tests");
    }

    @Test
    void createFromJksFile_shouldReturnManager() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null, null);

        X509Certificate cert = loadTestCertificate();
        keyStore.setCertificateEntry("cert-alias", cert);

        Path trustStorePath = tempTrustStoreDir.resolve("sample.jks");
        try (var out = Files.newOutputStream(trustStorePath)) {
            keyStore.store(out, "changeit".toCharArray());
        }

        X509TrustManager tm =
                TrustManagerFactoryHelper.createFromJksFile(
                        trustStorePath, "changeit".toCharArray(), Mockito.mock(Monitor.class)
                );

        assertThat(tm.getAcceptedIssuers()).isNotEmpty();
    }

    @Test
    void createFromPkcs12File_shouldReturnManager() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setCertificateEntry("cert-entry", loadTestCertificate());

        Path trustStorePath = tempTrustStoreDir.resolve("sample.p12");
        try (var out = Files.newOutputStream(trustStorePath)) {
            keyStore.store(out, "secret".toCharArray());
        }

        X509TrustManager tm =
                TrustManagerFactoryHelper.createFromPkcs12File(
                        trustStorePath, "secret".toCharArray(), Mockito.mock(Monitor.class)
                );

        assertThat(tm.getAcceptedIssuers()).isNotEmpty();
    }

    @Test
    void createFromPemDirectory_shouldReturnManager() throws Exception {
        Path pemPath = tempTrustStoreDir.resolve("ca-cert.pem");

        X509Certificate cert = loadTestCertificate();
        String pem = convertToPem(cert);

        Files.writeString(pemPath, pem);

        X509TrustManager tm =
                TrustManagerFactoryHelper.createFromPemDirectory(
                        tempTrustStoreDir, Mockito.mock(Monitor.class)
                );

        assertThat(tm.getAcceptedIssuers()).isNotEmpty();
    }

    // Utility Methods
    private X509Certificate loadTestCertificate() throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");

        // using certificate authority certificate (check local-certs directory)
        String pem = """
-----BEGIN CERTIFICATE-----
MIIDqDCCApCgAwIBAgIEPhwe6TANBgkqhkiG9w0BAQsFADBiMRswGQYDVQQDDBJ3
d3cubW9ja3NlcnZlci5jb20xEzARBgNVBAoMCk1vY2tTZXJ2ZXIxDzANBgNVBAcM
BkxvbmRvbjEQMA4GA1UECAwHRW5nbGFuZDELMAkGA1UEBhMCVUswIBcNMTYwNjIw
MTYzNDE0WhgPMjExNzA1MjcxNjM0MTRaMGIxGzAZBgNVBAMMEnd3dy5tb2Nrc2Vy
dmVyLmNvbTETMBEGA1UECgwKTW9ja1NlcnZlcjEPMA0GA1UEBwwGTG9uZG9uMRAw
DgYDVQQIDAdFbmdsYW5kMQswCQYDVQQGEwJVSzCCASIwDQYJKoZIhvcNAQEBBQAD
ggEPADCCAQoCggEBAPGORrdkwTY1H1dvQPYaA+RpD+pSbsvHTtUSU6H7NQS2qu1p
sE6TEG2fE+Vb0QIXkeH+jjKzcfzHGCpIU/0qQCu4RVycrIW4CCdXjl+T3L4C0I3R
mIMciTig5qcAvY9P5bQAdWDkU36YGrCjGaX3QlndGxD9M974JdpVK4cqFyc6N4gA
Onys3uS8MMmSHTjTFAgR/WFeJiciQnal+Zy4ZF2x66CdjN+hP8ch2yH/CBwrSBc0
ZeH2flbYGgkh3PwKEqATqhVa+mft4dCrvqBwGhBTnzEGWK/qrl9xB4mTs4GQ/Z5E
8rXzlvpKzVJbfDHfqVzgFw4fQFGV0XMLTKyvOX0CAwEAAaNkMGIwHQYDVR0OBBYE
FH3W3sL4XRDM/VnRayaSamVLISndMA8GA1UdEwEB/wQFMAMBAf8wCwYDVR0PBAQD
AgG2MCMGA1UdJQQcMBoGCCsGAQUFBwMBBggrBgEFBQcDAgYEVR0lADANBgkqhkiG
9w0BAQsFAAOCAQEAecfgKuMxCBe/NxVqoc4kzacf9rjgz2houvXdZU2UDBY3hCs4
MBbM7U9Oi/3nAoU1zsA8Rg2nBwc76T8kSsfG1TK3iJkfGIOVjcwOoIjy3Z8zLM2V
YjYbOUyAQdO/s2uShAmzzjh9SV2NKtcNNdoE9e6udvwDV8s3NGMTUpY5d7BHYQqV
sqaPGlsKi8dN+gdLcRbtQo29bY8EYR5QJm7QJFDI1njODEnrUjjMvWw2yjFlje59
j/7LBRe2wfNmjXFYm5GqWft10UJ7Ypb3XYoGwcDac+IUvrgmgTHD+E3klV3SUi8i
Gm5MBedhPkXrLWmwuoMJd7tzARRHHT6PBH/ZGw==
-----END CERTIFICATE-----
""";

        return (X509Certificate) factory.generateCertificate(
                new java.io.ByteArrayInputStream(pem.getBytes())
        );
    }

    private String convertToPem(X509Certificate cert) throws Exception {
        return "-----BEGIN CERTIFICATE-----\n"
                + java.util.Base64.getMimeEncoder(64, "\n".getBytes())
                .encodeToString(cert.getEncoded())
                + "\n-----END CERTIFICATE-----\n";
    }
}

