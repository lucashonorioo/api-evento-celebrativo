package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.AdminPasswordResetRequestDTO;
import com.eventoscelebrativos.dto.request.PersonActiveRequestDTO;
import com.eventoscelebrativos.dto.request.SelfPasswordChangeRequestDTO;
import com.eventoscelebrativos.dto.request.UserAccountCreateRequestDTO;
import com.eventoscelebrativos.dto.request.UserAccountEnabledRequestDTO;
import com.eventoscelebrativos.dto.response.UserAccountLifecycleResponseDTO;
import com.eventoscelebrativos.exception.exceptions.LifecycleConflictException;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.model.UserAccount;
import com.eventoscelebrativos.model.UserAccountRole;
import com.eventoscelebrativos.repository.EventAssignmentRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.repository.RoleRepository;
import com.eventoscelebrativos.repository.UserAccountRepository;
import com.eventoscelebrativos.repository.UserAccountRoleRepository;
import com.eventoscelebrativos.security.AuthenticatedUserResolver;
import com.eventoscelebrativos.service.impl.UserAccountLifecycleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre exclusivamente o ciclo de vida pos-provisionamento (habilitar/desabilitar conta, ativar/
 * desativar pessoa, redefinir/alterar senha, atualizar role, criar conta para pessoa existente).
 * O provisionamento na criacao (antigo applyCreationAccess/applyMinisterialUpdateAccess) foi
 * substituido por {@link com.eventoscelebrativos.service.impl.PersonAccountCoordinatorImpl},
 * coberto em PersonAccountCoordinatorImplTest.
 */
