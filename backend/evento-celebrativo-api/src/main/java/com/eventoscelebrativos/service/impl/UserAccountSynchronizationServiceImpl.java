package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.exception.exceptions.UserAccountConsistencyException;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.model.UserAccount;
import com.eventoscelebrativos.model.UserAccountRole;
import com.eventoscelebrativos.repository.UserAccountRepository;
import com.eventoscelebrativos.repository.UserAccountRoleRepository;
import com.eventoscelebrativos.service.UserAccountSynchronizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserAccountSynchronizationServiceImpl implements UserAccountSynchronizationService {

    private final UserAccountRepository userAccountRepository;
    private final UserAccountRoleRepository userAccountRoleRepository;
    private final Clock clock;

    public UserAccountSynchronizationServiceImpl(
            UserAccountRepository userAccountRepository,
            UserAccountRoleRepository userAccountRoleRepository,
            Clock clock
    ) {
        this.userAccountRepository = userAccountRepository;
        this.userAccountRoleRepository = userAccountRoleRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void synchronizeNewPerson(Person person) {
        if (person == null || person.getId() == null) {
            throw new UserAccountConsistencyException(
                    "Nao e possivel criar UserAccount para uma Person sem Id persistido");
        }
        if (userAccountRepository.existsByPersonId(person.getId())) {
            throw new UserAccountConsistencyException(
                    "Ja existe UserAccount para a pessoa " + person.getId() + "; criacao duplicada nao permitida");
        }

        LocalDateTime now = currentSecond();
        UserAccount account = new UserAccount(person, person.getPhoneNumber(), person.getPassword(), now, now);
        UserAccount saved = userAccountRepository.save(account);
        replaceRoles(saved, person.getRoles());
    }

    @Override
    @Transactional
    public void synchronizeExistingPerson(Person person) {
        if (person == null || person.getId() == null) {
            throw new UserAccountConsistencyException(
                    "Nao e possivel sincronizar UserAccount para uma Person sem Id persistido");
        }

        UserAccount account = userAccountRepository.findByPersonIdForUpdate(person.getId())
                .orElseThrow(() -> new UserAccountConsistencyException(
                        "UserAccount ausente para a pessoa " + person.getId()
                                + "; divergencia entre modelo legado e modelo paralelo"));

        account.setUsername(person.getPhoneNumber());
        account.setPasswordHash(person.getPassword());
        account.setUpdatedAt(currentSecond());
        userAccountRepository.save(account);

        replaceRoles(account, person.getRoles());
    }

    private void replaceRoles(UserAccount account, Set<Role> desiredRoles) {
        List<UserAccountRole> current = userAccountRoleRepository.findByUserAccountId(account.getId());
        Set<Long> desiredRoleIds = desiredRoles.stream().map(Role::getId).collect(Collectors.toSet());
        Set<Long> currentRoleIds = current.stream().map(uar -> uar.getRole().getId()).collect(Collectors.toSet());

        List<UserAccountRole> toRemove = current.stream()
                .filter(uar -> !desiredRoleIds.contains(uar.getRole().getId()))
                .toList();
        List<UserAccountRole> toAdd = desiredRoles.stream()
                .filter(role -> !currentRoleIds.contains(role.getId()))
                .map(role -> new UserAccountRole(account, role))
                .toList();

        if (!toRemove.isEmpty()) {
            userAccountRoleRepository.deleteAll(toRemove);
        }
        if (!toAdd.isEmpty()) {
            userAccountRoleRepository.saveAll(toAdd);
        }
    }

    private LocalDateTime currentSecond() {
        return LocalDateTime.now(clock).withNano(0);
    }
}
