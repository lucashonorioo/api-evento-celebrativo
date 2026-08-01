package com.eventoscelebrativos.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Ponto central de resolucao do usuario autenticado. Aceita somente {@link AuthenticatedUser} como
 * principal; nunca consulta o banco novamente (o estado atual ja foi validado por
 * {@link com.eventoscelebrativos.security.UserAccountJwtAuthenticationConverter} na entrada da
 * requisicao) e nunca aceita {@code Person} como fallback.
 */
@Component
public class AuthenticatedUserResolver {

    public AuthenticatedUser requireCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("Usuario nao autenticado");
        }
        if (!(authentication.getPrincipal() instanceof AuthenticatedUser authenticatedUser)) {
            throw new AuthenticationCredentialsNotFoundException("Principal autenticado invalido");
        }
        return authenticatedUser;
    }

    public Long requireCurrentAccountId() {
        return requireCurrentUser().accountId();
    }

    public Long requireCurrentPersonId() {
        return requireCurrentUser().personId();
    }

    public String requireCurrentUsername() {
        return requireCurrentUser().username();
    }
}
