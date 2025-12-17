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

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.tractusx.edc.certificate.validator.CertificateValidator;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;

/**
 * Test endpoint for certificate validation
 * 
 * Provides REST endpoints for manually testing certificate validation functionality
 */
@Path("/test/certificate")
@Consumes({ MediaType.APPLICATION_OCTET_STREAM, MediaType.TEXT_PLAIN, APPLICATION_JSON })
@Produces(APPLICATION_JSON)
public class CertificateTestEndpoint {

    private final CertificateValidator validator;
    private final Monitor monitor;

    public CertificateTestEndpoint(CertificateValidator validator, Monitor monitor) {
        this.validator = validator;
        this.monitor = monitor;
    }

    /**
     * Validate a certificate
     * 
     * Accepts certificate in multiple formats:
     * - Binary DER format (application/octet-stream)
     * - Base64 encoded PEM (text/plain)
     * - Base64 encoded DER (text/plain)
     * 
     * @param certBytes Certificate bytes (DER, PEM, or Base64 encoded)
     * @return Validation result
     */
    @POST
    @Path("/validate")
    public Response validateCertificate(byte[] certBytes) {
        if (certBytes == null || certBytes.length == 0) {
            return Response.status(BAD_REQUEST)
                    .entity(createErrorResponse("Certificate data is required"))
                    .build();
        }

        try {
            X509Certificate cert = parseCertificate(certBytes);
            monitor.info("Validating certificate: " + cert.getSubjectDN());
            
            Result<Void> result = validator.validateCertificate(cert);
            
            if (result.succeeded()) {
                return Response.ok()
                        .entity(createSuccessResponse("Certificate is valid", cert))
                        .build();
            } else {
                return Response.status(BAD_REQUEST)
                        .entity(createErrorResponse("Certificate validation failed: " + result.getFailureDetail()))
                        .build();
            }
        } catch (CertificateException e) {
            monitor.warning("Failed to parse certificate: " + e.getMessage());
            return Response.status(BAD_REQUEST)
                    .entity(createErrorResponse("Failed to parse certificate: " + e.getMessage()))
                    .build();
        } catch (Exception e) {
            monitor.severe("Unexpected error during certificate validation: " + e.getMessage(), e);
            return Response.status(INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Internal server error: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Validate an eIDAS certificate
     */
    @POST
    @Path("/validate/eidas")
    public Response validateEidasCertificate(byte[] certBytes) {
        if (certBytes == null || certBytes.length == 0) {
            return Response.status(BAD_REQUEST)
                    .entity(createErrorResponse("Certificate data is required"))
                    .build();
        }

        try {
            X509Certificate cert = parseCertificate(certBytes);
            monitor.info("Validating eIDAS certificate: " + cert.getSubjectDN());
            
            Result<Void> result = validator.validateEidasCertificate(cert);
            
            if (result.succeeded()) {
                return Response.ok()
                        .entity(createSuccessResponse("eIDAS certificate is valid", cert))
                        .build();
            } else {
                return Response.status(BAD_REQUEST)
                        .entity(createErrorResponse("eIDAS certificate validation failed: " + result.getFailureDetail()))
                        .build();
            }
        } catch (CertificateException e) {
            return Response.status(BAD_REQUEST)
                    .entity(createErrorResponse("Failed to parse certificate: " + e.getMessage()))
                    .build();
        } catch (Exception e) {
            monitor.severe("Unexpected error during eIDAS certificate validation: " + e.getMessage(), e);
            return Response.status(INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Internal server error: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Validate a Clearing House certificate
     */
    @POST
    @Path("/validate/clearinghouse")
    public Response validateClearingHouseCertificate(byte[] certBytes) {
        if (certBytes == null || certBytes.length == 0) {
            return Response.status(BAD_REQUEST)
                    .entity(createErrorResponse("Certificate data is required"))
                    .build();
        }

        try {
            X509Certificate cert = parseCertificate(certBytes);
            monitor.info("Validating Clearing House certificate: " + cert.getSubjectDN());
            
            Result<Void> result = validator.validateClearingHouseCertificate(cert);
            
            if (result.succeeded()) {
                return Response.ok()
                        .entity(createSuccessResponse("Clearing House certificate is valid", cert))
                        .build();
            } else {
                return Response.status(BAD_REQUEST)
                        .entity(createErrorResponse("Clearing House certificate validation failed: " + result.getFailureDetail()))
                        .build();
            }
        } catch (CertificateException e) {
            return Response.status(BAD_REQUEST)
                    .entity(createErrorResponse("Failed to parse certificate: " + e.getMessage()))
                    .build();
        } catch (Exception e) {
            monitor.severe("Unexpected error during Clearing House certificate validation: " + e.getMessage(), e);
            return Response.status(INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Internal server error: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Parse certificate from bytes
     * Supports DER, PEM, and Base64 encoded formats
     */
    private X509Certificate parseCertificate(byte[] certBytes) throws CertificateException {
        try {
            // Try parsing as DER first
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            ByteArrayInputStream bis = new ByteArrayInputStream(certBytes);
            return (X509Certificate) factory.generateCertificate(bis);
        } catch (CertificateException e) {
            // Try parsing as Base64 encoded
            try {
                String certString = new String(certBytes);
                
                // Remove PEM headers/footers if present
                certString = certString.replace("-----BEGIN CERTIFICATE-----", "")
                                       .replace("-----END CERTIFICATE-----", "")
                                       .replaceAll("\\s", "");
                
                // Decode Base64
                byte[] decoded = Base64.getDecoder().decode(certString);
                CertificateFactory factory = CertificateFactory.getInstance("X.509");
                ByteArrayInputStream bis = new ByteArrayInputStream(decoded);
                return (X509Certificate) factory.generateCertificate(bis);
            } catch (Exception e2) {
                throw new CertificateException("Failed to parse certificate. Tried DER and Base64/PEM formats. " + 
                        "Original error: " + e.getMessage() + ", Base64 error: " + e2.getMessage(), e);
            }
        }
    }

    /**
     * Create success response JSON
     */
    private String createSuccessResponse(String message, X509Certificate cert) {
        return String.format(
            "{\"success\":true,\"message\":\"%s\",\"subject\":\"%s\",\"issuer\":\"%s\",\"serialNumber\":\"%s\"}",
            message,
            escapeJson(cert.getSubjectDN().getName()),
            escapeJson(cert.getIssuerDN().getName()),
            cert.getSerialNumber().toString()
        );
    }

    /**
     * Create error response JSON
     */
    private String createErrorResponse(String message) {
        return String.format("{\"success\":false,\"message\":\"%s\"}", escapeJson(message));
    }

    /**
     * Escape JSON string
     */
    private String escapeJson(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\")
                 .replace("\"", "\\\"")
                 .replace("\n", "\\n")
                 .replace("\r", "\\r")
                 .replace("\t", "\\t");
    }
}

