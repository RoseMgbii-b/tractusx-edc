/********************************************************************************
 * Copyright (c) 2025 Contributors
 *
 * SPDX-License-Identifier: Apache-2.0
 ********************************************************************************/

package org.eclipse.tractusx.edc.clearinghouse.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request DTO for Trust Anchor chain verification from URI
 */
public class TrustAnchorChainUriRequestDto {
    @JsonProperty("uri")
    private String uri;

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }
}

