package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.model.UserAccount;
import com.eventoscelebrativos.model.UserAccountRole;
import com.eventoscelebrativos.service.LegacyMinistryTypeResolver;
import com.eventoscelebrativos.service.PersonMinistryReadService;
import com.eventoscelebrativos.service.impl.PersonMinistryReadServiceImpl;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.eventoscelebrativos.support.LegacyMinistryTestFactory.personMinistry;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({PersonMinistryReadServiceImpl.class, LegacyMinistryTypeResolver.class})
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
    private PersonMinistryReadService personMinistryReadService;

    @Autowired
    private MinistryRepository ministryRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

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
                null,
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
                ministryId(MinistryType.READER),
                null,
                null,
                null,
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
                null,
                null,
                null,
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
                ministryId(MinistryType.READER),
                "ROLE_ADMIN",
                null,
                null,
                null,
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
                null,
                null,
                null,
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
                null,
                null,
                null,
                PageRequest.of(0, 10)
        );

        assertEquals(1, result.getTotalElements());
        assertEquals(person.getId(), result.getContent().get(0));
    }

    @Test
    void shouldFilterPeopleByPersonActive() {
        Person active = savePersonWithoutAccount("Active Filter Person", "34970100001");
        Person inactive = savePersonWithoutAccount("Inactive Filter Person", "34970100002");
        Person managedInactive = personRepository.findById(inactive.getId()).orElseThrow();
        managedInactive.deactivate();
        personRepository.saveAndFlush(managedInactive);
        entityManager.clear();

        Page<Long> activeResult = personRepository.findAdminPageIds(
                "Filter Person", null, null, null, true, null, null, PageRequest.of(0, 10));
        Page<Long> inactiveResult = personRepository.findAdminPageIds(
                "Filter Person", null, null, null, false, null, null, PageRequest.of(0, 10));

        assertTrue(activeResult.getContent().contains(active.getId()));
        assertFalse(activeResult.getContent().contains(inactive.getId()));
        assertTrue(inactiveResult.getContent().contains(inactive.getId()));
        assertFalse(inactiveResult.getContent().contains(active.getId()));
    }

    @Test
    void shouldFilterPeopleByAccountExists() {
        Person withAccount = savePersonWithRole("Account Exists Person", "34970100003", operatorRole());
        Person withoutAccount = savePersonWithoutAccount("Account Missing Person", "34970100004");

        Page<Long> withAccountResult = personRepository.findAdminPageIds(
                "Account", null, null, null, null, true, null, PageRequest.of(0, 10));
        Page<Long> withoutAccountResult = personRepository.findAdminPageIds(
                "Account", null, null, null, null, false, null, PageRequest.of(0, 10));

        assertTrue(withAccountResult.getContent().contains(withAccount.getId()));
        assertFalse(withAccountResult.getContent().contains(withoutAccount.getId()));
        assertTrue(withoutAccountResult.getContent().contains(withoutAccount.getId()));
        assertFalse(withoutAccountResult.getContent().contains(withAccount.getId()));
    }

    @Test
    void shouldFilterPeopleByAccountEnabled() {
        Person enabledAccountPerson = savePersonWithRole("Enabled Account Person", "34970100005", operatorRole());
        Person disabledAccountPerson = savePersonWithRole("Disabled Account Person", "34970100006", operatorRole());
        UserAccount disabledAccount = userAccountRepository.findByPersonId(disabledAccountPerson.getId()).orElseThrow();
        disabledAccount.disable(LocalDateTime.now().withNano(0));
        userAccountRepository.save(disabledAccount);
        entityManager.flush();
        entityManager.clear();

        Page<Long> enabledResult = personRepository.findAdminPageIds(
                "Account Person", null, null, null, null, null, true, PageRequest.of(0, 10));
        Page<Long> disabledResult = personRepository.findAdminPageIds(
                "Account Person", null, null, null, null, null, false, PageRequest.of(0, 10));

        assertTrue(enabledResult.getContent().contains(enabledAccountPerson.getId()));
        assertFalse(enabledResult.getContent().contains(disabledAccountPerson.getId()));
        assertTrue(disabledResult.getContent().contains(disabledAccountPerson.getId()));
        assertFalse(disabledResult.getContent().contains(enabledAccountPerson.getId()));
    }

    @Test
    void shouldUseConstantNumberOfQueriesRegardlessOfPageItemCount() {
        long smallBatchQueries = runFullAdminListingPipeline("NPlusOne Small Batch", 2, "34971000");
        long largeBatchQueries = runFullAdminListingPipeline("NPlusOne Large Batch", 9, "34972000");

        assertTrue(smallBatchQueries > 0, "A contagem de statements deveria refletir consultas reais, nao um falso positivo de 0");
        assertEquals(smallBatchQueries, largeBatchQueries,
                "A quantidade de consultas SQL deve ser constante independentemente do numero de itens na pagina");
    }

    @Test
    void shouldPersistAndReloadPersonWithSamePublicId() {
        Person person = newPerson(
                "Public Id Person",
                "34979999999"
        );

        UUID publicIdBeforePersist = person.getPublicId();

        entityManager.persist(person);
        entityManager.flush();
        entityManager.clear();

        Person reloaded = personRepository
                .findById(person.getId())
                .orElseThrow();

        assertNotSame(person, reloaded);
        assertEquals(publicIdBeforePersist, reloaded.getPublicId());
        assertEquals(person, reloaded);
        assertEquals(person.hashCode(), reloaded.hashCode());
    }


    /**
     * entityManager.clear() antes da medicao (apos o setup de cada lote) esvazia o contexto de
     * persistencia para que as consultas medidas nao sejam satisfeitas pelo cache de primeiro nivel;
     * statistics.clear() reseta o contador do Hibernate para isolar a medicao de cada lote entre as
     * duas chamadas sequenciais desta mesma instancia de SessionFactory.
     */
    private long runFullAdminListingPipeline(String namePrefix, int personCount, String phonePrefix) {
        entityManager.clear();
        IntStream.rangeClosed(1, personCount).forEach(i -> {
            Person person = savePersonWithRole(namePrefix + " " + i, phonePrefix + String.format("%03d", i), operatorRole());
            entityManager.persist(personMinistry(person, MinistryType.READER, ministryRepository));
            entityManager.flush();
        });
        entityManager.clear();

        Statistics statistics = hibernateStatistics();
        statistics.clear();

        Page<Long> idPage = personRepository.findAdminPageIds(
                namePrefix, null, null, null, null, null, null, PageRequest.of(0, personCount + 5));
        List<Long> ids = idPage.getContent();
        assertEquals(personCount, ids.size());

        Map<Long, Person> peopleById = personRepository.findAllByIdIn(ids).stream()
                .collect(Collectors.toMap(Person::getId, Function.identity()));
        assertEquals(personCount, peopleById.size());
        personMinistryReadService.findActiveMinistriesByPersonIds(ids);
        userAccountRoleRepository.findRoleAuthoritiesByPersonIdsGroupedByPerson(ids);
        userAccountRepository.findAccountStatesByPersonIdInGroupedByPerson(ids);

        return statistics.getPrepareStatementCount();
    }

    private Statistics hibernateStatistics() {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        sessionFactory.getStatistics().setStatisticsEnabled(true);
        return sessionFactory.getStatistics();
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
        return new Person(name, phoneNumber, LocalDate.of(1990, 1, 10));
    }

    private void saveMinistry(Person person, MinistryType ministryType) {
        entityManager.persist(personMinistry(person, ministryType, ministryRepository));
        entityManager.flush();
        entityManager.clear();
    }

    private Long ministryId(MinistryType ministryType) {
        return com.eventoscelebrativos.support.LegacyMinistryTestFactory
                .persistentMinistry(ministryType, ministryRepository)
                .getId();
    }




    private Role operatorRole() {
        return roleRepository.findByAuthority("ROLE_OPERATOR").orElseThrow();
    }

    private Role adminRole() {
        return roleRepository.findByAuthority("ROLE_ADMIN").orElseThrow();
    }




}
