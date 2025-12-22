package org.eclipse.tractusx.edc.clearinghouse.mapper;

import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationFinalized;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.services.spi.contractagreement.ContractAgreementService;
import org.eclipse.edc.connector.controlplane.services.spi.transferprocess.TransferProcessService;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessCompleted;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.spi.event.Event;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.tractusx.edc.spi.clearinghouse.model.TransactionEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * This resolves the full evidence chain:
 * Event to ContractId
 * ContractId to AgreementId (for BPNs)
 * Format as CHN evidence
 * todo: change chnEvent to proper naming
 */

public class ClearingHouseEventMapper {
    private final Monitor monitor;
    private final ContractAgreementService contractAgreementService;
    private final TransferProcessService transferProcessService;

    public ClearingHouseEventMapper(Monitor monitor, ContractAgreementService contractAgreementService, TransferProcessService transferProcessService) {
        this.monitor = monitor;
        this.contractAgreementService = contractAgreementService;
        this.transferProcessService = transferProcessService;

        monitor.info("[ClearingHouseEventMapper]: Event mapper initialized");
    }

    public TransactionEvent mapToChnEvent(Event edcEvent) {

        monitor.debug("[ClearingHouseEventMapper]: Mapping EDC event to CHN format");

        if (edcEvent == null) {
            monitor.warning("[ClearingHouseEventMapper]: Cannot map null event");
            return null;
        }

        try {
            if (edcEvent instanceof ContractNegotiationFinalized) {
                return mapContractFinalized((ContractNegotiationFinalized) edcEvent);
            } else if (edcEvent instanceof TransferProcessCompleted) {
                return mapTransferCompleted((TransferProcessCompleted) edcEvent);
            } else {
                monitor.debug("[ClearingHouseEventMapper]: Unsupported event type: " +
                        edcEvent.getClass().getSimpleName());
                return null;
            }
        } catch (Exception e) {
            monitor.severe("[ClearingHouseEventMapper]: Error mapping event. Exception: " +
                    e.getClass().getSimpleName() + ". Message: " + e.getMessage());
            return null;
        }

    }

    private TransactionEvent mapContractFinalized(ContractNegotiationFinalized event) {
        monitor.debug("[ClearingHouseEventMapper]: Mapping ContractNegotiationFinalized event");

        ContractAgreement agreement = event.getContractAgreement();
        if (agreement == null) {
            monitor.warning("[ClearingHouseEventMapper]: Contract agreement is null in finalized event");
            return null;
        }

        monitor.debug("[ClearingHouseEventMapper]: Found agreement ID: " + agreement.getId() +
                ", Provider BPN: " + agreement.getProviderId() +
                ", Consumer BPN: " + agreement.getConsumerId());

        return TransactionEvent.Builder.newInstance()
                .eventId(generateEventId())
                .eventType("CONTRACT_AGREEMENT_CREATED")
                .timestamp(Instant.now().toString())
                .transactionId(event.getContractNegotiationId())
                .contractId(agreement.getId())
                .providerBpn(agreement.getProviderId())
                .consumerBpn(agreement.getConsumerId())
                .assetId(agreement.getAssetId())
                .hash(calculateAgreementHash(agreement))
                .build();
    }

    private TransactionEvent mapTransferCompleted(TransferProcessCompleted event) {
        monitor.debug("[ClearingHouseEventMapper]: Mapping TransferProcessCompleted event: " +
                event.getTransferProcessId());

        // Trying to find contract ID from transfer process
        String contractId = extractContractIdFromTransfer(event);
        if (contractId == null) {
            monitor.warning("[ClearingHouseEventMapper]: Could not extract contract ID from transfer: " +
                    event.getTransferProcessId());
            return null;
        }
        // Use ContractAgreementService to find the agreement
        monitor.debug("[ClearingHouseEventMapper]: Looking up contract agreement: " + contractId);
        ContractAgreement agreement = contractAgreementService.findById(contractId);

        if (agreement == null) {
            monitor.warning("[ClearingHouseEventMapper]: No contract agreement found for ID: " + contractId);
            return null;
        }

        monitor.debug("[ClearingHouseEventMapper]: Found agreement for transfer. Provider BPN: " +
                agreement.getProviderId() + ", Consumer BPN: " + agreement.getConsumerId());

        return TransactionEvent.Builder.newInstance()
                .eventId(generateEventId())
                .eventType("DATA_TRANSFER_COMPLETED")
                .timestamp(Instant.now().toString())
                .transactionId(event.getTransferProcessId())
                .contractId(agreement.getId())
                .providerBpn(agreement.getProviderId())
                .consumerBpn(agreement.getConsumerId())
                .assetId(agreement.getAssetId())
                .build();
    }

    private String generateEventId() {
        return "evt-" + UUID.randomUUID().toString();
    }

    private String calculateAgreementHash(ContractAgreement agreement) {
        // simplee hash calculation for demo, maybe prod can use stronger hashing
        String dataToHash = agreement.getId()
                + agreement.getProviderId()
                + agreement.getConsumerId()
                + agreement.getAssetId()
                + agreement.getContractSigningDate();
        return Integer.toHexString(dataToHash.hashCode());
    }

    // getting the transfer process and using it to extract contract ID
    private String extractContractIdFromTransfer(TransferProcessCompleted event) {
        try {
            var transferProcess = transferProcessService.findById(event.getTransferProcessId());

            if (transferProcess == null) {
                monitor.warning("[ClearingHouseEventMapper]: Transfer process not found: " + event.getTransferProcessId());
                return null;
            }
            var contractId = transferProcess.getContractId();

            if (contractId == null || contractId.isBlank()) {
                monitor.warning("[ClearingHouseEventMapper]: No contract ID found for transfer process: " +
                        event.getTransferProcessId());
                // try extracting contractid from private properties as fallback
                contractId = extractContractIdFromProperties(transferProcess);
            }

            monitor.debug("[ClearingHouseEventMapper]: Extracted contract ID: " + contractId +
                    " for transfer process: " + event.getTransferProcessId());
            return contractId;

        } catch (Exception e) {
            monitor.severe("[ClearingHouseEventMapper]: Error extracting contract ID", e);
            return null;
        }
    }

    private String extractContractIdFromProperties(TransferProcess transferProcess) {
        // checking various property locations for contract ID as fallback
        if (transferProcess.getContentDataAddress() != null) {
            var contentAddress = transferProcess.getContentDataAddress();
            var contractId = contentAddress.getStringProperty("contractId");
            if (contractId != null) return contractId;
        }

        if (transferProcess.getDataDestination() != null) {
            var dataDestination = transferProcess.getDataDestination();
            var contractId = dataDestination.getStringProperty("contractId");
            if (contractId != null) return contractId;
        }
        // checking private properties as well
        // Check private properties
        var privateProperties = transferProcess.getPrivateProperties();
        var contractId = (String) privateProperties.get("contractId");
        if (contractId != null) return contractId;

        return null;
    }

}

