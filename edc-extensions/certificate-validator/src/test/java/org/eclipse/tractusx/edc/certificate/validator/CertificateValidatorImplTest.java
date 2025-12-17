/********************************************************************************
 * Copyright (c) 2025 Contributors
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 ********************************************************************************/

package org.eclipse.tractusx.edc.certificate.validator;

import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.tractusx.edc.certificate.clearinghouse.ClearingHouseCertificateValidator;
import org.eclipse.tractusx.edc.certificate.eidas.EidasCertificateValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.security.auth.x500.X500Principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CertificateValidatorImplTest {

    @Mock
    private Monitor monitor;
    @Mock
    private EidasCertificateValidator eidasValidator;
    @Mock
    private ClearingHouseCertificateValidator chValidator;
    @Mock
    private X509Certificate certificate;

    private CertificateValidatorImpl validator;

    @BeforeEach
    void setUp() {
        // Initialize BouncyCastle provider if not already done
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        
        validator = new CertificateValidatorImpl(
                eidasValidator,
                chValidator,
                true, // cache enabled
                3600, // cache TTL
                monitor
        );
    }

    @Test
    void validateCertificate_whenValid_shouldSucceed() throws CertificateNotYetValidException, CertificateExpiredException {
        // Mock certificate methods needed for validation and cache key generation
        doNothing().when(certificate).checkValidity();
        when(certificate.getSerialNumber()).thenReturn(BigInteger.valueOf(12345));
        when(certificate.getIssuerDN()).thenReturn(new X500Principal("CN=Test CA"));
        when(certificate.getBasicConstraints()).thenReturn(-1); // Not a CA certificate

        Result<Void> result = validator.validateCertificate(certificate);

        assertThat(result.succeeded()).isTrue();
    }

    @Test
    void validateCertificate_whenInvalid_shouldFail() throws Exception {
        // Use a different serial number to avoid cache interference from other tests
        when(certificate.getSerialNumber()).thenReturn(BigInteger.valueOf(99999));
        when(certificate.getIssuerDN()).thenReturn(new X500Principal("CN=Test CA Invalid"));
        // Mock certificate validity check to throw exception
        doThrow(new CertificateExpiredException("Certificate expired")).when(certificate).checkValidity();

        Result<Void> result = validator.validateCertificate(certificate);

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureDetail()).contains("Certificate validation failed");
        assertThat(result.getFailureDetail()).contains("Certificate expired");
    }

    @Test
    void validateEidasCertificate_whenValidatorEnabled_shouldCallEidasValidator() {
        when(eidasValidator.validateCertificate(any())).thenReturn(Result.success());

        Result<Void> result = validator.validateEidasCertificate(certificate);

        assertThat(result.succeeded()).isTrue();
        verify(eidasValidator).validateCertificate(certificate);
    }

    @Test
    void validateEidasCertificate_whenValidatorDisabled_shouldFail() {
        CertificateValidatorImpl validatorNoEidas = new CertificateValidatorImpl(
                null, // eIDAS disabled
                chValidator,
                true,
                3600,
                monitor
        );

        Result<Void> result = validatorNoEidas.validateEidasCertificate(certificate);

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureDetail()).contains("eIDAS certificate validator is not enabled");
    }

    @Test
    void validateClearingHouseCertificate_whenValidatorEnabled_shouldCallChValidator() {
        when(chValidator.validateCertificate(any())).thenReturn(Result.success());

        Result<Void> result = validator.validateClearingHouseCertificate(certificate);

        assertThat(result.succeeded()).isTrue();
        verify(chValidator).validateCertificate(certificate);
    }

    @Test
    void validateClearingHouseCertificate_whenValidatorDisabled_shouldFail() {
        CertificateValidatorImpl validatorNoCh = new CertificateValidatorImpl(
                eidasValidator,
                null, // CH disabled
                true,
                3600,
                monitor
        );

        Result<Void> result = validatorNoCh.validateClearingHouseCertificate(certificate);

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureDetail()).contains("Clearing House certificate validator is not enabled");
    }

    @Test
    void validateCertificateChain_whenEmptyChain_shouldFail() {
        List<X509Certificate> emptyChain = new ArrayList<>();

        Result<Void> result = validator.validateCertificateChain(emptyChain);

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureDetail()).contains("Certificate chain is null or empty");
    }

    @Test
    void validateCertificateChain_whenValidChain_shouldSucceed() throws Exception {
        // Create a validator with cache disabled to avoid interference
        CertificateValidatorImpl validatorNoCache = new CertificateValidatorImpl(
                eidasValidator,
                chValidator,
                false, // cache disabled
                3600,
                monitor
        );
        
        // Generate a real certificate chain using BouncyCastle
        // This allows verify() to work since we're using real certificates
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA", "BC");
        keyGen.initialize(2048);
        
        // Generate issuer (CA) key pair
        KeyPair issuerKeyPair = keyGen.generateKeyPair();
        
        // Create issuer (CA) certificate
        X500Name issuerName = new X500Name("CN=Test CA, O=Test Organization");
        Date notBefore = new Date(System.currentTimeMillis() - 86400000);
        Date notAfter = new Date(System.currentTimeMillis() + 86400000 * 365);
        
        JcaX509v3CertificateBuilder issuerBuilder = new JcaX509v3CertificateBuilder(
                issuerName,
                BigInteger.valueOf(1),
                notBefore,
                notAfter,
                issuerName,
                issuerKeyPair.getPublic()
        );
        
        ContentSigner issuerSigner = new JcaContentSignerBuilder("SHA256WithRSA")
                .setProvider("BC")
                .build(issuerKeyPair.getPrivate());
        
        X509CertificateHolder issuerHolder = issuerBuilder.build(issuerSigner);
        X509Certificate issuerCert = new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(issuerHolder);
        
        // Generate end-entity key pair
        KeyPair endEntityKeyPair = keyGen.generateKeyPair();
        
        // Create end-entity certificate signed by issuer
        X500Name endEntityName = new X500Name("CN=Test Certificate, O=Test Organization");
        JcaX509v3CertificateBuilder endEntityBuilder = new JcaX509v3CertificateBuilder(
                issuerName, // issuer
                BigInteger.valueOf(2),
                notBefore,
                notAfter,
                endEntityName, // subject
                endEntityKeyPair.getPublic()
        );
        
        ContentSigner endEntitySigner = new JcaContentSignerBuilder("SHA256WithRSA")
                .setProvider("BC")
                .build(issuerKeyPair.getPrivate()); // Signed by issuer
        
        X509CertificateHolder endEntityHolder = endEntityBuilder.build(endEntitySigner);
        X509Certificate endEntityCert = new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(endEntityHolder);
        
        // Create chain: end-entity -> issuer
        List<X509Certificate> chain = List.of(endEntityCert, issuerCert);
        
        Result<Void> result = validatorNoCache.validateCertificateChain(chain);

        // With real certificates, the chain should validate successfully
        assertThat(result.succeeded()).isTrue();
    }
}

