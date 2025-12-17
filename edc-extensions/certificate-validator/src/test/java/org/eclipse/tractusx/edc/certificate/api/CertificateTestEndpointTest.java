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

package org.eclipse.tractusx.edc.certificate.api;

import jakarta.ws.rs.core.Response;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.tractusx.edc.certificate.validator.CertificateValidator;
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
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CertificateTestEndpointTest {

    @Mock
    private CertificateValidator validator;
    @Mock
    private Monitor monitor;

    private CertificateTestEndpoint endpoint;
    private static byte[] validCertificateBytes;

    @BeforeEach
    void setUp() {
        endpoint = new CertificateTestEndpoint(validator, monitor);
        // Initialize BouncyCastle provider if not already done
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        // Create valid certificate bytes once
        if (validCertificateBytes == null) {
            validCertificateBytes = createValidTestCertificate();
        }
    }

    @Test
    void validateCertificate_whenNullData_shouldReturnBadRequest() {
        Response response = endpoint.validateCertificate(null);

        assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
        assertThat(response.getEntity().toString()).contains("Certificate data is required");
    }

    @Test
    void validateCertificate_whenEmptyData_shouldReturnBadRequest() {
        Response response = endpoint.validateCertificate(new byte[0]);

        assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
        assertThat(response.getEntity().toString()).contains("Certificate data is required");
    }

    @Test
    void validateCertificate_whenInvalidCertificateFormat_shouldReturnBadRequest() {
        byte[] invalidData = "invalid certificate data".getBytes();

        Response response = endpoint.validateCertificate(invalidData);

        assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
        assertThat(response.getEntity().toString()).contains("Failed to parse certificate");
    }

    @Test
    void validateCertificate_whenValidCertificate_shouldReturnSuccess() {
        byte[] certBytes = validCertificateBytes;
        
        when(validator.validateCertificate(any(X509Certificate.class)))
                .thenReturn(Result.success());

        Response response = endpoint.validateCertificate(certBytes);

        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        assertThat(response.getEntity().toString()).contains("success");
        assertThat(response.getEntity().toString()).contains("Certificate is valid");
        verify(validator).validateCertificate(any(X509Certificate.class));
    }

    @Test
    void validateCertificate_whenValidationFails_shouldReturnBadRequest() {
        byte[] certBytes = validCertificateBytes;
        
        when(validator.validateCertificate(any(X509Certificate.class)))
                .thenReturn(Result.failure("Certificate expired"));

        Response response = endpoint.validateCertificate(certBytes);

        assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
        assertThat(response.getEntity().toString()).contains("Certificate validation failed");
        assertThat(response.getEntity().toString()).contains("Certificate expired");
    }

    @Test
    void validateEidasCertificate_whenNullData_shouldReturnBadRequest() {
        Response response = endpoint.validateEidasCertificate(null);

        assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
        assertThat(response.getEntity().toString()).contains("Certificate data is required");
    }

    @Test
    void validateEidasCertificate_whenEmptyData_shouldReturnBadRequest() {
        Response response = endpoint.validateEidasCertificate(new byte[0]);

        assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
    }

    @Test
    void validateEidasCertificate_whenValid_shouldReturnSuccess() {
        byte[] certBytes = validCertificateBytes;
        
        when(validator.validateEidasCertificate(any(X509Certificate.class)))
                .thenReturn(Result.success());

        Response response = endpoint.validateEidasCertificate(certBytes);

        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        assertThat(response.getEntity().toString()).contains("eIDAS certificate is valid");
        verify(validator).validateEidasCertificate(any(X509Certificate.class));
    }

    @Test
    void validateEidasCertificate_whenValidationFails_shouldReturnBadRequest() {
        byte[] certBytes = validCertificateBytes;
        
        when(validator.validateEidasCertificate(any(X509Certificate.class)))
                .thenReturn(Result.failure("eIDAS validation failed: QCStatement not found"));

        Response response = endpoint.validateEidasCertificate(certBytes);

        assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
        assertThat(response.getEntity().toString()).contains("eIDAS certificate validation failed");
        assertThat(response.getEntity().toString()).contains("QCStatement not found");
    }

    @Test
    void validateClearingHouseCertificate_whenNullData_shouldReturnBadRequest() {
        Response response = endpoint.validateClearingHouseCertificate(null);

        assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
        assertThat(response.getEntity().toString()).contains("Certificate data is required");
    }

    @Test
    void validateClearingHouseCertificate_whenEmptyData_shouldReturnBadRequest() {
        Response response = endpoint.validateClearingHouseCertificate(new byte[0]);

        assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
    }

    @Test
    void validateClearingHouseCertificate_whenValid_shouldReturnSuccess() {
        byte[] certBytes = validCertificateBytes;
        
        when(validator.validateClearingHouseCertificate(any(X509Certificate.class)))
                .thenReturn(Result.success());

        Response response = endpoint.validateClearingHouseCertificate(certBytes);

        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        assertThat(response.getEntity().toString()).contains("Clearing House certificate is valid");
        verify(validator).validateClearingHouseCertificate(any(X509Certificate.class));
    }

    @Test
    void validateClearingHouseCertificate_whenValidationFails_shouldReturnBadRequest() {
        byte[] certBytes = validCertificateBytes;
        
        when(validator.validateClearingHouseCertificate(any(X509Certificate.class)))
                .thenReturn(Result.failure("Clearing House trust anchor not configured"));

        Response response = endpoint.validateClearingHouseCertificate(certBytes);

        assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
        assertThat(response.getEntity().toString()).contains("Clearing House certificate validation failed");
        assertThat(response.getEntity().toString()).contains("trust anchor not configured");
    }

    @Test
    void validateCertificate_whenExceptionThrown_shouldReturnInternalServerError() throws Exception {
        byte[] certBytes = validCertificateBytes;
        
        when(validator.validateCertificate(any(X509Certificate.class)))
                .thenThrow(new RuntimeException("Unexpected error"));

        Response response = endpoint.validateCertificate(certBytes);

        assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
        assertThat(response.getEntity().toString()).contains("Internal server error");
    }

    /**
     * Create a valid test certificate using BouncyCastle
     * This generates a minimal self-signed X.509 certificate for testing
     */
    private byte[] createValidTestCertificate() {
        try {
            // Generate key pair
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA", "BC");
            keyGen.initialize(2048);
            KeyPair keyPair = keyGen.generateKeyPair();

            // Create certificate
            X500Name issuer = new X500Name("CN=Test CA, O=Test Organization");
            X500Name subject = new X500Name("CN=Test Certificate, O=Test Organization");
            
            Date notBefore = new Date(System.currentTimeMillis() - 86400000); // Yesterday
            Date notAfter = new Date(System.currentTimeMillis() + 86400000 * 365); // One year from now
            
            JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                    issuer,
                    BigInteger.valueOf(System.currentTimeMillis()),
                    notBefore,
                    notAfter,
                    subject,
                    keyPair.getPublic()
            );

            ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA")
                    .setProvider("BC")
                    .build(keyPair.getPrivate());

            X509CertificateHolder certHolder = certBuilder.build(signer);
            X509Certificate cert = new JcaX509CertificateConverter()
                    .setProvider("BC")
                    .getCertificate(certHolder);

            // Convert to DER bytes
            return cert.getEncoded();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test certificate", e);
        }
    }

    /**
     * Get valid test certificate bytes
     */
    private byte[] createTestCertificateBytes() {
        return validCertificateBytes;
    }
}

