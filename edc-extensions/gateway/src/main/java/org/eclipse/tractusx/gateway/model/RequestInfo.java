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

package org.eclipse.tractusx.gateway.model;

import java.time.Instant;
import java.util.Map;

public class RequestInfo {
    private final String clientIp;
    private final String path;
    private final String method;
    private final Map<String, String> headers;
    private final Instant timestamp;
    private final String userIdentifier;

    public RequestInfo(String clientIp, String path, String method, Map<String, String> headers, Instant timestamp, String userIdentifier) {
        this.clientIp = clientIp;
        this.path = path;
        this.method = method;
        this.headers = headers;
        this.timestamp = timestamp;
        this.userIdentifier = userIdentifier;
    }

    public String getClientIp() {
        return clientIp;
    }

    public String getPath() {
        return path;
    }

    public String getMethod() {
        return method;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getUserIdentifier() {
        return userIdentifier;
    }
}
