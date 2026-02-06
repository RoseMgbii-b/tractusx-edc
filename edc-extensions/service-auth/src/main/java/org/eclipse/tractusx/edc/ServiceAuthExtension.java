/********************************************************************************
 * Copyright (c) 2025 Contributors
 *
 * SPDX-License-Identifier: Apache-2.0
 ********************************************************************************/

package org.eclipse.tractusx.edc;

import org.eclipse.edc.connector.controlplane.asset.spi.index.AssetIndex;
import org.eclipse.edc.connector.controlplane.services.spi.contractdefinition.ContractDefinitionService;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.ApiContext;
import org.eclipse.tractusx.edc.api.ServiceApiController;
import org.eclipse.tractusx.edc.clearinghouse.client.GaiaXRegistryComplianceClient;
import org.eclipse.tractusx.edc.controller.OpenIdConfigurationController;
import org.eclipse.tractusx.edc.controller.VpTokenController;
import org.eclipse.tractusx.edc.filter.ServiceAuthorizationFilter;
import org.eclipse.tractusx.edc.service.TokenSigner;
import org.eclipse.tractusx.edc.service.TokenSignerImpl;
import org.eclipse.tractusx.edc.service.VpVerifier;
import org.eclipse.tractusx.edc.service.VpVerifierImpl;


/**
 * Service Auth Extension - Enables VP-based authentication for service APIs.
 *
 * This extension implements the runtime access phase for service exchange:
 * - OpenID Configuration endpoint
 * - Token endpoint (VP → JWT)
 * - PDP authorization filter
 */

@Extension("Service Auth Extension")
public class ServiceAuthExtension implements ServiceExtension {

    @Setting(value = "Enable service auth extension", defaultValue = "true")
    public static final String ENABLED = "edc.serviceauth.enabled";

    @Setting(value = "Token expiration time in seconds", defaultValue = "7200")
    public static final String TOKEN_EXPIRATION = "edc.serviceauth.token.expiration.seconds";

    @Setting(value = "Token issuer DID", required = false)
    public static final String TOKEN_ISSUER = "edc.serviceauth.token.issuer";

    @Setting(value = "OpenID config path", defaultValue = "/.well-known/openid-configuration")
    public static final String OPENID_CONFIG_PATH = "edc.serviceauth.openid.config.path";

    @Setting(value = "Token endpoint path", defaultValue = "/vp-token")
    public static final String TOKEN_ENDPOINT_PATH = "edc.serviceauth.token.endpoint.path";

    @Inject
    private Monitor monitor;

    @Inject
    private WebService webService;

    @Inject
    private AssetIndex assetIndex;

    @Inject
    private PolicyEngine policyEngine;

    @Inject
    private ContractDefinitionService contractDefinitionService;

    @Inject
    private org.eclipse.edc.connector.controlplane.policy.spi.store.PolicyDefinitionStore policyDefinitionStore;

    @Inject(required = false)
    private GaiaXRegistryComplianceClient gxClient;

    @Override
    public void initialize(ServiceExtensionContext context) {
        var enabled = context.getConfig().getBoolean(ENABLED, true);
        if (!enabled) {
            monitor.info("[ServiceAuth] Service auth extension is disabled");
            return;
        }

        var tokenExpiration = context.getConfig().getInteger(TOKEN_EXPIRATION, 7200);
        var tokenIssuer = context.getConfig().getString(TOKEN_ISSUER, context.getParticipantId());
        var openIdConfigPath = context.getConfig().getString(OPENID_CONFIG_PATH, "/.well-known/openid-configuration");
        var tokenEndpointPath = context.getConfig().getString(TOKEN_ENDPOINT_PATH, "/token");

        monitor.info("[ServiceAuth] Initializing service auth extension");

        // Create services
        var vpVerifier = createVpVerifier(context);
        var tokenSigner = createTokenSigner(context, tokenIssuer, tokenExpiration);

        // Register OpenID config endpoint
        var openIdController = new OpenIdConfigurationController(monitor, tokenEndpointPath);
        webService.registerResource(ApiContext.PUBLIC, openIdController);
        monitor.info("[ServiceAuth] Registered OpenID config endpoint: " + openIdConfigPath);

        // Register token endpoint
        var tokenController = new VpTokenController(monitor, vpVerifier, tokenSigner);
        webService.registerResource(ApiContext.PUBLIC, tokenController);
        monitor.info("[ServiceAuth] Registered token endpoint: " + tokenEndpointPath);

        // Register PDP authorization filter
        var authFilter = new ServiceAuthorizationFilter(
                monitor, tokenSigner, policyEngine, assetIndex, contractDefinitionService, policyDefinitionStore);
        webService.registerResource(ApiContext.PUBLIC, authFilter);
        monitor.info("[ServiceAuth] Registered service authorization filter");

        // Register example service API controller
        var serviceApiController = new ServiceApiController(monitor);
        webService.registerResource(ApiContext.PUBLIC, serviceApiController);
        monitor.info("[ServiceAuth] Registered example service API controller");

        monitor.info("[ServiceAuth] Service auth extension initialized successfully");
    }

    private VpVerifier createVpVerifier(ServiceExtensionContext context) {
        return new VpVerifierImpl(monitor, gxClient);
    }

    private TokenSigner createTokenSigner(ServiceExtensionContext context, String issuer, int expirationSeconds) {
        return new TokenSignerImpl(monitor, issuer, expirationSeconds);
    }
}
