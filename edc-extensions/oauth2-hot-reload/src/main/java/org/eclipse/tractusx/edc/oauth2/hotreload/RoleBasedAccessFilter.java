package org.eclipse.tractusx.edc.oauth2.hotreload;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import org.eclipse.edc.spi.monitor.Monitor;

import java.io.IOException;
import java.security.Principal;
import java.text.ParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class RoleBasedAccessFilter implements ContainerRequestFilter {

    private final Monitor monitor;

    public RoleBasedAccessFilter(Monitor monitor) {
        this.monitor = monitor;
    }


    private static final Map<String, Set<String>> PATH_ROLE_MAP = Map.of(
            // Paths are relative to Management API context
            "v1/business-partner-groups", Set.of("ADMIN"),
            "v3/business-partner-groups", Set.of("ADMIN"),
            "v3/policydefinitions", Set.of("ADMIN", "POLICY_WRITER"),
            "v3/contractdefinitions", Set.of("ADMIN", "CONTRACT_WRITER"),
            "v3/assets", Set.of("ADMIN", "ASSET_MANAGER")
    );


    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String path = requestContext.getUriInfo().getPath();
        String method = requestContext.getMethod();
        String fullUri = requestContext.getUriInfo().getRequestUri().toString();
        
        monitor.debug("=== RBAC Filter Called ===");
        monitor.debug("Method: " + method + ", Path: " + path + ", Full URI: " + fullUri);

         if ("GET".equals(method)) {
             monitor.debug("Skipping RBAC check for GET request");
             return;
         }

        // Extract roles from JWT token
        Set<String> userRoles = extractRolesFromToken(requestContext);

        if (userRoles.isEmpty()) {
            monitor.warning("No roles found in JWT token for path: " + path);
            requestContext.abortWith(
                    Response.status(Response.Status.FORBIDDEN)
                            .entity("{\"message\":\"No roles found in token\",\"type\":\"Forbidden\"}")
                            .build()
            );
            return;
        }

        // Find matching path pattern and check roles
        for (Map.Entry<String, Set<String>> entry : PATH_ROLE_MAP.entrySet()) {
            if (path.startsWith(entry.getKey())) {
                Set<String> requiredRoles = entry.getValue();

                // Check if user has ANY of the required roles
                boolean hasAccess = requiredRoles.stream()
                        .anyMatch(userRoles::contains);

                if (!hasAccess) {
                    monitor.warning("Access denied for path: " + path +
                            ", user roles: " + userRoles +
                            ", required roles: " + requiredRoles);
                    requestContext.abortWith(
                            Response.status(Response.Status.FORBIDDEN)
                                    .entity(String.format(
                                            "{\"message\":\"Access denied. Required roles: %s\",\"type\":\"Forbidden\"}",
                                            requiredRoles
                                    ))
                                    .build()
                    );
                    return;
                }

                // User has required role, allow request to proceed
                monitor.debug("Access granted for path: " + path + " with roles: " + userRoles);
                return;
            }
        }
        // No specific rule for this path - allow it (or deny if you want explicit allow-list)
        monitor.debug("No RBAC rule for path: " + path + " - allowing by default");
    }

    /**
     * Extracts roles from JWT token claims
     *
     * Your JWT token structure:
     * {
     *   "realm_access": {
     *     "roles": ["ADMIN", "offline_access", ...]
     *   }
     * }
     */
    @SuppressWarnings("unchecked")
    private Set<String> extractRolesFromToken(ContainerRequestContext requestContext) {
        try {
            // OPTION 1: Try to get from request property (common pattern)
            Object claimsObj = requestContext.getProperty("edc.jwt.claims");
            if (claimsObj instanceof Map<?, ?>) {
                Map<String, Object> claims = (Map<String, Object>) claimsObj;
                monitor.debug("Found JWT claims in request property");
                return extractRolesFromClaims(claims);
            }

            // OPTION 2: Try to get from security context
            Principal principal = requestContext.getSecurityContext().getUserPrincipal();
            if (principal != null) {
                monitor.debug("Principal found in security context: " + principal.getName());
                // Principal might have claims attached - you might need to cast it
                // depending on implementation
            }

            // OPTION 3: Parse Authorization header directly (fallback)
            String authHeader = requestContext.getHeaderString("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring("Bearer ".length()).trim();
                monitor.debug("Parsing JWT token from Authorization header");

                try {
                    // Parse JWT token using nimbus-jwt library
                    SignedJWT signedJWT = SignedJWT.parse(token);
                    JWTClaimsSet claimsSet = signedJWT.getJWTClaimsSet();

                    // Convert claims to Map<String, Object>
                    Map<String, Object> claims = claimsSet.getClaims();

                    // Extract roles using the existing method
                    Set<String> roles = extractRolesFromClaims(claims);
                    monitor.debug("Extracted roles from JWT token: " + roles);
                    return roles;

                } catch (ParseException e) {
                    monitor.warning("Failed to parse JWT token from Authorization header: " + e.getMessage());
                    return Set.of();
                } catch (Exception e) {
                    monitor.warning("Error extracting roles from JWT token: " + e.getMessage(), e);
                    return Set.of();
                }
            }

            monitor.warning("No JWT token found in request - no Authorization header");
            return Set.of();

        } catch (Exception e) {
            monitor.warning("Failed to extract roles from token: " + e.getMessage(), e);
            return Set.of();
        }
    }

    /**
     * Extracts roles from JWT claims map
     */
    @SuppressWarnings("unchecked")
    private Set<String> extractRolesFromClaims(Map<String, Object> claims) {
        Set<String> roles = new HashSet<>();

        // Extract from realm_access.roles (your token structure)
        Object realmAccess = claims.get("realm_access");
        if (realmAccess instanceof Map<?, ?>) {
            Map<String, Object> realmAccessMap = (Map<String, Object>) realmAccess;
            Object rolesObj = realmAccessMap.get("roles");
            if (rolesObj instanceof List<?>) {
                List<?> roleList = (List<?>) rolesObj;
                roles.addAll(roleList.stream()
                        .map(Object::toString)
                        .map(String::toUpperCase)
                        .collect(Collectors.toSet()));
            }
        }

        // Also check resource_access if needed (for client-specific roles)
        Object resourceAccess = claims.get("resource_access");
        if (resourceAccess instanceof Map<?, ?>) {
            Map<String, Object> resourceAccessMap = (Map<String, Object>) resourceAccess;
            // Iterate through resource access entries if needed
        }

        return roles;
    }

}
