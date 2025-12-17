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

package org.eclipse.tractusx.edc.certificate;

import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.ApiContext;
import org.eclipse.tractusx.edc.certificate.api.CertificateTestEndpoint;
import org.eclipse.tractusx.edc.certificate.clearinghouse.ClearingHouseCertificateValidator;
import org.eclipse.tractusx.edc.certificate.eidas.EidasCertificateValidator;
import org.eclipse.tractusx.edc.certificate.trustlist.EuTrustListService;
import org.eclipse.tractusx.edc.certificate.validator.CertificateValidator;
import org.eclipse.tractusx.edc.certificate.validator.CertificateValidatorImpl;

/**
 * Certificate Validator Extension
 *
 * PURPOSE:
 * This extension provides comprehensive certificate validation capabilities for
 * eIDAS and Clearing House certificates. It serves as the foundation for all
 * certificate-related operations in the trust chain.
 *
 * CAPABILITIES:
 * 1. eIDAS Certificate Validation
 *    - Validates QCStatement extension (eIDAS compliance)
 *    - Validates certificate types (QCP, QCP-l, QCP-n, QCP-n-l)
 *    - Validates certificate profile compliance
 *    - Validates certificate chains against EU Trust List
 *
 * 2. Clearing House Certificate Validation
 *    - Validates Clearing House as trust anchor
 *    - Validates certificate chains to CH root
 *    - Validates certificate revocation (CRL)
 *
 * 3. EU Trust List Integration
 *    - Downloads EU Trust List (LOTL)
 *    - Parses and validates trust list XML
 *    - Provides trust service provider lookup
 *    - Caches trust list for performance
 *
 * 4. Generic Certificate Validation
 *    - Validates certificate chains
 *    - Validates certificate paths
 *    - Checks certificate expiration and validity
 */
@Extension(value = "Certificate Validator Extension", categories = { "security", "certificate", "trust" })
public class CertificateValidatorExtension implements ServiceExtension {

    @Setting(value = "Enable eIDAS certificate validation", defaultValue = "true")
    public static final String EIDAS_VALIDATION_ENABLED = "edc.certificate.validator.eidas.enabled";

    @Setting(value = "Enable Clearing House certificate validation", defaultValue = "true")
    public static final String CH_VALIDATION_ENABLED = "edc.certificate.validator.clearing.house.enabled";

    @Setting(value = "Enable EU Trust List integration", defaultValue = "true")
    public static final String TRUST_LIST_ENABLED = "edc.certificate.validator.trust.list.enabled";

    @Setting(value = "EU Trust List URL", defaultValue = "https://ec.europa.eu/tools/lotl/eu-lotl.xml")
    public static final String TRUST_LIST_URL = "edc.certificate.validator.trust.list.url";

    @Setting(value = "EU Trust List cache TTL in hours", defaultValue = "24")
    public static final String TRUST_LIST_CACHE_TTL_HOURS = "edc.certificate.validator.trust.list.cache.ttl.hours";

    @Setting(value = "Clearing House trust anchor certificate path (PKCS12 keystore)")
    public static final String CH_TRUST_ANCHOR_PATH = "edc.certificate.validator.clearing.house.trust.anchor.path";

    @Setting(value = "Clearing House trust anchor keystore password (vault alias)")
    public static final String CH_TRUST_ANCHOR_PASSWORD_ALIAS = "edc.certificate.validator.clearing.house.trust.anchor.password.alias";

    @Setting(value = "Clearing House trust anchor keystore type", defaultValue = "PKCS12")
    public static final String CH_TRUST_ANCHOR_TYPE = "edc.certificate.validator.clearing.house.trust.anchor.type";

    @Setting(value = "Enable certificate caching", defaultValue = "true")
    public static final String CERT_CACHE_ENABLED = "edc.certificate.validator.cache.enabled";

    @Setting(value = "Certificate cache TTL in seconds", defaultValue = "3600")
    public static final String CERT_CACHE_TTL_SECONDS = "edc.certificate.validator.cache.ttl.seconds";

    @Inject
    private Monitor monitor;

    @Inject
    private Vault vault;

    @Inject
    private EdcHttpClient httpClient;

    @Inject(required = false)
    private WebService webService;

    private EuTrustListService trustListService;
    private EidasCertificateValidator eidasValidator;
    private ClearingHouseCertificateValidator chValidator;
    private CertificateValidator certificateValidator;

