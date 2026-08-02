package com.eventoscelebrativos.service;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Estado de credenciais de uma {@link com.eventoscelebrativos.model.UserAccount} carregado durante
 * o login. Existe apenas durante a autenticacao; nunca e colocado no {@code SecurityContext} (ao
 * contrario de {@link com.eventoscelebrativos.security.AuthenticatedUser}, que nao contem hash).
 */
public record UserAccountCredentials(
        Long accountId,
        Long personId,
        String username,
        String passwordHash,
        Long tokenVersion,
        boolean accountEnabled,
        boolean personActive,
        Set<GrantedAuthority> authorities
) {

    public UserAccountCredentials {
        tokenVersion = tokenVersion == null ? 0L : tokenVersion;
        authorities = authorities == null
                ? Collections.emptySet()
                : authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .map(SimpleGrantedAuthority::new)
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                        Collections::unmodifiableSet
                ));
    }

    @Override
    public String toString() {
        return "UserAccountCredentials[accountId=%s, personId=%s, username=%s, tokenVersion=%s, accountEnabled=%s, personActive=%s, authorities=%s]"
                .formatted(accountId, personId, maskedUsername(), tokenVersion, accountEnabled, personActive, authorities);
    }

    private String maskedUsername() {
        if (username == null || username.length() <= 4) {
            return "***";
        }
        return "***" + username.substring(username.length() - 4);
    }
}
