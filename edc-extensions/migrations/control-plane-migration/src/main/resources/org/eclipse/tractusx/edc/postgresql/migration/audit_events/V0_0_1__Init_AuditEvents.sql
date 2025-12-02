--
--  Copyright (c) 2024 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
--
--  This program and the accompanying materials are made available under the
--  terms of the Apache License, Version 2.0 which is available at
--  https://www.apache.org/licenses/LICENSE-2.0
--
--  SPDX-License-Identifier: Apache-2.0
--
--  Contributors:
--       Mercedes-Benz Tech Innovation GmbH - Initial Database Schema
--
-- V0.0.1 - Initial Audit Events Table Creation
-- Create audit_events table
CREATE TABLE IF NOT EXISTS audit_events (
    id            VARCHAR(255) PRIMARY KEY,
    timestamp     TIMESTAMPTZ NOT NULL,
    category      VARCHAR(100) NOT NULL,
    event_name    VARCHAR(150) NOT NULL,
    outcome       VARCHAR(50)  NOT NULL,
    subject_id    VARCHAR(255),
    actor_id      VARCHAR(255),
    description   TEXT
);
