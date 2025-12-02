package org.eclipse.tractusx.edc.spi.audit.service;

import org.eclipse.tractusx.edc.spi.audit.types.AuditEvent;

/**
 * The Audit Service
 */
public interface AuditService {
    /**
     * Publishes the audit event to all configured sinks
     * @param event     AuditEvent to be published
     */
    void publish(AuditEvent event);
}
