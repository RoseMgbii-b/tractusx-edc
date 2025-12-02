package org.eclipse.tractusx.edc.audit.mapping;

import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationInitiated;
import org.eclipse.edc.spi.event.Event;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.tractusx.edc.spi.audit.types.AuditEventCategory;
import org.eclipse.tractusx.edc.spi.audit.types.AuditEventName;
import org.eclipse.tractusx.edc.spi.audit.types.AuditOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AuditEventMapperRegistryTest {

    private AuditEventMapperRegistry registry;
    private Monitor monitor;

    @BeforeEach
    void setup() {
        monitor = mock(Monitor.class);
        registry = new AuditEventMapperRegistry(monitor);
    }

    @Test
    void map_givenContractNegotiationInitiated_shouldMapCorrectly() {
        var monitor = mock(Monitor.class);
        var registry = new AuditEventMapperRegistry(monitor);

        var event = mock(ContractNegotiationInitiated.class);
        when(event.getContractNegotiationId()).thenReturn("cn-123");
        when(event.getCounterPartyId()).thenReturn("provider-A");

        var result = registry.map(event).orElseThrow();

        assertThat(result.getCategory()).isEqualTo(AuditEventCategory.CONTRACT_NEGOTIATION);
        assertThat(result.getEventName()).isEqualTo(AuditEventName.CONTRACT_NEGOTIATION_INITIATED);
        assertThat(result.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(result.getSubjectId()).isEqualTo("cn-123");
        assertThat(result.getActorId()).isEqualTo("provider-A");
    }

    @Test
    void map_givenUnknownEvent_shouldReturnEmpty() {
        var result = registry.map(new Event() {
            @Override
            public String name() { return "UnknownEvent"; }
        });

        assertThat(result).isEmpty();
    }

    @Test
    void map_givenContractEventWithMissingFields_shouldNotThrow() {
        var event = mock(ContractNegotiationInitiated.class);

        // Missing fields → return null
        when(event.getContractNegotiationId()).thenReturn(null);
        when(event.getCounterPartyId()).thenReturn(null);

        var result = registry.map(event).orElseThrow();

        assertThat(result.getSubjectId()).isNull();
        assertThat(result.getActorId()).isNull();
    }


}