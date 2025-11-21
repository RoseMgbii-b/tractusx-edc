/*
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
 */

package org.eclipse.tractusx.edc.spi.audit.model;

/**
 * Defines the types of audit events that can be recorded in the system.
 * These types help categorize audit entries for routing and filtering.
 */
public enum AuditEventType {
    /**
     * Transaction-related events (data transfers)
     */
    TRANSACTION,
    
    /**
     * Contract negotiation events
     */
    CONTRACT_NEGOTIATION,
    
    /**
     * Contract request events
     */
    CONTRACT_REQUEST,
    
    /**
     * Policy validation events
     */
    VALIDATION,
    
    /**
     * User action events
     */
    USER_ACTION,
    
    /**
     * General system events
     */
    SYSTEM
}
