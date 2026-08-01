package com.eventoscelebrativos.service;

import org.springframework.security.core.GrantedAuthority;

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
        boolean accountEnabled,
        boolean personActive,
        Set<GrantedAuthority> authorities
) {

    public UserAccountCredentials {
        authorities = authorities == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(authorities));
    }
}
