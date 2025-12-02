package org.eclipse.tractusx.edc.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.spi.event.Event;
import org.eclipse.edc.spi.event.EventRouter;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.Config;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.eclipse.tractusx.edc.audit.service.AuditServiceImpl;
import org.eclipse.tractusx.edc.audit.sql.sinks.postgres.PostgresqlAuditSink;
import org.eclipse.tractusx.edc.audit.sql.sinks.redis.RedisAuditSink;
import org.eclipse.tractusx.edc.audit.sql.statement.PostgresAuditEventStatements;
import org.eclipse.tractusx.edc.spi.audit.service.AuditService;
import org.eclipse.tractusx.edc.spi.audit.sink.AuditSink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.eclipse.tractusx.edc.audit.sql.sinks.elasticsearch.ElasticsearchAuditSink;
import java.lang.reflect.Field;
import java.util.ArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

public class AuditExtensionTest {
    private DataSourceRegistry dataSourceRegistry;
    private TransactionContext transactionContext;
    private EdcHttpClient httpClient;
    private EventRouter eventRouter;
    private Monitor monitor;
    private ServiceExtensionContext context;
    private TypeManager typeManager;
    private Config config;

    private AuditExtension extension;

    @BeforeEach
    void setup() {
        extension = new AuditExtension();

        dataSourceRegistry = mock(DataSourceRegistry.class);
        transactionContext = mock(TransactionContext.class);
        httpClient = mock(EdcHttpClient.class);
        eventRouter = mock(EventRouter.class);
        monitor = mock(Monitor.class);
        context = mock(ServiceExtensionContext.class);
        config = mock(Config.class);
        typeManager = mock(TypeManager.class);

        when(context.getMonitor()).thenReturn(monitor);
        when(context.getService(TypeManager.class)).thenReturn(typeManager);
        when(typeManager.getMapper()).thenReturn(new ObjectMapper());
        when(context.getConfig()).thenReturn(config);

        when(config.getString(anyString(), any())).thenReturn(null);

        // injecting @Inject fields manually
        inject("dataSourceRegistry", dataSourceRegistry);
        inject("transactionContext", transactionContext);
        inject("httpClient", httpClient);
        inject("eventRouter", eventRouter);
    }

    private void inject(String field, Object value) {
        try {
            Field f = AuditExtension.class.getDeclaredField(field);
            f.setAccessible(true);
            f.set(extension, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void initialize_shouldRegisterService() {

        // return null index URL
        when(config.getString(anyString(), isNull())).thenReturn(null);
        extension.initialize(context);
        verify(context).registerService(eq(AuditService.class), any());
    }

    @Test
    void initialize_shouldRegisterAuditServiceAndSubscriber() {

        // Force only Redis enabled to observe sink count
        when(config.getBoolean("edc.audit.redis.enabled", false)).thenReturn(false);
        when(config.getString("edc.audit.elasticsearch.index.url", null)).thenReturn(null);
        when(config.getString("edc.sql.store.audit.datasource", DataSourceRegistry.DEFAULT_DATASOURCE))
                .thenReturn("");

        extension.initialize(context);

        // Verify that subscriber is registeresd
        verify(eventRouter).registerSync(eq(Event.class), any());
        verify(context).registerService(eq(org.eclipse.tractusx.edc.spi.audit.service.AuditService.class),
                any(AuditServiceImpl.class));

        verify(monitor).info(contains("audit event subscriber registered"));
    }

    //Elasticsearch sink tests
    @Test
    void addElasticsearchSink_whenConfigured_shouldAddSink() {
        var sinks = new ArrayList<AuditSink>();
        var url = "http://localhost:9200/audit-events/_doc";

        extension.addElasticsearchSink(url, sinks, monitor, typeManager);

        assertThat(sinks).hasSize(1);
        assertThat(sinks.get(0)).isInstanceOf(ElasticsearchAuditSink.class);
        verify(monitor).info(contains("Elasticsearch sink added"));
    }

    @Test
    void addElasticsearchSink_whenMissingConfig_shouldNotAddSink() {
        var sinks = new ArrayList<AuditSink>();

        extension.addElasticsearchSink(null, sinks, monitor, typeManager);

        assertThat(sinks).isEmpty();
        verify(monitor).warning(contains("not configured"));
    }

    // Postgres sink tests
    @Test
    void addPostgresSink_whenDatasourceConfigured_shouldAddSink() {
        var sinks = new ArrayList<AuditSink>();

        extension.addPostgresSink(
                "audit-ds",
                new PostgresAuditEventStatements(),
                sinks,
                monitor
        );

        assertThat(sinks).hasSize(1);
        assertThat(sinks.get(0)).isInstanceOf(PostgresqlAuditSink.class);
        verify(monitor).info(contains("PostgreSQL Audit Sink added"));
    }

    @Test
    void addPostgresSink_whenDatasourceMissing_shouldNotAddSink() {
        var sinks = new ArrayList<AuditSink>();

        extension.addPostgresSink("", new PostgresAuditEventStatements(), sinks, monitor);

        assertThat(sinks).isEmpty();
        verify(monitor).warning(contains("not configured"));
    }

    // Redis Sink tests
    @Test
    void addRedisSink_whenEnabled_shouldAddSink() {
        var sinks = new ArrayList<AuditSink>();

        extension.addRedisSink(
                true,
                "localhost",
                6379,
                sinks,
                100,
                monitor,
                typeManager
        );

        assertThat(sinks).hasSize(1);
        assertThat(sinks.get(0)).isInstanceOf(RedisAuditSink.class);
        verify(monitor).info(contains("Redis sink added"));
    }

    @Test
    void addRedisSink_whenDisabled_shouldNotAddSink() {
        var sinks = new ArrayList<AuditSink>();

        extension.addRedisSink(
                false,
                "localhost",
                6379,
                sinks,
                100,
                monitor,
                typeManager
        );

        assertThat(sinks).isEmpty();
        verify(monitor).warning(contains("not enabled"));
    }

}
