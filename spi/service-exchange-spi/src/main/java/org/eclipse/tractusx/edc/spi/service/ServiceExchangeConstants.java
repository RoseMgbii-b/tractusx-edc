package org.eclipse.tractusx.edc.spi.service;

/**
 *
 */
public interface ServiceExchangeConstants {

    // Asset marker (edc:resourceType = service)
    String RESOURCE_TYPE_KEY = "edc:resourceType";
    String RESOURCE_TYPE_SERVICE = "service";

    // DCAT-like service metadata (catalog-friendly)
    String DCAT_ENDPOINT_URL = "dcat:endpointURL";
    String DCAT_ENDPOINT_DESCRIPTION = "dcat:endpointDescription";

    // DataAddress contract for service invocation (dataplane-friendly)
    // (type = ServiceHttp)
    String SERVICE_DATAADDRESS_TYPE = "ServiceHttp";

    String BASE_URL = "baseUrl";
    String METHOD = "method";

    // Optional fields (for later maybe)
    String PATH = "path";
    String TIMEOUT = "timeout";
}

