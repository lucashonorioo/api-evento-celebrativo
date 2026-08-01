package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.model.UserAccount;
import com.eventoscelebrativos.repository.UserAccountRepository;
import com.eventoscelebrativos.security.AuthenticatedUser;
import com.eventoscelebrativos.service.UserAccountAuthenticationService;
import com.eventoscelebrativos.service.UserAccountCredentials;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserAccountAuthenticationServiceImpl implements UserAccountAuthenticationService {

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
        Set<GrantedAuthority> authorities = authoritiesOf(account);
        if (authorities.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new UserAccountCredentials(
                account.getId(),
                account.getPerson().getId(),
                account.getUsername(),
                account.getPasswordHash(),
                account.isEnabled(),
                account.getPerson().isActive(),
                authorities
        ));
    }

    private Optional<AuthenticatedUser> authenticatedUserOf(UserAccount account) {
        Set<GrantedAuthority> authorities = authoritiesOf(account);
        if (authorities.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new AuthenticatedUser(
                account.getId(),
                account.getPerson().getId(),
                account.getUsername(),
                authorities
        ));
    }

    private Set<GrantedAuthority> authoritiesOf(UserAccount account) {
        return account.getRoles().stream()
                .map(userAccountRole -> (GrantedAuthority) userAccountRole.getRole())
                .collect(Collectors.toUnmodifiableSet());
    }
}
