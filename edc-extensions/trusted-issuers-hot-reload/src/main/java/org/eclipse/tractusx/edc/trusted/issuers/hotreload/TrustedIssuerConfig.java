package org.eclipse.tractusx.edc.trusted.issuers.hotreload;

import java.util.Set;

public class TrustedIssuerConfig {

    final String id;
    final Set<String> supportedTypes;

    TrustedIssuerConfig(String id, Set<String> supportedTypes) {
        this.id = id;
        this.supportedTypes = supportedTypes != null ? supportedTypes : Set.of("*");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrustedIssuerConfig that = (TrustedIssuerConfig) o;
        return id.equals(that.id) && supportedTypes.equals(that.supportedTypes);
    }

    @Override
    public int hashCode() {
        return id.hashCode() * 31 + supportedTypes.hashCode();
    }
}
