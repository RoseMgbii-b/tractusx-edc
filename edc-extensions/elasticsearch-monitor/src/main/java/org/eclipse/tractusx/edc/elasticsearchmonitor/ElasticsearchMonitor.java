package org.eclipse.tractusx.edc.elasticsearchmonitor;

import org.eclipse.edc.spi.monitor.Monitor;
import java.util.function.Supplier;

/**
 * Elasticsearch Monitor
 * Forwards logs messages to an ElasticsearchLogSink
 * while also delegating to a fallback Monitor and keeping console output logs intact.
 */
public class ElasticsearchMonitor implements Monitor {

    private static final String LEVEL_INFO = "INFO";
    private static final String LEVEL_WARNING = "WARNING";
    private static final String LEVEL_ERROR = "ERROR";
    private static final String LEVEL_DEBUG = "DEBUG";

    private final Monitor fallback;
    private final ElasticsearchLogSink sink;

    public ElasticsearchMonitor(Monitor fallback, ElasticsearchLogSink sink) {
        this.fallback = fallback;
        this.sink = sink;
    }

    // INFO
    @Override
    public void info(String message, Throwable... errors) {
        fallback.info(message, errors);
        sink.writeLog(LEVEL_INFO, message, firstError(errors));
    }
    @Override
    public void info(Supplier<String> supplier, Throwable... errors) {
        var msg = supply(supplier);
        fallback.info(msg, errors);
        sink.writeLog(LEVEL_INFO, msg, firstError(errors));
    }

    // WARNING
    @Override
    public void warning(String message, Throwable... errors) {
        fallback.warning(message, errors);
        safeWrite(LEVEL_WARNING, sanitize(message), firstError(errors));
    }
    @Override
    public void warning(Supplier<String> supplier, Throwable... errors) {
        var msg = sanitize(supply(supplier));
        fallback.warning(msg, errors);
        safeWrite(LEVEL_WARNING, msg, firstError(errors));
    }

    // SEVERE
    @Override
    public void severe(String message, Throwable... errors) {
        fallback.severe(message, errors);
        safeWrite(LEVEL_ERROR, sanitize(message), firstError(errors));
    }
    @Override
    public void severe(Supplier<String> supplier, Throwable... errors) {
        var msg = sanitize(supply(supplier));
        fallback.severe(msg, errors);
        safeWrite(LEVEL_ERROR, msg, firstError(errors));
    }

    // Debug
    @Override
    public void debug(String message, Throwable... errors) {
        fallback.debug(message, errors);
        safeWrite(LEVEL_DEBUG, sanitize(message), firstError(errors));
    }
    @Override
    public void debug(Supplier<String> supplier, Throwable... errors) {
        var msg = sanitize(supply(supplier));
        fallback.debug(msg, errors);
        safeWrite(LEVEL_DEBUG, msg, firstError(errors));
    }


    // Utility helpers
    private void safeWrite(String level, String message, Throwable error) {
        try {
            sink.writeLog(level, message, error);
        } catch (Exception e) {
            fallback.warning("[ElasticsearchMonitor] Sink write failed: " + e.getMessage());
        }
    }

    private Throwable firstError(Throwable... errors) {
        return (errors != null && errors.length > 0) ? errors[0] : null;
    }

    private String supply(Supplier<String> supplier) {
        try {
            return supplier != null ? supplier.get() : null;
        } catch (Exception e) {
            return "[Elasticsearch Monitor] LOG SUPPLIER FAILED: " + e.getMessage();
        }
    }

    private String sanitize(String msg) {
        return (msg == null || msg.isBlank()) ? "<empty>" : msg;
    }
}

