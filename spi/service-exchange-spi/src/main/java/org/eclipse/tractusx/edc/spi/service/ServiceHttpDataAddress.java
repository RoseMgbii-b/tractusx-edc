package org.eclipse.tractusx.edc.spi.service;

import org.eclipse.edc.spi.types.domain.DataAddress;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

import static org.eclipse.tractusx.edc.spi.service.ServiceExchangeConstants.*;

public class ServiceHttpDataAddress {
    // we can add more later
    private static final Set<String> ALLOWED_METHODS = Set.of("POST", "GET");

    private final String baseUrl;
    private final String method;

    private ServiceHttpDataAddress(String baseUrl, String method) {
        this.baseUrl = baseUrl;
        this.method = method;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String method() {
        return method;
    }

    public static ServiceHttpDataAddress from(DataAddress address){
        if (address == null) {
            throw new IllegalArgumentException("address must not be null");
        }

        if (!SERVICE_DATAADDRESS_TYPE.equals(address.getType())) {
            throw new IllegalArgumentException("Expected DataAddress type '" + SERVICE_DATAADDRESS_TYPE + "' but was '" + address.getType() + "'");
        }

        var baseUrl = address.getStringProperty(BASE_URL);
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Missing required DataAddress property: " + BASE_URL);
        }

        validateAbsoluteUrl(baseUrl);

        // normalize trailing slash
        baseUrl = normalizeBaseUrl(baseUrl);

        var method = address.getStringProperty(METHOD, "POST");
        // null check for method
        if (method == null) {
            method = "POST";
        }
        method = method.toUpperCase(Locale.ROOT);

        // validate allowedd methods
        if (!ALLOWED_METHODS.contains(method)) {
            throw new IllegalArgumentException("Unsupported HTTP method: " + method + ". Allowed: " + ALLOWED_METHODS);
        }

        return new ServiceHttpDataAddress(baseUrl, method);
    }

    private static String normalizeBaseUrl(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static void validateAbsoluteUrl(String url) {
        try {
            var uri = URI.create(url);
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new IllegalArgumentException("baseUrl must be an absolute URL, got: " + url);
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid baseUrl: " + url, e);
        }
    }
}
