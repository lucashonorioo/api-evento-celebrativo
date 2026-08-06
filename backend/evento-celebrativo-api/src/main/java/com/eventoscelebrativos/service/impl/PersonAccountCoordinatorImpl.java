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
import com.eventoscelebrativos.service.PersonAccountCoordinator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;

@Service
public class PersonAccountCoordinatorImpl implements PersonAccountCoordinator {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_OPERATOR = "ROLE_OPERATOR";
    private static final Set<String> ALLOWED_ROLES = Set.of(ROLE_ADMIN, ROLE_OPERATOR);

    private final UserAccountRepository userAccountRepository;
    private final UserAccountRoleRepository userAccountRoleRepository;
    private final RoleRepository roleRepository;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public PersonAccountCoordinatorImpl(
            UserAccountRepository userAccountRepository,
            UserAccountRoleRepository userAccountRoleRepository,
            RoleRepository roleRepository,
            PasswordPolicy passwordPolicy,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        this.userAccountRepository = userAccountRepository;
        this.userAccountRoleRepository = userAccountRoleRepository;
        this.roleRepository = roleRepository;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    public PersonAccessProvisioningPlan interpretCreationRequest(PersonAccessRequest request) {
        if (request == null) {
            throw new BadRequestException("Dados de acesso sao obrigatorios");
        }
        Boolean createAccess = request.getCreateAccess();
        String password = request.getPassword();
        String role = normalizeOptional(request.getAccessRole());

        if (Boolean.FALSE.equals(createAccess)) {
            if (password != null || role != null) {
                throw new BadRequestException("Senha e perfil nao podem ser enviados quando createAccess=false");
            }
            return PersonAccessProvisioningPlan.withoutAccess();
        }

        if (Boolean.TRUE.equals(createAccess)) {
            passwordPolicy.validateRequired(password);
            return PersonAccessProvisioningPlan.withAccess(password, role == null ? ROLE_OPERATOR : normalizeRequiredRole(role));
        }

        if (password == null) {
            if (role != null) {
                throw new BadRequestException("accessRole exige senha para criar acesso");
            }
            return PersonAccessProvisioningPlan.withoutAccess();
        }

        passwordPolicy.validatePresent(password);
        return PersonAccessProvisioningPlan.withAccess(password, role == null ? ROLE_OPERATOR : normalizeRequiredRole(role));
    }

    @Override
    @Transactional
    public void provisionAccess(Person person, PersonAccessRequest request) {
        if (person == null || person.getId() == null) {
            throw new BadRequestException("Pessoa persistida e obrigatoria para provisionar acesso");
        }
        PersonAccessProvisioningPlan plan = interpretCreationRequest(request);
        if (!plan.createAccess()) {
            return;
        }
        if (!person.isActive()) {
            throw conflict("Somente pessoa ativa pode receber uma conta.", "PERSON_INACTIVE");
        }
        Role role = requireRoleWithAdminMutex(plan.roleAuthority());
        validateUsernameAvailable(person.getPhoneNumber(), null);

        String passwordHash = passwordEncoder.encode(plan.rawPassword());
        LocalDateTime now = currentSecond();
        UserAccount account = userAccountRepository.save(
                new UserAccount(person, person.getPhoneNumber(), passwordHash, now, now));
        userAccountRoleRepository.save(new UserAccountRole(account, role));
    }

    @Override
    @Transactional
    public void synchronizeAccountAfterPersonUpdate(Person person) {
        if (person == null || person.getId() == null) {
            throw new BadRequestException("Pessoa persistida e obrigatoria para sincronizar conta");
        }
        UserAccount account = userAccountRepository.findByPersonIdForUpdate(person.getId()).orElse(null);
        if (account == null) {
            return;
        }
        if (account.getUsername().equals(person.getPhoneNumber())) {
            return;
        }
        validateUsernameAvailable(person.getPhoneNumber(), account.getId());
        account.incrementTokenVersion();
        account.setUsername(person.getPhoneNumber());
        account.setUpdatedAt(currentSecond());
        userAccountRepository.save(account);
    }

    private void validateUsernameAvailable(String username, Long currentAccountId) {
        userAccountRepository.findByUsernameForUpdate(username)
                .filter(account -> currentAccountId == null || !account.getId().equals(currentAccountId))
                .ifPresent(account -> {
                    throw conflict("Username ja esta associado a outra conta.", "USER_ACCOUNT_USERNAME_CONFLICT");
                });
    }

    private Role requireRoleWithAdminMutex(String roleName) {
        String normalized = normalizeRequiredRole(roleName);
        if (ROLE_ADMIN.equals(normalized)) {
            return lockAdminRoleMutex();
        }
        return roleRepository.findByAuthority(normalized)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de acesso", normalized));
    }

    private Role lockAdminRoleMutex() {
        return roleRepository.findByAuthorityForUpdate(ROLE_ADMIN)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de acesso", ROLE_ADMIN));
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

    private LocalDateTime currentSecond() {
        return LocalDateTime.now(clock).withNano(0);
    }

    private LifecycleConflictException conflict(String message, String code) {
        return new LifecycleConflictException(message, code);
    }
}
