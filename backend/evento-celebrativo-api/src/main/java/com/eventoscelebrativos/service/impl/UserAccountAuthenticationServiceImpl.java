package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.model.UserAccount;
import com.eventoscelebrativos.repository.UserAccountRepository;
import com.eventoscelebrativos.security.AuthenticatedUser;
import com.eventoscelebrativos.service.UserAccountAuthenticationService;
import com.eventoscelebrativos.service.UserAccountCredentials;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserAccountAuthenticationServiceImpl implements UserAccountAuthenticationService {

    private static final Set<String> VALID_AUTHORITIES = Set.of("ROLE_ADMIN", "ROLE_OPERATOR");

    private final UserAccountRepository userAccountRepository;

    public UserAccountAuthenticationServiceImpl(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAccountCredentials> loadCredentialsByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return userAccountRepository.findByUsernameForAuthentication(username)
                .flatMap(this::credentialsOf);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AuthenticatedUser> loadCurrentUser(Long accountId) {
        if (accountId == null) {
            return Optional.empty();
        }
        return userAccountRepository.findByIdForAuthentication(accountId)
                .filter(UserAccount::isEnabled)
                .filter(account -> account.getPerson().isActive())
                .flatMap(this::authenticatedUserOf);
    }

    private Optional<UserAccountCredentials> credentialsOf(UserAccount account) {
        if (!hasValidRole(account)) {
            return Optional.empty();
        }
        Set<GrantedAuthority> authorities = authoritiesOf(account);
        if (authorities.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new UserAccountCredentials(
                account.getId(),
                account.getPerson().getId(),
                account.getUsername(),
                account.getPasswordHash(),
                account.getTokenVersion(),
                account.isEnabled(),
                account.getPerson().isActive(),
                authorities
        ));
    }

    private Optional<AuthenticatedUser> authenticatedUserOf(UserAccount account) {
        if (!hasValidRole(account)) {
            return Optional.empty();
        }
        Set<GrantedAuthority> authorities = authoritiesOf(account);
        if (authorities.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new AuthenticatedUser(
                account.getId(),
                account.getPerson().getId(),
                account.getUsername(),
                account.getTokenVersion(),
                authorities
        ));
    }

    private Set<GrantedAuthority> authoritiesOf(UserAccount account) {
        return account.getRoles().stream()
                .map(userAccountRole -> (GrantedAuthority) new SimpleGrantedAuthority(userAccountRole.getRole().getAuthority()))
                .collect(Collectors.toUnmodifiableSet());
    }

    // Conta com zero ou mais de uma role e um estado inconsistente que nao deve autenticar, mesmo
    // que a colecao de authorities resultante nao fique vazia (caso de duas roles distintas). Uma
    // unica role cuja authority nao seja ROLE_ADMIN/ROLE_OPERATOR tambem e invalida (dado corrompido
    // ou role desconhecida nunca deve autenticar).
    private boolean hasValidRole(UserAccount account) {
        return account.getRoles().size() == 1
                && VALID_AUTHORITIES.contains(account.getRoles().iterator().next().getRole().getAuthority());
    }
}
