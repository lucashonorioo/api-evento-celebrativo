package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.request.PersonAccessRequest;
import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.exception.exceptions.LifecycleConflictException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.model.UserAccount;
import com.eventoscelebrativos.model.UserAccountRole;
import com.eventoscelebrativos.repository.RoleRepository;
import com.eventoscelebrativos.repository.UserAccountRepository;
import com.eventoscelebrativos.repository.UserAccountRoleRepository;
import com.eventoscelebrativos.service.PasswordPolicy;
import com.eventoscelebrativos.service.PersonAccessProvisioningPlan;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Cobre o unico ponto de provisionamento/sincronizacao de UserAccount a partir de Person, que
 * substituiu o antigo UserAccountSynchronizationService: como Person nao carrega mais password nem
 * roles, nao ha mais "espelhamento" - apenas interpretacao de createAccess/password/accessRole na
 * criacao e propagacao de telefone/username na atualizacao.
 */
@ExtendWith(MockitoExtension.class)
class PersonAccountCoordinatorImplTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-08-01T12:00:00.123456Z"), ZoneOffset.UTC);

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private UserAccountRoleRepository userAccountRoleRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordPolicy passwordPolicy;

    @Mock
    private PasswordEncoder passwordEncoder;

    private PersonAccountCoordinatorImpl coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new PersonAccountCoordinatorImpl(
                userAccountRepository, userAccountRoleRepository, roleRepository, passwordPolicy, passwordEncoder, FIXED_CLOCK);
    }

    // ------------------------------------------------------------------
    // interpretCreationRequest
    // ------------------------------------------------------------------

    @Test
    void shouldRejectNullRequestOnInterpret() {
        assertThrows(BadRequestException.class, () -> coordinator.interpretCreationRequest(null));
    }

    @Test
    void shouldInterpretExplicitCreateAccessTrueWithExplicitRole() {
        PersonAccessProvisioningPlan plan = coordinator.interpretCreationRequest(
                request(true, "senha123", "ROLE_ADMIN"));

        assertTrue(plan.createAccess());
        assertEquals("senha123", plan.rawPassword());
        assertEquals("ROLE_ADMIN", plan.roleAuthority());
    }

    @Test
    void shouldDefaultToOperatorWhenCreateAccessTrueWithoutRole() {
        PersonAccessProvisioningPlan plan = coordinator.interpretCreationRequest(
                request(true, "senha123", null));

        assertEquals("ROLE_OPERATOR", plan.roleAuthority());
    }

    @Test
    void shouldRequirePasswordWhenCreateAccessIsTrue() {
        doThrow(new BadRequestException("A senha e obrigatoria")).when(passwordPolicy).validateRequired(null);
        assertThrows(BadRequestException.class, () -> coordinator.interpretCreationRequest(request(true, null, null)));
    }

    @Test
    void shouldRejectPasswordAndRoleWhenCreateAccessIsFalse() {
        assertThrows(BadRequestException.class, () -> coordinator.interpretCreationRequest(request(false, "senha123", null)));
        assertThrows(BadRequestException.class, () -> coordinator.interpretCreationRequest(request(false, null, "ROLE_ADMIN")));
    }

    @Test
    void shouldReturnWithoutAccessWhenCreateAccessIsFalseAndNoOtherFieldsPresent() {
        PersonAccessProvisioningPlan plan = coordinator.interpretCreationRequest(request(false, null, null));

        assertFalse(plan.createAccess());
    }

    @Test
    void shouldPreserveLegacyPayloadWhenCreateAccessAbsentAndPasswordPresent() {
        PersonAccessProvisioningPlan plan = coordinator.interpretCreationRequest(request(null, "senha123", null));

        assertTrue(plan.createAccess());
        assertEquals("ROLE_OPERATOR", plan.roleAuthority());
    }

    @Test
    void shouldReturnWithoutAccessWhenCreateAccessAndPasswordAreBothAbsent() {
        PersonAccessProvisioningPlan plan = coordinator.interpretCreationRequest(request(null, null, null));

        assertFalse(plan.createAccess());
    }

    @Test
    void shouldRejectRoleWithoutPasswordWhenCreateAccessAbsent() {
        assertThrows(BadRequestException.class, () -> coordinator.interpretCreationRequest(request(null, null, "ROLE_ADMIN")));
    }

    // ------------------------------------------------------------------
    // provisionAccess
    // ------------------------------------------------------------------

    @Test
    void shouldRejectProvisioningForUnpersistedPerson() {
        Person transientPerson = new Person();
        assertThrows(BadRequestException.class, () -> coordinator.provisionAccess(transientPerson, request(true, "senha123", null)));
        verifyNoInteractions(userAccountRepository, userAccountRoleRepository);
    }

    @Test
    void shouldDoNothingWhenPlanDoesNotRequestAccess() {
        Person person = person(1L, "34999999991", true);

        coordinator.provisionAccess(person, request(false, null, null));

        verifyNoInteractions(userAccountRepository, userAccountRoleRepository, passwordEncoder);
    }

    @Test
    void shouldRejectProvisioningForInactivePerson() {
        Person person = person(1L, "34999999991", false);

        LifecycleConflictException exception = assertThrows(LifecycleConflictException.class,
                () -> coordinator.provisionAccess(person, request(true, "senha123", null)));

        assertEquals("PERSON_INACTIVE", exception.getErrorCode());
        verifyNoInteractions(userAccountRepository, userAccountRoleRepository);
    }

    @Test
    void shouldCreateAccountAndSingleRoleEncodingPasswordOnce() {
        Person person = person(1L, "34999999991", true);
        Role operatorRole = new Role(1L, "ROLE_OPERATOR");
        when(roleRepository.findByAuthority("ROLE_OPERATOR")).thenReturn(Optional.of(operatorRole));
        when(userAccountRepository.findByUsername("34999999991")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("senha123")).thenReturn("encoded-hash");
        when(userAccountRepository.saveAndFlush(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        coordinator.provisionAccess(person, request(true, "senha123", null));

        ArgumentCaptor<UserAccount> accountCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository).saveAndFlush(accountCaptor.capture());
        UserAccount created = accountCaptor.getValue();
        assertEquals("34999999991", created.getUsername());
        assertEquals("encoded-hash", created.getPasswordHash());
        assertEquals(LocalDateTime.of(2026, 8, 1, 12, 0, 0), created.getCreatedAt());

        ArgumentCaptor<UserAccountRole> roleCaptor = ArgumentCaptor.forClass(UserAccountRole.class);
        verify(userAccountRoleRepository).save(roleCaptor.capture());
        assertEquals("ROLE_OPERATOR", roleCaptor.getValue().getRole().getAuthority());
        verify(passwordEncoder, times(1)).encode("senha123");
    }

    @Test
    void shouldLockAdminRoleMutexWhenProvisioningAdminAccess() {
        Person person = person(1L, "34999999991", true);
        Role adminRole = new Role(2L, "ROLE_ADMIN");
        when(roleRepository.findByAuthorityForUpdate("ROLE_ADMIN")).thenReturn(Optional.of(adminRole));
        when(userAccountRepository.findByUsername("34999999991")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("senha123")).thenReturn("encoded-hash");
        when(userAccountRepository.saveAndFlush(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        coordinator.provisionAccess(person, request(true, "senha123", "ROLE_ADMIN"));

        verify(roleRepository).findByAuthorityForUpdate("ROLE_ADMIN");
        verify(roleRepository, never()).findByAuthority("ROLE_ADMIN");
    }

    @Test
    void shouldRejectProvisioningWhenUsernameAlreadyTaken() {
        Person person = person(1L, "34999999991", true);
        Role operatorRole = new Role(1L, "ROLE_OPERATOR");
        when(roleRepository.findByAuthority("ROLE_OPERATOR")).thenReturn(Optional.of(operatorRole));
        UserAccount other = new UserAccount(person(2L, "34999999991", true), "34999999991", "hash", LocalDateTime.now(), LocalDateTime.now());
        when(userAccountRepository.findByUsername("34999999991")).thenReturn(Optional.of(other));

        LifecycleConflictException exception = assertThrows(LifecycleConflictException.class,
                () -> coordinator.provisionAccess(person, request(true, "senha123", null)));

        assertEquals("USER_ACCOUNT_USERNAME_CONFLICT", exception.getErrorCode());
        verify(userAccountRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldThrowResourceNotFoundWhenRoleDoesNotExist() {
        Person person = person(1L, "34999999991", true);
        when(roleRepository.findByAuthority("ROLE_OPERATOR")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> coordinator.provisionAccess(person, request(true, "senha123", null)));
    }

    // ------------------------------------------------------------------
    // synchronizeAccountAfterPersonUpdate
    // ------------------------------------------------------------------

    @Test
    void shouldRejectSynchronizationForUnpersistedPerson() {
        Person transientPerson = new Person();
        assertThrows(BadRequestException.class, () -> coordinator.synchronizeAccountAfterPersonUpdate(transientPerson));
    }

    @Test
    void shouldNoOpWhenPersonHasNoAccount() {
        Person person = person(1L, "34999999991", true);
        when(userAccountRepository.findByPersonIdForUpdate(1L)).thenReturn(Optional.empty());

        coordinator.synchronizeAccountAfterPersonUpdate(person);

        verify(userAccountRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldNotSaveOrIncrementTokenVersionWhenUsernameUnchanged() {
        Person person = person(1L, "34999999991", true);
        UserAccount existing = existingAccount(person, 10L, "34999999991");
        when(userAccountRepository.findByPersonIdForUpdate(1L)).thenReturn(Optional.of(existing));

        coordinator.synchronizeAccountAfterPersonUpdate(person);

        assertEquals(0L, existing.getTokenVersion());
        verify(userAccountRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldUpdateUsernameAndIncrementTokenVersionOnceWhenPhoneNumberChanged() {
        Person person = person(1L, "34999999992", true);
        UserAccount existing = existingAccount(person, 10L, "34999999991");
        when(userAccountRepository.findByPersonIdForUpdate(1L)).thenReturn(Optional.of(existing));
        when(userAccountRepository.findByUsername("34999999992")).thenReturn(Optional.empty());
        when(userAccountRepository.saveAndFlush(existing)).thenReturn(existing);

        coordinator.synchronizeAccountAfterPersonUpdate(person);

        assertEquals("34999999992", existing.getUsername());
        assertEquals(1L, existing.getTokenVersion());
        verify(userAccountRepository).saveAndFlush(existing);
    }

    @Test
    void shouldRejectSynchronizationWhenNewUsernameAlreadyTakenByAnotherAccount() {
        Person person = person(1L, "34999999992", true);
        UserAccount existing = existingAccount(person, 10L, "34999999991");
        UserAccount other = existingAccount(person(2L, "34999999992", true), 20L, "34999999992");
        when(userAccountRepository.findByPersonIdForUpdate(1L)).thenReturn(Optional.of(existing));
        when(userAccountRepository.findByUsername("34999999992")).thenReturn(Optional.of(other));

        LifecycleConflictException exception = assertThrows(LifecycleConflictException.class,
                () -> coordinator.synchronizeAccountAfterPersonUpdate(person));

        assertEquals("USER_ACCOUNT_USERNAME_CONFLICT", exception.getErrorCode());
        verify(userAccountRepository, never()).saveAndFlush(any());
    }

    /**
     * Simula a corrida real que antes dependia de deadlock de gap lock do InnoDB: a checagem
     * amigavel (sem lock) nao encontra conflito porque a outra transacao concorrente ainda nao
     * commitou, mas o flush explicito colide com a constraint uk_tb_user_account_username quando a
     * outra transacao vence a corrida primeiro.
     */
    @Test
    void shouldTranslateUniqueConstraintViolationOnFlushToUsernameConflict() {
        Person person = person(1L, "34999999992", true);
        UserAccount existing = existingAccount(person, 10L, "34999999991");
        when(userAccountRepository.findByPersonIdForUpdate(1L)).thenReturn(Optional.of(existing));
        when(userAccountRepository.findByUsername("34999999992")).thenReturn(Optional.empty());
        when(userAccountRepository.saveAndFlush(existing)).thenThrow(usernameConstraintViolation());

        LifecycleConflictException exception = assertThrows(LifecycleConflictException.class,
                () -> coordinator.synchronizeAccountAfterPersonUpdate(person));

        assertEquals("USER_ACCOUNT_USERNAME_CONFLICT", exception.getErrorCode());
    }

    @Test
    void shouldRethrowUnrelatedDataIntegrityViolationOnFlush() {
        Person person = person(1L, "34999999992", true);
        UserAccount existing = existingAccount(person, 10L, "34999999991");
        DataIntegrityViolationException unrelated = new DataIntegrityViolationException("unrelated constraint");
        when(userAccountRepository.findByPersonIdForUpdate(1L)).thenReturn(Optional.of(existing));
        when(userAccountRepository.findByUsername("34999999992")).thenReturn(Optional.empty());
        when(userAccountRepository.saveAndFlush(existing)).thenThrow(unrelated);

        DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
                () -> coordinator.synchronizeAccountAfterPersonUpdate(person));

        assertEquals(unrelated, thrown);
    }

    private DataIntegrityViolationException usernameConstraintViolation() {
        SQLIntegrityConstraintViolationException sqlException = new SQLIntegrityConstraintViolationException(
                "Duplicate entry '34999999992' for key 'tb_user_account.uk_tb_user_account_username'"
        );
        ConstraintViolationException hibernateException = new ConstraintViolationException(
                "could not execute statement", sqlException, "uk_tb_user_account_username");
        return new DataIntegrityViolationException("could not execute statement", hibernateException);
    }

    private PersonAccessRequest request(Boolean createAccess, String password, String accessRole) {
        return new PersonAccessRequest() {
            @Override
            public Boolean getCreateAccess() {
                return createAccess;
            }

            @Override
            public String getAccessRole() {
                return accessRole;
            }

            @Override
            public String getPassword() {
                return password;
            }
        };
    }

    private Person person(Long id, String phoneNumber, boolean active) {
        Person person = new Person();
        person.setId(id);
        person.setName("Person " + id);
        person.setPhoneNumber(phoneNumber);
        person.setActive(active);
        return person;
    }

    private UserAccount existingAccount(Person person, Long id, String username) {
        UserAccount account = new UserAccount(person, username, "hash", LocalDateTime.of(2020, 1, 1, 0, 0), LocalDateTime.of(2020, 1, 1, 0, 0));
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
}
