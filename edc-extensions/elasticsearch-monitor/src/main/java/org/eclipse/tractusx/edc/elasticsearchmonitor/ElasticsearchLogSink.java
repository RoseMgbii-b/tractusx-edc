package org.eclipse.tractusx.edc.elasticsearchmonitor;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;

import java.time.Instant;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

/**
 * Asynchronous Elasticsearch Log Sink
 * Writes monitor log entries to Elasticsearch with configurable max stacktrace size.
 */
public class ElasticsearchLogSink {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static final String SET_MAX_TRACE_LENGTH = "edc.monitor.elasticsearch.maxStacktraceLength";
    // Default maximum stacktrace length: 32 KB
    private static final int DEFAULT_MAX_STACKTRACE_LENGTH = 32_000;


    private final EdcHttpClient httpClient;
    private final Monitor monitor;
    private final String indexUrl;
    private final TypeManager typeManager;
    private final int maxStacktraceLength;

    // Constructor
    public ElasticsearchLogSink(EdcHttpClient httpClient,
                                Monitor monitor,
                                String indexUrl,
                                TypeManager typeManager,
                                ServiceExtensionContext context) {

        this.httpClient = httpClient;
        this.monitor = monitor;
        this.indexUrl = indexUrl;
        this.typeManager = typeManager;

        // Load configurable max size (default 32KB)
        this.maxStacktraceLength = context.getConfig()
                .getInteger(SET_MAX_TRACE_LENGTH,
                        DEFAULT_MAX_STACKTRACE_LENGTH);

        monitor.info("[ElasticsearchLogSink] Max stacktrace length set to: " + maxStacktraceLength + " chars");
    }

    /**
     * Asynchronously sends a log document to Elasticsearch.
     * @param level         The Level
     * @param message       The Message
     * @param exception     Any Exception
     */
    public void writeLog(String level, String message, Throwable exception) {
        try {
            Map<String, Object> doc = new HashMap<>();
            doc.put("timestamp", Instant.now().toString());
            doc.put("level", level);
            doc.put("message", message);
            doc.put("thread", Thread.currentThread().getName());
            doc.put("instanceId", System.getenv().getOrDefault("HOSTNAME", "unknown"));
            doc.put("service", "edc-connector");

            if (exception != null) {
                doc.put("exception", exception.getClass().getName());
                doc.put("stacktrace", truncateStacktrace(stacktrace(exception)));
            }

            var mapper = typeManager.getMapper();
            var json = mapper.writeValueAsString(doc);
            var body = RequestBody.create(json, JSON);

            var request = new Request.Builder()
                    .url(indexUrl)
                    .post(body)
                    .build();

            // Run asynchronously
            CompletableFuture
                    .supplyAsync(() -> {
                        try (var resp = httpClient.execute(request)) {
                            return resp;
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    })
                    .exceptionally(error -> {
                        monitor.warning("[ElasticsearchLogSink] Async log send failed: " + error.getMessage());
                        return null;
                    });

        } catch (Exception e) {
            monitor.warning("[ElasticsearchLogSink] Failed to queue async log entry: " + e.getMessage());
        }
    }

    // Helper Methods

    private String stacktrace(Throwable t) {
        var sw = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }

    private String truncateStacktrace(String text) {
        if (text.length() <= maxStacktraceLength) {
            return text;
        }
        return text.substring(0, maxStacktraceLength) + "... [TRUNCATED]";
    }
}