@ExtendWith(MockitoExtension.class)
class UserAccountLifecycleServiceImplTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-08-01T12:00:00.900Z"), ZoneOffset.UTC);
    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);
    private static final LocalDateTime CURRENT_SECOND = LocalDateTime.of(2026, 8, 1, 12, 0, 0);

    @Mock
    private PersonRepository personRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private UserAccountRoleRepository userAccountRoleRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private EventAssignmentRepository eventAssignmentRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticatedUserResolver authenticatedUserResolver;

    private UserAccountLifecycleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserAccountLifecycleServiceImpl(
                personRepository,
                userAccountRepository,
                userAccountRoleRepository,
                roleRepository,
                eventAssignmentRepository,
                new PasswordPolicy(),
                passwordEncoder,
                authenticatedUserResolver,
                FIXED_CLOCK
        );
    }

    @Test
    void shouldCreateAccountForActivePersonWithDefaultRoleAndTokenVersionZero() {
        Person person = person(1L, "34999999991", true);
        Role operator = role(1L, "ROLE_OPERATOR");
        UserAccountCreateRequestDTO request = accountCreateRequest("123456", null);
        when(roleRepository.findByAuthority("ROLE_OPERATOR")).thenReturn(Optional.of(operator));
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person));
        when(userAccountRepository.findByPersonIdForUpdate(1L)).thenReturn(Optional.empty());
        when(userAccountRepository.findByUsernameForUpdate("34999999991")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("123456")).thenReturn("encoded-password");
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount account = invocation.getArgument(0);
            setField(account, "id", 10L);
            return account;
        });
        when(userAccountRoleRepository.findByUserAccountId(10L)).thenReturn(List.of());

        UserAccountLifecycleResponseDTO response = service.createAccount(1L, request);

        assertEquals(1L, response.getPersonId());
        assertTrue(response.isAccountExists());
        assertEquals(List.of("ROLE_OPERATOR"), response.getRoles());

        ArgumentCaptor<UserAccount> accountCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository).save(accountCaptor.capture());
        UserAccount account = accountCaptor.getValue();
        assertEquals("34999999991", account.getUsername());
        assertEquals("encoded-password", account.getPasswordHash());
        assertTrue(account.isEnabled());
        assertEquals(0L, account.getTokenVersion());
        assertEquals(CURRENT_SECOND, account.getCreatedAt());
        assertEquals(CURRENT_SECOND, account.getUpdatedAt());
        verify(passwordEncoder, times(1)).encode("123456");
        verify(personRepository, never()).save(any());
    }

    @Test
    void shouldRejectCreateAccountForInactivePerson() {
        Person inactive = person(1L, "34999999991", false);
        Role operator = role(1L, "ROLE_OPERATOR");
        when(roleRepository.findByAuthority("ROLE_OPERATOR")).thenReturn(Optional.of(operator));
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(inactive));

        LifecycleConflictException exception = assertThrows(
                LifecycleConflictException.class,
                () -> service.createAccount(1L, accountCreateRequest("123456", "ROLE_OPERATOR"))
        );

        assertEquals("PERSON_INACTIVE", exception.getErrorCode());
        verify(passwordEncoder, never()).encode(any());
        verify(userAccountRepository, never()).save(any());
    }

    @Test
    void shouldDisableAccountIncrementingVersionOnlyOnEnabledToDisabledTransition() {
        Person person = person(1L, "34999999991", true);
        UserAccount account = account(10L, person, "34999999991", "hash", true, Set.of(role(1L, "ROLE_OPERATOR")));
        when(roleRepository.findByAuthorityForUpdate("ROLE_ADMIN")).thenReturn(Optional.of(role(2L, "ROLE_ADMIN")));
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person));
        when(userAccountRepository.findByPersonIdForUpdate(1L)).thenReturn(Optional.of(account));
        when(authenticatedUserResolver.requireCurrentAccountId()).thenReturn(99L);
        UserAccountEnabledRequestDTO request = enabledRequest(false);

        service.updateAccountEnabled(1L, request);
        service.updateAccountEnabled(1L, request);

        assertFalse(account.isEnabled());
        assertEquals(1L, account.getTokenVersion());
        assertTrue(person.isActive());
        verify(userAccountRepository, times(1)).save(account);
    }

    @Test
    void shouldBlockSelfAccountDisableBeforeChangingState() {
        Person person = person(1L, "34999999991", true);
        UserAccount account = account(10L, person, "34999999991", "hash", true, Set.of(role(2L, "ROLE_ADMIN")));
        when(roleRepository.findByAuthorityForUpdate("ROLE_ADMIN")).thenReturn(Optional.of(role(2L, "ROLE_ADMIN")));
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person));
        when(userAccountRepository.findByPersonIdForUpdate(1L)).thenReturn(Optional.of(account));
        when(authenticatedUserResolver.requireCurrentAccountId()).thenReturn(10L);

        LifecycleConflictException exception = assertThrows(
                LifecycleConflictException.class,
                () -> service.updateAccountEnabled(1L, enabledRequest(false))
        );

        assertEquals("SELF_ACCOUNT_DISABLE_NOT_ALLOWED", exception.getErrorCode());
        assertTrue(account.isEnabled());
        assertEquals(0L, account.getTokenVersion());
        verify(userAccountRepository, never()).save(any());
    }

    @Test
    void shouldEnableAccountWithoutIncrementingVersion() {
        Person person = person(1L, "34999999991", true);
        UserAccount account = account(10L, person, "34999999991", "hash", false, Set.of(role(1L, "ROLE_OPERATOR")));
        setField(account, "tokenVersion", 3L);
        when(roleRepository.findByAuthorityForUpdate("ROLE_ADMIN")).thenReturn(Optional.of(role(2L, "ROLE_ADMIN")));
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person));
        when(userAccountRepository.findByPersonIdForUpdate(1L)).thenReturn(Optional.of(account));

        service.updateAccountEnabled(1L, enabledRequest(true));

        assertTrue(account.isEnabled());
        assertEquals(3L, account.getTokenVersion());
        verify(userAccountRepository).save(account);
    }

    @Test
    void shouldRejectEnablingAccountWithoutRoles() {
        Person person = person(1L, "34999999991", true);
        UserAccount account = account(10L, person, "34999999991", "hash", false, Set.of());
        when(roleRepository.findByAuthorityForUpdate("ROLE_ADMIN")).thenReturn(Optional.of(role(2L, "ROLE_ADMIN")));
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person));
        when(userAccountRepository.findByPersonIdForUpdate(1L)).thenReturn(Optional.of(account));

        LifecycleConflictException exception = assertThrows(
                LifecycleConflictException.class,
                () -> service.updateAccountEnabled(1L, enabledRequest(true))
        );

        assertEquals("USER_ACCOUNT_ROLE_REQUIRED", exception.getErrorCode());
        verify(userAccountRepository, never()).save(account);
    }

    @Test
    void shouldDeactivatePersonWithAccountIncrementingTokenVersionAndPreservingAccountEnabled() {
        Person person = person(1L, "34999999991", true);
        UserAccount account = account(10L, person, "34999999991", "hash", true, Set.of(role(1L, "ROLE_OPERATOR")));
        when(roleRepository.findByAuthorityForUpdate("ROLE_ADMIN")).thenReturn(Optional.of(role(2L, "ROLE_ADMIN")));
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person));
        when(userAccountRepository.findByPersonIdForUpdate(1L)).thenReturn(Optional.of(account));
        when(authenticatedUserResolver.requireCurrentPersonId()).thenReturn(99L);
        when(eventAssignmentRepository.existsActiveOrFutureAssignmentByPersonId(1L, CURRENT_SECOND)).thenReturn(false);

        service.updatePersonActive(1L, activeRequest(false));

        assertFalse(person.isActive());
        assertTrue(account.isEnabled());
        assertEquals(1L, account.getTokenVersion());
        assertEquals(CURRENT_SECOND, account.getUpdatedAt());
        verify(personRepository).save(person);
        verify(userAccountRepository).save(account);
    }

    @Test
    void shouldBlockSelfPersonDeactivationBeforeChangingState() {
        Person person = person(1L, "34999999991", true);
        UserAccount account = account(10L, person, "34999999991", "hash", true, Set.of(role(2L, "ROLE_ADMIN")));
        when(roleRepository.findByAuthorityForUpdate("ROLE_ADMIN")).thenReturn(Optional.of(role(2L, "ROLE_ADMIN")));
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person));
        when(userAccountRepository.findByPersonIdForUpdate(1L)).thenReturn(Optional.of(account));
        when(authenticatedUserResolver.requireCurrentPersonId()).thenReturn(1L);

        LifecycleConflictException exception = assertThrows(
                LifecycleConflictException.class,
                () -> service.updatePersonActive(1L, activeRequest(false))
        );

        assertEquals("SELF_PERSON_DEACTIVATION_NOT_ALLOWED", exception.getErrorCode());
        assertTrue(person.isActive());
        assertEquals(0L, account.getTokenVersion());
        verify(eventAssignmentRepository, never()).existsActiveOrFutureAssignmentByPersonId(1L, CURRENT_SECOND);
        verify(personRepository, never()).save(any());
        verify(userAccountRepository, never()).save(any());
    }

    @Test
    void shouldKeepTokenVersionWhenDeactivationRequestIsIdempotentForAlreadyInactivePerson() {
        Person person = person(1L, "34999999991", false);
        UserAccount account = account(10L, person, "34999999991", "hash", true, Set.of(role(1L, "ROLE_OPERATOR")));
        setField(account, "tokenVersion", 4L);
        when(roleRepository.findByAuthorityForUpdate("ROLE_ADMIN")).thenReturn(Optional.of(role(2L, "ROLE_ADMIN")));
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person));
        when(userAccountRepository.findByPersonIdForUpdate(1L)).thenReturn(Optional.of(account));
        when(authenticatedUserResolver.requireCurrentPersonId()).thenReturn(99L);

        service.updatePersonActive(1L, activeRequest(false));

        assertFalse(person.isActive());
        assertEquals(4L, account.getTokenVersion());
        verify(eventAssignmentRepository, never()).existsActiveOrFutureAssignmentByPersonId(1L, CURRENT_SECOND);
        verify(personRepository, never()).save(any());
        verify(userAccountRepository, never()).save(any());
    }

    @Test
    void shouldReactivatePersonWithoutIncrementingTokenVersionOrChangingAccountEnabled() {
        Person person = person(1L, "34999999991", false);
        UserAccount account = account(10L, person, "34999999991", "hash", false, Set.of(role(1L, "ROLE_OPERATOR")));
        setField(account, "tokenVersion", 5L);
        when(roleRepository.findByAuthorityForUpdate("ROLE_ADMIN")).thenReturn(Optional.of(role(2L, "ROLE_ADMIN")));
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person));
        when(userAccountRepository.findByPersonIdForUpdate(1L)).thenReturn(Optional.of(account));

        service.updatePersonActive(1L, activeRequest(true));

        assertTrue(person.isActive());
        assertFalse(account.isEnabled());
        assertEquals(5L, account.getTokenVersion());
        verify(eventAssignmentRepository, never()).existsActiveOrFutureAssignmentByPersonId(1L, CURRENT_SECOND);
        verify(personRepository).save(person);
        verify(userAccountRepository, never()).save(any());
    }

    @Test
    void shouldRejectPersonDeactivationWhenActiveOrFutureAssignmentExists() {
        Person person = person(1L, "34999999991", true);
        UserAccount account = account(10L, person, "34999999991", "hash", true, Set.of(role(1L, "ROLE_OPERATOR")));
        when(roleRepository.findByAuthorityForUpdate("ROLE_ADMIN")).thenReturn(Optional.of(role(2L, "ROLE_ADMIN")));
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person));
        when(userAccountRepository.findByPersonIdForUpdate(1L)).thenReturn(Optional.of(account));
        when(authenticatedUserResolver.requireCurrentPersonId()).thenReturn(99L);
        when(eventAssignmentRepository.existsActiveOrFutureAssignmentByPersonId(1L, CURRENT_SECOND)).thenReturn(true);

        LifecycleConflictException exception = assertThrows(
                LifecycleConflictException.class,
                () -> service.updatePersonActive(1L, activeRequest(false))
        );

        assertEquals("PERSON_HAS_ACTIVE_ASSIGNMENTS", exception.getErrorCode());
        assertTrue(person.isActive());
        assertEquals(0L, account.getTokenVersion());
        verify(personRepository, never()).save(any());
        verify(userAccountRepository, never()).save(any());
    }

    @Test
    void shouldResetPasswordOnlyOnUserAccountAndIncrementVersion() {
        Person person = person(1L, "34999999991", false);
        UserAccount account = account(10L, person, "34999999991", "old-account-hash", false, Set.of(role(1L, "ROLE_OPERATOR")));
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person));
        when(userAccountRepository.findByPersonIdForUpdate(1L)).thenReturn(Optional.of(account));
        when(authenticatedUserResolver.requireCurrentAccountId()).thenReturn(99L);
        when(passwordEncoder.encode("123456")).thenReturn("new-hash");

        service.resetPassword(1L, adminPasswordRequest("123456"));

        assertEquals("new-hash", account.getPasswordHash());
        assertEquals(1L, account.getTokenVersion());
        verify(passwordEncoder, times(1)).encode("123456");
        verify(personRepository, never()).save(any());
        verify(userAccountRepository).save(account);
    }

    @Test
    void shouldChangeOwnPasswordValidatingCurrentPasswordOnlyAgainstUserAccountHash() {
        Person person = person(1L, "34999999991", true);
        UserAccount account = account(10L, person, "34999999991", "current-account-hash", true, Set.of(role(1L, "ROLE_OPERATOR")));
        when(authenticatedUserResolver.requireCurrentPersonId()).thenReturn(1L);
        when(authenticatedUserResolver.requireCurrentAccountId()).thenReturn(10L);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person));
        when(userAccountRepository.findByPersonIdForUpdate(1L)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("123456", "current-account-hash")).thenReturn(true);
        when(passwordEncoder.encode("654321")).thenReturn("new-hash");

        service.changeOwnPassword(selfPasswordRequest("123456", "654321"));

        assertEquals("new-hash", account.getPasswordHash());
        assertEquals(1L, account.getTokenVersion());
        verify(passwordEncoder, times(1)).matches("123456", "current-account-hash");
        verify(passwordEncoder, times(1)).encode("654321");
        verify(personRepository, never()).save(any());
    }

    @Test
    void shouldRejectWrongCurrentPasswordWithoutEncodingNewPassword() {
        Person person = person(1L, "34999999991", true);
        UserAccount account = account(10L, person, "34999999991", "current-account-hash", true, Set.of(role(1L, "ROLE_OPERATOR")));
        when(authenticatedUserResolver.requireCurrentPersonId()).thenReturn(1L);
        when(authenticatedUserResolver.requireCurrentAccountId()).thenReturn(10L);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person));
        when(userAccountRepository.findByPersonIdForUpdate(1L)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("wrong1", "current-account-hash")).thenReturn(false);

        LifecycleConflictException exception = assertThrows(
                LifecycleConflictException.class,
                () -> service.changeOwnPassword(selfPasswordRequest("wrong1", "654321"))
        );

        assertEquals("CURRENT_PASSWORD_INVALID", exception.getErrorCode());
        verify(passwordEncoder, never()).encode("654321");
        assertEquals(0L, account.getTokenVersion());
    }

    @Test
    void shouldUpdateRoleUsingAccountRolesForLastAdminAndNotIncrementTokenVersion() {
        Person person = person(1L, "34999999991", true);
        Role admin = role(2L, "ROLE_ADMIN");
        Role operator = role(1L, "ROLE_OPERATOR");
        UserAccount account = account(10L, person, "34999999991", "hash", true, Set.of(admin));
        UserAccountRole currentAdminRole = new UserAccountRole(account, admin);
        when(roleRepository.findByAuthorityForUpdate("ROLE_ADMIN")).thenReturn(Optional.of(admin));
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person));
        when(userAccountRepository.findByPersonIdForUpdate(1L)).thenReturn(Optional.of(account));
        when(authenticatedUserResolver.requireCurrentPersonId()).thenReturn(99L);
        when(userAccountRoleRepository.countEffectiveAdministrators()).thenReturn(2L);
        when(roleRepository.findByAuthority("ROLE_OPERATOR")).thenReturn(Optional.of(operator));
        when(userAccountRoleRepository.findByUserAccountId(10L)).thenReturn(List.of(currentAdminRole));

        Person updated = service.updateRole(1L, "ROLE_OPERATOR");

        assertEquals(person, updated);
        assertEquals(0L, account.getTokenVersion());
        verify(userAccountRoleRepository).deleteAllByUserAccountId(10L);
        verify(userAccountRoleRepository).save(any(UserAccountRole.class));
        verify(userAccountRepository).save(account);
        verify(personRepository, never()).save(any());
    }

    @Test
    void shouldBlockLastEffectiveAdministratorBasedOnUserAccountRoles() {
        Person person = person(1L, "34999999991", true);
        Role admin = role(2L, "ROLE_ADMIN");
        UserAccount account = account(10L, person, "34999999991", "hash", true, Set.of(admin));
        when(roleRepository.findByAuthorityForUpdate("ROLE_ADMIN")).thenReturn(Optional.of(admin));
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person));
        when(userAccountRepository.findByPersonIdForUpdate(1L)).thenReturn(Optional.of(account));
        when(authenticatedUserResolver.requireCurrentPersonId()).thenReturn(99L);
        when(userAccountRoleRepository.countEffectiveAdministrators()).thenReturn(1L);

        LifecycleConflictException exception = assertThrows(
                LifecycleConflictException.class,
                () -> service.updateRole(1L, "ROLE_OPERATOR")
        );

        assertEquals("LAST_ACTIVE_ADMIN_REQUIRED", exception.getErrorCode());
        verify(userAccountRepository, never()).save(any());
    }

    private UserAccountCreateRequestDTO accountCreateRequest(String password, String role) {
        UserAccountCreateRequestDTO request = new UserAccountCreateRequestDTO();
        request.setInitialPassword(password);
        request.setRole(role);
        return request;
    }

    private UserAccountEnabledRequestDTO enabledRequest(boolean enabled) {
        UserAccountEnabledRequestDTO request = new UserAccountEnabledRequestDTO();
        request.setEnabled(enabled);
        return request;
    }

    private PersonActiveRequestDTO activeRequest(boolean active) {
        PersonActiveRequestDTO request = new PersonActiveRequestDTO();
        request.setActive(active);
        return request;
    }

    private AdminPasswordResetRequestDTO adminPasswordRequest(String password) {
        AdminPasswordResetRequestDTO request = new AdminPasswordResetRequestDTO();
        request.setNewPassword(password);
        return request;
    }

    private SelfPasswordChangeRequestDTO selfPasswordRequest(String currentPassword, String newPassword) {
        SelfPasswordChangeRequestDTO request = new SelfPasswordChangeRequestDTO();
        request.setCurrentPassword(currentPassword);
        request.setNewPassword(newPassword);
        return request;
    }

    private Person person(Long id, String phoneNumber, boolean active) {
        Person person = new Person();
        person.setId(id);
        person.setName("Person " + id);
        person.setPhoneNumber(phoneNumber);
        person.setBirthdayDate(BIRTHDAY);
        person.setActive(active);
        return person;
    }

    private UserAccount account(Long id, Person person, String username, String hash, boolean enabled, Set<Role> roles) {
        UserAccount account = new UserAccount(person, username, hash, CURRENT_SECOND, CURRENT_SECOND);
        setField(account, "id", id);
        setField(account, "enabled", enabled);
        Set<UserAccountRole> userAccountRoles = roles.stream()
                .map(role -> new UserAccountRole(account, role))
                .collect(java.util.stream.Collectors.toSet());
        setField(account, "roles", userAccountRoles);
        return account;
    }

    private Role role(Long id, String authority) {
        return new Role(id, authority);
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
