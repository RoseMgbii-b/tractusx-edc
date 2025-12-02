package org.eclipse.tractusx.edc.audit.mapping;

import org.eclipse.edc.connector.controlplane.contract.spi.event.contractdefinition.ContractDefinitionCreated;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractdefinition.ContractDefinitionUpdated;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.*;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.*;
import org.eclipse.edc.spi.event.Event;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.tractusx.edc.audit.mapping.extractor.ReflectionExtractor;
import org.eclipse.tractusx.edc.spi.audit.types.AuditEvent;
import org.eclipse.tractusx.edc.spi.audit.types.AuditEventCategory;
import org.eclipse.tractusx.edc.spi.audit.types.AuditEventName;
import org.eclipse.tractusx.edc.spi.audit.types.AuditOutcome;

import org.eclipse.edc.connector.controlplane.asset.spi.event.AssetCreated;
import org.eclipse.edc.connector.controlplane.asset.spi.event.AssetUpdated;
import org.eclipse.edc.connector.controlplane.asset.spi.event.AssetDeleted;
import org.eclipse.edc.connector.controlplane.policy.spi.event.PolicyDefinitionCreated;
import org.eclipse.edc.connector.controlplane.policy.spi.event.PolicyDefinitionUpdated;
import org.eclipse.edc.connector.controlplane.policy.spi.event.PolicyDefinitionDeleted;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * The Audit Event Mapper Registry
 * Decides how specific event class are translated into Audit Event
 * BiConsumer receives (event, builder) and fills in the audit fields.
 */
public class AuditEventMapperRegistry {
    private final Map<Class<? extends Event>, BiConsumer<Event, AuditEvent.Builder>> handlers = new ConcurrentHashMap<>();
    private final ReflectionExtractor extractor;
    private final Monitor monitor;

    public AuditEventMapperRegistry(Monitor monitor) {
        this.monitor = monitor;
        this.extractor = new ReflectionExtractor(monitor);
        registerDefaultMappings();
    }

