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

import org.eclipse.edc.runtime.metamodel.annotation.ExtensionPoint;
import org.eclipse.edc.spi.result.Result;

import java.security.cert.X509Certificate;
import java.util.List;

/**
 * SPI for certificate validation
 * 
 * Provides unified interface for validating certificates from various sources
 * (eIDAS, Clearing House, generic X.509)
 */
@ExtensionPoint
public interface CertificateValidator {

    /**
     * Validate a single certificate
     * 
     * @param certificate The certificate to validate
     * @return Result indicating success or failure with details
     */
    Result<Void> validateCertificate(X509Certificate certificate);

    /**
     * Validate a certificate chain
     * 
     * @param chain The certificate chain (end-entity first, root last)
     * @return Result indicating success or failure with details
     */
    Result<Void> validateCertificateChain(List<X509Certificate> chain);

    /**
     * Validate certificate for eIDAS compliance
     * 
     * @param certificate The certificate to validate
     * @return Result indicating success or failure with details
     */
    Result<Void> validateEidasCertificate(X509Certificate certificate);

    /**
     * Validate certificate chain for eIDAS compliance
     * 
     * @param chain The certificate chain
     * @return Result indicating success or failure with details
     */
    Result<Void> validateEidasCertificateChain(List<X509Certificate> chain);

    /**
     * Validate certificate for Clearing House compliance
     * 
     * @param certificate The certificate to validate
     * @return Result indicating success or failure with details
     */
    Result<Void> validateClearingHouseCertificate(X509Certificate certificate);

    /**
     * Validate certificate chain for Clearing House compliance
     * 
     * @param chain The certificate chain
     * @return Result indicating success or failure with details
     */
    Result<Void> validateClearingHouseCertificateChain(List<X509Certificate> chain);
}

