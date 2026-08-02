package com.eventoscelebrativos.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.io.Serializable;
import java.security.Principal;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Principal autenticado colocado no {@code SecurityContext} apos o corte de autenticacao para
 * UserAccount. Imutavel, sem passwordHash e sem referencia a entidade JPA (nunca serializa proxy
 * Hibernate). {@link #getName()} retorna o username atual (telefone) para preservar o claim
 * {@code sub} do JWT sem depender de {@link org.springframework.security.core.userdetails.UserDetails}.
 */
public record AuthenticatedUser(
        Long accountId,
        Long personId,
        String username,
        Long tokenVersion,
        Set<GrantedAuthority> authorities
) implements Principal, Serializable {

    public AuthenticatedUser {
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
    public String getName() {
        return username;
    }

    @Override
    public String toString() {
        return "AuthenticatedUser[accountId=%s, personId=%s, username=%s, tokenVersion=%s, authorities=%s]"
                .formatted(accountId, personId, maskedUsername(), tokenVersion, authorities);
    }

    private String maskedUsername() {
        if (username == null || username.length() <= 4) {
            return "***";
        }
        return "***" + username.substring(username.length() - 4);
    }
}
