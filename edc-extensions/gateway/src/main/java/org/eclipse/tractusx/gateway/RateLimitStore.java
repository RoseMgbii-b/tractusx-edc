/********************************************************************************
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 ********************************************************************************/

package org.eclipse.tractusx.gateway;

public interface RateLimitStore {

    /**
     * Checks if a request should be allowed based on rate limits.
     *
     * @param identifier Client identifier (IP address or user ID)
     * @param maxRequests Maximum requests allowed in the time window
     * @param windowSeconds Time window in seconds
     * @return true if request is allowed, false if rate limit exceeded
     */
    boolean checkRateLimit(String identifier, Long maxRequests, Long windowSeconds);

    /**
     * Records a request for rate limiting purposes.
     *
     * @param identifier Client identifier
     */
    void recordRequest(String identifier);

    /**
     * Gets the number of requests in the current window for an identifier.
     *
     * @param identifier Client identifier
     * @param windowSeconds Time window in seconds
     * @return Number of requests in the window
     */
    int getRequestCount(String identifier, int windowSeconds);
}
