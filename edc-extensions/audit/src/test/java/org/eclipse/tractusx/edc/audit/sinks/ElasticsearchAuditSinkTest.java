package org.eclipse.tractusx.edc.audit.sinks;

import okhttp3.*;
import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.json.JacksonTypeManager;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.tractusx.edc.spi.audit.types.AuditEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.eclipse.tractusx.edc.audit.sql.sinks.elasticsearch.ElasticsearchAuditSink;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

public class ElasticsearchAuditSinkTest {
    private final EdcHttpClient mockedHttpClient = mock();
    private final TypeManager typeManager = new JacksonTypeManager();
    private final Monitor mockedMonitor = mock();
    private final String indexUrl = "http://localhost:9200/audit-events/_doc";
    private ElasticsearchAuditSink sink;

    @BeforeEach
    void setup() {
        sink = new ElasticsearchAuditSink(mockedHttpClient, mockedMonitor, indexUrl, typeManager);
    }

    private Response buildResponse(int statusCode) {
        var mediaType = MediaType.get("application/json; charset=utf-8");
        var request = new Request.Builder().url(indexUrl).build();
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(statusCode)
                .message(statusCode == 200 ? "OK" : "FAIL")
                .body(ResponseBody.create("{}", mediaType))
                .build();
    }

    @Test
    void write_schedules_async_request_and_handles_successful_response() {
        var mockedEvent = mock(AuditEvent.class);
        when(mockedEvent.getId()).thenReturn("evt1");
        when(mockedEvent.getTimestamp()).thenReturn(Instant.now());
        when(mockedEvent.getCategory()).thenReturn(null);
        when(mockedEvent.getEventName()).thenReturn(null);
        when(mockedEvent.getDescription()).thenReturn("d");
        when(mockedEvent.getActorId()).thenReturn("actor");
        when(mockedEvent.getSubjectId()).thenReturn("subject");
        when(mockedEvent.getOutcome()).thenReturn(org.eclipse.tractusx.edc.spi.audit.types.AuditOutcome.SUCCESS);

        var mockedResponse = buildResponse(200);
        when(mockedHttpClient.executeAsync(any(), anyList())).thenReturn(CompletableFuture.completedFuture(mockedResponse));

        sink.write(mockedEvent);

        // verify the client was asked to execute an async request
        verify(mockedHttpClient, timeout(500)).executeAsync(any(), anyList());
    }

    @Test
    void write_handles_rejected_response_and_calls_monitor_warning() {
        var mockedEvent = mock(AuditEvent.class);
        when(mockedEvent.getId()).thenReturn("evt2");
        when(mockedEvent.getTimestamp()).thenReturn(Instant.now());
        when(mockedEvent.getCategory()).thenReturn(null);
        when(mockedEvent.getEventName()).thenReturn(null);
        when(mockedEvent.getDescription()).thenReturn("d");
        when(mockedEvent.getActorId()).thenReturn("actor");
        when(mockedEvent.getSubjectId()).thenReturn("subject");
        when(mockedEvent.getOutcome()).thenReturn(org.eclipse.tractusx.edc.spi.audit.types.AuditOutcome.FAILURE);

        var mockedResponse = buildResponse(500);
        when(mockedHttpClient.executeAsync(any(), anyList())).thenReturn(CompletableFuture.completedFuture(mockedResponse));

        sink.write(mockedEvent);

        // ensure async schedule attempted
        verify(mockedHttpClient, timeout(500)).executeAsync(any(), anyList());
        // since response is 500, warning should be logged; allow some time for callback
        verify(mockedMonitor, timeout(500)).warning(contains
                        ("[ElasticsearchAuditSink]: Failed to serialize AuditEvent to JSON: evt2"), any());
    }



}

