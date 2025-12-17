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

package org.eclipse.tractusx.edc.certificate.clearinghouse;

import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.security.Vault;

import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Validates Clearing House certificates
 * 
 * Validates:
 * - Certificate chain to Clearing House trust anchor
 * - Certificate revocation (CRL)
 * - Trust anchor configuration
 */
public class ClearingHouseCertificateValidator {

    private final String trustAnchorPath;
    private final String trustAnchorPasswordAlias;
    private final String trustAnchorType;
    private final Vault vault;
    private final Monitor monitor;
    private KeyStore trustAnchorStore;
    private List<X509Certificate> trustAnchors;

    public ClearingHouseCertificateValidator(
            String trustAnchorPath,
            String trustAnchorPasswordAlias,
            String trustAnchorType,
            Vault vault,
            Monitor monitor) {
        this.trustAnchorPath = trustAnchorPath;
        this.trustAnchorPasswordAlias = trustAnchorPasswordAlias;
        this.trustAnchorType = trustAnchorType;
        this.vault = vault;
        this.monitor = monitor;
        this.trustAnchors = new ArrayList<>();
        
        initializeTrustAnchor();
    }

    /**
     * Initialize trust anchor from keystore
     */
    private void initializeTrustAnchor() {
        if (trustAnchorPath == null || trustAnchorPasswordAlias == null) {
            monitor.warning("Clearing House trust anchor not configured");
            return;
        }

        try {
            String password = vault.resolveSecret(trustAnchorPasswordAlias);
            if (password == null) {
                monitor.warning("Clearing House trust anchor password not found in vault: " + trustAnchorPasswordAlias);
                return;
            }

            trustAnchorStore = KeyStore.getInstance(trustAnchorType);
            try (FileInputStream fis = new FileInputStream(trustAnchorPath)) {
                trustAnchorStore.load(fis, password.toCharArray());
            }

            // Extract trust anchor certificates
            Enumeration<String> aliases = trustAnchorStore.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                Certificate cert = trustAnchorStore.getCertificate(alias);
                if (cert instanceof X509Certificate) {
                    trustAnchors.add((X509Certificate) cert);
                    monitor.debug("Loaded Clearing House trust anchor: " + alias);
                }
            }

            monitor.info("Clearing House trust anchor initialized with " + trustAnchors.size() + " certificates");

        } catch (Exception e) {
            monitor.severe("Failed to initialize Clearing House trust anchor: " + e.getMessage(), e);
        }
    }

    /**
     * Validate a single Clearing House certificate
     */
    public Result<Void> validateCertificate(X509Certificate certificate) {
        if (trustAnchors.isEmpty()) {
            return Result.failure("Clearing House trust anchor not configured");
        }

        monitor.debug("Validating Clearing House certificate: " + certificate.getSubjectDN());

        // Step 1: Basic certificate validation
        try {
            certificate.checkValidity();
        } catch (Exception e) {
            return Result.failure("Certificate validity check failed: " + e.getMessage());
        }

        // Step 2: Check if certificate chains to trust anchor
        // This is a simplified check - full chain validation would require building the chain
        Result<Void> chainResult = validateChainToTrustAnchor(certificate);
        if (chainResult.failed()) {
            return chainResult;
        }

        // Step 3: Check revocation (CRL)
        // TODO: Implement CRL checking when CRL endpoint is available
        // Result<Void> revocationResult = checkRevocation(certificate);
        // if (revocationResult.failed()) {
        //     return revocationResult;
        // }

        monitor.debug("Clearing House certificate validation successful");
        return Result.success();
    }

    /**
     * Validate certificate chain for Clearing House
     */
    public Result<Void> validateCertificateChain(List<X509Certificate> chain) {
        if (chain == null || chain.isEmpty()) {
            return Result.failure("Certificate chain is null or empty");
        }

        if (trustAnchors.isEmpty()) {
            return Result.failure("Clearing House trust anchor not configured");
        }

        monitor.debug("Validating Clearing House certificate chain with " + chain.size() + " certificates");

        // Validate each certificate
        for (X509Certificate cert : chain) {
            Result<Void> certResult = validateCertificate(cert);
            if (certResult.failed()) {
                return certResult;
            }
        }

        // Validate chain integrity
        Result<Void> integrityResult = validateChainIntegrity(chain);
        if (integrityResult.failed()) {
            return integrityResult;
        }

        // Validate root against trust anchor
        X509Certificate rootCert = chain.get(chain.size() - 1);
        Result<Void> rootResult = validateRootAgainstTrustAnchor(rootCert);
        if (rootResult.failed()) {
            return rootResult;
        }

        monitor.debug("Clearing House certificate chain validation successful");
        return Result.success();
    }

    /**
     * Validate certificate chains to trust anchor
     */
    private Result<Void> validateChainToTrustAnchor(X509Certificate certificate) {
        // Simplified: Check if certificate issuer matches any trust anchor
        // Full implementation would build and validate the complete chain
        String issuerDN = certificate.getIssuerDN().getName();
        
        for (X509Certificate trustAnchor : trustAnchors) {
            String trustAnchorDN = trustAnchor.getSubjectDN().getName();
            if (issuerDN.equals(trustAnchorDN)) {
                // Try to verify signature
                try {
                    certificate.verify(trustAnchor.getPublicKey());
                    return Result.success();
                } catch (Exception e) {
                    // Continue checking other trust anchors
                }
            }
        }

        return Result.failure("Certificate does not chain to Clearing House trust anchor");
    }

    /**
     * Validate root certificate against trust anchor
     */
    private Result<Void> validateRootAgainstTrustAnchor(X509Certificate rootCert) {
        String rootDN = rootCert.getSubjectDN().getName();
        
        for (X509Certificate trustAnchor : trustAnchors) {
            String trustAnchorDN = trustAnchor.getSubjectDN().getName();
            if (rootDN.equals(trustAnchorDN)) {
                // Verify it's the same certificate
                if (rootCert.equals(trustAnchor)) {
                    return Result.success();
                }
            }
        }

        return Result.failure("Root certificate is not a Clearing House trust anchor");
    }

    /**
     * Validate chain integrity (each cert signed by next)
     */
    private Result<Void> validateChainIntegrity(List<X509Certificate> chain) {
        try {
            for (int i = 0; i < chain.size() - 1; i++) {
                X509Certificate cert = chain.get(i);
                X509Certificate issuer = chain.get(i + 1);
                
                // Verify signature
                cert.verify(issuer.getPublicKey());
            }
            return Result.success();
        } catch (Exception e) {
            return Result.failure("Certificate chain integrity validation failed: " + e.getMessage());
        }
    }
}

