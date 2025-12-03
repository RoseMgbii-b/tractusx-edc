package org.eclipse.tractusx.edc.oauth2.hotreload;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import org.eclipse.edc.spi.monitor.Monitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HotReloadableJwtValidationFilterTest {

    private static final String HMAC_KEY = "01234567890123456789012345678901";

    @Mock
    private Monitor monitor;
    @Mock
    private ContainerRequestContext requestContext;

    private HotReloadableJwtValidationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new HotReloadableJwtValidationFilter(monitor);
    }

    @Test
    void filter_whenValidatorNotInitialized_shouldAbortUnauthorized() throws Exception {
        filter.filter(requestContext);

        verify(requestContext).abortWith(argThat(response -> {
            assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
            return true;
        }));
        verify(monitor).warning(contains("not initialized"));
    }

    @Test
    void filter_whenMissingAuthorizationHeader_shouldAbortUnauthorized() throws Exception {
        setField(filter, "jwtProcessor", mock(ConfigurableJWTProcessor.class));
        when(requestContext.getHeaderString("Authorization")).thenReturn(null);

        filter.filter(requestContext);

        verify(requestContext).abortWith(argThat(response -> {
            assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
            return true;
        }));
    }

    @Test
    void filter_whenTokenValid_shouldPopulateContext() throws Exception {
        var jwtProcessor = mock(ConfigurableJWTProcessor.class);
        setField(filter, "jwtProcessor", jwtProcessor);

        var claimsSet = new JWTClaimsSet.Builder()
                .subject("alice")
                .claim("realm_access", Map.of("roles", List.of("ADMIN")))
                .build();

        when(jwtProcessor.process(any(SignedJWT.class), isNull())).thenReturn(claimsSet);

        var token = createJwtToken();
        when(requestContext.getHeaderString("Authorization")).thenReturn("Bearer " + token);

        filter.filter(requestContext);

        verify(requestContext, never()).abortWith(any());
        verify(requestContext).setProperty("edc.jwt.claims", claimsSet.getClaims());
        verify(requestContext).setProperty("edc.jwt.subject", "alice");
        verify(requestContext).setSecurityContext(argThat(ctx -> {
            assertThat(ctx.getUserPrincipal().getName()).isEqualTo("alice");
            return true;
        }));
        verify(monitor).debug(contains("JWT token validated successfully"));
    }

    @Test
    void filter_whenProcessorThrows_shouldAbortUnauthorized() throws Exception {
        var jwtProcessor = mock(ConfigurableJWTProcessor.class);
        setField(filter, "jwtProcessor", jwtProcessor);
        when(jwtProcessor.process(any(SignedJWT.class), isNull())).thenThrow(new com.nimbusds.jose.proc.BadJOSEException("bad audience"));

        when(requestContext.getHeaderString("Authorization")).thenReturn("Bearer " + createJwtToken());

        filter.filter(requestContext);

        verify(requestContext).abortWith(argThat(response -> {
            assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
            return true;
        }));
        verify(monitor).warning(contains("JWT validation failed"));
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = HotReloadableJwtValidationFilter.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static String createJwtToken() throws JOSEException {
        var claims = new JWTClaimsSet.Builder()
                .subject("alice")
                .claim("realm_access", Map.of("roles", List.of("ADMIN")))
                .build();
        var signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        signedJWT.sign(new MACSigner(HMAC_KEY));
        return signedJWT.serialize();
    }
}

