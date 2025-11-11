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
import jakarta.json.JsonObject;

import java.util.List;

/**
 * DTO for user representation
 */
public class UserDto {
    private final String id;
    private final String username;
    private final String email;
    private final String firstName;
    private final String lastName;
    private final Boolean enabled;
    private final Boolean emailVerified;
    private final List<String> roles;

    @JsonCreator
    public UserDto(@JsonProperty("id") String id,
                   @JsonProperty("username") String username,
                   @JsonProperty("email") String email,
                   @JsonProperty("firstName") String firstName,
                   @JsonProperty("lastName") String lastName,
                   @JsonProperty("enabled") Boolean enabled,
                   @JsonProperty("emailVerified") Boolean emailVerified,
                   @JsonProperty("roles") List<String> roles) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.enabled = enabled;
        this.emailVerified = emailVerified;
        this.roles = roles;
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public Boolean getEmailVerified() {
        return emailVerified;
    }

    public List<String> getRoles() {
        return roles;
    }

    public static UserDto from(JsonObject json) {
        return new UserDto(
                json.getString("id", null),
                json.getString("username", null),
                json.getString("email", null),
                json.getString("firstName", null),
                json.getString("lastName", null),
                json.getBoolean("enabled", true),
                json.getBoolean("emailVerified", false),
                null // Roles would need to be extracted separately
        );
    }
}