    /**
     * Entry point used by the subscriber
     */
    public Optional<AuditEvent> map(Event event) {
        var handler = findHandler(event.getClass());
        if (handler == null) {
            monitor.debug("[AuditEventMapperRegistry] No mapping for event type: "
                    + event.getClass().getName());
            return Optional.empty();
        }

        var builder = AuditEvent.Builder.newInstance()
                .id(UUID.randomUUID().toString())
                .timestamp(Instant.now());

        handler.accept(event, builder);

        try {
            return Optional.of(builder.build());
        } catch (Exception e) {
            monitor.warning("[AuditEventMapperRegistry] Failed to build AuditEvent for "
                    + event.getClass().getSimpleName() + ": " + e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Resolves the most specific mapping handler for a given event type.
     */
    BiConsumer<Event, AuditEvent.Builder> findHandler(Class<?> eventType) {

        Class<?> current = eventType;
        while (current != null && current != Object.class) {
            var handler = handlers.get(current);
            if (handler != null) {
                return handler;
            }
            current = current.getSuperclass();
        }
        return null;
    }

    /**
     * Registers a mapping function for a specific event class.
     */
    public <E extends Event> void register(Class<E> eventClass, BiConsumer<E, AuditEvent.Builder> consumer) {
        handlers.put(eventClass, (Event e, AuditEvent.Builder b) -> consumer.accept(eventClass.cast(e), b));
    }

    /**
     * Attempts to resolve a string value from an event by name
     */
    private String resolveProperty(Object payload, String fieldName) {
        return extractor.extractStringProperty(payload, fieldName);
    }

    /**
     * Registers concrete event mappings
     *
     */
    private void registerDefaultMappings() {
        // CONTRACT NEGOTIATION
        register(ContractNegotiationInitiated.class, (event, b) -> b
                .category(AuditEventCategory.CONTRACT_NEGOTIATION)
                .eventName(AuditEventName.CONTRACT_NEGOTIATION_INITIATED)
                .outcome(AuditOutcome.SUCCESS)
                .subjectId(resolveProperty(event, "contractNegotiationId"))
                .actorId(resolveProperty(event, "counterPartyId"))
                .description("Contract negotiation initiated.")
        );

        register(ContractNegotiationRequested.class, (event, b) -> b
                .category(AuditEventCategory.CONTRACT_REQUEST)
                .eventName(AuditEventName.CONTRACT_NEGOTIATION_REQUESTED)
                .outcome(AuditOutcome.SUCCESS)
                .subjectId(resolveProperty(event, "contractNegotiationId"))
                .actorId(resolveProperty(event, "counterPartyId"))
                .description("Contract negotiation requested.")
        );

        register(ContractNegotiationOffered.class, (event, b) -> b
                .category(AuditEventCategory.CONTRACT_NEGOTIATION)
                .eventName(AuditEventName.CONTRACT_NEGOTIATION_OFFERED)
                .outcome(AuditOutcome.SUCCESS)
                .subjectId(resolveProperty(event, "contractNegotiationId"))
                .actorId(resolveProperty(event, "counterPartyId"))
                .description("Contract negotiation offered.")
        );

        register(ContractNegotiationAccepted.class, (event, b) -> b
                .category(AuditEventCategory.CONTRACT_NEGOTIATION)
                .eventName(AuditEventName.CONTRACT_NEGOTIATION_ACCEPTED)
                .outcome(AuditOutcome.SUCCESS)
                .subjectId(resolveProperty(event, "contractNegotiationId"))
                .actorId(resolveProperty(event, "counterPartyId"))
                .description("Contract negotiation accepted.")
        );

        register(ContractNegotiationAgreed.class, (event, b) -> b
                .category(AuditEventCategory.CONTRACT_NEGOTIATION)
                .eventName(AuditEventName.CONTRACT_NEGOTIATION_AGREED)
                .outcome(AuditOutcome.SUCCESS)
                .subjectId(resolveProperty(event, "contractNegotiationId"))
                .actorId(resolveProperty(event, "counterPartyId"))
                .description("Contract negotiation agreed.")
        );

        register(ContractNegotiationFinalized.class, (event, b) -> b
                .category(AuditEventCategory.CONTRACT_VALIDATION)
                .eventName(AuditEventName.CONTRACT_NEGOTIATION_FINALIZED)
                .outcome(AuditOutcome.SUCCESS)
                .subjectId(resolveProperty(event, "contractNegotiationId"))
                .actorId(resolveProperty(event, "counterPartyId"))
                .description("Contract negotiation finalized (agreement created).")
        );

        register(ContractNegotiationVerified.class, (event, b) -> b
                .category(AuditEventCategory.CONTRACT_VALIDATION)
                .eventName(AuditEventName.CONTRACT_NEGOTIATION_VERIFIED)
                .outcome(AuditOutcome.SUCCESS)
                .subjectId(resolveProperty(event, "contractNegotiationId"))
                .actorId(resolveProperty(event, "counterPartyId"))
                .description("Contract negotiation verified.")
        );

        register(ContractNegotiationTerminated.class, (event, b) -> b
                .category(AuditEventCategory.CONTRACT_NEGOTIATION)
                .eventName(AuditEventName.CONTRACT_NEGOTIATION_TERMINATED)
                .outcome(AuditOutcome.WARNING)
                .subjectId(resolveProperty(event, "contractNegotiationId"))
                .actorId(resolveProperty(event, "counterPartyId"))
                .description("Contract negotiation terminated.")
        );

        // CONTRACT DEFINITION
        register(ContractDefinitionCreated.class, (event, b) -> b
                .category(AuditEventCategory.CONTRACT_DEFINITION)
                .eventName(AuditEventName.CONTRACT_DEFINITION_CREATED)
                .outcome(AuditOutcome.SUCCESS)
                .subjectId(resolveProperty(event, "contractDefinitionId"))
                .description("Contract definition created.")
        );

        register(ContractDefinitionUpdated.class, (event, b) -> b
                .category(AuditEventCategory.CONTRACT_DEFINITION)
                .eventName(AuditEventName.CONTRACT_DEFINITION_UPDATED)
                .outcome(AuditOutcome.SUCCESS)
                .subjectId(resolveProperty(event, "contractDefinitionId"))
                .description("Contract definition updated.")
        );

        // ASSET
        register(AssetCreated.class, (event, b) -> b
                .category(AuditEventCategory.ASSET)
                .eventName(AuditEventName.ASSET_CREATED)
                .outcome(AuditOutcome.SUCCESS)
                .subjectId(resolveProperty(event, "assetId"))
                .description("Asset created.")
        );

        register(AssetUpdated.class, (event, b) -> b
                .category(AuditEventCategory.ASSET)
                .eventName(AuditEventName.ASSET_UPDATED)
                .outcome(AuditOutcome.SUCCESS)
                .subjectId(resolveProperty(event, "assetId"))
                .description("Asset updated.")
        );

        register(AssetDeleted.class, (event, b) -> b
                .category(AuditEventCategory.ASSET)
                .eventName(AuditEventName.ASSET_DELETED)
                .outcome(AuditOutcome.SUCCESS)
                .subjectId(resolveProperty(event, "assetId"))
                .description("Asset deleted.")
        );

        // TRANSFER PROCESS
        register(TransferProcessInitiated.class, (event, b) -> b
                .category(AuditEventCategory.TRANSFER_PROCESS)
                .eventName(AuditEventName.TRANSFER_PROCESS_INITIATED)
                .outcome(AuditOutcome.SUCCESS)
                .subjectId(resolveProperty(event, "transferProcessId"))
                .description("Transfer process initiated.")
        );


        register(TransferProcessRequested.class, (event, b) -> b
                .category(AuditEventCategory.TRANSFER_PROCESS)
                .eventName(AuditEventName.TRANSFER_PROCESS_REQUESTED)
                .outcome(AuditOutcome.SUCCESS)
                .subjectId(resolveProperty(event, "transferProcessId"))
                .description("Transfer process requested.")
        );

        register(TransferProcessStarted.class, (event, b) -> b
                .category(AuditEventCategory.TRANSFER_PROCESS)
                .eventName(AuditEventName.TRANSFER_PROCESS_STARTED)
                .outcome(AuditOutcome.SUCCESS)
                .subjectId(resolveProperty(event, "transferProcessId"))
                .description("Transfer process started.")
        );

        register(TransferProcessSuspended.class, (event, b) -> b
                .category(AuditEventCategory.TRANSFER_PROCESS)
                .eventName(AuditEventName.TRANSFER_PROCESS_SUSPENDED)
                .outcome(AuditOutcome.WARNING)
                .subjectId(resolveProperty(event, "transferProcessId"))
                .description("Transfer process suspended.")
        );

        register(TransferProcessTerminated.class, (event, b) -> b
                .category(AuditEventCategory.TRANSFER_PROCESS)
                .eventName(AuditEventName.TRANSFER_PROCESS_TERMINATED)
                .outcome(AuditOutcome.WARNING)
                .subjectId(resolveProperty(event, "transferProcessId"))
                .description("Transfer process terminated.")
        );

        register(TransferProcessProvisioningRequested.class, (event, b) -> b
                .category(AuditEventCategory.TRANSFER_PROCESS)
                .eventName(AuditEventName.TRANSFER_PROCESS_PROVISIONING_REQUESTED)
                .outcome(AuditOutcome.SUCCESS)
                .subjectId(resolveProperty(event, "transferProcessId"))
                .description("Transfer process provisioning requested.")
        );

        register(TransferProcessProvisioned.class, (event, b) -> b
                .category(AuditEventCategory.TRANSFER_PROCESS)
                .eventName(AuditEventName.TRANSFER_PROCESS_PROVISIONED)
                .outcome(AuditOutcome.SUCCESS)
                .subjectId(resolveProperty(event, "transferProcessId"))
                .description("Transfer process provisioned.")
        );

        register(TransferProcessDeprovisioningRequested.class, (event, b) -> b
                .category(AuditEventCategory.TRANSFER_PROCESS)
                .eventName(AuditEventName.TRANSFER_PROCESS_DEPROVISIONING_REQUESTED)
                .outcome(AuditOutcome.SUCCESS)
                .subjectId(resolveProperty(event, "transferProcessId"))
                .description("Transfer process deprovisioning requested.")
        );

        // POLICY DEFINITION
        register(PolicyDefinitionCreated.class, (event, b) -> b
                .category(AuditEventCategory.POLICY)
                .eventName(AuditEventName.POLICY_DEFINITION_CREATED)
                .outcome(AuditOutcome.SUCCESS)
                .subjectId(resolveProperty(event, "policyDefinitionId"))
                .description("Policy definition created.")
        );

        register(PolicyDefinitionUpdated.class, (event, b) -> b
                .category(AuditEventCategory.POLICY)
                .eventName(AuditEventName.POLICY_DEFINITION_UPDATED)
                .outcome(AuditOutcome.SUCCESS)
                .subjectId(resolveProperty(event, "policyDefinitionId"))
                .description("Policy definition updated.")
        );

        register(PolicyDefinitionDeleted.class, (event, b) -> b
                .category(AuditEventCategory.POLICY)
                .eventName(AuditEventName.POLICY_DEFINITION_DELETED)
                .outcome(AuditOutcome.SUCCESS)
                .subjectId(resolveProperty(event, "policyDefinitionId"))
                .description("Policy definition deleted.")
        );
    }

}
