package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.model.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class PersonRepositoryTest {

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void shouldPagePeopleOrderedByNameAndId() {
        Person first = savePerson("AAA User Same", "34970000001", operatorRole());
        Person second = savePerson("AAA User Same", "34970000002", operatorRole());

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
        Person person = savePerson("Repository Alice", "34970000003", operatorRole());
        savePerson("Repository Bob", "34970000004", operatorRole());

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
        Person person = savePerson("Phone Filter", "34970000005", operatorRole());

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
        Person reader = savePerson("Type Reader", "34970000006", operatorRole());
        saveMinistry(reader, MinistryType.READER);
        Person commentator = savePerson("Type Commentator", "34970000007", operatorRole());
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
        Person admin = savePerson("Role Admin", "34970000008", adminRole());
        Person operator = savePerson("Role Operator", "34970000009", operatorRole());

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
        Person match = savePerson("Combined Alice", "34970000010", adminRole());
        saveMinistry(match, MinistryType.READER);
        Person otherRole = savePerson("Combined Alice", "34970000011", operatorRole());
        saveMinistry(otherRole, MinistryType.READER);
        Person otherMinistry = savePerson("Combined Alice", "34970000016", adminRole());
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
    void shouldNotDuplicatePersonWithMultipleRolesOrCountRolesAsPeople() {
        Person person = savePerson("Multiple Roles", "34970000012", operatorRole(), adminRole());

        Page<Long> result = personRepository.findAdminPageIds(
                "Multiple Roles",
                null,
                null,
                null,
                PageRequest.of(0, 10)
        );

        assertEquals(1, result.getTotalElements());
        assertEquals(List.of(person.getId()), result.getContent());
    }

    @Test
    void shouldLoadRolesInBatchForPageIds() {
        Person person = savePerson("Batch Roles", "34970000013", operatorRole(), adminRole());

        List<Person> people = personRepository.findAllByIdInWithRoles(List.of(person.getId()));

        assertEquals(1, people.size());
        assertEquals(2, people.get(0).getRoles().size());
    }

    @Test
    void shouldFindPersonWithoutRolesWhenNoRoleFilterIsApplied() {
        Person person = savePerson("No Roles", "34970000014");

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

    @Test
    void shouldReturnDistinctAdministratorsForUpdateLockQuery() {
        Person admin = savePerson("Locked Admin", "34970000015", adminRole(), operatorRole());

        List<Person> administrators = personRepository.findPeopleByRoleForUpdate("ROLE_ADMIN");

        assertTrue(administrators.stream().anyMatch(person -> person.getId().equals(admin.getId())));
        assertEquals(
                administrators.size(),
                administrators.stream().map(Person::getId).distinct().count()
        );
    }

    private Person savePerson(String name, String phoneNumber, Role... roles) {
        Person person = new Person();
        fillPerson(person, name, phoneNumber, roles);
        entityManager.persist(person);
        entityManager.flush();
        entityManager.clear();
        return person;
    }

    private void saveMinistry(Person person, MinistryType ministryType) {
        entityManager.persist(new PersonMinistry(person, ministryType));
        entityManager.flush();
        entityManager.clear();
    }

    private void fillPerson(Person person, String name, String phoneNumber, Role... roles) {
        person.setName(name);
        person.setPhoneNumber(phoneNumber);
        person.setBirthdayDate(LocalDate.of(1990, 1, 10));
        person.setPassword("encoded-password");
        for (Role role : roles) {
            person.addRole(role);
        }
    }

    private Role operatorRole() {
        return roleRepository.findByAuthority("ROLE_OPERATOR").orElseThrow();
    }

    private Role adminRole() {
        return roleRepository.findByAuthority("ROLE_ADMIN").orElseThrow();
    }
}
