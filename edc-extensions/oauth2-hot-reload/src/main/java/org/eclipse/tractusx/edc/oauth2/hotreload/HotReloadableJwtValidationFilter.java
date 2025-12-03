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

package org.eclipse.tractusx.edc.oauth2.hotreload;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import org.eclipse.edc.spi.monitor.Monitor;

import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Custom JWT Validation Filter with Hot Reload Support
 *
 * This filter replaces the native EDC DAC (Delegated Authentication Client) implementation
 * and provides the ability to hot-reload OAuth2 configuration (JWKS URL, audience) without
 * restarting the connector.
 *
 * Features:
 * - Validates JWT token signatures using JWKS (JSON Web Key Set)
 * - Validates issuer and audience claims
 * - Supports hot reload of JWKS URL and audience
 * - Thread-safe configuration updates
 * - Caches JWKS for performance
 */
public class HotReloadableJwtValidationFilter implements ContainerRequestFilter {

    private final Monitor monitor;
    private final ReadWriteLock configLock = new ReentrantReadWriteLock();
    
    // Volatile configuration that can be updated at runtime
    private volatile String jwksUrl;
    private volatile String expectedAudience;
    private volatile String expectedIssuer;
    
    // JWT processor and JWK source (updated when config changes)
    private volatile ConfigurableJWTProcessor<SecurityContext> jwtProcessor;
    private volatile RemoteJWKSet<SecurityContext> remoteJwkSet;

    public HotReloadableJwtValidationFilter(Monitor monitor) {
        this.monitor = monitor;
    }

