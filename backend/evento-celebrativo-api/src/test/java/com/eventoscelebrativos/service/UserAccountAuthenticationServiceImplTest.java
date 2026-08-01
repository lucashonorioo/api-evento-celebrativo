package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.model.UserAccount;
import com.eventoscelebrativos.model.UserAccountRole;
import com.eventoscelebrativos.repository.UserAccountRepository;
import com.eventoscelebrativos.security.AuthenticatedUser;
import com.eventoscelebrativos.service.impl.UserAccountAuthenticationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.security.core.GrantedAuthority;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountAuthenticationServiceImplTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    private UserAccountAuthenticationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserAccountAuthenticationServiceImpl(userAccountRepository);
    }

    @Test
    void shouldLoadCredentialsWithAccountAndPersonState() {
        UserAccount account = account(1L, person(2L, true), "34999999999", "hash", true, Set.of(role(3L, "ROLE_OPERATOR")));
        when(userAccountRepository.findByUsernameForAuthentication("34999999999")).thenReturn(Optional.of(account));

        UserAccountCredentials credentials = service.loadCredentialsByUsername("34999999999").orElseThrow();

        assertEquals(1L, credentials.accountId());
        assertEquals(2L, credentials.personId());
        assertEquals("34999999999", credentials.username());
        assertEquals("hash", credentials.passwordHash());
        assertTrue(credentials.accountEnabled());
        assertTrue(credentials.personActive());
        assertEquals(Set.of("ROLE_OPERATOR"), authorityNames(credentials.authorities()));
    }

    @Test
    void shouldReportDisabledAccountAndInactivePersonInCredentials() {
        UserAccount account = account(1L, person(2L, false), "34999999999", "hash", false, Set.of(role(3L, "ROLE_OPERATOR")));
        when(userAccountRepository.findByUsernameForAuthentication("34999999999")).thenReturn(Optional.of(account));

        UserAccountCredentials credentials = service.loadCredentialsByUsername("34999999999").orElseThrow();

        assertFalse(credentials.accountEnabled());
        assertFalse(credentials.personActive());
    }

    @Test
    void shouldReturnEmptyCredentialsWhenUsernameDoesNotExist() {
        when(userAccountRepository.findByUsernameForAuthentication("34999999999")).thenReturn(Optional.empty());

        assertTrue(service.loadCredentialsByUsername("34999999999").isEmpty());
    }

    @Test
    void shouldReturnEmptyCredentialsWhenAccountHasNoRolesWithoutConsultingLegacyPersonRoles() {
        Person person = spy(person(2L, true));
        person.addRole(role(4L, "ROLE_ADMIN"));
        UserAccount account = account(1L, person, "34999999999", "hash", true, Set.of());
        when(userAccountRepository.findByUsernameForAuthentication("34999999999")).thenReturn(Optional.of(account));

        assertTrue(service.loadCredentialsByUsername("34999999999").isEmpty());
        verify(person, never()).getRoles();
        verify(person, never()).getAuthorities();
    }

    @Test
    void shouldReturnEmptyCredentialsForBlankOrNullUsername() {
        assertTrue(service.loadCredentialsByUsername(null).isEmpty());
        assertTrue(service.loadCredentialsByUsername("  ").isEmpty());
    }

    @Test
    void shouldLoadCurrentUserWhenAccountEnabledAndPersonActive() {
        UserAccount account = account(1L, person(2L, true), "34999999999", "hash", true, Set.of(role(3L, "ROLE_ADMIN")));
        when(userAccountRepository.findByIdForAuthentication(1L)).thenReturn(Optional.of(account));

        AuthenticatedUser user = service.loadCurrentUser(1L).orElseThrow();

        assertEquals(1L, user.accountId());
        assertEquals(2L, user.personId());
        assertEquals("34999999999", user.username());
        assertEquals(Set.of("ROLE_ADMIN"), authorityNames(user.authorities()));
    }

    @Test
    void shouldReturnEmptyCurrentUserWhenAccountDisabled() {
        UserAccount account = account(1L, person(2L, true), "34999999999", "hash", false, Set.of());
        when(userAccountRepository.findByIdForAuthentication(1L)).thenReturn(Optional.of(account));

        assertTrue(service.loadCurrentUser(1L).isEmpty());
    }

    @Test
    void shouldReturnEmptyCurrentUserWhenPersonInactive() {
        UserAccount account = account(1L, person(2L, false), "34999999999", "hash", true, Set.of());
        when(userAccountRepository.findByIdForAuthentication(1L)).thenReturn(Optional.of(account));

        assertTrue(service.loadCurrentUser(1L).isEmpty());
    }

    @Test
    void shouldReturnEmptyCurrentUserWhenAccountIdDoesNotExist() {
        when(userAccountRepository.findByIdForAuthentication(99L)).thenReturn(Optional.empty());

        assertTrue(service.loadCurrentUser(99L).isEmpty());
    }

    @Test
    void shouldReturnEmptyCurrentUserWhenAccountHasNoRolesWithoutConsultingLegacyPersonRoles() {
        Person person = spy(person(2L, true));
        person.addRole(role(4L, "ROLE_ADMIN"));
        UserAccount account = account(1L, person, "34999999999", "hash", true, Set.of());
        when(userAccountRepository.findByIdForAuthentication(1L)).thenReturn(Optional.of(account));

        assertTrue(service.loadCurrentUser(1L).isEmpty());
        verify(person, never()).getRoles();
        verify(person, never()).getAuthorities();
    }

    @Test
    void shouldReturnEmptyCurrentUserWhenAccountIdIsNull() {
        assertTrue(service.loadCurrentUser(null).isEmpty());
    }

    private Set<String> authorityNames(Set<? extends GrantedAuthority> authorities) {
        return authorities.stream().map(GrantedAuthority::getAuthority).collect(java.util.stream.Collectors.toSet());
    }

    private Person person(Long id, boolean active) {
        Person person = new Person();
        person.setId(id);
        person.setActive(active);
        return person;
    }

    private Role role(Long id, String authority) {
        return new Role(id, authority);
    }

    private UserAccount account(Long id, Person person, String username, String passwordHash, boolean enabled, Set<Role> roles) {
        LocalDateTime now = LocalDateTime.now();
        UserAccount account = new UserAccount(person, username, passwordHash, now, now);
        setField(account, "id", id);
        if (!enabled) {
            setField(account, "enabled", false);
        }
        Set<UserAccountRole> userAccountRoles = roles.stream()
                .map(role -> new UserAccountRole(account, role))
                .collect(java.util.stream.Collectors.toSet());
        setField(account, "roles", userAccountRoles);
        return account;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
