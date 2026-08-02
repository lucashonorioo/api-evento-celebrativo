package com.eventoscelebrativos.service;

import com.eventoscelebrativos.exception.exceptions.UserAccountConsistencyException;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.model.UserAccount;
import com.eventoscelebrativos.model.UserAccountRole;
import com.eventoscelebrativos.repository.UserAccountRepository;
import com.eventoscelebrativos.repository.UserAccountRoleRepository;
import com.eventoscelebrativos.service.impl.UserAccountSynchronizationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountSynchronizationServiceImplTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-08-01T12:00:00.123456Z"), ZoneOffset.UTC);

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private UserAccountRoleRepository userAccountRoleRepository;

    private UserAccountSynchronizationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserAccountSynchronizationServiceImpl(userAccountRepository, userAccountRoleRepository, FIXED_CLOCK);
    }

    @Test
    void shouldCreateAccountWithSecondPrecisionTimestampsAndCopiedRoles() {
        Person person = person(1L, "34999999991", "encoded-hash", Set.of(role(1L, "ROLE_OPERATOR")));
        when(userAccountRepository.existsByPersonId(1L)).thenReturn(false);
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.synchronizeNewPerson(person);

        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository).save(captor.capture());
        UserAccount created = captor.getValue();
        assertEquals("34999999991", created.getUsername());
        assertEquals("encoded-hash", created.getPasswordHash());
        assertTrue(created.isEnabled());
        assertEquals(LocalDateTime.of(2026, 8, 1, 12, 0, 0), created.getCreatedAt());
        assertEquals(created.getCreatedAt(), created.getUpdatedAt());

        verify(userAccountRoleRepository).saveAll(argThat(iterable -> {
            List<UserAccountRole> list = toList(iterable);
            return list.size() == 1 && list.get(0).getRole().getAuthority().equals("ROLE_OPERATOR");
        }));
    }

    @Test
    void shouldFailWhenCreatingAccountForPersonThatAlreadyHasOne() {
        Person person = person(1L, "34999999991", "encoded-hash", Set.of());
        when(userAccountRepository.existsByPersonId(1L)).thenReturn(true);

        assertThrows(UserAccountConsistencyException.class, () -> service.synchronizeNewPerson(person));
        verify(userAccountRepository, never()).save(any());
    }

    @Test
    void shouldFailWhenSynchronizingExistingPersonWithoutAccount() {
        Person person = person(1L, "34999999991", "encoded-hash", Set.of());
        when(userAccountRepository.findByPersonIdForUpdate(1L)).thenReturn(Optional.empty());

        assertThrows(UserAccountConsistencyException.class, () -> service.synchronizeExistingPerson(person));
        verify(userAccountRepository, never()).save(any());
    }

    @Test
    void shouldSyncUsernamePasswordPreserveIdCreatedAtAndDisabledEnabledFlag() {
        Person person = person(1L, "34999999992", "new-hash", Set.of(role(1L, "ROLE_OPERATOR")));
        UserAccount existing = existingAccount(person, 10L, "34999999991", "old-hash", false,
                LocalDateTime.of(2020, 1, 1, 0, 0));
        when(userAccountRepository.findByPersonIdForUpdate(1L)).thenReturn(Optional.of(existing));
        when(userAccountRepository.save(existing)).thenReturn(existing);
        when(userAccountRoleRepository.findByUserAccountId(10L)).thenReturn(List.of());

        service.synchronizeExistingPerson(person);

        assertEquals("34999999992", existing.getUsername());
        assertEquals("new-hash", existing.getPasswordHash());
        assertEquals(10L, existing.getId());
        assertEquals(LocalDateTime.of(2020, 1, 1, 0, 0), existing.getCreatedAt());
        assertEquals(LocalDateTime.of(2026, 8, 1, 12, 0, 0), existing.getUpdatedAt());
        assertFalse(existing.isEnabled());
    }

    @Test
    void shouldPreserveCreatedAtAndAdvanceUpdatedAtAcrossCreationThenSynchronizationWithControlledClock() {
        Clock creationClock = Clock.fixed(Instant.parse("2026-08-10T10:00:00Z"), ZoneOffset.UTC);
        Clock syncClock = Clock.fixed(Instant.parse("2026-08-10T10:00:01Z"), ZoneOffset.UTC);
        UserAccountSynchronizationServiceImpl creationService =
                new UserAccountSynchronizationServiceImpl(userAccountRepository, userAccountRoleRepository, creationClock);
        UserAccountSynchronizationServiceImpl syncService =
                new UserAccountSynchronizationServiceImpl(userAccountRepository, userAccountRoleRepository, syncClock);

        Person person = person(1L, "34999999991", "hash-1", Set.of(role(1L, "ROLE_OPERATOR")));
        when(userAccountRepository.existsByPersonId(1L)).thenReturn(false);
        ArgumentCaptor<UserAccount> createCaptor = ArgumentCaptor.forClass(UserAccount.class);
        when(userAccountRepository.save(createCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        creationService.synchronizeNewPerson(person);
        UserAccount created = createCaptor.getValue();
        setField(created, "id", 10L);

        assertEquals(LocalDateTime.of(2026, 8, 10, 10, 0, 0), created.getCreatedAt());
        assertEquals(created.getCreatedAt(), created.getUpdatedAt());

        person.setPhoneNumber("34999999992");
        person.setPassword("hash-2");
        when(userAccountRepository.findByPersonIdForUpdate(1L)).thenReturn(Optional.of(created));
        when(userAccountRepository.save(created)).thenReturn(created);
        when(userAccountRoleRepository.findByUserAccountId(10L)).thenReturn(List.of());

        syncService.synchronizeExistingPerson(person);

        assertEquals(10L, created.getId());
        assertEquals(LocalDateTime.of(2026, 8, 10, 10, 0, 0), created.getCreatedAt());
        assertEquals(LocalDateTime.of(2026, 8, 10, 10, 0, 1), created.getUpdatedAt());
        assertTrue(created.isEnabled());
    }

    @Test
    void shouldAddAndRemoveRolesToMatchDesiredSetExactly() {
        Person person = person(1L, "34999999991", "hash", Set.of(role(2L, "ROLE_ADMIN")));
        UserAccount existing = existingAccount(person, 10L, "34999999991", "hash", true, LocalDateTime.now());
        when(userAccountRepository.findByPersonIdForUpdate(1L)).thenReturn(Optional.of(existing));
        when(userAccountRepository.save(existing)).thenReturn(existing);
        UserAccountRole staleOperatorRole = new UserAccountRole(existing, role(1L, "ROLE_OPERATOR"));
        when(userAccountRoleRepository.findByUserAccountId(10L)).thenReturn(List.of(staleOperatorRole));

        service.synchronizeExistingPerson(person);

        verify(userAccountRoleRepository).deleteAll(List.of(staleOperatorRole));
        verify(userAccountRoleRepository).saveAll(argThat(iterable -> {
            List<UserAccountRole> list = toList(iterable);
            return list.size() == 1 && list.get(0).getRole().getAuthority().equals("ROLE_ADMIN");
        }));
    }

    @Test
    void shouldNotTouchRolesWhenDesiredSetAlreadyMatchesCurrentSet() {
        Person person = person(1L, "34999999991", "hash", Set.of(role(1L, "ROLE_OPERATOR")));
        UserAccount existing = existingAccount(person, 10L, "34999999991", "hash", true, LocalDateTime.now());
        when(userAccountRepository.findByPersonIdForUpdate(1L)).thenReturn(Optional.of(existing));
        UserAccountRole currentOperatorRole = new UserAccountRole(existing, role(1L, "ROLE_OPERATOR"));
        when(userAccountRoleRepository.findByUserAccountId(10L)).thenReturn(List.of(currentOperatorRole));

        service.synchronizeExistingPerson(person);

        verify(userAccountRoleRepository, never()).deleteAll(any());
        verify(userAccountRoleRepository, never()).saveAll(any());
    }

    private Person person(Long id, String phoneNumber, String password, Set<Role> roles) {
        Person person = new Person();
        person.setId(id);
        person.setName("Person " + id);
        person.setPhoneNumber(phoneNumber);
        person.setPassword(password);
        roles.forEach(person::addRole);
        return person;
    }

    private UserAccount existingAccount(
            Person person, Long id, String username, String passwordHash, boolean enabled, LocalDateTime createdAt
    ) {
        UserAccount account = new UserAccount(person, username, passwordHash, createdAt, createdAt);
        if (!enabled) {
            setField(account, "enabled", false);
        }
        setField(account, "id", id);
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

    private Role role(Long id, String authority) {
        return new Role(id, authority);
    }

    private List<UserAccountRole> toList(Iterable<?> iterable) {
        List<UserAccountRole> result = new ArrayList<>();
        for (Object o : iterable) {
            result.add((UserAccountRole) o);
        }
        return result;
    }
}
