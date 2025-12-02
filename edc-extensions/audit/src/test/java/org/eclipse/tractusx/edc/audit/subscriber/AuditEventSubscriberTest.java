package org.eclipse.tractusx.edc.audit.subscriber;

import org.eclipse.edc.spi.event.Event;
import org.eclipse.edc.spi.event.EventEnvelope;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.tractusx.edc.audit.mapping.AuditEventMapperRegistry;
import org.eclipse.tractusx.edc.spi.audit.service.AuditService;

import org.eclipse.tractusx.edc.spi.audit.types.AuditEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import java.time.Instant;
import java.util.Optional;
import static org.mockito.Mockito.*;

public class AuditEventSubscriberTest {
    private AuditService auditService;
    private Monitor monitor;
    private AuditEventMapperRegistry mapperRegistry;
    private AuditEventSubscriber subscriber;

    @BeforeEach
    void setup() {
        auditService = mock(AuditService.class);
        monitor = mock(Monitor.class);
        mapperRegistry = mock(AuditEventMapperRegistry.class);

        subscriber = new AuditEventSubscriber(auditService, monitor, mapperRegistry);
    }

    @Test
    void on_whenMapperReturnsAuditEvent_shouldPublish() {
        // Arrange
        var event = mock(Event.class);
        var envelope = EventEnvelope.Builder.newInstance()
                .id("id1")
                .at(Instant.now().toEpochMilli())
                .payload(event)
                .build();

        var auditEvent = mock(AuditEvent.class);
        when(mapperRegistry.map(event)).thenReturn(Optional.of(auditEvent));

        // Act
        subscriber.on(envelope);

        // Assert
        verify(auditService).publish(auditEvent);
    }

    @Test
    void on_whenMapperReturnsEmpty_shouldNotPublish() {
        // Arrange
        var event = mock(Event.class);
        var envelope = EventEnvelope.Builder.newInstance()
                .id("id2")
                .at(Instant.now().toEpochMilli())
                .payload(event)
                .build();

        when(mapperRegistry.map(event)).thenReturn(Optional.empty());

        // Act
        subscriber.on(envelope);

        // Assert
        verifyNoInteractions(auditService);
    }

    @Test
    void on_whenMapperThrowsException_shouldLogErrorAndNotPublish() {
        // Arrange
        var event = mock(Event.class);
        var envelope = EventEnvelope.Builder.newInstance()
                .id("id3")
                .at(Instant.now().toEpochMilli())
                .payload(event)
                .build();

        when(mapperRegistry.map(event)).thenThrow(new RuntimeException("mapping failed"));

        // Act
        subscriber.on(envelope);

        // Assert
        verify(monitor).warning(startsWith("[AuditEventSubscriber] Error mapping event"), any());
        verifyNoInteractions(auditService);
    }

    @Test
    void on_shouldUseEnvelopePayload() {
        // Arrange
        var event = mock(Event.class);
        var envelope = EventEnvelope.Builder.newInstance()
                .id("id4")
                .payload(event)
                .at(Instant.now().toEpochMilli())
                .build();

        when(mapperRegistry.map(event)).thenReturn(Optional.empty());

        // Act
        subscriber.on(envelope);

        // Assert (payload forwarded correctly)
        verify(mapperRegistry).map(event);
    }
}
