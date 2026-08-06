package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.model.UserAccount;
import com.eventoscelebrativos.model.UserAccountRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class PersonRepositoryTest {

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserAccountRoleRepository userAccountRoleRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void shouldPagePeopleOrderedByNameAndId() {
        Person first = savePersonWithRole("AAA User Same", "34970000001", operatorRole());
        Person second = savePersonWithRole("AAA User Same", "34970000002", operatorRole());

        Page<Long> result = personRepository.findAdminPageIds(
                "AAA User Same",
                null,
                null,
                null,
                PageRequest.of(0, 10)
        );

        assertEquals(2, result.getTotalElements());
        assertEquals(List.of(first.getId(), second.getId()), result.getContent());
    }

    @Test
    void shouldFilterPeopleByPartialNameCaseInsensitive() {
        Person person = savePersonWithRole("Repository Alice", "34970000003", operatorRole());
        savePersonWithRole("Repository Bob", "34970000004", operatorRole());

        Page<Long> result = personRepository.findAdminPageIds(
                "repoSITORY ali",
                null,
                null,
                null,
                PageRequest.of(0, 10)
        );

        assertEquals(1, result.getTotalElements());
        assertEquals(person.getId(), result.getContent().get(0));
    }

    @Test
    void shouldFilterPeopleByPartialPhoneNumber() {
        Person person = savePersonWithRole("Phone Filter", "34970000005", operatorRole());

        Page<Long> result = personRepository.findAdminPageIds(
                null,
                "000005",
                null,
                null,
                PageRequest.of(0, 10)
        );

        assertTrue(result.getContent().contains(person.getId()));
    }

    @Test
    void shouldFilterPeopleByActiveMinistryType() {
        Person reader = savePersonWithRole("Type Reader", "34970000006", operatorRole());
        saveMinistry(reader, MinistryType.READER);
        Person commentator = savePersonWithRole("Type Commentator", "34970000007", operatorRole());
        saveMinistry(commentator, MinistryType.COMMENTATOR);

        Page<Long> result = personRepository.findAdminPageIds(
                "Type",
                null,
                MinistryType.READER,
                null,
                PageRequest.of(0, 10)
        );

        assertTrue(result.getContent().contains(reader.getId()));
        assertFalse(result.getContent().contains(commentator.getId()));
    }

    @Test
    void shouldFilterPeopleByRole() {
        Person admin = savePersonWithRole("Role Admin", "34970000008", adminRole());
        Person operator = savePersonWithRole("Role Operator", "34970000009", operatorRole());

        Page<Long> result = personRepository.findAdminPageIds(
                "Role",
                null,
                null,
                "ROLE_ADMIN",
                PageRequest.of(0, 10)
        );

        assertTrue(result.getContent().contains(admin.getId()));
        assertFalse(result.getContent().contains(operator.getId()));
    }

    @Test
    void shouldCombineFilters() {
        Person match = savePersonWithRole("Combined Alice", "34970000010", adminRole());
        saveMinistry(match, MinistryType.READER);
        Person otherRole = savePersonWithRole("Combined Alice", "34970000011", operatorRole());
        saveMinistry(otherRole, MinistryType.READER);
        Person otherMinistry = savePersonWithRole("Combined Alice", "34970000016", adminRole());
        saveMinistry(otherMinistry, MinistryType.COMMENTATOR);

        Page<Long> result = personRepository.findAdminPageIds(
                "Combined",
                "00010",
                MinistryType.READER,
                "ROLE_ADMIN",
                PageRequest.of(0, 10)
        );

        assertEquals(1, result.getTotalElements());
        assertEquals(match.getId(), result.getContent().get(0));
    }

    @Test
    void shouldNotDuplicatePersonInPageWhenRoleFilterMatches() {
        Person person = savePersonWithRole("Single Role Match", "34970000012", operatorRole());

        Page<Long> result = personRepository.findAdminPageIds(
                "Single Role Match",
                null,
                null,
                "ROLE_OPERATOR",
                PageRequest.of(0, 10)
        );

        assertEquals(1, result.getTotalElements());
        assertEquals(List.of(person.getId()), result.getContent());
    }

    @Test
    void shouldLoadRoleAuthoritiesInBatchByPersonIds() {
        Person withRole = savePersonWithRole("Batch Role Person", "34970000013", adminRole());
        Person withoutAccount = savePersonWithoutAccount("Batch No Account Person", "34970000017");

        Map<Long, List<String>> rolesByPerson = userAccountRoleRepository
                .findRoleAuthoritiesByPersonIdsGroupedByPerson(List.of(withRole.getId(), withoutAccount.getId()));

        assertEquals(List.of("ROLE_ADMIN"), rolesByPerson.get(withRole.getId()));
        assertFalse(rolesByPerson.containsKey(withoutAccount.getId()));
    }

    @Test
    void shouldFindPersonWithoutRolesWhenNoRoleFilterIsApplied() {
        Person person = savePersonWithoutAccount("No Roles", "34970000014");

        Page<Long> result = personRepository.findAdminPageIds(
                "No Roles",
                null,
                null,
                null,
                PageRequest.of(0, 10)
        );

        assertEquals(1, result.getTotalElements());
        assertEquals(person.getId(), result.getContent().get(0));
    }

    private Person savePersonWithRole(String name, String phoneNumber, Role role) {
        Person person = newPerson(name, phoneNumber);
        entityManager.persist(person);
        entityManager.flush();
        LocalDateTime now = LocalDateTime.now().withNano(0);
        UserAccount account = new UserAccount(person, phoneNumber, "encoded-password", now, now);
        entityManager.persist(account);
        entityManager.persist(new UserAccountRole(account, role));
        entityManager.flush();
        entityManager.clear();
        return person;
    }

    private Person savePersonWithoutAccount(String name, String phoneNumber) {
        Person person = newPerson(name, phoneNumber);
        entityManager.persist(person);
        entityManager.flush();
        entityManager.clear();
        return person;
    }

    private Person newPerson(String name, String phoneNumber) {
        Person person = new Person();
        person.setName(name);
        person.setPhoneNumber(phoneNumber);
        person.setBirthdayDate(LocalDate.of(1990, 1, 10));
        return person;
    }

    private void saveMinistry(Person person, MinistryType ministryType) {
        entityManager.persist(new PersonMinistry(person, ministryType));
        entityManager.flush();
        entityManager.clear();
    }

    private Role operatorRole() {
        return roleRepository.findByAuthority("ROLE_OPERATOR").orElseThrow();
    }

    private Role adminRole() {
        return roleRepository.findByAuthority("ROLE_ADMIN").orElseThrow();
    }
}
