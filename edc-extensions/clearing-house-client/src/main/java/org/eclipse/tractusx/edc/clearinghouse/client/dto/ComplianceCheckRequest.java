/********************************************************************************
 * Copyright (c) 2025 Contributors
 *
 * SPDX-License-Identifier: Apache-2.0
 ********************************************************************************/

package org.eclipse.tractusx.edc.clearinghouse.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request DTO for Compliance Check API
 * Contains a Verifiable Presentation to be checked
 */
public class ComplianceCheckRequest {
    @JsonProperty("verifiablePresentation")
    private Object verifiablePresentation;

    public Object getVerifiablePresentation() {
        return verifiablePresentation;
    }

    public void setVerifiablePresentation(Object verifiablePresentation) {
        this.verifiablePresentation = verifiablePresentation;
    }
}

