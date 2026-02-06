package org.eclipse.tractusx.edc.spi.service;

import java.util.Map;

import static org.eclipse.tractusx.edc.spi.service.ServiceExchangeConstants.*;

public final class ServiceAsset {
    private ServiceAsset() { }

    public static boolean isService(Map<String, ?> assetProperties) {
        if (assetProperties == null) return false;
        var value = assetProperties.get(RESOURCE_TYPE_KEY);
        return RESOURCE_TYPE_SERVICE.equals(value == null ? null : String.valueOf(value));
    }
}
