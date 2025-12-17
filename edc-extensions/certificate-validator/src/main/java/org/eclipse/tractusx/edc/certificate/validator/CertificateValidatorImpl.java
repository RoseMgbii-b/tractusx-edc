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

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Implementation of CertificateValidator
 * 
 * Orchestrates validation across eIDAS and Clearing House validators
 * and provides caching for performance
 */
public class CertificateValidatorImpl implements CertificateValidator {

    private final EidasCertificateValidator eidasValidator;
    private final ClearingHouseCertificateValidator chValidator;
    private final boolean cacheEnabled;
    private final int cacheTtlSeconds;
    private final Monitor monitor;
    
    // Cache for validation results
    private final ConcurrentMap<String, CachedValidationResult> cache = new ConcurrentHashMap<>();

    public CertificateValidatorImpl(
            EidasCertificateValidator eidasValidator,
            ClearingHouseCertificateValidator chValidator,
            boolean cacheEnabled,
            int cacheTtlSeconds,
            Monitor monitor) {
        this.eidasValidator = eidasValidator;
        this.chValidator = chValidator;
        this.cacheEnabled = cacheEnabled;
        this.cacheTtlSeconds = cacheTtlSeconds;
        this.monitor = monitor;
    }

    @Override
    public Result<Void> validateCertificate(X509Certificate certificate) {
        String cacheKey = getCacheKey(certificate);
        
        // Check cache
        if (cacheEnabled) {
            CachedValidationResult cached = cache.get(cacheKey);
            if (cached != null && !cached.isExpired()) {
                monitor.debug("Certificate validation cache hit for: " + cacheKey);
                return cached.result();
            }
        }

        // Perform validation
        Result<Void> result = performValidation(certificate);

        // Cache result
        if (cacheEnabled && result.succeeded()) {
            cache.put(cacheKey, new CachedValidationResult(result, System.currentTimeMillis(), cacheTtlSeconds));
        }

        return result;
    }

    @Override
    public Result<Void> validateCertificateChain(List<X509Certificate> chain) {
        if (chain == null || chain.isEmpty()) {
            return Result.failure("Certificate chain is null or empty");
        }

        // Validate each certificate in chain
        for (X509Certificate cert : chain) {
            Result<Void> certResult = validateCertificate(cert);
            if (certResult.failed()) {
                return Result.failure("Certificate validation failed in chain: " + certResult.getFailureDetail());
            }
        }

        // Validate chain integrity
        return validateChainIntegrity(chain);
    }

    @Override
    public Result<Void> validateEidasCertificate(X509Certificate certificate) {
        if (eidasValidator == null) {
            return Result.failure("eIDAS certificate validator is not enabled");
        }
        return eidasValidator.validateCertificate(certificate);
    }

    @Override
    public Result<Void> validateEidasCertificateChain(List<X509Certificate> chain) {
        if (eidasValidator == null) {
            return Result.failure("eIDAS certificate validator is not enabled");
        }
        return eidasValidator.validateCertificateChain(chain);
    }

    @Override
    public Result<Void> validateClearingHouseCertificate(X509Certificate certificate) {
        if (chValidator == null) {
            return Result.failure("Clearing House certificate validator is not enabled");
        }
        return chValidator.validateCertificate(certificate);
    }

    @Override
    public Result<Void> validateClearingHouseCertificateChain(List<X509Certificate> chain) {
        if (chValidator == null) {
            return Result.failure("Clearing House certificate validator is not enabled");
        }
        return chValidator.validateCertificateChain(chain);
    }

    /**
     * Perform generic certificate validation
     */
    private Result<Void> performValidation(X509Certificate certificate) {
        // Basic validation: expiration, basic constraints, etc.
        try {
            certificate.checkValidity();
            
            // Check basic constraints
            if (certificate.getBasicConstraints() < 0) {
                // Not a CA certificate - this is fine for end-entity certs
            }
            
            return Result.success();
        } catch (Exception e) {
            return Result.failure("Certificate validation failed: " + e.getMessage());
        }
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

    /**
     * Generate cache key from certificate
     */
    private String getCacheKey(X509Certificate certificate) {
        return certificate.getSerialNumber().toString() + "-" + 
               certificate.getIssuerDN().getName().hashCode();
    }

    /**
     * Cached validation result
     */
    private record CachedValidationResult(Result<Void> result, long timestamp, int ttlSeconds) {
        boolean isExpired() {
            long ageSeconds = (System.currentTimeMillis() - timestamp) / 1000;
            return ageSeconds > ttlSeconds;
        }
    }
}

