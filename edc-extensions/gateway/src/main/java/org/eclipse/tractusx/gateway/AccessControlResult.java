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

/**
 * Result of access control evaluation.
 */
public class AccessControlResult {
    private final boolean allowed;
    private final String reason;
    
    private AccessControlResult(boolean allowed, String reason) {
        this.allowed = allowed;
        this.reason = reason;
    }
    
    public static AccessControlResult allowed(String reason) {
        return new AccessControlResult(true, reason);
    }
    
    public static AccessControlResult denied(String reason) {
        return new AccessControlResult(false, reason);
    }
    
    public boolean isAllowed() {
        return allowed;
    }
    
    public String getReason() {
        return reason;
    }
}


