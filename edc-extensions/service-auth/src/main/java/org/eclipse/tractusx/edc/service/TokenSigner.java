/********************************************************************************
 * Copyright (c) 2025 Contributors
 *
 * SPDX-License-Identifier: Apache-2.0
 ********************************************************************************/

package org.eclipse.tractusx.edc.service;

import org.eclipse.edc.spi.result.Result;

import java.util.Map;

public interface TokenSigner {

    /**
     * Signs a JWT access token with the given claims.
     *
     * @param claims The claims to include in the token
     * @return Signed JWT token string
     */
    String sign(Map<String, Object> claims);

    /**
     * Verifies and extracts claims from a JWT token.
     *
     * @param token The JWT token to verify
     * @return Result containing claims map if verification succeeds, failure otherwise
     */
    Result<Map<String, Object>> verify(String token);

    /**
     * Gets the token expiration time in seconds.
     *
     * @return Expiration time in seconds
     */
    int getExpirationSeconds();
}
