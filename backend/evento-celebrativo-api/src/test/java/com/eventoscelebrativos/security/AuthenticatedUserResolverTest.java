package com.eventoscelebrativos.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticatedUserResolverTest {

    private final AuthenticatedUserResolver resolver = new AuthenticatedUserResolver();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldResolveAccountPersonAndUsernameFromAuthenticatedUser() {
        Set<GrantedAuthority> authorities = Set.of(new SimpleGrantedAuthority("ROLE_OPERATOR"));
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(10L, 20L, "34999999999", authorities);
        setAuthentication(new UsernamePasswordAuthenticationToken(authenticatedUser, null, authorities));

        assertEquals(authenticatedUser, resolver.requireCurrentUser());
        assertEquals(10L, resolver.requireCurrentAccountId());
        assertEquals(20L, resolver.requireCurrentPersonId());
        assertEquals("34999999999", resolver.requireCurrentUsername());
    }

    @Test
    void shouldResolveRepeatedlyFromSameSecurityContextWithoutCredentialsOrRepositoryDependency() {
        Set<GrantedAuthority> authorities = Set.of(new SimpleGrantedAuthority("ROLE_OPERATOR"));
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(10L, 20L, "34999999999", authorities);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(authenticatedUser, null, authorities);
        setAuthentication(authentication);

        assertSame(authenticatedUser, resolver.requireCurrentUser());
        assertSame(authenticatedUser, resolver.requireCurrentUser());
        assertEquals(10L, resolver.requireCurrentAccountId());
        assertEquals(20L, resolver.requireCurrentPersonId());
        assertEquals("34999999999", resolver.requireCurrentUsername());
        assertSame(authentication, SecurityContextHolder.getContext().getAuthentication());
        assertNull(SecurityContextHolder.getContext().getAuthentication().getCredentials());
        assertTrue(Arrays.stream(AuthenticatedUserResolver.class.getDeclaredFields())
                .noneMatch(field -> field.getType().getSimpleName().endsWith("Repository")));
    }

    @Test
    void authenticatedUserShouldNotDeclareSensitiveCredentialFields() {
        Set<String> fieldNames = Arrays.stream(AuthenticatedUser.class.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertFalse(fieldNames.contains("password"));
        assertFalse(fieldNames.contains("passwordHash"));
        assertFalse(fieldNames.contains("credentials"));
    }

    @Test
    void shouldRejectAnonymousContext() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        assertThrows(AuthenticationCredentialsNotFoundException.class, resolver::requireCurrentUser);
    }

    @Test
    void shouldRejectMissingAuthentication() {
        SecurityContextHolder.clearContext();

        assertThrows(AuthenticationCredentialsNotFoundException.class, resolver::requireCurrentUser);
    }

    @Test
    void shouldRejectLegacyUserDetailsPrincipal() {
        User legacyPrincipal = new User("34999999999", "hash", List.of(new SimpleGrantedAuthority("ROLE_OPERATOR")));
        setAuthentication(new UsernamePasswordAuthenticationToken(legacyPrincipal, null, legacyPrincipal.getAuthorities()));

        assertThrows(AuthenticationCredentialsNotFoundException.class, resolver::requireCurrentUser);
    }

    private void setAuthentication(UsernamePasswordAuthenticationToken authentication) {
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
