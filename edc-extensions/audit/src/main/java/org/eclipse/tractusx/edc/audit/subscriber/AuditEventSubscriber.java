package org.eclipse.tractusx.edc.audit.subscriber;


import org.eclipse.edc.spi.event.Event;
import org.eclipse.edc.spi.event.EventEnvelope;
import org.eclipse.edc.spi.event.EventSubscriber;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.tractusx.edc.audit.mapping.AuditEventMapperRegistry;
import org.eclipse.tractusx.edc.spi.audit.service.AuditService;

/**
 * The Audit Event Subscriber
 * Subscribes to published events (comes in the event envelope)
 */
public class AuditEventSubscriber implements EventSubscriber {

    private final AuditService auditService;
    private final Monitor monitor;
    private final AuditEventMapperRegistry mapperRegistry;

    public AuditEventSubscriber(AuditService auditService, Monitor monitor, AuditEventMapperRegistry mapperRegistry) {
        this.auditService = auditService;
        this.monitor = monitor;
        this.mapperRegistry = mapperRegistry;
    }

    @Override
    public <E extends Event> void on(EventEnvelope<E> eventEnvelope) {
        var payload = eventEnvelope.getPayload();
        try {
            mapperRegistry.map(payload)
                    .ifPresent(auditService::publish);
        } catch (Exception e) {
            monitor.warning("[AuditEventSubscriber] Error mapping event: "
                    + payload.getClass().getSimpleName(), e);
        }
    }
}

