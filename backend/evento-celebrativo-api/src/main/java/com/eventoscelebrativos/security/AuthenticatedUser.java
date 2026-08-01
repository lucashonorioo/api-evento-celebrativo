package com.eventoscelebrativos.security;

import org.springframework.security.core.GrantedAuthority;

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
        Set<GrantedAuthority> authorities
) implements Principal, Serializable {

    public AuthenticatedUser {
        authorities = authorities == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(authorities));
    }

    @Override
    public String getName() {
        return username;
    }
}
