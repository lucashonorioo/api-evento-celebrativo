package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.Commentator;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Reader;
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
        Reader first = saveReader("AAA User Same", "34970000001", operatorRole());
        Reader second = saveReader("AAA User Same", "34970000002", operatorRole());

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
        Reader reader = saveReader("Repository Alice", "34970000003", operatorRole());
        saveReader("Repository Bob", "34970000004", operatorRole());

        Page<Long> result = personRepository.findAdminPageIds(
                "repoSITORY ali",
                null,
                null,
                null,
                PageRequest.of(0, 10)
        );

        assertEquals(1, result.getTotalElements());
        assertEquals(reader.getId(), result.getContent().get(0));
    }

    @Test
    void shouldFilterPeopleByPartialPhoneNumber() {
        Reader reader = saveReader("Phone Filter", "34970000005", operatorRole());

        Page<Long> result = personRepository.findAdminPageIds(
                null,
                "000005",
                null,
                null,
                PageRequest.of(0, 10)
        );

        assertTrue(result.getContent().contains(reader.getId()));
    }

    @Test
    void shouldFilterPeopleByPersonType() {
        Reader reader = saveReader("Type Reader", "34970000006", operatorRole());
        Commentator commentator = saveCommentator("Type Commentator", "34970000007", operatorRole());

        Page<Long> result = personRepository.findAdminPageIds(
                "Type",
                null,
                "reader",
                null,
                PageRequest.of(0, 10)
        );

        assertTrue(result.getContent().contains(reader.getId()));
        assertFalse(result.getContent().contains(commentator.getId()));
    }

    @Test
    void shouldFilterPeopleByRole() {
        Reader admin = saveReader("Role Admin", "34970000008", adminRole());
        Reader operator = saveReader("Role Operator", "34970000009", operatorRole());

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
        Reader match = saveReader("Combined Alice", "34970000010", adminRole());
        saveReader("Combined Alice", "34970000011", operatorRole());
        saveCommentator("Combined Alice", "34970000016", adminRole());

        Page<Long> result = personRepository.findAdminPageIds(
                "Combined",
                "00010",
                "reader",
                "ROLE_ADMIN",
                PageRequest.of(0, 10)
        );

        assertEquals(1, result.getTotalElements());
        assertEquals(match.getId(), result.getContent().get(0));
    }

    @Test
    void shouldNotDuplicatePersonWithMultipleRolesOrCountRolesAsPeople() {
        Reader person = saveReader("Multiple Roles", "34970000012", operatorRole(), adminRole());

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
        Reader person = saveReader("Batch Roles", "34970000013", operatorRole(), adminRole());

        List<Person> people = personRepository.findAllByIdInWithRoles(List.of(person.getId()));

        assertEquals(1, people.size());
        assertEquals(2, people.get(0).getRoles().size());
    }

    @Test
    void shouldFindPersonWithoutRolesWhenNoRoleFilterIsApplied() {
        Reader person = saveReader("No Roles", "34970000014");

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
        Reader admin = saveReader("Locked Admin", "34970000015", adminRole(), operatorRole());

        List<Person> administrators = personRepository.findPeopleByRoleForUpdate("ROLE_ADMIN");

        assertTrue(administrators.stream().anyMatch(person -> person.getId().equals(admin.getId())));
        assertEquals(
                administrators.size(),
                administrators.stream().map(Person::getId).distinct().count()
        );
    }

    private Reader saveReader(String name, String phoneNumber, Role... roles) {
        Reader reader = new Reader();
        fillPerson(reader, name, phoneNumber, roles);
        entityManager.persist(reader);
        entityManager.flush();
        entityManager.clear();
        return reader;
    }

    private Commentator saveCommentator(String name, String phoneNumber, Role... roles) {
        Commentator commentator = new Commentator();
        fillPerson(commentator, name, phoneNumber, roles);
        entityManager.persist(commentator);
        entityManager.flush();
        entityManager.clear();
        return commentator;
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
