package org.eclipse.tractusx.edc.elasticsearchmonitor;

import org.eclipse.edc.spi.monitor.Monitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.function.Supplier;
import static org.mockito.Mockito.*;


public class ElasticsearchMonitorTest {
    private Monitor fallback;
    private ElasticsearchLogSink sink;
    private ElasticsearchMonitor monitor;

    @BeforeEach
    void setup() {
        fallback = mock(Monitor.class);
        sink = mock(ElasticsearchLogSink.class);
        monitor = new ElasticsearchMonitor(fallback, sink);
    }

    // INFO LOGIC
    @Test
    void info_shouldForwardMessageAndUseLevelINFO() {
        monitor.info("message-1");

        verify(fallback).info(eq("message-1"));
        verify(sink).writeLog(eq("INFO"), eq("message-1"), isNull());
    }

    @Test
    void info_supplier_shouldEvaluateSupplierAndForward() {
        Supplier<String> supplier = () -> "dynamic-message";

        monitor.info(supplier);

        verify(fallback).info(eq("dynamic-message"));
        verify(sink).writeLog(eq("INFO"), eq("dynamic-message"), isNull());
    }

    @Test
    void info_supplierThrows_shouldLogFailureMessage() {
        Supplier<String> supplier = () -> { throw new RuntimeException("boom"); };

        monitor.info(supplier);

        verify(fallback).info(contains("LOG SUPPLIER FAILED"));
        verify(sink).writeLog(eq("INFO"), contains("LOG SUPPLIER FAILED"), any());
    }

    // WARNING LOGIC
    @Test
    void warning_nullMessage_shouldSanitizeToEmpty() {
        monitor.warning((String) null);

        verify(fallback).warning(eq((String) null));
        verify(sink).writeLog(eq("WARNING"), eq("<empty>"), any());
    }

    @Test
    void warning_blankMessage_shouldBeSanitized() {
        monitor.warning("   ");

        verify(fallback).warning(eq("   "));
        verify(sink).writeLog(eq("WARNING"), eq("<empty>"), any());
    }

    // ERROR / SEVERE LOGIC
    @Test
    void severe_shouldForwardAndUseLevelERROR() {
        monitor.severe("problem!");

        verify(fallback).severe(eq("problem!"));
        verify(sink).writeLog(eq("ERROR"), eq("problem!"), any());
    }

    @Test
    void severe_withThrowable_shouldForwardThrowable() {
        var ex = new IllegalStateException("failure");

        monitor.severe("oops", ex);

        verify(sink).writeLog(eq("ERROR"), eq("oops"), eq(ex));
    }

    // DEBUG LOGIC
    @Test
    void debug_shouldMapToDEBUGLevel() {
        monitor.debug("dbg");

        verify(fallback).debug(eq("dbg"));
        verify(sink).writeLog(eq("DEBUG"), eq("dbg"), any());
    }

    // FALLBACK LOGIC WHEN SINK FAILS
    @Test
    void whenSinkThrows_monitorShouldFallbackToWarning() {
        doThrow(new RuntimeException("sink failure"))
                .when(sink).writeLog(anyString(), anyString(), any());

        monitor.debug("test");

        verify(fallback).warning(contains("Sink write failed"));
    }

    // FIRST ERROR LOGIC
    @Test
    void firstError_shouldPickFirstThrowable() {
        var ex1 = new RuntimeException();
        var ex2 = new IllegalArgumentException();

        monitor.debug("msg", ex1, ex2);

        verify(sink).writeLog(eq("DEBUG"), eq("msg"), eq(ex1));
    }

    @Test
    void firstError_shouldReturnNullWhenNoErrors() {
        monitor.debug("msg");

        verify(sink).writeLog(eq("DEBUG"), eq("msg"), isNull());
    }

}
