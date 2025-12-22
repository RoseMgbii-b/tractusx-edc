package org.eclipse.tractusx.edc.spi.clearinghouse.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Arrays;
import java.util.Objects;


/**
 * CHN Participant Status Model. CHN API response is mapped to this model.
 * CHN validation
 */

@JsonDeserialize(builder = ParticipantStatus.Builder.class)
public class ParticipantStatus {
    private String bpn;
    private String did;
    private boolean active;
    private String status;
    private String validationTimestamp;
    private String[] roles;
    private String complianceLevel;

    // Private constructor for Builder
    private ParticipantStatus() {}

    // Getterss
    @JsonProperty("bpn")
    public String getBpn() { return bpn; }

    @JsonProperty("did")
    public String getDid() { return did; }

    @JsonProperty("active")
    public boolean isActive() { return active; }

    @JsonProperty("status")
    public String getStatus() { return status; }

    @JsonProperty("validationTimestamp")
    public String getValidationTimestamp() { return validationTimestamp; }

    @JsonProperty("roles")
    public String[] getRoles() { return roles != null ? roles.clone() : null; }

    @JsonProperty("complianceLevel")
    public String getComplianceLevel() { return complianceLevel; }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private final ParticipantStatus instance;

        private Builder() {
            instance = new ParticipantStatus();
        }

        @JsonCreator
        public static Builder newInstance() {
            return new Builder();
        }

        public Builder bpn(String bpn) {
            instance.bpn = bpn;
            return this;
        }

        public Builder did(String did) {
            instance.did = did;
            return this;
        }

        public Builder active(boolean active) {
            instance.active = active;
            return this;
        }

        public Builder status(String status) {
            instance.status = status;
            return this;
        }

        public Builder validationTimestamp(String validationTimestamp) {
            instance.validationTimestamp = validationTimestamp;
            return this;
        }

        public Builder roles(String[] roles) {
            instance.roles = roles != null ? roles.clone() : null;
            return this;
        }

        public Builder complianceLevel(String complianceLevel) {
            instance.complianceLevel = complianceLevel;
            return this;
        }

        public ParticipantStatus build() {
            Objects.requireNonNull(instance.bpn, "bpn cannot be null");
            Objects.requireNonNull(instance.status, "status cannot be null");
            Objects.requireNonNull(instance.validationTimestamp, "validationTimestamp cannot be null");
            return instance;
        }
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        ParticipantStatus that = (ParticipantStatus) object;
        return active == that.active &&
                Objects.equals(bpn, that.bpn) &&
                Objects.equals(did, that.did) &&
                Objects.equals(status, that.status) &&
                Objects.equals(validationTimestamp, that.validationTimestamp) &&
                Objects.equals(complianceLevel, that.complianceLevel) &&
                Arrays.equals(roles, that.roles);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(bpn, did, active, status, validationTimestamp, complianceLevel);
        result = 31 * result + Arrays.hashCode(roles);
        return result;
    }
}
