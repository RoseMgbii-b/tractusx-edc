package org.eclipse.tractusx.edc.spi.clearinghouse.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

/**
 * Represents a log receipt for an event recorded in the clearing house.
 * It is basically like a signed proof from chn that an event has been logged on the blockchain.
 */

@JsonDeserialize(builder = LogReceipt.Builder.class)
public class LogReceipt {
    private String receiptId;
    private String eventId;
    private String timestamp;
    private String signature;
    private String merkleProof;
    private String blockchainTxHash;

    // Private constructor for Builder
    private LogReceipt() {}

    // Simple getters (no setters)
    @JsonProperty("receiptId")
    public String getReceiptId() { return receiptId; }

    @JsonProperty("eventId")
    public String getEventId() { return eventId; }

    @JsonProperty("timestamp")
    public String getTimestamp() { return timestamp; }

    @JsonProperty("signature")
    public String getSignature() { return signature; }

    @JsonProperty("merkleProof")
    public String getMerkleProof() { return merkleProof; }

    @JsonProperty("blockchainTxHash")
    public String getBlockchainTxHash() { return blockchainTxHash; }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private final LogReceipt logReceipt;

        private Builder() {
            logReceipt = new LogReceipt();
        }

        @JsonCreator
        public static Builder newInstance() {
            return new Builder();
        }

        public Builder receiptId(String receiptId) {
            logReceipt.receiptId = receiptId;
            return this;
        }

        public Builder eventId(String eventId) {
            logReceipt.eventId = eventId;
            return this;
        }

        public Builder timestamp(String timestamp) {
            logReceipt.timestamp = timestamp;
            return this;
        }

        public Builder signature(String signature) {
            logReceipt.signature = signature;
            return this;
        }

        public Builder merkleProof(String merkleProof) {
            logReceipt.merkleProof = merkleProof;
            return this;
        }

        public Builder blockchainTxHash(String blockchainTxHash) {
            logReceipt.blockchainTxHash = blockchainTxHash;
            return this;
        }

        public LogReceipt build() {
            Objects.requireNonNull(logReceipt.receiptId, "receiptId cannot be null");
            Objects.requireNonNull(logReceipt.eventId, "eventId cannot be null");
            Objects.requireNonNull(logReceipt.timestamp, "timestamp cannot be null");
            Objects.requireNonNull(logReceipt.signature, "signature cannot be null");
            return logReceipt;
        }
    }
}