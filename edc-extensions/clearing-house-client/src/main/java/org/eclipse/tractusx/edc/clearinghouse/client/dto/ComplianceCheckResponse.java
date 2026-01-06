/********************************************************************************
 * Copyright (c) 2025 Contributors
 *
 * SPDX-License-Identifier: Apache-2.0
 ********************************************************************************/

package org.eclipse.tractusx.edc.clearinghouse.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for Compliance Check API
 * Contains a Verifiable Credential issued after compliance check
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ComplianceCheckResponse {
    @JsonProperty("verifiableCredential")
    private Object verifiableCredential;

    @JsonProperty("compliant")
    private Boolean compliant;

    @JsonProperty("level")
    private String level;

    public Object getVerifiableCredential() {
        return verifiableCredential;
    }

    public void setVerifiableCredential(Object verifiableCredential) {
        this.verifiableCredential = verifiableCredential;
    }

    public Boolean getCompliant() {
        return compliant;
    }

    public void setCompliant(Boolean compliant) {
        this.compliant = compliant;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }
}

