/********************************************************************************
 * Copyright (c) 2025 Contributors
 *
 * SPDX-License-Identifier: Apache-2.0
 ********************************************************************************/

package org.eclipse.tractusx.edc.service;

import org.eclipse.edc.spi.result.Result;

import java.util.Map;

/**
 * Interface for verifying Verifiable Presentations (VP) and extracting claims.
 */
public interface VpVerifier {

    /**
     * Verifies a Verifiable Presentation token and extracts claims.
     *
     * @param vpToken The signed VP JWT token
     * @param scope The required scope (e.g., "service:telemetry")
     * @return Result containing claims map if verification succeeds, failure otherwise
     */
    Result<Map<String, Object>> verify(String vpToken, String scope);
}
