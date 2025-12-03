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

package org.eclipse.tractusx.edc.usermanagement.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Request DTO for creating a user
 */
public class CreateUserRequest {
    private final String username;
    private final String email;
    private final String firstName;
    private final String lastName;
    private final String temporaryPassword;
    private final List<String> roles;

    @JsonCreator
    public CreateUserRequest(@JsonProperty("username") String username,
                            @JsonProperty("email") String email,
                            @JsonProperty("firstName") String firstName,
                            @JsonProperty("lastName") String lastName,
                            @JsonProperty("temporaryPassword") String temporaryPassword,
                            @JsonProperty("roles") List<String> roles) {
        this.username = username;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.temporaryPassword = temporaryPassword;
        this.roles = roles;
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

    public String getTemporaryPassword() {
        return temporaryPassword;
    }

    public List<String> getRoles() {
        return roles;
    }
}

