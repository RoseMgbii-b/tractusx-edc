package org.eclipse.tractusx.edc.clearinghouse.config;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

@JsonDeserialize(builder = ClearingHouseConfig.Builder.class)
public class ClearingHouseConfig {
    private String baseUrl;
    private String apiKey;
    private int connectTimeoutSeconds;
    private int readTimeoutSeconds;

    // Private constructor for Builder
    private ClearingHouseConfig() {}

    // Simple getters (no setters)
    public String getBaseUrl() { return baseUrl; }
    public String getApiKey() { return apiKey; }
    public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
    public int getReadTimeoutSeconds() { return readTimeoutSeconds; }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private final ClearingHouseConfig instance;

        private Builder() {
            instance = new ClearingHouseConfig();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder baseUrl(String baseUrl) {
            instance.baseUrl = baseUrl;
            return this;
        }

        public Builder apiKey(String apiKey) {
            instance.apiKey = apiKey;
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

        public ClearingHouseConfig build() {
            Objects.requireNonNull(instance.baseUrl, "baseUrl cannot be null");
            Objects.requireNonNull(instance.apiKey, "apiKey cannot be null");

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