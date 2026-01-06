/********************************************************************************
 * Copyright (c) 2025 Contributors
 *
 * SPDX-License-Identifier: Apache-2.0
 ********************************************************************************/

package org.eclipse.tractusx.edc.clearinghouse.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response DTO for Trust Anchor API responses
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TrustAnchorResponse {
    @JsonProperty("trustAnchors")
    private List<TrustAnchor> trustAnchors;

    public List<TrustAnchor> getTrustAnchors() {
        return trustAnchors;
    }

    public void setTrustAnchors(List<TrustAnchor> trustAnchors) {
        this.trustAnchors = trustAnchors;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TrustAnchor {
        @JsonProperty("subjectName")
        private String subjectName;

        @JsonProperty("certificate")
        private String certificate;

        @JsonProperty("fingerprint")
        private String fingerprint;

        public String getSubjectName() {
            return subjectName;
        }

        public void setSubjectName(String subjectName) {
            this.subjectName = subjectName;
        }

        public String getCertificate() {
            return certificate;
        }

        public void setCertificate(String certificate) {
            this.certificate = certificate;
        }

        public String getFingerprint() {
            return fingerprint;
        }

        public void setFingerprint(String fingerprint) {
            this.fingerprint = fingerprint;
        }
    }
}

