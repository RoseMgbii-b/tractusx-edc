--
-- Copyright (c) 2025 Contributors to the Eclipse Foundation
--
-- See the NOTICE file(s) distributed with this work for additional
-- information regarding copyright ownership.
--
-- This program and the accompanying materials are made available under the
-- terms of the Apache License, Version 2.0 which is available at
-- https://www.apache.org/licenses/LICENSE-2.0.
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
-- WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
-- License for the specific language governing permissions and limitations
-- under the License.
--
-- SPDX-License-Identifier: Apache-2.0
--

-- Create audit entry table with immutability and encryption considerations
CREATE TABLE IF NOT EXISTS edc_audit_entry (
    id VARCHAR(255) PRIMARY KEY,
    timestamp BIGINT NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    event_name VARCHAR(255) NOT NULL,
    description TEXT,
    result VARCHAR(255),
    metadata JSONB,
    critical BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for common queries
CREATE INDEX IF NOT EXISTS idx_audit_timestamp ON edc_audit_entry(timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_audit_event_type ON edc_audit_entry(event_type);
CREATE INDEX IF NOT EXISTS idx_audit_critical ON edc_audit_entry(critical);
CREATE INDEX IF NOT EXISTS idx_audit_event_type_critical ON edc_audit_entry(event_type, critical);

-- Comment explaining encryption
COMMENT ON TABLE edc_audit_entry IS 'Audit log table. Encryption should be configured at the PostgreSQL level using transparent data encryption or column-level encryption.';
