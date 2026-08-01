package com.eventoscelebrativos.config.customgrant;

import com.eventoscelebrativos.security.AuthenticatedUser;
import com.eventoscelebrativos.service.UserAccountAuthenticationService;
import com.eventoscelebrativos.service.UserAccountCredentials;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContext;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomPasswordAuthenticationProviderTest {

    @Mock
    private OAuth2AuthorizationService authorizationService;

    @Mock
    private OAuth2TokenGenerator<OAuth2Token> tokenGenerator;

    @Mock
    private UserAccountAuthenticationService userAccountAuthenticationService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private CustomPasswordAuthenticationProvider provider;

    @BeforeEach
    void setUp() {
        AuthorizationServerSettings authorizationServerSettings = AuthorizationServerSettings.builder()
                .issuer("http://localhost")
                .build();
        AuthorizationServerContextHolder.setContext(new AuthorizationServerContext() {
            @Override
            public String getIssuer() {
                return authorizationServerSettings.getIssuer();
            }

            @Override
            public AuthorizationServerSettings getAuthorizationServerSettings() {
                return authorizationServerSettings;
            }
        });
        provider = new CustomPasswordAuthenticationProvider(
                authorizationService,
                tokenGenerator,
                userAccountAuthenticationService,
                passwordEncoder
        );
    }

    @AfterEach
    void clearAuthorizationServerContext() {
        AuthorizationServerContextHolder.resetContext();
    }

    @Test
    void shouldReturnSanitizedAuthenticatedUserWithoutPasswordCredentialsOrOriginalGrantToken() {
        Set<GrantedAuthority> authorities = Set.of(new SimpleGrantedAuthority("ROLE_OPERATOR"));
        UserAccountCredentials credentials = new UserAccountCredentials(
                10L,
                20L,
                "34999999999",
                "stored-password-hash",
                true,
                true,
                authorities
        );
        when(userAccountAuthenticationService.loadCredentialsByUsername("34999999999"))
                .thenReturn(Optional.of(credentials));
        when(passwordEncoder.matches("raw-password", "stored-password-hash")).thenReturn(true);
        when(tokenGenerator.generate(any(OAuth2TokenContext.class))).thenReturn(accessToken());

        CustomPasswordAuthenticationToken originalGrantToken = new CustomPasswordAuthenticationToken(
                authenticatedClient(),
                Set.of(),
                Map.of("username", "34999999999", "password", "raw-password")
        );

        Authentication authentication = provider.authenticate(originalGrantToken);

        assertInstanceOf(OAuth2AccessTokenAuthenticationToken.class, authentication);
        assertInstanceOf(AuthenticatedUser.class, authentication.getPrincipal());
        assertNull(authentication.getCredentials());
        assertNull(authentication.getDetails());

        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        assertEquals(10L, authenticatedUser.accountId());
        assertEquals(20L, authenticatedUser.personId());
        assertEquals("34999999999", authenticatedUser.username());
        assertEquals(Set.of("ROLE_OPERATOR"), authorityNames(authenticatedUser));
        assertNotSame(originalGrantToken, authentication);
        assertNotSame(originalGrantToken, authentication.getPrincipal());

        ArgumentCaptor<OAuth2Authorization> authorizationCaptor = ArgumentCaptor.forClass(OAuth2Authorization.class);
        verify(authorizationService).save(authorizationCaptor.capture());
        Authentication savedPrincipal = authorizationCaptor.getValue().getAttribute(Principal.class.getName());

        assertInstanceOf(UsernamePasswordAuthenticationToken.class, savedPrincipal);
        assertSame(authenticatedUser, savedPrincipal.getPrincipal());
        assertNull(savedPrincipal.getCredentials());
        assertNull(savedPrincipal.getDetails());
        assertNotSame(originalGrantToken, savedPrincipal);
    }

    private OAuth2AccessToken accessToken() {
        Instant issuedAt = Instant.now();
        return new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "generated-access-token",
                issuedAt,
                issuedAt.plusSeconds(60),
                Set.of()
        );
    }

    private OAuth2ClientAuthenticationToken authenticatedClient() {
        RegisteredClient registeredClient = RegisteredClient.withId("client-registration-id")
                .clientId("client-id")
                .clientSecret("client-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(new AuthorizationGrantType("password"))
                .scope("read")
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(5))
                        .build())
                .build();
        return new OAuth2ClientAuthenticationToken(
                registeredClient,
                ClientAuthenticationMethod.CLIENT_SECRET_BASIC,
                "client-secret"
        );
    }

    private Set<String> authorityNames(AuthenticatedUser authenticatedUser) {
        return authenticatedUser.authorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(java.util.stream.Collectors.toSet());
    }
}
