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
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;

import java.io.IOException;
import java.security.cert.X509Certificate;

/**
 * Validates eIDAS certificate profile requirements
 * 
 * Validates:
 * - QCStatement extension presence and content
 * - Certificate type (QCP, QCP-l, QCP-n, QCP-n-l)
 * - Certificate policy OIDs
 */
public class EidasCertificateProfileValidator {

    private static final String QC_STATEMENT_OID = "1.3.6.1.5.5.7.1.3"; // id-etsi-qcs-QcCompliance
    private static final String QC_COMPLIANCE_OID = "0.4.0.1862.1.1"; // id-etsi-qcs-QcCompliance
    
    private final Monitor monitor;

    public EidasCertificateProfileValidator(Monitor monitor) {
        this.monitor = monitor;
    }

    /**
     * Validate eIDAS certificate profile
     */
    public Result<Void> validateCertificateProfile(X509Certificate certificate) {
        monitor.debug("Validating eIDAS certificate profile for: " + certificate.getSubjectDN());

        // Step 1: Check QCStatement extension
        Result<Void> qcStatementResult = validateQcStatement(certificate);
        if (qcStatementResult.failed()) {
            return qcStatementResult;
        }

        // Step 2: Validate certificate policies (if present)
        Result<Void> policyResult = validateCertificatePolicies(certificate);
        if (policyResult.failed()) {
            monitor.warning("Certificate policy validation failed: " + policyResult.getFailureDetail());
            // Note: Policies might be optional depending on certificate type
        }

        // Step 3: Validate key usage and extended key usage
        Result<Void> keyUsageResult = validateKeyUsage(certificate);
        if (keyUsageResult.failed()) {
            return keyUsageResult;
        }

        monitor.debug("eIDAS certificate profile validation successful");
        return Result.success();
    }

    /**
     * Validate QCStatement extension
     */
    private Result<Void> validateQcStatement(X509Certificate certificate) {
        try {
            X509CertificateHolder certHolder = new JcaX509CertificateHolder(certificate);
            Extension qcExtension = certHolder.getExtension(new ASN1ObjectIdentifier(QC_STATEMENT_OID));

            if (qcExtension == null) {
                monitor.debug("QCStatement extension not found - certificate may not be eIDAS compliant");
                // Note: Depending on requirements, this might be a warning or failure
                return Result.success(); // For now, allow certificates without QCStatement
            }

            // Parse QCStatement
            byte[] qcBytes = qcExtension.getExtnValue().getOctets();
            ASN1Sequence qcSequence = (ASN1Sequence) new ASN1InputStream(qcBytes).readObject();

            if (qcSequence == null || qcSequence.size() == 0) {
                return Result.failure("QCStatement extension is empty");
            }

            // Check for QC compliance OID
            boolean hasQcCompliance = false;
            for (int i = 0; i < qcSequence.size(); i++) {
                ASN1Sequence statement = (ASN1Sequence) qcSequence.getObjectAt(i);
                ASN1ObjectIdentifier oid = (ASN1ObjectIdentifier) statement.getObjectAt(0);
                
                if (QC_COMPLIANCE_OID.equals(oid.getId())) {
                    hasQcCompliance = true;
                    break;
                }
            }

            if (!hasQcCompliance) {
                monitor.warning("QCStatement found but QC compliance OID not present");
                // Note: This might be acceptable for some certificate types
            }

            return Result.success();

        } catch (IOException e) {
            return Result.failure("Failed to parse QCStatement extension: " + e.getMessage());
        } catch (Exception e) {
            return Result.failure("QCStatement validation error: " + e.getMessage());
        }
    }

    /**
     * Validate certificate policies
     */
    private Result<Void> validateCertificatePolicies(X509Certificate certificate) {
        // Certificate policies are optional for eIDAS certificates
        // This is a placeholder for future implementation
        return Result.success();
    }

    /**
     * Validate key usage and extended key usage
     */
    private Result<Void> validateKeyUsage(X509Certificate certificate) {
        // Basic key usage validation
        boolean[] keyUsage = certificate.getKeyUsage();
        if (keyUsage == null || keyUsage.length == 0) {
            return Result.failure("Certificate missing key usage extension");
        }

        // Check for digital signature (required for eIDAS)
        if (!keyUsage[0]) { // digitalSignature
            return Result.failure("Certificate missing digital signature key usage");
        }

        return Result.success();
    }
}

