package com.eventoscelebrativos.security;

import com.eventoscelebrativos.service.UserAccountAuthenticationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountJwtAuthenticationConverterTest {

    private static final Object MISSING_CLAIM = new Object();

    @Mock
    private UserAccountAuthenticationService userAccountAuthenticationService;

    private UserAccountJwtAuthenticationConverter converter;

    @BeforeEach
    void setUp() {
        converter = new UserAccountJwtAuthenticationConverter(userAccountAuthenticationService);
    }

    @Test
    void shouldBuildAuthenticatedUserWhenAccountIsValidAndUsernameMatches() {
        Set<GrantedAuthority> authorities = Set.of(new SimpleGrantedAuthority("ROLE_OPERATOR"));
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(10L, 20L, "34999999999", 0L, authorities);
        when(userAccountAuthenticationService.loadCurrentUser(10L)).thenReturn(Optional.of(authenticatedUser));

        Jwt jwt = jwt(10L, "34999999999", "34999999999");

        AbstractAuthenticationToken result = converter.convert(jwt);

        assertEquals(authenticatedUser, result.getPrincipal());
        assertNull(result.getCredentials());
        assertEquals(authorities, Set.copyOf(result.getAuthorities()));
    }

    @Test
    void shouldRejectWhenAccountIdClaimIsMissing() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("34999999999")
                .claim("username", "34999999999")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        assertThrows(InvalidBearerTokenException.class, () -> converter.convert(jwt));
        verify(userAccountAuthenticationService, never()).loadCurrentUser(any());
    }

    @Test
    void shouldRejectWhenAccountIdClaimIsNotANumber() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("34999999999")
                .claim("username", "34999999999")
                .claim("account_id", "not-a-number")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        assertThrows(InvalidBearerTokenException.class, () -> converter.convert(jwt));
        verify(userAccountAuthenticationService, never()).loadCurrentUser(any());
    }

    @Test
    void shouldRejectWhenAccountIdClaimIsDecimalNumber() {
        Jwt jwt = jwt(BigDecimal.valueOf(10.5), "34999999999", "34999999999");

        assertThrows(InvalidBearerTokenException.class, () -> converter.convert(jwt));
        verify(userAccountAuthenticationService, never()).loadCurrentUser(any());
    }

    @Test
    void shouldRejectWhenAccountIdClaimIsZero() {
        Jwt jwt = jwt(0L, "34999999999", "34999999999");

        assertThrows(InvalidBearerTokenException.class, () -> converter.convert(jwt));
        verify(userAccountAuthenticationService, never()).loadCurrentUser(any());
    }

    @Test
    void shouldRejectWhenAccountIdClaimIsNegative() {
        Jwt jwt = jwt(-10L, "34999999999", "34999999999");

        assertThrows(InvalidBearerTokenException.class, () -> converter.convert(jwt));
        verify(userAccountAuthenticationService, never()).loadCurrentUser(any());
    }

    @Test
    void shouldRejectWhenAccountIdClaimHasUnexpectedStructure() {
        Jwt jwt = jwt(Map.of("id", 10L), "34999999999", "34999999999");

        assertThrows(InvalidBearerTokenException.class, () -> converter.convert(jwt));
        verify(userAccountAuthenticationService, never()).loadCurrentUser(any());
    }

    @Test
    void shouldRejectWhenAccountNoLongerExistsOrIsInvalid() {
        when(userAccountAuthenticationService.loadCurrentUser(99L)).thenReturn(Optional.empty());

        Jwt jwt = jwt(99L, "34999999999", "34999999999");

        assertThrows(InvalidBearerTokenException.class, () -> converter.convert(jwt));
    }

    @Test
    void shouldAcceptLegacyTokenWithoutTokenVersionOnlyWhenCurrentVersionIsZero() {
        Set<GrantedAuthority> authorities = Set.of(new SimpleGrantedAuthority("ROLE_OPERATOR"));
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(10L, 20L, "34999999999", 0L, authorities);
        when(userAccountAuthenticationService.loadCurrentUser(10L)).thenReturn(Optional.of(authenticatedUser));

        AbstractAuthenticationToken result = converter.convert(jwt(10L, "34999999999", "34999999999"));

        assertEquals(authenticatedUser, result.getPrincipal());
    }

    @Test
    void shouldRejectLegacyTokenWithoutTokenVersionAfterVersionIncrement() {
        Set<GrantedAuthority> authorities = Set.of(new SimpleGrantedAuthority("ROLE_OPERATOR"));
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(10L, 20L, "34999999999", 1L, authorities);
        when(userAccountAuthenticationService.loadCurrentUser(10L)).thenReturn(Optional.of(authenticatedUser));

        assertThrows(InvalidBearerTokenException.class, () -> converter.convert(jwt(10L, "34999999999", "34999999999")));
    }

    @Test
    void shouldRejectInvalidTokenVersionTypes() {
        assertThrows(InvalidBearerTokenException.class, () -> converter.convert(jwt(10L, "34999999999", "34999999999", -1L)));
        assertThrows(InvalidBearerTokenException.class, () -> converter.convert(jwt(10L, "34999999999", "34999999999", BigDecimal.valueOf(1.5))));
        assertThrows(InvalidBearerTokenException.class, () -> converter.convert(jwt(10L, "34999999999", "34999999999", "1")));
        assertThrows(InvalidBearerTokenException.class, () -> converter.convert(jwt(10L, "34999999999", "34999999999", Map.of("version", 1))));
        verify(userAccountAuthenticationService, never()).loadCurrentUser(any());
    }

    @Test
    void shouldRejectTokenVersionDivergentFromCurrentAccountVersion() {
        Set<GrantedAuthority> authorities = Set.of(new SimpleGrantedAuthority("ROLE_OPERATOR"));
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(10L, 20L, "34999999999", 2L, authorities);
        when(userAccountAuthenticationService.loadCurrentUser(10L)).thenReturn(Optional.of(authenticatedUser));

        assertThrows(InvalidBearerTokenException.class, () -> converter.convert(jwt(10L, "34999999999", "34999999999", 1L)));
    }

    @Test
    void shouldRejectWhenCurrentUsernameNoLongerMatchesTokenSubject() {
        Set<GrantedAuthority> authorities = Set.of(new SimpleGrantedAuthority("ROLE_OPERATOR"));
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(10L, 20L, "34988888888", 0L, authorities);
        when(userAccountAuthenticationService.loadCurrentUser(10L)).thenReturn(Optional.of(authenticatedUser));

        // token emitido quando o username ainda era 34999999999; a conta ja mudou para 34988888888
        Jwt jwt = jwt(10L, "34999999999", "34999999999");

        assertThrows(InvalidBearerTokenException.class, () -> converter.convert(jwt));
    }

    @Test
    void shouldRejectWhenSubjectIsMissing() {
        Set<GrantedAuthority> authorities = Set.of(new SimpleGrantedAuthority("ROLE_OPERATOR"));
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(10L, 20L, "34999999999", 0L, authorities);
        when(userAccountAuthenticationService.loadCurrentUser(10L)).thenReturn(Optional.of(authenticatedUser));

        Jwt jwt = jwt(10L, null, "34999999999");

        assertThrows(InvalidBearerTokenException.class, () -> converter.convert(jwt));
        verify(userAccountAuthenticationService).loadCurrentUser(eq(10L));
    }

    @Test
    void shouldRejectWhenUsernameClaimIsMissing() {
        Set<GrantedAuthority> authorities = Set.of(new SimpleGrantedAuthority("ROLE_OPERATOR"));
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(10L, 20L, "34999999999", 0L, authorities);
        when(userAccountAuthenticationService.loadCurrentUser(10L)).thenReturn(Optional.of(authenticatedUser));

        Jwt jwt = jwt(10L, "34999999999", MISSING_CLAIM);

        assertThrows(InvalidBearerTokenException.class, () -> converter.convert(jwt));
        verify(userAccountAuthenticationService).loadCurrentUser(eq(10L));
    }

    @Test
    void shouldRejectWhenSubjectDivergesFromCurrentUsername() {
        Set<GrantedAuthority> authorities = Set.of(new SimpleGrantedAuthority("ROLE_OPERATOR"));
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(10L, 20L, "34999999999", 0L, authorities);
        when(userAccountAuthenticationService.loadCurrentUser(10L)).thenReturn(Optional.of(authenticatedUser));

        Jwt jwt = jwt(10L, "34988888888", "34999999999");

        assertThrows(InvalidBearerTokenException.class, () -> converter.convert(jwt));
        verify(userAccountAuthenticationService).loadCurrentUser(eq(10L));
    }

    @Test
    void shouldRejectWhenUsernameClaimDivergesFromCurrentUsername() {
        Set<GrantedAuthority> authorities = Set.of(new SimpleGrantedAuthority("ROLE_OPERATOR"));
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(10L, 20L, "34999999999", 0L, authorities);
        when(userAccountAuthenticationService.loadCurrentUser(10L)).thenReturn(Optional.of(authenticatedUser));

        Jwt jwt = jwt(10L, "34999999999", "34988888888");

        assertThrows(InvalidBearerTokenException.class, () -> converter.convert(jwt));
        verify(userAccountAuthenticationService).loadCurrentUser(eq(10L));
    }

    private Jwt jwt(Object accountId, String subject, Object usernameClaim) {
        return jwt(accountId, subject, usernameClaim, MISSING_CLAIM);
    }

    private Jwt jwt(Object accountId, String subject, Object usernameClaim, Object tokenVersionClaim) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("authorities", List.of("ROLE_OPERATOR"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        if (subject != null) {
            builder.subject(subject);
        }
        if (usernameClaim != MISSING_CLAIM) {
            builder.claim("username", usernameClaim);
        }
        if (tokenVersionClaim != MISSING_CLAIM) {
            builder.claim("token_version", tokenVersionClaim);
        }
        return builder.claim("account_id", accountId).build();
    }
}
