package org.eclipse.tractusx.edc.elasticsearchmonitor;


import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;


/**
 * Elasticsearch Monitor Extension
 * Registers an Elasticsearch-backed Monitor that forwards logs to ES
 * while preserving all fallback console output.
 */
@Extension("Elasticsearch Logging Monitor Extension")
public class ElasticsearchMonitorExtension implements ServiceExtension {

    private static final String SETTING_ENABLED = "edc.monitor.elasticsearch.enabled";
    private static final String SETTING_INDEX_URL = "edc.monitor.elasticsearch.index.url";

    @Inject
    private Monitor fallbackMonitor;

    @Inject
    private EdcHttpClient httpClient;

    @Inject
    private TypeManager typeManager;


    @Setting(
            key = SETTING_ENABLED,
            defaultValue = "false",
            description = "Enable Elasticsearch logging monitor"
    )
    private boolean enabled;

    @Override
    public void initialize(ServiceExtensionContext context) {

        if (!enabled) {
            fallbackMonitor.debug("[ElasticsearchMonitorExtension] Elasticsearch monitor disabled via config ("
                    + SETTING_ENABLED + "=false)");
            return;
        }

        var cfg = context.getConfig();
        var indexUrl = cfg.getString(SETTING_INDEX_URL, null);

        if (indexUrl == null || indexUrl.isBlank()) {
            fallbackMonitor.warning("[ElasticsearchMonitorExtension] No index URL provided. "
                    + "Please set " + SETTING_INDEX_URL + ". Monitor NOT enabled.");
            return;
        }

        fallbackMonitor.info("[ElasticsearchMonitorExtension] Elasticsearch index URL: " + indexUrl);

        try {
            var sink = new ElasticsearchLogSink(
                    httpClient,
                    fallbackMonitor,
                    indexUrl,
                    typeManager,
                    context
            );

            // Add prefix to distinguish logs produced by this monitor
            var customMonitor = new ElasticsearchMonitor(fallbackMonitor.withPrefix("ES-LOG"), sink);

            // Register the new monitor — EDC will use this instead of default
            context.registerService(Monitor.class, customMonitor);

            fallbackMonitor.info("[ElasticsearchMonitorExtension] INSTALLED Elasticsearch logging monitor.");

        } catch (Exception e) {
            fallbackMonitor.warning("[ElasticsearchMonitorExtension] Failed to initialize Elasticsearch monitor: "
                    + e.getMessage(), e);
        }
    }

    // For testing
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}

