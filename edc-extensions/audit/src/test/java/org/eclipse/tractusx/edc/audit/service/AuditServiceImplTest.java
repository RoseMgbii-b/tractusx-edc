package org.eclipse.tractusx.edc.audit.service;

import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.tractusx.edc.spi.audit.sink.AuditSink;
import org.eclipse.tractusx.edc.spi.audit.types.AuditEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.*;

public class AuditServiceImplTest {

    @Test
    void publish_invokes_all_sinks_and_handles_exceptions() {
        var sink1 = mock(AuditSink.class);
        var sink2 = mock(AuditSink.class);
        doThrow(new RuntimeException("boom")).when(sink2).write(any());

        var monitor = mock(Monitor.class);
        var auditService = new AuditServiceImpl(List.of(sink1, sink2), monitor);

        var mockedEvent = mock(AuditEvent.class);
        auditService.publish(mockedEvent);

        verify(sink1).write(mockedEvent);
        verify(sink2).write(mockedEvent);

        verify(monitor).warning(contains("[AuditServiceImpl] Failed to write audit event to sink: " + sink1.getClass().getSimpleName() + " due to: boom"));
    }
}
