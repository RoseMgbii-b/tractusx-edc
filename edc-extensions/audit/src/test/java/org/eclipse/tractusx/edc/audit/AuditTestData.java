package org.eclipse.tractusx.edc.audit;

import org.eclipse.edc.spi.event.Event;

/**
 * Audit Test Data
 * Provides test utilities to avoid depending on real EDC event classes
 */
public class AuditTestData {

    public abstract static class TestEvent extends Event {
        private final String name;

        protected TestEvent(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }
    }

    //  Synthetic events used for AuditEventSubscriber tests
    public static class AssetCreated extends TestEvent {
        private final String assetId;

        public AssetCreated(String id) {
            super("AssetCreated");
            this.assetId = id;
        }

        public String getAssetId() {
            return assetId;
        }
    }

    public static class ContractInitEvent extends TestEvent {
        private final String contractNegotiationId;
        private final String counterPartyId;

        public ContractInitEvent(String id, String cp) {
            super("ContractNegotiationInitiated");
            this.contractNegotiationId = id;
            this.counterPartyId = cp;
        }

        public String getContractNegotiationId() {
            return contractNegotiationId;
        }

        public String getCounterPartyId() {
            return counterPartyId;
        }
    }

    public static class TransferCompletedEvent extends TestEvent {
        private final String transferProcessId;

        public TransferCompletedEvent(String id) {
            super("TransferProcessCompleted");
            this.transferProcessId = id;
        }

        public String getTransferProcessId() {
            return transferProcessId;
        }
    }

    public static class PolicyDeletedEvent extends TestEvent {
        private final String policyDefinitionId;

        public PolicyDeletedEvent(String id) {
            super("PolicyDefinitionDeleted");
            this.policyDefinitionId = id;
        }

        public String getPolicyDefinitionId() {
            return policyDefinitionId;
        }
    }

    public static class UnknownEvent extends TestEvent {
        public UnknownEvent() {
            super("Unknown");
        }
    }

    // Create a dummy contract negotiation event
    public static class DummyContractNegotiationInitiated extends Event {
        private final String contractNegotiationId;
        private final String counterPartyId;

        public DummyContractNegotiationInitiated(String id, String cp) {
            this.contractNegotiationId = id;
            this.counterPartyId = cp;
        }

        public String getContractNegotiationId() {
            return contractNegotiationId;
        }

        public String getCounterPartyId() {
            return counterPartyId;
        }

        @Override
        public String name() {
            return "ContractNegotiationInitiated";
        }
    }


}
