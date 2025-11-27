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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class RequestWindow {
    private final List<Instant> requests = new CopyOnWriteArrayList<>();
    private final Long defaultWindowSeconds;

    public RequestWindow(Long defaultWindowSeconds) {
        this.defaultWindowSeconds = defaultWindowSeconds;
    }

    public void addRequest(Instant timestamp) {
        requests.add(timestamp);
    }

    public void removeOldEntries(Long windowSeconds) {
        var cutoff = Instant.now().minusSeconds(windowSeconds);
        requests.removeIf(timestamp -> timestamp.isBefore(cutoff));
    }

    public int getRequestCount() {
        return requests.size();
    }

}