    /**
     * Initialize or update the JWT validator with new configuration
     * This method is thread-safe and can be called to hot-reload configuration
     */
    public void updateConfiguration(String jwksUrl, String audience, String issuer) {
        configLock.writeLock().lock();
        try {
            if (jwksUrl == null || jwksUrl.trim().isEmpty()) {
                monitor.warning("JWKS URL is null or empty. JWT validation will fail.");
                return;
            }

            this.jwksUrl = jwksUrl.trim();
            this.expectedAudience = audience != null ? audience.trim() : null;
            this.expectedIssuer = issuer != null ? issuer.trim() : null;

            try {
                // Create new RemoteJWKSet for fetching keys from JWKS URL
                // RemoteJWKSet uses its own HTTP client internally, so we just pass the URL
                URL jwksUrlObj = new URL(this.jwksUrl);
                this.remoteJwkSet = new RemoteJWKSet<>(jwksUrlObj);

                // Create JWT processor with key selector
                ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
                JWSKeySelector<SecurityContext> keySelector = new JWSVerificationKeySelector<>(
                        JWSAlgorithm.RS256, // Most common algorithm for OAuth2/OIDC
                        remoteJwkSet
                );
                processor.setJWSKeySelector(keySelector);

                // Use default verifier for expiration, we'll validate issuer/audience separately
                processor.setJWTClaimsSetVerifier(new com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier<>(
                        null, // Accept any issuer initially
                        null, // Accept any audience initially  
                        null, // No required claims
                        null  // No required claims
                ));

                // Atomically update the processor
                this.jwtProcessor = processor;

                monitor.info("JWT validator configuration updated - JWKS URL: " + this.jwksUrl +
                        (expectedAudience != null ? ", Audience: " + expectedAudience : "") +
                        (expectedIssuer != null ? ", Issuer: " + expectedIssuer : ""));

            } catch (Exception e) {
                monitor.severe("Failed to initialize JWT validator with JWKS URL: " + jwksUrl, e);
                throw new RuntimeException("Failed to initialize JWT validator", e);
            }
        } finally {
            configLock.writeLock().unlock();
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        // Check if validator is initialized
        if (jwtProcessor == null) {
            monitor.warning("JWT validator not initialized. Rejecting request.");
            requestContext.abortWith(
                    Response.status(Response.Status.UNAUTHORIZED)
                            .entity("{\"message\":\"JWT validator not configured\",\"type\":\"Unauthorized\"}")
                            .build()
            );
            return;
        }

        // Extract JWT token from Authorization header
        String authHeader = requestContext.getHeaderString("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            monitor.debug("Missing or invalid Authorization header");
            requestContext.abortWith(
                    Response.status(Response.Status.UNAUTHORIZED)
                            .entity("{\"message\":\"Missing or invalid Authorization header\",\"type\":\"Unauthorized\"}")
                            .build()
            );
            return;
        }

        String token = authHeader.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            monitor.debug("Empty JWT token");
            requestContext.abortWith(
                    Response.status(Response.Status.UNAUTHORIZED)
                            .entity("{\"message\":\"Empty JWT token\",\"type\":\"Unauthorized\"}")
                            .build()
            );
            return;
        }

        // Validate JWT token
        configLock.readLock().lock();
        try {
            // Parse and validate JWT signature and expiration
            SignedJWT signedJwt = SignedJWT.parse(token);
            JWTClaimsSet claimsSet = jwtProcessor.process(signedJwt, null);

            // Additional validation: issuer and audience (if configured)
            String currentIssuer = expectedIssuer;
            String currentAudience = expectedAudience;
            
            if (currentIssuer != null && !currentIssuer.isEmpty()) {
                String tokenIssuer = claimsSet.getIssuer();
                if (tokenIssuer == null || !tokenIssuer.equals(currentIssuer)) {
                    throw new BadJOSEException("JWT issuer mismatch. Expected: " + currentIssuer + ", Got: " + tokenIssuer);
                }
            }

            if (currentAudience != null && !currentAudience.isEmpty()) {
                Object audClaim = claimsSet.getClaim("aud");
                if (audClaim == null) {
                    throw new BadJOSEException("JWT missing audience claim");
                }
                
                boolean audienceValid = false;
                if (audClaim instanceof String) {
                    audienceValid = currentAudience.equals(audClaim);
                } else if (audClaim instanceof java.util.List) {
                    @SuppressWarnings("unchecked")
                    java.util.List<String> audiences = (java.util.List<String>) audClaim;
                    audienceValid = audiences.contains(currentAudience);
                }
                
                if (!audienceValid) {
                    throw new BadJOSEException("JWT audience mismatch. Expected: " + currentAudience + ", Got: " + audClaim);
                }
            }

            // Extract claims and set in request context for downstream filters
            Map<String, Object> claims = claimsSet.getClaims();
            requestContext.setProperty("edc.jwt.claims", claims);
            requestContext.setProperty("edc.jwt.subject", claimsSet.getSubject());

            // Set security context with principal
            String subject = claimsSet.getSubject();
            if (subject != null) {
                requestContext.setSecurityContext(new JwtSecurityContext(subject, claims));
            }

            monitor.debug("JWT token validated successfully for subject: " + subject);

        } catch (ParseException e) {
            monitor.warning("Failed to parse JWT token: " + e.getMessage());
            requestContext.abortWith(
                    Response.status(Response.Status.UNAUTHORIZED)
                            .entity("{\"message\":\"Invalid JWT token format\",\"type\":\"Unauthorized\"}")
                            .build()
            );
        } catch (BadJOSEException e) {
            monitor.warning("JWT validation failed: " + e.getMessage());
            requestContext.abortWith(
                    Response.status(Response.Status.UNAUTHORIZED)
                            .entity("{\"message\":\"JWT validation failed: " + e.getMessage() + "\",\"type\":\"Unauthorized\"}")
                            .build()
            );
        } catch (JOSEException e) {
            monitor.severe("JWT processing error: " + e.getMessage(), e);
            requestContext.abortWith(
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                            .entity("{\"message\":\"JWT processing error\",\"type\":\"InternalServerError\"}")
                            .build()
            );
        } catch (Exception e) {
            monitor.severe("Unexpected error during JWT validation: " + e.getMessage(), e);
            requestContext.abortWith(
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                            .entity("{\"message\":\"Unexpected error during JWT validation\",\"type\":\"InternalServerError\"}")
                            .build()
            );
        } finally {
            configLock.readLock().unlock();
        }
    }

    /**
     * Simple SecurityContext implementation for JWT-based authentication
     */
    private static class JwtSecurityContext implements jakarta.ws.rs.core.SecurityContext {
        private final String subject;
        private final Map<String, Object> claims;

        JwtSecurityContext(String subject, Map<String, Object> claims) {
            this.subject = subject;
            this.claims = claims;
        }

        @Override
        public java.security.Principal getUserPrincipal() {
            return () -> subject;
        }

        @Override
        public boolean isUserInRole(String role) {
            // Extract roles from claims (Keycloak format: realm_access.roles)
            Object realmAccess = claims.get("realm_access");
            if (realmAccess instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> realmAccessMap = (Map<String, Object>) realmAccess;
                Object rolesObj = realmAccessMap.get("roles");
                if (rolesObj instanceof java.util.List) {
                    @SuppressWarnings("unchecked")
                    java.util.List<String> roles = (java.util.List<String>) rolesObj;
                    return roles.contains(role);
                }
            }
            return false;
        }

        @Override
        public boolean isSecure() {
            return true; // Assume HTTPS in production
        }

        @Override
        public String getAuthenticationScheme() {
            return "Bearer";
        }
    }

}

