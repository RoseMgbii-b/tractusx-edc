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
 * Response DTO for Trusted Issuers API
 * 
 * Actual API response structure:
 * {
 *   "registry": ["registry.lab.gaia-x.eu/v1", "registry.lab.gaia-x.eu/v2", ...]
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TrustedIssuersResponse {
    @JsonProperty("registry")
    private List<String> registry;

    public List<String> getRegistry() {
        return registry;
    }

    public void setRegistry(List<String> registry) {
        this.registry = registry;
    }
}

