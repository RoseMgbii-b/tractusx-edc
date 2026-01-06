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
 * Response DTO for Certification Notary API
 * 
 * Actual API response structure (likely similar to trusted issuers):
 * {
 *   "registry": ["url1", "url2", ...]
 * }
 * or may be a map structure - will adjust based on actual API response
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CertificationNotaryResponse {
    @JsonProperty("registry")
    private List<String> registry;

    public List<String> getRegistry() {
        return registry;
    }

    public void setRegistry(List<String> registry) {
        this.registry = registry;
    }
}

