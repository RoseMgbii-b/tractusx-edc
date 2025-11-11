///********************************************************************************
// * Copyright (c) 2025 Your Company Name
// *
// * See the NOTICE file(s) distributed with this work for additional
// * information regarding copyright ownership.
// *
// * This program and the accompanying materials are made available under the
// * terms of the Apache License, Version 2.0 which is available at
// * https://www.apache.org/licenses/LICENSE-2.0.
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
// * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
// * License for the specific language governing permissions and limitations
// * under the License.
// *
// * SPDX-License-Identifier: Apache-2.0
// ********************************************************************************/
//
//
//package org.eclipse.tractusx.user.management.service;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import jakarta.json.JsonObject;
//import org.eclipse.edc.spi.monitor.Monitor;
//import org.eclipse.edc.spi.result.Result;
//import org.eclipse.tractusx.user.management.dto.UserDto;
//import org.eclipse.tractusx.user.management.request.CreateUserRequest;
//
//import java.net.URI;
//import java.net.http.HttpClient;
//import java.net.http.HttpRequest;
//import java.net.http.HttpResponse;
//import java.time.Instant;
//import java.util.concurrent.locks.ReentrantLock;
//
//public class KeycloakAdminApiService {
//    private static final String TOKEN_ENDPOINT = "/protocol/openid-connect/token";
//    private static final String USERS_ENDPOINT = "/admin/realms/%s/users";
//    private static final String USER_BY_ID_ENDPOINT = "/admin/realms/%s/users/%s";
//    private static final String USER_PASSWORD_ENDPOINT = "/admin/realms/%s/users/%s/reset-password";
//    private static final String USER_ROLES_ENDPOINT = "/admin/realms/%s/users/%s/role-mappings/realm";
//
//    private final String keycloakUrl;
//    private final String realm;
//    private final String clientId;
//    private final String clientSecret;
//    private final HttpClient httpClient;
//    private final Monitor monitor;
//    private final ObjectMapper objectMapper;
//
//    // Token management (thread-safe)
//    private final ReentrantLock tokenLock = new ReentrantLock();
//    private volatile String accessToken;
//    private volatile Instant tokenExpiresAt;
//    private static final long TOKEN_REFRESH_BUFFER_SECONDS = 60; // Refresh 60s before expiry
//
//    public KeycloakAdminApiService(
//            String keycloakUrl,
//            String realm,
//            String clientId,
//            String clientSecret,
//            HttpClient httpClient,
//            Monitor monitor) {
//        this.keycloakUrl = keycloakUrl.endsWith("/") ? keycloakUrl.substring(0, keycloakUrl.length() - 1) : keycloakUrl;
//        this.realm = realm;
//        this.clientId = clientId;
//        this.clientSecret = clientSecret;
//        this.httpClient = httpClient;
//        this.monitor = monitor;
//        this.objectMapper = new ObjectMapper();
//    }
//
//
//    // Token management (thread-safe)
//    private final ReentrantLock tokenLock = new ReentrantLock();
//    private volatile String accessToken;
//    private volatile Instant tokenExpiresAt;
//    private static final long TOKEN_REFRESH_BUFFER_SECONDS = 60; // Refresh 60s before expiry
//    public Result<UserDto> createUser(CreateUserRequest request) {
//        // 1. Ensure we have valid access token
//        ensureValidToken();
//
//        // 2. Convert EDC request to Keycloak format
//        JsonObject keycloakUserJson = convertToKeycloakFormat(request);
//
//        // 3. Make HTTP POST to Keycloak
//        HttpRequest httpRequest = HttpRequest.newBuilder()
//                .uri(URI.create(keycloakUrl + "/admin/realms/" + realm + "/users"))
//                .header("Authorization", "Bearer " + accessToken)
//                .header("Content-Type", "application/json")
//                .POST(HttpRequest.BodyPublishers.ofString(keycloakUserJson.toString()))
//                .build();
//
//        // 4. Send request and handle response
//        HttpResponse<String> response = httpClient.send(httpRequest, ...);
//
//        // 5. Convert Keycloak response to EDC format
//        return parseKeycloakResponse(response);
//    }
//
//    private void ensureValidToken() {
//
//    }
//
//    private Result<UserDto> parseKeycloakResponse(HttpResponse<String> response) {
//        return null;
//    }
//
//    private JsonObject convertToKeycloakFormat(CreateUserRequest request) {
//        return null;
//    }
//}
