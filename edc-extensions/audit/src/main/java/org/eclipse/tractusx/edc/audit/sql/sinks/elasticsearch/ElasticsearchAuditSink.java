package org.eclipse.tractusx.edc.audit.sql.sinks.elasticsearch;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.tractusx.edc.spi.audit.sink.AuditSink;
import org.eclipse.tractusx.edc.spi.audit.types.AuditEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;


/**
 * The ElasticSearch Audit Sink
 * Implements Audit Sink, persists audit events to ElasticSearch
 */
public class ElasticsearchAuditSink implements AuditSink {

    private static final MediaType JSON =
            MediaType.parse("application/json; charset=utf-8");

    private final EdcHttpClient edcHttpClient;
    private final Monitor monitor;
    private final TypeManager typeManager;
    private final String indexUrl; // url including index and type

    public ElasticsearchAuditSink(EdcHttpClient edcHttpClient, Monitor monitor, String indexUrl, TypeManager typeManager) {
        this.edcHttpClient = edcHttpClient;
        this.monitor = monitor;
        this.indexUrl = indexUrl;
        this.typeManager = typeManager;
    }


    @Override
    public void write(AuditEvent event) {
        try {
            var json = createDocument(event);
            var body = RequestBody.create(json, JSON);

            var request = new Request.Builder()
                    .url(indexUrl)
                    .post(body)
                    .build();

            // Non-blocking async call using EDC client
            CompletableFuture<Response> future =
                    edcHttpClient.executeAsync(request, List.of());

            future.whenComplete((response, error) -> {
                if (error != null) {
                    monitor.warning("[ElasticsearchAuditSink] Failed async write for event "
                            + event.getId() + ": " + error.getMessage());
                    return;
                }
                try (response) {
                    if (!response.isSuccessful()) {
                        monitor.warning("[ElasticsearchAuditSink] Elasticsearch rejected event "
                                + event.getId() + ": HTTP " + response.code());
                    }
                    else {
                            monitor.debug("[ElasticsearchAuditSink] Successfully wrote event "
                                    + event.getId() + " to Elasticsearch.");
                    }
                }
            });
        } catch (Exception e) {
            monitor.severe("[ElasticsearchAuditSink] Unexpected error scheduling write for event: "
                    + event.getId(), e);
        }
    }

    private String createDocument(AuditEvent event) {
        try {
            Map<String, Object> payloadDoc = new HashMap<>();

            payloadDoc.put("id", event.getId());
            payloadDoc.put("timestamp", event.getTimestamp().toString());
            payloadDoc.put("category", event.getCategory() != null ? event.getCategory().name() : "UNKNOWN_CATEGORY");
            payloadDoc.put("eventName", event.getEventName() != null ? event.getEventName().name() : "UNKNOWN_EVENT_NAME");
            payloadDoc.put("description", event.getDescription());
            payloadDoc.put("actorId", event.getActorId());
            payloadDoc.put("subjectId", event.getSubjectId());
            payloadDoc.put("outcome", event.getOutcome().name());
            // Composite key for easier searching
            payloadDoc.put( "key", event.getCategory().name() + ":" + event.getEventName().name());
            // Optional debugging metadata (personal opinion)
            payloadDoc.put("instanceId", System.getenv().getOrDefault("HOSTNAME", "unknown"));

            var mapper = typeManager.getMapper();
            return mapper.writeValueAsString(payloadDoc);
        } catch (Exception e) {
            monitor.warning("[ElasticsearchAuditSink]: Failed to serialize AuditEvent to JSON: "
                    + event.getId(), e);
            return "{}";
        }
    }
}
