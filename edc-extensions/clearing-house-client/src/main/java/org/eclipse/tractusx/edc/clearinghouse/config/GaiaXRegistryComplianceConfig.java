/********************************************************************************
 * Copyright (c) 2025 Contributors
 *
 * SPDX-License-Identifier: Apache-2.0
 ********************************************************************************/

package org.eclipse.tractusx.edc.clearinghouse.config;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

/**
 * Configuration for Gaia-X Registry and Compliance services
 */
@JsonDeserialize(builder = GaiaXRegistryComplianceConfig.Builder.class)
public class GaiaXRegistryComplianceConfig {
    private String registryBaseUrl;
    private String complianceBaseUrl;
    private int connectTimeoutSeconds;
    private int readTimeoutSeconds;

    private GaiaXRegistryComplianceConfig() {}

    public String getRegistryBaseUrl() {
        return registryBaseUrl;
    }

    public String getComplianceBaseUrl() {
        return complianceBaseUrl;
    }

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public int getReadTimeoutSeconds() {
        return readTimeoutSeconds;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private final GaiaXRegistryComplianceConfig instance;

        private Builder() {
            instance = new GaiaXRegistryComplianceConfig();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder registryBaseUrl(String registryBaseUrl) {
            instance.registryBaseUrl = registryBaseUrl;
            return this;
        }

        public Builder complianceBaseUrl(String complianceBaseUrl) {
            instance.complianceBaseUrl = complianceBaseUrl;
            return this;
        }

        public Builder connectTimeoutSeconds(int connectTimeoutSeconds) {
            instance.connectTimeoutSeconds = connectTimeoutSeconds;
            return this;
        }

        public Builder readTimeoutSeconds(int readTimeoutSeconds) {
            instance.readTimeoutSeconds = readTimeoutSeconds;
            return this;
        }

        public GaiaXRegistryComplianceConfig build() {
            Objects.requireNonNull(instance.registryBaseUrl, "registryBaseUrl cannot be null");
            Objects.requireNonNull(instance.complianceBaseUrl, "complianceBaseUrl cannot be null");

            if (instance.connectTimeoutSeconds <= 0) {
                instance.connectTimeoutSeconds = 10;
            }
            if (instance.readTimeoutSeconds <= 0) {
                instance.readTimeoutSeconds = 30;
            }

            return instance;
        }
    }
}

