package org.eclipse.tractusx.edc.elasticsearchmonitor;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;


public class ElasticSearchLogSinkTest {
    private static final String INDEX_URL = "http://localhost:9200/index/_doc";

    private EdcHttpClient httpClient;
    private Monitor monitor;
    private TypeManager typeManager;
    private ServiceExtensionContext context;

    @BeforeEach
    void setUp() {
        httpClient = mock(EdcHttpClient.class);
        monitor = mock(Monitor.class);
        typeManager = mock(TypeManager.class);
        // allow deep stubbing on context.getConfig().getInteger(...)
        context = mock(ServiceExtensionContext.class, RETURNS_DEEP_STUBS);
    }

    @Test
    void constructor_readsConfig_and_logsMaxStacktraceLength() {
        // Arrange: make config return a specific max value
        when(context.getConfig().getInteger(anyString(), anyInt())).thenReturn(123);

        when(typeManager.getMapper()).thenReturn(new ObjectMapper());

        // Act
        new ElasticsearchLogSink(httpClient, monitor, INDEX_URL, typeManager, context);

        // Assert: monitor.info called with configured value
        verify(monitor).info(contains("Max stacktrace length set to: 123"));
    }

    @Test
    void writeLog_schedulesAsync_httpClientExecuteInvoked_onSuccess() throws Exception {
        // Arrange
        when(context.getConfig().getInteger(anyString(), anyInt())).thenReturn(32_000);
        when(typeManager.getMapper()).thenReturn(new ObjectMapper());

        var sink = new ElasticsearchLogSink(httpClient, monitor, INDEX_URL, typeManager, context);

        var response = buildResponseForUrl(INDEX_URL, 200);
        when(httpClient.execute(any())).thenReturn(response);

        // Act: no exception passed
        sink.writeLog("INFO", "test message", null);

        // Assert: httpClient.execute should be invoked asynchronously (allow some time)
        verify(httpClient, timeout(1_000)).execute(any());
        // no warnings should be logged for successful send (async callbacks may or may not call debug)
        // but ensure no severe/warning call due to supplyAsync path
        verify(monitor, never()).warning(contains("Async log send failed"), any());
    }

    @Test
    void writeLog_whenHttpClientThrows_triggersMonitorWarning() throws Exception {
        // Arrange
        when(context.getConfig().getInteger(anyString(), anyInt())).thenReturn(32_000);
        when(typeManager.getMapper()).thenReturn(new ObjectMapper());

        var sink = new ElasticsearchLogSink(httpClient, monitor, INDEX_URL, typeManager, context);

        // make the synchronous call inside the async supplier throw
        when(httpClient.execute(any())).thenThrow(new RuntimeException("boom"));

        // Intercept monitor.warning to capture message and notify the test via latch
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> capturedMsg = new AtomicReference<>();

        // Monitor.warning has varargs for Throwables; match the signature using a varargs cast
        doAnswer(invocation -> {
            // first argument is the message
            capturedMsg.set((String) invocation.getArgument(0));
            latch.countDown();
            return null;
        }).when(monitor).warning(anyString(), (Throwable[]) any());

            // Act
            sink.writeLog("ERROR", "will fail async", null);

            // Assert: exceptionally handler should have run synchronously, so warning is invoked
            verify(monitor, timeout(500)).warning(startsWith("[ElasticsearchLogSink] Async log send failed"));
    }

    @Test
    void writeLog_truncatesStacktrace_beforeSerializing_whenConfigured() throws Exception {
        // Arrange: set very small max stacktrace length
        int maxLen = 20;
        when(context.getConfig().getInteger(anyString(), anyInt())).thenReturn(maxLen);

        // spy mapper to capture the doc passed into writeValueAsString
        ObjectMapper realMapper = new ObjectMapper();
        ObjectMapper spyMapper = spy(realMapper);
        // capture argument passed to writeValueAsString
        AtomicReference<Object> capturedDoc = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedDoc.set(invocation.getArgument(0));
            // return some JSON string (not used by test)
            return realMapper.writeValueAsString(invocation.getArgument(0));
        }).when(spyMapper).writeValueAsString(any());

        when(typeManager.getMapper()).thenReturn(spyMapper);

        var sink = new ElasticsearchLogSink(httpClient, monitor, INDEX_URL, typeManager, context);

        // prepare httpClient to return a response if invoked (not the focus of this test)
        when(httpClient.execute(any())).thenReturn(buildResponseForUrl(INDEX_URL, 200));

        // Create an exception with a long stacktrace by throwing and catching
        RuntimeException ex;
        try {
            throw new RuntimeException("this is a long message to create a long stacktrace");
        } catch (RuntimeException e) {
            ex = e;
        }

        // Act
        sink.writeLog("ERROR", "with-exception", ex);

        // Assert: mapper.writeValueAsString was called and the captured doc contains a truncated stacktrace
        // writeValueAsString is executed synchronously in writeLog, so no need to wait.
        Object docObj = capturedDoc.get();
        assertThat(docObj).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> doc = (Map<String, Object>) docObj;

        // exception and stacktrace should be present
        assertThat(doc).containsKey("exception");
        assertThat(doc).containsKey("stacktrace");
        String stacktrace = (String) doc.get("stacktrace");
        assertThat(stacktrace).isNotNull();

        // if original stacktrace exceeds maxLen, the saved stacktrace must be truncated and end with the truncation marker
        if (stacktrace.length() > maxLen) {
            assertThat(stacktrace).endsWith("... [TRUNCATED]");
            assertThat(stacktrace.length()).isLessThanOrEqualTo(maxLen + "... [TRUNCATED]".length());
        } else {
            // If stacktrace is shorter than limit, it should be the full stacktrace
            assertThat(stacktrace.length()).isLessThanOrEqualTo(maxLen);
        }
    }


    private Response buildResponseForUrl(String url, int statusCode) {
        var req = new Request.Builder().url(url).build();
        return new Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(statusCode)
                .message(statusCode == 200 ? "OK" : "ERR")
                .body(ResponseBody.create("{}", MediaType.parse("application/json; charset=utf-8")))
                .build();
    }

}
