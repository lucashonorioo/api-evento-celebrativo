package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.request.AdminPasswordResetRequestDTO;
import com.eventoscelebrativos.dto.request.PersonActiveRequestDTO;
import com.eventoscelebrativos.dto.request.SelfPasswordChangeRequestDTO;
import com.eventoscelebrativos.dto.request.UserAccountCreateRequestDTO;
import com.eventoscelebrativos.dto.request.UserAccountEnabledRequestDTO;
import com.eventoscelebrativos.dto.response.PersonAssignmentConflictDTO;
import com.eventoscelebrativos.dto.response.UserAccountLifecycleResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.exception.exceptions.LifecycleConflictException;
import com.eventoscelebrativos.exception.exceptions.PersonHasActiveAssignmentsException;
import com.eventoscelebrativos.exception.exceptions.PersonHasActiveParishResponsibilitiesException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.model.UserAccount;
import com.eventoscelebrativos.model.UserAccountRole;
import com.eventoscelebrativos.projection.PersonUnavailabilityAssignmentConflictProjection;
import com.eventoscelebrativos.repository.EventAssignmentRepository;
import com.eventoscelebrativos.repository.ParishStaffAssignmentRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.repository.RoleRepository;
import com.eventoscelebrativos.repository.UserAccountRepository;
import com.eventoscelebrativos.repository.UserAccountRoleRepository;
import com.eventoscelebrativos.security.AuthenticatedUserResolver;
import com.eventoscelebrativos.service.PasswordPolicy;
import com.eventoscelebrativos.service.UserAccountLifecycleService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class UserAccountLifecycleServiceImpl implements UserAccountLifecycleService {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_OPERATOR = "ROLE_OPERATOR";
    private static final Set<String> ALLOWED_ROLES = Set.of(ROLE_ADMIN, ROLE_OPERATOR);

    private final PersonRepository personRepository;
    private final UserAccountRepository userAccountRepository;
    private final UserAccountRoleRepository userAccountRoleRepository;
    private final RoleRepository roleRepository;
    private final EventAssignmentRepository eventAssignmentRepository;
    private final ParishStaffAssignmentRepository parishStaffAssignmentRepository;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final Clock clock;

    public UserAccountLifecycleServiceImpl(
            PersonRepository personRepository,
            UserAccountRepository userAccountRepository,
            UserAccountRoleRepository userAccountRoleRepository,
            RoleRepository roleRepository,
            EventAssignmentRepository eventAssignmentRepository,
            ParishStaffAssignmentRepository parishStaffAssignmentRepository,
            PasswordPolicy passwordPolicy,
            PasswordEncoder passwordEncoder,
            AuthenticatedUserResolver authenticatedUserResolver,
            Clock clock
    ) {
        this.personRepository = personRepository;
        this.userAccountRepository = userAccountRepository;
        this.userAccountRoleRepository = userAccountRoleRepository;
        this.roleRepository = roleRepository;
        this.eventAssignmentRepository = eventAssignmentRepository;
        this.parishStaffAssignmentRepository = parishStaffAssignmentRepository;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.authenticatedUserResolver = authenticatedUserResolver;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public UserAccountLifecycleResponseDTO findAccountState(Long personId) {
        validateId(personId);
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa", personId));
        Optional<UserAccount> account = userAccountRepository.findByPersonIdWithRoles(personId);
        return account
                .map(userAccount -> toResponse(person, userAccount))
                .orElseGet(() -> new UserAccountLifecycleResponseDTO(
                        person.getId(), person.isActive(), false, null, null, List.of()));
    }

    @Override
    @Transactional
    public UserAccountLifecycleResponseDTO createAccount(Long personId, UserAccountCreateRequestDTO request) {
        validateId(personId);
        String roleName = normalizeRoleOrDefault(request == null ? null : request.getRole());
        String password = request == null ? null : request.getInitialPassword();
        passwordPolicy.validateRequired(password);
        Role role = requireRoleWithAdminMutex(roleName);

        Person person = personRepository.findByIdForUpdate(personId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa", personId));
        if (!person.isActive()) {
            throw conflict("Pessoa inativa nao pode receber conta de acesso.", "PERSON_INACTIVE");
        }
        if (userAccountRepository.findByPersonIdForUpdate(personId).isPresent()) {
            throw conflict("Pessoa ja possui conta de acesso.", "USER_ACCOUNT_ALREADY_EXISTS");
        }
        validateUsernameAvailable(person.getPhoneNumber(), null);

        String passwordHash = passwordEncoder.encode(password);
        LocalDateTime now = currentSecond();
        UserAccount account = userAccountRepository.save(
                new UserAccount(person, person.getPhoneNumber(), passwordHash, now, now)
        );
        replaceAccountRole(account, role);

        return toResponse(person, account, List.of(role.getAuthority()));
    }

    @Override
    @Transactional
    public void updateAccountEnabled(Long personId, UserAccountEnabledRequestDTO request) {
        validateId(personId);
        if (request == null || request.getEnabled() == null) {
            throw new BadRequestException("O campo enabled e obrigatorio");
        }
        boolean desiredEnabled = request.getEnabled();

        lockAdminRoleMutex();
        Person person = personRepository.findByIdForUpdate(personId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa", personId));
        UserAccount account = userAccountRepository.findByPersonIdForUpdate(personId)
                .orElseThrow(() -> conflict("Pessoa nao possui conta de acesso.", "USER_ACCOUNT_NOT_FOUND"));

        if (!desiredEnabled && authenticatedUserResolver.requireCurrentAccountId().equals(account.getId())) {
            throw conflict("Administrador nao pode desabilitar a propria conta.", "SELF_ACCOUNT_DISABLE_NOT_ALLOWED");
        }
        if (account.isEnabled() == desiredEnabled) {
            return;
        }
        if (desiredEnabled) {
            if (account.getRoles().isEmpty()) {
                throw conflict("Conta sem perfil nao pode ser habilitada.", "USER_ACCOUNT_ROLE_REQUIRED");
            }
            account.enable(currentSecond());
        } else {
            if (isEffectiveAdmin(account, person)) {
                validateAnotherEffectiveAdministratorExists();
            }
            account.disable(currentSecond());
        }
        userAccountRepository.save(account);
    }

    @Override
    @Transactional
    public void updatePersonActive(Long personId, PersonActiveRequestDTO request) {
        validateId(personId);
        if (request == null || request.getActive() == null) {
            throw new BadRequestException("O campo active e obrigatorio");
        }
        boolean desiredActive = request.getActive();
        LocalDateTime currentSecond = currentSecond();

        lockAdminRoleMutex();
        Person person = personRepository.findByIdForUpdate(personId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa", personId));
        Optional<UserAccount> accountOptional = userAccountRepository.findByPersonIdForUpdate(personId);

        if (!desiredActive && authenticatedUserResolver.requireCurrentPersonId().equals(person.getId())) {
            throw conflict("Administrador nao pode desativar a propria pessoa.", "SELF_PERSON_DEACTIVATION_NOT_ALLOWED");
        }
        if (person.isActive() == desiredActive) {
            return;
        }
        if (!desiredActive) {
            if (parishStaffAssignmentRepository.existsByPersonIdAndActiveTrue(personId)) {
                throw new PersonHasActiveParishResponsibilitiesException();
            }
            List<PersonUnavailabilityAssignmentConflictProjection> activeAssignments =
                    eventAssignmentRepository.findActiveOrFutureAssignmentsByPersonId(personId, currentSecond);
            if (!activeAssignments.isEmpty()) {
                throw new PersonHasActiveAssignmentsException(activeAssignments.stream()
                        .map(a -> new PersonAssignmentConflictDTO(
                                a.getEventId(), a.getEventName(), a.getStartAt(), a.getEndAt(), a.getAssignmentType()))
                        .toList());
            }
            if (accountOptional.filter(account -> isEffectiveAdmin(account, person)).isPresent()) {
                validateAnotherEffectiveAdministratorExists();
            }
            accountOptional.ifPresent(account -> {
                account.incrementTokenVersion();
                account.setUpdatedAt(currentSecond);
                userAccountRepository.save(account);
            });
        }
        person.setActive(desiredActive);
        personRepository.save(person);
    }

    @Override
    @Transactional
    public void resetPassword(Long personId, AdminPasswordResetRequestDTO request) {
        validateId(personId);
        String newPassword = request == null ? null : request.getNewPassword();
        passwordPolicy.validateRequired(newPassword);

        personRepository.findByIdForUpdate(personId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa", personId));
        UserAccount account = userAccountRepository.findByPersonIdForUpdate(personId)
                .orElseThrow(() -> conflict("Pessoa nao possui conta de acesso.", "USER_ACCOUNT_NOT_FOUND"));
        if (authenticatedUserResolver.requireCurrentAccountId().equals(account.getId())) {
            throw conflict("Use o endpoint pessoal para alterar a propria senha.", "SELF_ADMIN_PASSWORD_RESET_NOT_ALLOWED");
        }
        updatePassword(account, newPassword);
    }

    @Override
    @Transactional
    public void changeOwnPassword(SelfPasswordChangeRequestDTO request) {
        String currentPassword = request == null ? null : request.getCurrentPassword();
        String newPassword = request == null ? null : request.getNewPassword();
        passwordPolicy.validateRequired(currentPassword);
        passwordPolicy.validateRequired(newPassword);

        Long currentPersonId = authenticatedUserResolver.requireCurrentPersonId();
        Long currentAccountId = authenticatedUserResolver.requireCurrentAccountId();
        personRepository.findByIdForUpdate(currentPersonId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa", currentPersonId));
        UserAccount account = userAccountRepository.findByPersonIdForUpdate(currentPersonId)
                .orElseThrow(() -> conflict("Pessoa nao possui conta de acesso.", "USER_ACCOUNT_NOT_FOUND"));
        if (!currentAccountId.equals(account.getId())) {
            throw conflict("Principal autenticado divergente da conta atual.", "USER_ACCOUNT_NOT_FOUND");
        }
        if (!passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
            throw conflict("Senha atual invalida.", "CURRENT_PASSWORD_INVALID");
        }
        updatePassword(account, newPassword);
    }

    @Override
    @Transactional
    public Person updateRole(Long personId, String requestedRole) {
        validateId(personId);
        String roleName = normalizeRequiredRole(requestedRole);
        lockAdminRoleMutex();

        Person person = personRepository.findByIdForUpdate(personId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa", personId));
        UserAccount account = userAccountRepository.findByPersonIdForUpdate(personId)
                .orElseThrow(() -> conflict("Pessoa nao possui conta de acesso.", "USER_ACCOUNT_NOT_FOUND"));

        if (hasAdminRole(account) && !ROLE_ADMIN.equals(roleName)) {
            if (authenticatedUserResolver.requireCurrentPersonId().equals(person.getId())) {
                throw conflict("Administrador nao pode remover o proprio perfil administrativo.", "SELF_ADMIN_DEMOTION_NOT_ALLOWED");
            }
            if (isEffectiveAdmin(account, person)) {
                validateAnotherEffectiveAdministratorExists();
            }
        }

        Role role = requireRole(roleName);
        replaceAccountRole(account, role);
        account.setUpdatedAt(currentSecond());
        userAccountRepository.save(account);
        return person;
    }

    @Override
    @Transactional(readOnly = true)
    public Role requireRole(String roleName) {
        String normalized = normalizeRequiredRole(roleName);
        return roleRepository.findByAuthority(normalized)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de acesso", normalized));
    }

    private void updatePassword(UserAccount account, String rawPassword) {
        String passwordHash = passwordEncoder.encode(rawPassword);
        account.setPasswordHash(passwordHash);
        account.incrementTokenVersion();
        account.setUpdatedAt(currentSecond());
        userAccountRepository.save(account);
    }

    private void replaceAccountRole(UserAccount account, Role role) {
        List<UserAccountRole> currentRoles = userAccountRoleRepository.findByUserAccountId(account.getId());
        boolean alreadyExactlyThisRole = currentRoles.size() == 1
                && currentRoles.getFirst().getRole().getId().equals(role.getId());
        if (alreadyExactlyThisRole) {
            return;
        }
        userAccountRoleRepository.deleteAllByUserAccountId(account.getId());
        userAccountRoleRepository.save(new UserAccountRole(account, role));
    }

    private UserAccountLifecycleResponseDTO toResponse(Person person, UserAccount account) {
        return toResponse(person, account, sortedRoleNames(account));
    }

    private UserAccountLifecycleResponseDTO toResponse(Person person, UserAccount account, List<String> roles) {
        return new UserAccountLifecycleResponseDTO(
                person.getId(),
                person.isActive(),
                true,
                account.getUsername(),
                account.isEnabled(),
                roles
        );
    }

    private List<String> sortedRoleNames(UserAccount account) {
        return account.getRoles().stream()
                .map(userAccountRole -> userAccountRole.getRole().getAuthority())
                .sorted()
                .toList();
    }

    private void validateUsernameAvailable(String username, Long currentAccountId) {
        userAccountRepository.findByUsernameForUpdate(username)
                .filter(account -> currentAccountId == null || !account.getId().equals(currentAccountId))
                .ifPresent(account -> {
                    throw conflict("Username ja esta associado a outra conta.", "USER_ACCOUNT_USERNAME_CONFLICT");
                });
    }

    private boolean isEffectiveAdmin(UserAccount account, Person person) {
        return account.isEnabled() && person.isActive() && hasAdminRole(account);
    }

    private boolean hasAdminRole(UserAccount account) {
        return account.getRoles().stream()
                .anyMatch(userAccountRole -> ROLE_ADMIN.equals(userAccountRole.getRole().getAuthority()));
    }

    private void validateAnotherEffectiveAdministratorExists() {
        if (userAccountRoleRepository.countEffectiveAdministrators() <= 1) {
            throw conflict("O ultimo administrador efetivo deve ser preservado.", "LAST_ACTIVE_ADMIN_REQUIRED");
        }
    }

    private Role lockAdminRoleMutex() {
        return roleRepository.findByAuthorityForUpdate(ROLE_ADMIN)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de acesso", ROLE_ADMIN));
    }

    private Role requireRoleWithAdminMutex(String roleName) {
        String normalized = normalizeRequiredRole(roleName);
        if (ROLE_ADMIN.equals(normalized)) {
            return lockAdminRoleMutex();
        }
        return roleRepository.findByAuthority(normalized)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de acesso", normalized));
    }

    private String normalizeRoleOrDefault(String role) {
        String normalized = normalizeOptional(role);
        return normalized == null ? ROLE_OPERATOR : normalizeRequiredRole(normalized);
    }

    private String normalizeRequiredRole(String role) {
        String normalized = normalizeOptional(role);
        if (normalized == null || !ALLOWED_ROLES.contains(normalized)) {
            throw new BadRequestException("Perfil de acesso invalido");
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new BadRequestException("O Id deve ser positivo e nao nulo");
        }
    }

    private LocalDateTime currentSecond() {
        return LocalDateTime.now(clock).withNano(0);
    }

    private LifecycleConflictException conflict(String message, String code) {
        return new LifecycleConflictException(message, code);
    }
}
