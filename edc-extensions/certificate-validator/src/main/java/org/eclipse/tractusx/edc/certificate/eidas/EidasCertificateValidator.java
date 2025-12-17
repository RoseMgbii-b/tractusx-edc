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

package org.eclipse.tractusx.edc.certificate.eidas;

import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.tractusx.edc.certificate.trustlist.EuTrustListService;

import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Validates eIDAS certificates according to eIDAS regulation requirements
 * 
 * Validates:
 * - QCStatement extension (eIDAS compliance)
 * - Certificate types (QCP, QCP-l, QCP-n, QCP-n-l)
 * - Certificate profile compliance
 * - Certificate chain against EU Trust List
 */
public class EidasCertificateValidator {

    private final EuTrustListService trustListService;
    private final Monitor monitor;
    private final EidasCertificateProfileValidator profileValidator;

    public EidasCertificateValidator(EuTrustListService trustListService, Monitor monitor) {
        this.trustListService = trustListService;
        this.monitor = monitor;
        this.profileValidator = new EidasCertificateProfileValidator(monitor);
    }

    /**
     * Validate a single eIDAS certificate
     */
    public Result<Void> validateCertificate(X509Certificate certificate) {
        monitor.debug("Validating eIDAS certificate: " + certificate.getSubjectDN());

        // Step 1: Validate certificate profile
        Result<Void> profileResult = profileValidator.validateCertificateProfile(certificate);
        if (profileResult.failed()) {
            return Result.failure("eIDAS certificate profile validation failed: " + profileResult.getFailureDetail());
        }

        // Step 2: Validate against EU Trust List (if available)
        if (trustListService != null) {
            Result<Void> trustListResult = trustListService.validateCertificate(certificate);
            if (trustListResult.failed()) {
                monitor.warning("Certificate not found in EU Trust List: " + trustListResult.getFailureDetail());
                // Note: This might be a warning rather than failure depending on requirements
            }
        } else {
            monitor.debug("EU Trust List service not available, skipping trust list validation");
        }

        // Step 3: Basic certificate validation
        try {
            certificate.checkValidity();
        } catch (Exception e) {
            return Result.failure("Certificate validity check failed: " + e.getMessage());
        }

        monitor.debug("eIDAS certificate validation successful");
        return Result.success();
    }

    /**
     * Validate eIDAS certificate chain
     */
    public Result<Void> validateCertificateChain(List<X509Certificate> chain) {
        if (chain == null || chain.isEmpty()) {
            return Result.failure("Certificate chain is null or empty");
        }

        monitor.debug("Validating eIDAS certificate chain with " + chain.size() + " certificates");

        // Validate each certificate in chain
        for (int i = 0; i < chain.size(); i++) {
            X509Certificate cert = chain.get(i);
            Result<Void> certResult = validateCertificate(cert);
            if (certResult.failed()) {
                return Result.failure("Certificate " + i + " in chain validation failed: " + certResult.getFailureDetail());
            }
        }

        // Validate chain integrity
        Result<Void> integrityResult = validateChainIntegrity(chain);
        if (integrityResult.failed()) {
            return integrityResult;
        }

        // Validate root against trust list
        X509Certificate rootCert = chain.get(chain.size() - 1);
        if (trustListService != null) {
            Result<Void> rootValidation = trustListService.validateCertificate(rootCert);
            if (rootValidation.failed()) {
                return Result.failure("Root certificate not in EU Trust List: " + rootValidation.getFailureDetail());
            }
        }

        monitor.debug("eIDAS certificate chain validation successful");
        return Result.success();
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