    @Override
    public String name() {
        return "Certificate Validator Extension";
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        monitor.info("=== Certificate Validator Extension Starting ===");

        // Load configuration
        boolean eidasEnabled = context.getSetting(EIDAS_VALIDATION_ENABLED, true);
        boolean chEnabled = context.getSetting(CH_VALIDATION_ENABLED, true);
        boolean trustListEnabled = context.getSetting(TRUST_LIST_ENABLED, true);
        String trustListUrl = context.getSetting(TRUST_LIST_URL, "https://ec.europa.eu/tools/lotl/eu-lotl.xml");
        int trustListCacheTtlHours = context.getSetting(TRUST_LIST_CACHE_TTL_HOURS, 24);
        boolean cacheEnabled = context.getSetting(CERT_CACHE_ENABLED, true);
        int cacheTtlSeconds = context.getSetting(CERT_CACHE_TTL_SECONDS, 3600);

        // Initialize EU Trust List Service (if enabled)
        if (trustListEnabled) {
            monitor.info("Initializing EU Trust List Service...");
            trustListService = new EuTrustListService(
                    trustListUrl,
                    trustListCacheTtlHours,
                    httpClient,
                    monitor
            );
            trustListService.initialize();
            context.registerService(EuTrustListService.class, trustListService);
            monitor.info("EU Trust List Service initialized");
        } else {
            monitor.info("EU Trust List Service disabled");
        }

        // Initialize eIDAS Certificate Validator (if enabled)
        if (eidasEnabled) {
            monitor.info("Initializing eIDAS Certificate Validator...");
            if (trustListService == null) {
                monitor.warning("eIDAS validation enabled but EU Trust List disabled. " +
                        "eIDAS validation will be limited without trust list.");
            }
            eidasValidator = new EidasCertificateValidator(
                    trustListService,
                    monitor
            );
            context.registerService(EidasCertificateValidator.class, eidasValidator);
            monitor.info("eIDAS Certificate Validator initialized");
        } else {
            monitor.info("eIDAS Certificate Validator disabled");
        }

        // Initialize Clearing House Certificate Validator (if enabled)
        if (chEnabled) {
            monitor.info("Initializing Clearing House Certificate Validator...");
            String trustAnchorPath = context.getSetting(CH_TRUST_ANCHOR_PATH, null);
            String trustAnchorPasswordAlias = context.getSetting(CH_TRUST_ANCHOR_PASSWORD_ALIAS, null);
            String trustAnchorType = context.getSetting(CH_TRUST_ANCHOR_TYPE, "PKCS12");

            if (trustAnchorPath == null || trustAnchorPasswordAlias == null) {
                monitor.warning("Clearing House validation enabled but trust anchor not configured. " +
                        "CH validation will be limited. Configure: " + CH_TRUST_ANCHOR_PATH + " and " +
                        CH_TRUST_ANCHOR_PASSWORD_ALIAS);
            }

            chValidator = new ClearingHouseCertificateValidator(
                    trustAnchorPath,
                    trustAnchorPasswordAlias,
                    trustAnchorType,
                    vault,
                    monitor
            );
            context.registerService(ClearingHouseCertificateValidator.class, chValidator);
            monitor.info("Clearing House Certificate Validator initialized");
        } else {
            monitor.info("Clearing House Certificate Validator disabled");
        }

        // Initialize generic Certificate Validator
        monitor.info("Initializing Certificate Validator...");
        certificateValidator = new CertificateValidatorImpl(
                eidasValidator,
                chValidator,
                cacheEnabled,
                cacheTtlSeconds,
                monitor
        );
        context.registerService(CertificateValidator.class, certificateValidator);
        monitor.info("Certificate Validator initialized");

        // Register test endpoint (if WebService is available)
        if (webService != null) {
            monitor.info("Registering certificate test endpoint...");
            CertificateTestEndpoint testEndpoint = new CertificateTestEndpoint(certificateValidator, monitor);
            webService.registerResource(ApiContext.MANAGEMENT, testEndpoint);
            monitor.info("Certificate test endpoint registered at: /api/management/test/certificate");
        } else {
            monitor.debug("WebService not available, skipping test endpoint registration");
        }

        monitor.info("=== Certificate Validator Extension Started Successfully ===");
        monitor.info("  - eIDAS validation: " + (eidasEnabled ? "ENABLED" : "DISABLED"));
        monitor.info("  - Clearing House validation: " + (chEnabled ? "ENABLED" : "DISABLED"));
        monitor.info("  - EU Trust List: " + (trustListEnabled ? "ENABLED" : "DISABLED"));
        monitor.info("  - Certificate caching: " + (cacheEnabled ? "ENABLED" : "DISABLED"));
    }

    @Override
    public void shutdown() {
        monitor.info("Shutting down Certificate Validator Extension");
        if (trustListService != null) {
            trustListService.shutdown();
        }
    }
}

