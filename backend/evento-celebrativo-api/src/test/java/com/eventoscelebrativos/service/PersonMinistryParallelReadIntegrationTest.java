package com.eventoscelebrativos.service;

import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
@Transactional
class PersonMinistryParallelReadIntegrationTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);

    @Autowired
    private PersonMinistryReadService readService;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private PersonMinistryRepository personMinistryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldFindActivePeopleByMinistryWithSafePaginationAndNoDuplicates() {
        Person first = savePerson("000 Parallel Alpha", "34973000001");
        Person second = savePerson("000 Parallel Beta", "34973000002");
        Person third = savePerson("000 Parallel Beta", "34973000003");
        Person inactive = savePerson("000 Parallel Inactive", "34973000004");

        saveMinistry(first, MinistryType.READER, true);
        saveMinistry(second, MinistryType.READER, true);
        saveMinistry(second, MinistryType.COMMENTATOR, true);
        saveMinistry(third, MinistryType.READER, true);
        saveMinistry(inactive, MinistryType.READER, false);

        Page<Person> firstPage = readService.findActivePeopleByMinistry(MinistryType.READER, PageRequest.of(0, 2));
        Page<Person> secondPage = readService.findActivePeopleByMinistry(MinistryType.READER, PageRequest.of(1, 2));
        Page<Person> emptyPage = readService.findActivePeopleByMinistry(MinistryType.READER, PageRequest.of(999, 2));

        assertEquals(List.of(first.getId(), second.getId()), firstPage.getContent().stream().map(Person::getId).toList());
        assertEquals(third.getId(), secondPage.getContent().get(0).getId());
        assertFalse(firstPage.getContent().stream().map(Person::getId).toList().contains(inactive.getId()));
        assertFalse(secondPage.getContent().stream().map(Person::getId).toList().contains(inactive.getId()));
        assertEquals(countActivePeopleByMinistry(MinistryType.READER), firstPage.getTotalElements());
        assertEquals((int) Math.ceil(firstPage.getTotalElements() / 2.0), firstPage.getTotalPages());
        assertEquals(2, firstPage.getSize());
        assertTrue(emptyPage.isEmpty());
    }

    @Test
    void shouldFindAllActivePeopleByMinistryOrderedWithoutDuplicates() {
        Person first = savePerson("000 Full Read Alpha", "34973000011");
        Person second = savePerson("000 Full Read Beta", "34973000012");
        Person third = savePerson("000 Full Read Beta", "34973000013");
        Person inactive = savePerson("000 Full Read Inactive", "34973000014");

        saveMinistry(first, MinistryType.READER, true);
        saveMinistry(second, MinistryType.READER, true);
        saveMinistry(second, MinistryType.COMMENTATOR, true);
        saveMinistry(third, MinistryType.READER, true);
        saveMinistry(inactive, MinistryType.READER, false);

        List<Person> result = readService.findAllActivePeopleByMinistry(MinistryType.READER);
        List<Long> resultIds = result.stream().map(Person::getId).toList();

        assertTrue(resultIds.contains(first.getId()));
        assertTrue(resultIds.contains(second.getId()));
        assertTrue(resultIds.contains(third.getId()));
        assertFalse(resultIds.contains(inactive.getId()));
        assertEquals(1, resultIds.stream().filter(second.getId()::equals).count());
        assertEquals(result.stream()
                .sorted(Comparator.comparing(Person::getName).thenComparing(Person::getId))
                .map(Person::getId)
                .toList(), resultIds);
    }

    @Test
    void shouldReturnEmptyPageWhenMinistryHasNoActivePeople() {
        jdbcTemplate.update("DELETE FROM tb_person_ministry WHERE ministry_type = ?", MinistryType.PRIEST.name());

        Page<Person> result = readService.findActivePeopleByMinistry(MinistryType.PRIEST, PageRequest.of(0, 10));

        assertTrue(result.isEmpty());
        assertEquals(0, result.getTotalElements());
    }

    @Test
    void shouldLoadActiveMinistriesByPersonIdsWithEmptySetsAndUniqueIds() {
        Person multiMinistry = savePerson("Batch Multi Ministry", "34973000005");
        Person withoutMinistry = savePerson("Batch Without Ministry", "34973000006");
        saveMinistry(multiMinistry, MinistryType.READER, true);
        saveMinistry(multiMinistry, MinistryType.COMMENTATOR, true);

        Map<Long, Set<MinistryType>> result = readService.findActiveMinistriesByPersonIds(List.of(
                multiMinistry.getId(),
                withoutMinistry.getId(),
                multiMinistry.getId()
        ));

        assertEquals(2, result.size());
        assertEquals(List.of(withoutMinistry.getId(), multiMinistry.getId()).stream().sorted().toList(),
                result.keySet().stream().toList());
        assertEquals(Set.of(MinistryType.READER, MinistryType.COMMENTATOR), result.get(multiMinistry.getId()));
        assertEquals(Set.of(), result.get(withoutMinistry.getId()));
        assertTrue(readService.findActiveMinistriesByPersonIds(List.of()).isEmpty());
    }

    @Test
    void shouldTreatAdditionalMinistryAsValidCapability() {
        Person person = savePerson("000 Additional Ministry Reader", "34973000007");
        saveMinistry(person, MinistryType.READER, true);
        saveMinistry(person, MinistryType.COMMENTATOR, true);

        assertContainsPerson(readService.findActivePeopleByMinistry(MinistryType.READER, PageRequest.of(0, 20)), person);
        assertContainsPerson(readService.findActivePeopleByMinistry(MinistryType.COMMENTATOR, PageRequest.of(0, 20)), person);
        assertEquals(1, countPersonMinistry(person.getId(), MinistryType.COMMENTATOR, true));
    }

    @Test
    void shouldExcludePersonFromReadWhenAllMinistriesAreRemovedWithoutChangingOtherData() {
        Person person = savePerson("000 Missing Ministry Reader", "34973000008");
        saveMinistry(person, MinistryType.READER, true);
        personMinistryRepository.deleteAllByPersonId(person.getId());
        personMinistryRepository.flush();

        assertTrue(personRepository.existsById(person.getId()));
        assertDoesNotContainPerson(readService.findActivePeopleByMinistry(MinistryType.READER, PageRequest.of(0, 20)), person);
        assertEquals(0, countPersonMinistries(person.getId()));
    }

    @Test
    void shouldExcludePersonFromReadWhenMinistryIsInactiveAndKeepAdditionalActive() {
        Person person = savePerson("000 Inactive Ministry Reader", "34973000009");
        saveMinistry(person, MinistryType.READER, false);
        saveMinistry(person, MinistryType.COMMENTATOR, true);

        assertDoesNotContainPerson(readService.findActivePeopleByMinistry(MinistryType.READER, PageRequest.of(0, 20)), person);
        assertContainsPerson(readService.findActivePeopleByMinistry(MinistryType.COMMENTATOR, PageRequest.of(0, 20)), person);
        assertEquals(1, countPersonMinistry(person.getId(), MinistryType.READER, false));
    }

    @Test
    void shouldRejectInvalidReadArguments() {
        assertThrows(BusinessException.class,
                () -> readService.findActivePeopleByMinistry(null, PageRequest.of(0, 10)));
    }

    private Person savePerson(String name, String phoneNumber) {
        Person person = new Person();
        person.setName(name);
        person.setPhoneNumber(phoneNumber + UUID.randomUUID().toString().replace("-", "").substring(0, 4));
        person.setBirthdayDate(BIRTHDAY);
        person.setPassword("encoded-password");
        Person saved = personRepository.saveAndFlush(person);
        personMinistryRepository.flush();
        return saved;
    }

    private void saveMinistry(Person person, MinistryType ministryType, boolean active) {
        PersonMinistry ministry = new PersonMinistry(person, ministryType);
        ministry.setActive(active);
        personMinistryRepository.saveAndFlush(ministry);
    }

    private void assertContainsPerson(Page<Person> page, Person person) {
        assertTrue(page.getContent().stream().map(Person::getId).toList().contains(person.getId()));
    }

    private void assertDoesNotContainPerson(Page<Person> page, Person person) {
        assertFalse(page.getContent().stream().map(Person::getId).toList().contains(person.getId()));
    }

    private long countActivePeopleByMinistry(MinistryType ministryType) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(DISTINCT person_id)
                FROM tb_person_ministry
                WHERE ministry_type = ?
                  AND active = TRUE
                """,
                Long.class,
                ministryType.name()
        );
        return count == null ? 0 : count;
    }

    private int countPersonMinistries(Long personId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_person_ministry WHERE person_id = ?",
                Integer.class,
                personId
        );
        return count == null ? 0 : count;
    }

    private int countPersonMinistry(Long personId, MinistryType ministryType, boolean active) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM tb_person_ministry
                WHERE person_id = ?
                  AND ministry_type = ?
                  AND active = ?
                """,
                Integer.class,
                personId,
                ministryType.name(),
                active
        );
        return count == null ? 0 : count;
    }
}
