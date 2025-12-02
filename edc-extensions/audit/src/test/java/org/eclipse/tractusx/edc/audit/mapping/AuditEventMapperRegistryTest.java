package org.eclipse.tractusx.edc.audit.mapping;

import org.eclipse.edc.spi.event.Event;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.tractusx.edc.audit.AuditTestData;
import org.eclipse.tractusx.edc.spi.audit.types.AuditEvent;
import org.eclipse.tractusx.edc.spi.audit.types.AuditEventCategory;
import org.eclipse.tractusx.edc.spi.audit.types.AuditEventName;
import org.eclipse.tractusx.edc.spi.audit.types.AuditOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;



class AuditEventMapperRegistryTest {
    private Monitor monitor;
    private AuditEventMapperRegistry registry;

    @BeforeEach
    void setup() {
        monitor = mock(Monitor.class);
        registry = new AuditEventMapperRegistry(monitor);
    }

    /**
     * Test that given an Event
     * A custom handler will be registered and event will be mapped to AuditEvent
     */
    @Test
    void map_givenRegisteredEvent_shouldBuildAuditEvent() {
        registry.register(BaseTestEvent.class, (event, b) -> {
            b.category(AuditEventCategory.POLICY)
                    .eventName(AuditEventName.POLICY_DEFINITION_CREATED)
                    .outcome(AuditOutcome.SUCCESS)
                    .subjectId(event.getCustomId())
                    .description("test-description");
        });

        var event = new BaseTestEvent("123");

        Optional<AuditEvent> result = registry.map(event);

        assertThat(result).isPresent();
        var audit = result.get();

        assertThat(audit.getCategory()).isEqualTo(AuditEventCategory.POLICY);
        assertThat(audit.getEventName()).isEqualTo(AuditEventName.POLICY_DEFINITION_CREATED);
        assertThat(audit.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(audit.getSubjectId()).isEqualTo("123");
        assertThat(audit.getDescription()).isEqualTo("test-description");
    }

    /**
     * TEST: Superclass handler applies to subclass event
     */
    @Test
    void map_givenSubclass_shouldUseSuperclassHandler() {
        registry.register(BaseTestEvent.class, (event, b) ->
                b.category(AuditEventCategory.ASSET)
                        .eventName(AuditEventName.ASSET_CREATED)
                        .outcome(AuditOutcome.SUCCESS)
                        .subjectId(event.getCustomId())
        );

        var event = new ChildTestEvent("child-456");

        Optional<AuditEvent> result = registry.map(event);

        assertThat(result).isPresent();
        var audit = result.get();

        assertThat(audit.getCategory()).isEqualTo(AuditEventCategory.ASSET);
        assertThat(audit.getEventName()).isEqualTo(AuditEventName.ASSET_CREATED);
        assertThat(audit.getSubjectId()).isEqualTo("child-456");
    }

    /**
     * Unknown event should return empty Optional
     */
    static class UnknownEvent extends Event {
        @Override
        public String name() { return "unknown"; }
    }

    @Test
    void map_givenUnknownEvent_shouldReturnEmpty() {
        var result = registry.map(new UnknownEvent());
        assertThat(result).isEmpty();

        verify(monitor).debug(contains("No mapping for event type"));
    }

    /**
     * Test default mapping for one real EDC event (ContractNegotiationInitiated)
     */
    @Test
    void map_givenContractNegotiationInitiated_shouldUseDefaultMapping() {
        var event = new AuditTestData.DummyContractNegotiationInitiated("cn-123", "provider-abc");

        // IMPORTANT: override registry with mapping for test event
        registry.register(AuditTestData.DummyContractNegotiationInitiated.class, (e, b) -> {
            b.category(AuditEventCategory.CONTRACT_NEGOTIATION)
                    .eventName(AuditEventName.CONTRACT_NEGOTIATION_INITIATED)
                    .outcome(AuditOutcome.SUCCESS)
                    .subjectId(e.getContractNegotiationId())
                    .actorId(e.getCounterPartyId())
                    .description("Contract negotiation initiated.");
        });

        var result = registry.map(event);

        assertThat(result).isPresent();
        var audit = result.get();

        assertThat(audit.getCategory()).isEqualTo(AuditEventCategory.CONTRACT_NEGOTIATION);
        assertThat(audit.getEventName()).isEqualTo(AuditEventName.CONTRACT_NEGOTIATION_INITIATED);
        assertThat(audit.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(audit.getSubjectId()).isEqualTo("cn-123");
        assertThat(audit.getActorId()).isEqualTo("provider-abc");
        assertThat(audit.getDescription()).isEqualTo("Contract negotiation initiated.");
    }



    // Dummy event types to test superclass handler lookup
    static class BaseTestEvent extends Event {
        private final String id;
        BaseTestEvent(String id) { this.id = id; }

        public String getCustomId() { return id; }

        @Override
        public String name() { return "base.test"; }
    }

    static class ChildTestEvent extends BaseTestEvent {
        ChildTestEvent(String id) { super(id); }
        @Override
        public String name() { return "child.test"; }
    }

}