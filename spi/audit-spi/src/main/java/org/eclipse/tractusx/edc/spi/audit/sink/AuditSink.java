package org.eclipse.tractusx.edc.spi.audit.sink;

import org.eclipse.tractusx.edc.spi.audit.types.AuditEvent;

public interface AuditSink {
    void write(AuditEvent event);
}
