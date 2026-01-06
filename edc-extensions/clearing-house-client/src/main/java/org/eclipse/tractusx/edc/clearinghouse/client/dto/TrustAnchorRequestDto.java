/********************************************************************************
 * Copyright (c) 2025 Contributors
 *
 * SPDX-License-Identifier: Apache-2.0
 ********************************************************************************/

package org.eclipse.tractusx.edc.clearinghouse.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request DTO for Trust Anchor search
 */
public class TrustAnchorRequestDto {
    @JsonProperty("certificate")
    private String certificate;

    @JsonProperty("fingerprint")
    private String fingerprint;

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

