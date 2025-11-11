/********************************************************************************
 * Copyright (c) 2025 Contributors
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

package org.eclipse.tractusx.edc.usermanagement.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for role representation
 */
public class RoleDto {
    private final String id;
    private final String name;
    private final String description;
    private final Boolean composite;
//    private final Boolean clientRole;

    @JsonCreator
    public RoleDto(@JsonProperty("id") String id,
                   @JsonProperty("name") String name,
                   @JsonProperty("description") String description,
                   @JsonProperty("composite") Boolean composite
//                   @JsonProperty("clientRole") Boolean clientRole
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.composite = composite;
//        this.clientRole = clientRole;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Boolean getComposite() {
        return composite;
    }

//    public Boolean getClientRole() {
//        return clientRole;
//    }
}

