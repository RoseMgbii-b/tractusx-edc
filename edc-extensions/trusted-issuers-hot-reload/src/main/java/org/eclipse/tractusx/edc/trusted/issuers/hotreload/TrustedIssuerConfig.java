/********************************************************************************
 * Copyright (c) 2025 Your Company
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

package org.eclipse.tractusx.edc.trusted.issuers.hotreload;

import java.util.Set;

public class TrustedIssuerConfig {

    final String id;
    final Set<String> supportedTypes;

    TrustedIssuerConfig(String id, Set<String> supportedTypes) {
        this.id = id;
        this.supportedTypes = supportedTypes != null ? supportedTypes : Set.of("*");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrustedIssuerConfig that = (TrustedIssuerConfig) o;
        return id.equals(that.id) && supportedTypes.equals(that.supportedTypes);
    }

    @Override
    public int hashCode() {
        return id.hashCode() * 31 + supportedTypes.hashCode();
    }
}
