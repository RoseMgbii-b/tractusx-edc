package org.eclipse.tractusx.edc.clearinghouse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationFinalized;
import org.eclipse.edc.connector.controlplane.services.spi.contractagreement.ContractAgreementService;
import org.eclipse.edc.connector.controlplane.services.spi.transferprocess.TransferProcessService;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessCompleted;
import org.eclipse.edc.spi.event.Event;
import org.eclipse.edc.spi.event.EventRouter;
import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.event.EventEnvelope;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.tractusx.edc.clearinghouse.client.ClearingHouseClient;
import org.eclipse.tractusx.edc.clearinghouse.config.ClearingHouseConfig;
import org.eclipse.tractusx.edc.clearinghouse.mapper.ClearingHouseEventMapper;
import org.eclipse.tractusx.edc.certificate.validator.CertificateValidator;

import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@Extension(value = "Clearing House Extension", categories = {"clearinghouse", "trust", "client"})
public class ClearingHouseExtension implements ServiceExtension {
    @Setting(value = "Clearing House Node base URL", required = true)
    public static final String CH_BASE_URL = "edc.clearinghouse.base.url";

    @Setting(value = "Clearing House API key alias/reference in vault", required = true)
    public static final String CH_API_KEY_ALIAS = "edc.clearinghouse.api.key.alias";

    @Setting(value = "Enable event logging", defaultValue = "true")
    public static final String CH_EVENT_LOGGING_ENABLED = "edc.clearinghouse.event.logging.enabled";

    @Setting(value = "Enable receipt verification", defaultValue = "true")
    public static final String CH_RECEIPT_VERIFICATION_ENABLED = "edc.clearinghouse.receipt.verification.enabled";

    @Setting(value = "Enable trust anchor fetching", defaultValue = "false")
    public static final String CH_TRUST_ANCHOR_ENABLED = "edc.clearinghouse.trust.anchor.enabled";

    @Inject private Monitor monitor;
    @Inject private EdcHttpClient httpClient;
    @Inject private EventRouter eventRouter;
    @Inject private ContractAgreementService contractAgreementService;
    @Inject private TransferProcessService transferProcessService;
    @Inject private Vault vault;
    @Inject private CertificateValidator certificateValidator;

    private ExecutorService executor;
    private ClearingHouseClient client;
    private ClearingHouseEventMapper mapper;

    private boolean eventLoggingEnabled;
    private boolean receiptVerificationEnabled;
    private boolean trustAnchorEnabled;

    @Override
    public void initialize(ServiceExtensionContext context) {
        eventLoggingEnabled = context.getSetting(CH_EVENT_LOGGING_ENABLED, true);
        receiptVerificationEnabled = context.getSetting(CH_RECEIPT_VERIFICATION_ENABLED, true);
        trustAnchorEnabled = context.getSetting(CH_TRUST_ANCHOR_ENABLED, false);

        var baseUrl = context.getSetting(CH_BASE_URL, null);
        var apiKeyAlias = context.getSetting(CH_API_KEY_ALIAS, null);

        if (baseUrl == null || apiKeyAlias == null) {
            monitor.warning("[ClearingHouseExtension] Missing required settings; extension disabled.");
            return;
        }

        var apiKey = vault.resolveSecret(apiKeyAlias);
        if (apiKey == null || apiKey.isBlank()) {
            monitor.severe("[ClearingHouseExtension] Vault secret not found or empty for alias: " + apiKeyAlias);
            return;
        }

        executor = Executors.newFixedThreadPool(4);

        var config = ClearingHouseConfig.Builder.newInstance()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .connectTimeoutSeconds(10)
                .readTimeoutSeconds(30)
                .build();

        client = new ClearingHouseClient(httpClient, config, new ObjectMapper(), monitor, executor);
        mapper = new ClearingHouseEventMapper(monitor, contractAgreementService, transferProcessService);

        if (eventLoggingEnabled) {
            registerEventHandlers();
        }

        if (trustAnchorEnabled) {
            client.fetchTrustAnchor()
                    .thenAccept(result -> {
                        if (result.succeeded()) {
                            var cert = result.getContent();

                            if (certificateValidator != null) {
                                var validation = certificateValidator.validateClearingHouseCertificate(cert);
                                if (validation.failed()) {
                                    monitor.warning("[ClearingHouseExtension] Trust anchor cert validation failed: " +
                                            validation.getFailureDetail());
                                    return;
                                }
                            }
                            monitor.info("[ClearingHouseExtension] Trust anchor subject: " + cert.getSubjectX500Principal());
                        } else {
                            monitor.warning("[ClearingHouseExtension] Trust anchor fetch failed: " +
                                    result.getFailureDetail());
                        }
                    });
        }

        monitor.info("[ClearingHouseExtension] Initialized. logging=" + eventLoggingEnabled +
                ", verify=" + receiptVerificationEnabled +
                ", trustAnchor=" + trustAnchorEnabled);
    }

