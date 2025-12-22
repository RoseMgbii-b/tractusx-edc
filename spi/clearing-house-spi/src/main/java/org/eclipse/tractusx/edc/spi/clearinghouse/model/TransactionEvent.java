package org.eclipse.tractusx.edc.spi.clearinghouse.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Map;
import java.util.Objects;

/**
 * Represents a transaction event in the clearing house system in the chn evidence format.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonDeserialize(builder = TransactionEvent.Builder.class)
public class TransactionEvent {
    private String eventId;
    private String eventType;
    private String timestamp;
    private String transactionId;
    private String contractId;
    private String assetId;
    private String providerDid;
    private String consumerDid;
    private String providerBpn;
    private String consumerBpn;
    private Map<String, Object> additionalProperties;
    private String hash;
    private String signature;

    // Private constructor for Builder
    private TransactionEvent() {}

    // Getters
    @JsonProperty("eventId")
    public String getEventId() { return eventId; }

    @JsonProperty("eventType")
    public String getEventType() { return eventType; }

    @JsonProperty("timestamp")
    public String getTimestamp() { return timestamp; }

    @JsonProperty("transactionId")
    public String getTransactionId() { return transactionId; }

    @JsonProperty("contractId")
    public String getContractId() { return contractId; }

    @JsonProperty("assetId")
    public String getAssetId() { return assetId; }

    @JsonProperty("providerDid")
    public String getProviderDid() { return providerDid; }

    @JsonProperty("consumerDid")
    public String getConsumerDid() { return consumerDid; }

    @JsonProperty("providerBpn")
    public String getProviderBpn() { return providerBpn; }

    @JsonProperty("consumerBpn")
    public String getConsumerBpn() { return consumerBpn; }

    @JsonProperty("additionalProperties")
    public Map<String, Object> getAdditionalProperties() { return additionalProperties; }

    @JsonProperty("hash")
    public String getHash() { return hash; }

    @JsonProperty("signature")
    public String getSignature() { return signature; }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private final TransactionEvent instance;

        private Builder() {
            instance = new TransactionEvent();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder eventId(String eventId) {
            instance.eventId = eventId;
            return this;
        }

        public Builder eventType(String eventType) {
            instance.eventType = eventType;
            return this;
        }

        public Builder timestamp(String timestamp) {
            instance.timestamp = timestamp;
            return this;
        }

        public Builder transactionId(String transactionId) {
            instance.transactionId = transactionId;
            return this;
        }

        public Builder contractId(String contractId) {
            instance.contractId = contractId;
            return this;
        }

        public Builder assetId(String assetId) {
            instance.assetId = assetId;
            return this;
        }

        public Builder providerDid(String providerDid) {
            instance.providerDid = providerDid;
            return this;
        }

        public Builder consumerDid(String consumerDid) {
            instance.consumerDid = consumerDid;
            return this;
        }

        public Builder providerBpn(String providerBpn) {
            instance.providerBpn = providerBpn;
            return this;
        }

        public Builder consumerBpn(String consumerBpn) {
            instance.consumerBpn = consumerBpn;
            return this;
        }

        public Builder additionalProperties(Map<String, Object> additionalProperties) {
            instance.additionalProperties = additionalProperties;
            return this;
        }

        public Builder hash(String hash) {
            instance.hash = hash;
            return this;
        }

        public Builder signature(String signature) {
            instance.signature = signature;
            return this;
        }

        public TransactionEvent build() {
            Objects.requireNonNull(instance.eventType, "eventType cannot be null");
            Objects.requireNonNull(instance.timestamp, "timestamp cannot be null");
            Objects.requireNonNull(instance.transactionId, "transactionId cannot be null");
            return instance;
        }
    }
}