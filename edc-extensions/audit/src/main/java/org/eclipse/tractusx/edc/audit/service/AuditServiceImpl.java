package org.eclipse.tractusx.edc.audit.service;

import org.eclipse.tractusx.edc.spi.audit.sink.AuditSink;
import org.eclipse.tractusx.edc.spi.audit.service.AuditService;
import org.eclipse.tractusx.edc.spi.audit.types.AuditEvent;

import org.eclipse.edc.spi.monitor.Monitor;
import java.util.List;

/**
 * The Audit Service Implementation
 * Receives AuditEvent, iterates over all the sinks
 */
public class AuditServiceImpl implements AuditService {
    private final List<AuditSink> sinks;
    private final Monitor monitor;

    /**
     * Constructor
     *
     * @param sinks the audit sinks
     */
    public AuditServiceImpl(List<AuditSink> sinks, Monitor monitor) {
        this.sinks = sinks;
        this.monitor = monitor;
    }

    /**
     * Publishes audit event to all configured sinks.
     *
     * @param event AuditEvent to be published
     */
    @Override
    public void publish(AuditEvent event) {
        sinks.forEach(sink -> {
            try {
                sink.write(event);
            } catch (Exception e) {
                monitor.warning("[AuditServiceImpl] Failed to write audit event to sink: "
                        + sink.getClass().getSimpleName()
                        + " due to: " + e.getMessage());
            }
        });
    }

}

