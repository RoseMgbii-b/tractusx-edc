/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
 */

package org.eclipse.tractusx.edc.audit.subscriber;

import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationAccepted;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationAgreed;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationEvent;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationFinalized;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationInitiated;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationOffered;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationRequested;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationTerminated;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationVerified;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessCompleted;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessDeprovisioned;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessEvent;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessInitiated;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessProvisioned;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessRequested;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessStarted;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessSuspended;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessTerminated;
import org.eclipse.edc.spi.event.Event;
import org.eclipse.edc.spi.event.EventEnvelope;
import org.eclipse.edc.spi.event.EventSubscriber;
import org.eclipse.tractusx.edc.spi.audit.AuditRegistry;
import org.eclipse.tractusx.edc.spi.audit.model.AuditEntry;
import org.eclipse.tractusx.edc.spi.audit.model.AuditEventType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Event subscriber that converts EDC business events into audit entries.
 * Focuses on contract negotiation and transfer process events for traceability.
 */
public class AuditEventSubscriber implements EventSubscriber {

    private final AuditRegistry auditRegistry;

    public AuditEventSubscriber(AuditRegistry auditRegistry) {
        this.auditRegistry = auditRegistry;
    }

    @Override
    public <E extends Event> void on(EventEnvelope<E> eventEnvelope) {
        var event = eventEnvelope.getPayload();
        
        // Handle contract negotiation events
        if (event instanceof ContractNegotiationEvent) {
            handleContractNegotiationEvent(eventEnvelope.getId(), (ContractNegotiationEvent) event);
        } else if (event instanceof TransferProcessEvent) {
            // Handle transfer process events
            handleTransferProcessEvent(eventEnvelope.getId(), (TransferProcessEvent) event);
        }
    }

    private void handleContractNegotiationEvent(String eventId, ContractNegotiationEvent event) {
        var metadata = new HashMap<String, Object>();
        metadata.put("contractNegotiationId", event.getContractNegotiationId());
        metadata.put("eventId", eventId);

        String eventName;
        String description;
        String result;
        boolean critical = true; // Contract negotiations are critical

        if (event instanceof ContractNegotiationInitiated) {
            eventName = "ContractNegotiationInitiated";
            description = "Contract negotiation process initiated";
            result = "INITIATED";
        } else if (event instanceof ContractNegotiationRequested) {
            eventName = "ContractNegotiationRequested";
            description = "Contract negotiation requested";
            result = "REQUESTED";
        } else if (event instanceof ContractNegotiationVerified) {
            eventName = "ContractNegotiationVerified";
            description = "Contract negotiation verified";
            result = "VERIFIED";
        } else if (event instanceof ContractNegotiationAccepted) {
            eventName = "ContractNegotiationAccepted";
            description = "Contract negotiation accepted";
            result = "ACCEPTED";
        } else if (event instanceof ContractNegotiationOffered) {
            eventName = "ContractNegotiationOffered";
            description = "Contract negotiation offer made";
            result = "OFFERED";
        } else if (event instanceof ContractNegotiationAgreed) {
            eventName = "ContractNegotiationAgreed";
            description = "Contract negotiation agreed";
            result = "AGREED";
        } else if (event instanceof ContractNegotiationFinalized) {
            eventName = "ContractNegotiationFinalized";
            description = "Contract negotiation finalized";
            result = "FINALIZED";
        } else if (event instanceof ContractNegotiationTerminated) {
            eventName = "ContractNegotiationTerminated";
            description = "Contract negotiation terminated";
            result = "TERMINATED";
        } else {
            eventName = event.getClass().getSimpleName();
            description = "Contract negotiation event: " + eventName;
            result = "UNKNOWN";
        }

        recordAuditEntry(AuditEventType.CONTRACT_NEGOTIATION, eventName, description, result, metadata, critical);
    }

    private void handleTransferProcessEvent(String eventId, TransferProcessEvent event) {
        var metadata = new HashMap<String, Object>();
        metadata.put("transferProcessId", event.getTransferProcessId());
        metadata.put("eventId", eventId);

        String eventName;
        String description;
        String result;
        boolean critical = true; // Data transfers are critical

        if (event instanceof TransferProcessInitiated) {
            eventName = "TransferProcessInitiated";
            description = "Data transfer process initiated";
            result = "INITIATED";
        } else if (event instanceof TransferProcessRequested) {
            eventName = "TransferProcessRequested";
            description = "Data transfer requested";
            result = "REQUESTED";
        } else if (event instanceof TransferProcessProvisioned) {
            eventName = "TransferProcessProvisioned";
            description = "Data transfer resources provisioned";
            result = "PROVISIONED";
        } else if (event instanceof TransferProcessStarted) {
            eventName = "TransferProcessStarted";
            description = "Data transfer started";
            result = "STARTED";
        } else if (event instanceof TransferProcessCompleted) {
            eventName = "TransferProcessCompleted";
            description = "Data transfer completed successfully";
            result = "COMPLETED";
        } else if (event instanceof TransferProcessSuspended) {
            eventName = "TransferProcessSuspended";
            description = "Data transfer suspended";
            result = "SUSPENDED";
        } else if (event instanceof TransferProcessDeprovisioned) {
            eventName = "TransferProcessDeprovisioned";
            description = "Data transfer resources deprovisioned";
            result = "DEPROVISIONED";
        } else if (event instanceof TransferProcessTerminated) {
            var terminatedEvent = (TransferProcessTerminated) event;
            eventName = "TransferProcessTerminated";
            description = "Data transfer terminated: " + terminatedEvent.getReason();
            result = "TERMINATED";
            metadata.put("reason", terminatedEvent.getReason());
        } else {
            eventName = event.getClass().getSimpleName();
            description = "Transfer process event: " + eventName;
            result = "UNKNOWN";
        }

        recordAuditEntry(AuditEventType.TRANSACTION, eventName, description, result, metadata, critical);
    }

    private void recordAuditEntry(AuditEventType type, String eventName, String description, 
                                  String result, Map<String, Object> metadata, boolean critical) {
        var entry = AuditEntry.builder()
                .id(UUID.randomUUID().toString())
                .timestamp(System.currentTimeMillis())
                .eventType(type)
                .eventName(eventName)
                .description(description)
                .result(result)
                .metadata(metadata)
                .critical(critical)
                .build();

        auditRegistry.record(entry);
    }
}