    private void registerEventHandlers() {
        eventRouter.register(ContractNegotiationFinalized.class, this::onEvent);
        eventRouter.register(TransferProcessCompleted.class, this::onEvent);
        monitor.info("[ClearingHouseExtension] Registered CHN event handlers");
    }

    private void onEvent(EventEnvelope<?> envelope) {
        var payload = envelope.getPayload();
        if (!(payload instanceof Event)) {
            return;
        }
        process((Event) payload, envelope.getId(), envelope.getAt());
    }

    private void process(Event edcEvent, String envelopeId, long atMillis) {
        if (!eventLoggingEnabled) return;
        if (client == null || mapper == null) return;

        var txEvent = mapper.mapToChnEvent(edcEvent);
        if (txEvent == null) return;

        // Ensure eventId/timestamp if mapper didn’t set them
        var effectiveEventId = (txEvent.getEventId() == null || txEvent.getEventId().isBlank())
                ? (envelopeId != null ? envelopeId : "evt-" + Instant.now().toEpochMilli())
                : txEvent.getEventId();

        var effectiveTimestamp = (txEvent.getTimestamp() == null || txEvent.getTimestamp().isBlank())
                ? Instant.ofEpochMilli(atMillis > 0 ? atMillis : System.currentTimeMillis()).toString()
                : txEvent.getTimestamp();

        // If your model doesn’t support “mutating”, rebuild only when necessary
        if (!effectiveEventId.equals(txEvent.getEventId()) || !effectiveTimestamp.equals(txEvent.getTimestamp())) {
            txEvent = org.eclipse.tractusx.edc.spi.clearinghouse.model.TransactionEvent.Builder.newInstance()
                    .eventId(effectiveEventId)
                    .eventType(txEvent.getEventType())
                    .timestamp(effectiveTimestamp)
                    .transactionId(txEvent.getTransactionId())
                    .contractId(txEvent.getContractId())
                    .assetId(txEvent.getAssetId())
                    .providerDid(txEvent.getProviderDid())
                    .consumerDid(txEvent.getConsumerDid())
                    .providerBpn(txEvent.getProviderBpn())
                    .consumerBpn(txEvent.getConsumerBpn())
                    .hash(txEvent.getHash())
                    .signature(txEvent.getSignature())
                    .additionalProperties(txEvent.getAdditionalProperties())
                    .build();
        }

        client.logEvent(txEvent).thenAccept(result -> {
            if (result.failed()) {
                monitor.warning("[ClearingHouseExtension] logEvent failed: " + result.getFailureDetail());
                return;
            }

            var receipt = result.getContent();
            monitor.info("[ClearingHouseExtension] Logged event. receiptId=" + receipt.getReceiptId());

            if (receiptVerificationEnabled) {
                client.verifyReceipt(receipt).thenAccept(v -> {
                    if (v.succeeded() && Boolean.TRUE.equals(v.getContent())) {
                        monitor.debug("[ClearingHouseExtension] Receipt verified: " + receipt.getReceiptId());
                    } else {
                        monitor.warning("[ClearingHouseExtension] Receipt verification failed: " +
                                (v.failed() ? v.getFailureDetail() : "invalid"));
                    }
                });
            }
        });
    }



    private void onContractFinalized(EventEnvelope<ContractNegotiationFinalized> envelope) {
        process(envelope.getPayload(), envelope.getId(), envelope.getAt());
    }

    private void onTransferCompleted(EventEnvelope<TransferProcessCompleted> envelope) {
        process(envelope.getPayload(), envelope.getId(), envelope.getAt());
    }

    @Override
    public void shutdown() {
        if (executor != null) {
            executor.shutdownNow();
        }
        monitor.info("[ClearingHouseExtension] Shutdown complete");
    }



}