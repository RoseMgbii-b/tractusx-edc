package org.eclipse.tractusx.edc.clearinghouse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.spi.event.EventRouter;
import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.ApiContext;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationInitiated;
import org.eclipse.tractusx.edc.clearinghouse.api.ClearingHouseTestController;
import org.eclipse.tractusx.edc.clearinghouse.client.GaiaXRegistryComplianceClient;
import org.eclipse.tractusx.edc.clearinghouse.config.GaiaXRegistryComplianceConfig;
import org.eclipse.tractusx.edc.clearinghouse.validation.ParticipantValidationSubscriber;
import org.eclipse.tractusx.edc.spi.identity.mapper.BdrsClient;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@Extension(value = "Clearing House Extension", categories = {"clearinghouse", "trust", "client"})
public class ClearingHouseExtension implements ServiceExtension {
    @Setting(value = "Enable participant validation before contract negotiation", defaultValue = "true")
    public static final String CH_PARTICIPANT_VALIDATION_ENABLED = "edc.clearinghouse.participant.validation.enabled";

    @Setting(value = "Gaia-X Registry base URL", required = false)
    public static final String GX_REGISTRY_BASE_URL = "edc.gaiax.registry.base.url";

    @Setting(value = "Gaia-X Compliance base URL", required = false)
    public static final String GX_COMPLIANCE_BASE_URL = "edc.gaiax.compliance.base.url";

    @Setting(value = "Path to X.509 certificate chain PEM file (for did:web certificate chain endpoint)", required = false, defaultValue = "x509CertificateChain.pem")
    public static final String CERTIFICATE_CHAIN_PATH = "edc.clearinghouse.certificate.chain.path";

    @Inject private Monitor monitor;
    @Inject private EdcHttpClient httpClient;
    @Inject private EventRouter eventRouter;
    @Inject(required = false) private WebService webService;
    @Inject(required = false) private BdrsClient bdrsClient;

    private ExecutorService executor;
    private GaiaXRegistryComplianceClient gxClient;
    private boolean participantValidationEnabled;

    public ClearingHouseExtension() {
        System.out.println("[ClearingHouseExtension] Constructor called - extension class loaded");
    }

    @Override
    public String name() {
        return "Clearing House Extension";
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        try {
            System.out.println("[ClearingHouseExtension] initialize() method called");
            
            if (monitor != null) {
                monitor.info("=== Clearing House Extension Starting ===");
                monitor.info("[ClearingHouseExtension] Initializing Gaia-X Registry/Compliance Extension...");
            }
            
            participantValidationEnabled = context.getSetting(CH_PARTICIPANT_VALIDATION_ENABLED, true);

        // Initialize executor (needed for Gaia-X client)
        executor = Executors.newFixedThreadPool(4);

        // Initialize Gaia-X Registry/Compliance client
        var registryBaseUrl = context.getSetting(GX_REGISTRY_BASE_URL, null);
        var complianceBaseUrl = context.getSetting(GX_COMPLIANCE_BASE_URL, null);

        if (registryBaseUrl != null && !registryBaseUrl.isBlank() && 
            complianceBaseUrl != null && !complianceBaseUrl.isBlank()) {
            try {
                var gxConfig = GaiaXRegistryComplianceConfig.Builder.newInstance()
                        .registryBaseUrl(registryBaseUrl.trim())
                        .complianceBaseUrl(complianceBaseUrl.trim())
                        .connectTimeoutSeconds(10)
                        .readTimeoutSeconds(30)
                        .build();

                gxClient = new GaiaXRegistryComplianceClient(httpClient, gxConfig, new ObjectMapper(), monitor, executor);
                monitor.info("[ClearingHouseExtension] ✓ Gaia-X Registry/Compliance client initialized");
            } catch (Exception e) {
                monitor.warning("[ClearingHouseExtension] ✗ Failed to initialize Gaia-X client: " + e.getMessage());
            }
        } else {
            monitor.warning("[ClearingHouseExtension] ⚠ Gaia-X Registry/Compliance client not configured. " +
                    "Set edc.gaiax.registry.base.url and edc.gaiax.compliance.base.url to enable features.");
        }

        // Register participant validation subscriber if enabled and gxClient is available
        if (participantValidationEnabled && gxClient != null) {
            try {
                var subscriber = new ParticipantValidationSubscriber(
                        gxClient, bdrsClient, monitor, true);
                eventRouter.register(ContractNegotiationInitiated.class, subscriber);
                monitor.info("[ClearingHouseExtension] ✓ Participant validation subscriber registered");
            } catch (Exception e) {
                monitor.warning("[ClearingHouseExtension] ✗ Failed to register participant validation: " + e.getMessage());
            }
        } else if (participantValidationEnabled) {
            monitor.warning("[ClearingHouseExtension] Participant validation requires Gaia-X Registry/Compliance client. " +
                    "Set edc.gaiax.registry.base.url and edc.gaiax.compliance.base.url");
        }

        // Register API endpoint
        if (webService != null) {
            try {
                var certificateChainPath = context.getSetting(CERTIFICATE_CHAIN_PATH, "x509CertificateChain.pem");
                ClearingHouseTestController apiController = new ClearingHouseTestController(gxClient, monitor, certificateChainPath);
                webService.registerResource(ApiContext.MANAGEMENT, apiController);
                monitor.info("[ClearingHouseExtension] ✓ API endpoint registered at: /api/management/v3/clearinghouse");
                monitor.info("[ClearingHouseExtension] Certificate chain path configured: " + certificateChainPath);
            } catch (Exception e) {
                monitor.severe("[ClearingHouseExtension] ✗ Failed to register API endpoint: " + e.getMessage(), e);
            }
        } else {
            monitor.warning("[ClearingHouseExtension] ✗ WebService not available, API endpoint not registered");
        }

            monitor.info("[ClearingHouseExtension] Initialization complete. " +
                    "participantValidation=" + participantValidationEnabled +
                    ", gxClient=" + (gxClient != null ? "configured" : "not configured"));
            monitor.info("=== Clearing House Extension Started ===");
        } catch (Exception e) {
            String errorMsg = "[ClearingHouseExtension] FATAL ERROR during initialization: " + e.getMessage();
            if (monitor != null) {
                monitor.severe(errorMsg, e);
            } else {
                System.err.println(errorMsg);
                e.printStackTrace();
            }
            throw new RuntimeException("Clearing House Extension initialization failed", e);
        }
    }


    @Override
    public void shutdown() {
        if (executor != null) {
            executor.shutdownNow();
        }
        monitor.info("[ClearingHouseExtension] Shutdown complete");
    }



}