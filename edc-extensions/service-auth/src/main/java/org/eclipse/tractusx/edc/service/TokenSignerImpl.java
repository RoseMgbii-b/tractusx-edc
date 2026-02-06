/********************************************************************************
 * Copyright (c) 2025 Contributors
 *
 * SPDX-License-Identifier: Apache-2.0
 ********************************************************************************/

package org.eclipse.tractusx.edc.service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class TokenSignerImpl implements TokenSigner {

    private final Monitor monitor;
    private final String issuer;
    private final int expirationSeconds;
    private final KeyPair keyPair;

    public TokenSignerImpl(Monitor monitor, String issuer, int expirationSeconds) {
        this.monitor = monitor;
        this.issuer = issuer;
        this.expirationSeconds = expirationSeconds;

        // TODO: Load key pair from vault or configuration
        // For now, generate a key pair (NOT for production!)
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            this.keyPair = keyGen.generateKeyPair();
            monitor.warning("[TokenSigner] Using generated key pair - NOT for production! Use vault keys.");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate key pair", e);
        }
    }

    @Override
    public String sign(Map<String, Object> claims) {
        try {
            var now = Instant.now();
            var expiration = now.plusSeconds(expirationSeconds);

            // Build JWT claims set
            var claimsBuilder = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .issueTime(Date.from(now))
                    .notBeforeTime(Date.from(now))
                    .expirationTime(Date.from(expiration));

            // Add standard claims
            if (claims.containsKey("sub")) {
                claimsBuilder.subject(claims.get("sub").toString());
            }
            if (claims.containsKey("aud")) {
                claimsBuilder.audience(claims.get("aud").toString());
            }
            if (claims.containsKey("scope")) {
                claimsBuilder.claim("scope", claims.get("scope"));
            }

            // Add custom claims from VC
            for (Map.Entry<String, Object> entry : claims.entrySet()) {
                String key = entry.getKey();
                if (!key.equals("sub") && !key.equals("iss") && !key.equals("aud") && !key.equals("scope")) {
                    claimsBuilder.claim(key, entry.getValue());
                }
            }

            var jwtClaimsSet = claimsBuilder.build();

            // Sign JWT
            var header = new JWSHeader(JWSAlgorithm.RS256);
            var signer = new RSASSASigner(keyPair.getPrivate());
            var signedJwt = new SignedJWT(header, jwtClaimsSet);
            signedJwt.sign(signer);

            String token = signedJwt.serialize();
            monitor.debug("[TokenSigner] JWT token signed successfully");
            return token;

        } catch (JOSEException e) {
            monitor.severe("[TokenSigner] Failed to sign JWT token: " + e.getMessage(), e);
            return null;
        }
    }

    @Override
    public Result<Map<String, Object>> verify(String token) {
        try {
            var signedJwt = SignedJWT.parse(token);

            // Verify signature
            var verifier = new RSASSAVerifier((RSAPublicKey) keyPair.getPublic());
            if (!signedJwt.verify(verifier)) {
                return Result.failure("JWT signature verification failed");
            }

            // Extract claims
            var claimsSet = signedJwt.getJWTClaimsSet();

            // Check expiration
            if (claimsSet.getExpirationTime() != null &&
                    claimsSet.getExpirationTime().before(Date.from(Instant.now()))) {
                return Result.failure("JWT token has expired");
            }

            // Extract claims to map
            Map<String, Object> claims = new HashMap<>();
            claims.put("sub", claimsSet.getSubject());
            claims.put("iss", claimsSet.getIssuer());
            if (claimsSet.getAudience() != null && !claimsSet.getAudience().isEmpty()) {
                claims.put("aud", claimsSet.getAudience().get(0));
            }

            // Add all other claims
            var allClaims = claimsSet.getClaims();
            for (Map.Entry<String, Object> entry : allClaims.entrySet()) {
                if (!claims.containsKey(entry.getKey())) {
                    claims.put(entry.getKey(), entry.getValue());
                }
            }

            return Result.success(claims);

        } catch (ParseException e) {
            return Result.failure("Failed to parse JWT token: " + e.getMessage());
        } catch (JOSEException e) {
            return Result.failure("JWT verification error: " + e.getMessage());
        }
    }

    @Override
    public int getExpirationSeconds() {
        return expirationSeconds;
    }
}
