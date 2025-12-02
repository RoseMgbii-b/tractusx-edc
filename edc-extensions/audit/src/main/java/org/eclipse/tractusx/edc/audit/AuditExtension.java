package org.eclipse.tractusx.edc.audit;

import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.spi.event.Event;
import org.eclipse.edc.spi.event.EventRouter;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.eclipse.tractusx.edc.audit.mapping.AuditEventMapperRegistry;
import org.eclipse.tractusx.edc.audit.service.AuditServiceImpl;
import org.eclipse.tractusx.edc.spi.audit.sink.AuditSink;
import org.eclipse.tractusx.edc.spi.audit.service.AuditService;
import org.eclipse.tractusx.edc.audit.sql.statement.PostgresAuditEventStatements;
import org.eclipse.tractusx.edc.audit.sql.sinks.elasticsearch.ElasticsearchAuditSink;
import org.eclipse.tractusx.edc.audit.sql.sinks.postgres.PostgresqlAuditSink;
import org.eclipse.tractusx.edc.audit.sql.sinks.redis.RedisAuditSink;
import org.eclipse.tractusx.edc.audit.subscriber.AuditEventSubscriber;

import java.util.ArrayList;
import java.util.List;

import static org.eclipse.tractusx.edc.audit.AuditExtension.NAME;


/**
 * The Audit Extension
 * Initializes the Audit Service with configured sinks
 */
@Provides(AuditService.class)
@Extension(value = NAME)
public class AuditExtension implements ServiceExtension {
    public static final String NAME = "Audit Extension";

    private static final String DATASOURCE_SETTING_KEY = "edc.sql.store.audit.datasource";
    private static final String REDIS_MAX_ENTRIES_SETTING_KEY = "edc.audit.redis.maxEntries";
    private static final String REDIS_ENABLED_SETTING_KEY = "edc.audit.redis.enabled";
    private static final String REDIS_HOST_SETTING_KEY = "edc.audit.redis.host";
    private static final String REDIS_PORT_SETTING_KEY = "edc.audit.redis.port";
    private static final String ELASTICSEARCH_INDEX_URL_SETTING_KEY = "edc.audit.elasticsearch.index.url";

    @Inject
    private DataSourceRegistry dataSourceRegistry;

    @Inject
    private TransactionContext transactionContext;

    @Inject
    private EdcHttpClient httpClient;

    @Inject
    private EventRouter eventRouter;

    /**
     * Initializes the Audit Extension
     *
     * @param context   the service extension context
     */
    @Override
    public void initialize(ServiceExtensionContext context) {
        Monitor monitor = context.getMonitor();
        TypeManager typeManager = context.getService(TypeManager.class);
        var config = context.getConfig();
        List<AuditSink> sinks = new ArrayList<>(3);

        // Add Elasticsearch Audit Sink
        var esIndexUrl = config.getString(ELASTICSEARCH_INDEX_URL_SETTING_KEY, null);
        addElasticsearchSink(esIndexUrl, sinks, monitor, typeManager);

        // Add PostgreSQL Audit Sink
        var sqlStatements = new PostgresAuditEventStatements();
        var auditDataSource = config.getString(DATASOURCE_SETTING_KEY, DataSourceRegistry.DEFAULT_DATASOURCE);
        addPostgresSink(auditDataSource, sqlStatements, sinks, monitor);

        // Add Redis Audit Sink
        var redisMaxEntries = config.getInteger(REDIS_MAX_ENTRIES_SETTING_KEY, 1000);
        var redisEnabled = config.getBoolean(REDIS_ENABLED_SETTING_KEY, false);
        var redisHost = config.getString(REDIS_HOST_SETTING_KEY, "localhost");
        var redisPort = config.getInteger(REDIS_PORT_SETTING_KEY, 6379);
        addRedisSink(redisEnabled, redisHost, redisPort, sinks, redisMaxEntries, monitor, typeManager);

        // Audit Service
        var auditService = new AuditServiceImpl(sinks, monitor);
        context.registerService(AuditService.class, auditService);

        // Mapper registry
        var mapperRegistry = new AuditEventMapperRegistry(monitor);

        // Register and subscribe to events
        eventRouter.registerSync(Event.class, new AuditEventSubscriber(auditService, monitor, mapperRegistry));
        monitor.info("[AuditExtension] Initialized with " + sinks.size() + " sinks and audit event subscriber registered");
    }

    /**
     * Adds a Redis sink to the list of sinks
     * @param redisEnabled      to indicate redis is enabled
     * @param redisHost         the redis host
     * @param redisPort         the redis port
     * @param sinks             the list of audit sinks
     * @param redisMaxEntries   the maximum allowed numbers of entries in the cache
     * @param monitor           the monitor for logging
     * @param typeManager       the type manager for serialization
     */
    void addRedisSink(Boolean redisEnabled, String redisHost, int redisPort, List<AuditSink> sinks, int redisMaxEntries, Monitor monitor, TypeManager typeManager){
        if (!redisEnabled) {
            monitor.warning("[AuditExtension] Redis sink not enabled.");
        } else {
            try {
                var redisSink = new RedisAuditSink(
                        redisHost,
                        redisPort,
                        monitor,
                        redisMaxEntries,
                        typeManager
                );
                sinks.add(redisSink);
                monitor.info("[AuditExtension] Redis sink added: " + redisHost + ":" + redisPort);
            } catch (Exception e) {
                monitor.warning("[AuditExtension] Problem adding Redis sink: " + e.getMessage());
            }
        }
    }

    /**
     * Adds a PostgreSQL Audit Sink to the list of sinks.
     *
     * @param statements the PostgreSQL audit event statements
     * @param sinks      the list of audit sinks
     * @param monitor    the monitor for logging
     */
    public void addPostgresSink(String auditDataSource, PostgresAuditEventStatements statements, List<AuditSink> sinks, Monitor monitor) {
        if (auditDataSource==null || auditDataSource.isBlank()){
            monitor.warning("[AuditExtension] PostgreSQL data source not configured");
        } else {
            try {
                var postgresSink = new PostgresqlAuditSink(
                        dataSourceRegistry,
                        auditDataSource,
                        transactionContext,
                        statements,
                        monitor
                );
                sinks.add(postgresSink);
                monitor.info("[AuditExtension] PostgreSQL Audit Sink added.");
            } catch (Exception e) {
                monitor.warning("[AuditExtension] Problem adding PostgreSQL sink: " + e.getMessage());
            }
        }
    }

    /**
     * Adds an Elasticsearch Audit Sink to the list of sinks if the index URL is provided.
     *
     * @param esIndexUrl the Elasticsearch index URL
     * @param sinks      the list of audit sinks
     * @param monitor    the monitor for logging
     * @param typeManager the type manager for serialization
     */
    public void addElasticsearchSink(String esIndexUrl, List<AuditSink> sinks, Monitor monitor, TypeManager typeManager) {
        if (esIndexUrl == null || esIndexUrl.isBlank()) {
            monitor.warning("[AuditExtension] Elasticsearch index url not configured.");
        } else {
            try {
                sinks.add(new ElasticsearchAuditSink(
                        httpClient,
                        monitor,
                        esIndexUrl,
                        typeManager
                ));
                monitor.info("[AuditExtension] Elasticsearch sink added: " + esIndexUrl);
            } catch (Exception e) {
                monitor.warning("[AuditExtension] Problem adding Elasticsearch sink: " + e.getMessage());
            }
        }
    }
}


