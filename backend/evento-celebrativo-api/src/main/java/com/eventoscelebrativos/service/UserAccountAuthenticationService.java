package com.eventoscelebrativos.service;

import com.eventoscelebrativos.security.AuthenticatedUser;

import java.util.Optional;

/**
 * Fonte oficial e exclusiva de autenticacao apos o corte para UserAccount. Nunca consulta
 * {@code Person.password} ou {@code Person.roles}; nunca recorre a {@code PersonDetailsServiceImpl}.
 */
public interface UserAccountAuthenticationService {

    /**
     * Carrega as credenciais para validacao de login. Vazio quando o username nao corresponde a
     * nenhuma conta.
     */
    Optional<UserAccountCredentials> loadCredentialsByUsername(String username);

    /**
     * Carrega o estado atual da conta para validacao de bearer token. Vazio quando a conta nao
     * existe, {@code UserAccount.enabled} for falso ou {@code Person.active} for falso.
     */
    Optional<AuthenticatedUser> loadCurrentUser(Long accountId);
}
