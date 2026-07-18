package com.eventoscelebrativos.service;

import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.model.Commentator;
import com.eventoscelebrativos.model.EucharisticMinister;
import com.eventoscelebrativos.model.MinisterOfTheWord;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.model.Priest;
import com.eventoscelebrativos.model.Reader;
import com.eventoscelebrativos.repository.CommentatorRepository;
import com.eventoscelebrativos.repository.EucharisticMinisterRepository;
import com.eventoscelebrativos.repository.MinisterOfTheWordRepository;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.repository.PriestRepository;
import com.eventoscelebrativos.repository.ReaderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    private PersonMinistryConsistencyService consistencyService;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private PersonMinistryRepository personMinistryRepository;

    @Autowired
    private ReaderRepository readerRepository;

    @Autowired
    private CommentatorRepository commentatorRepository;

    @Autowired
    private PriestRepository priestRepository;

    @Autowired
    private MinisterOfTheWordRepository ministerOfTheWordRepository;

    @Autowired
    private EucharisticMinisterRepository eucharisticMinisterRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @ParameterizedTest
    @EnumSource(MinistryType.class)
    void shouldMatchLegacyRepositoryIdsForMigratedData(MinistryType ministryType) {
        List<Long> legacyIds = legacyPeople(ministryType).stream()
                .sorted(Comparator.comparing(Person::getName).thenComparing(Person::getId))
                .map(Person::getId)
                .toList();

        Page<Person> parallelPage = readService.findActivePeopleByMinistry(
                ministryType,
                PageRequest.of(0, Math.max(legacyIds.size(), 1))
        );

        assertEquals(legacyIds, parallelPage.getContent().stream().map(Person::getId).toList());
        assertEquals(legacyIds.size(), parallelPage.getTotalElements());
    }

    @Test
    void shouldFindActivePeopleByMinistryWithSafePaginationAndNoDuplicates() {
        Reader first = savePerson(new Reader(), "000 Parallel Alpha", "34973000001");
        Reader second = savePerson(new Reader(), "000 Parallel Beta", "34973000002");
        Reader third = savePerson(new Reader(), "000 Parallel Beta", "34973000003");
        Reader inactive = savePerson(new Reader(), "000 Parallel Inactive", "34973000004");

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
    void shouldReturnEmptyPageWhenMinistryHasNoActivePeople() {
        jdbcTemplate.update("DELETE FROM tb_person_ministry WHERE ministry_type = ?", MinistryType.PRIEST.name());

        Page<Person> result = readService.findActivePeopleByMinistry(MinistryType.PRIEST, PageRequest.of(0, 10));

        assertTrue(result.isEmpty());
        assertEquals(0, result.getTotalElements());
    }

    @Test
    void shouldLoadActiveMinistriesByPersonIdsWithEmptySetsAndUniqueIds() {
        Reader multiMinistry = savePerson(new Reader(), "Batch Multi Ministry", "34973000005");
        Reader withoutMinistry = savePerson(new Reader(), "Batch Without Ministry", "34973000006");
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
        Reader reader = savePerson(new Reader(), "000 Additional Ministry Reader", "34973000007");
        saveMinistry(reader, MinistryType.READER, true);
        saveMinistry(reader, MinistryType.COMMENTATOR, true);

        assertContainsPerson(readService.findActivePeopleByMinistry(MinistryType.READER, PageRequest.of(0, 20)), reader);
        assertContainsPerson(readService.findActivePeopleByMinistry(MinistryType.COMMENTATOR, PageRequest.of(0, 20)), reader);
        assertFalse(commentatorRepository.findAll().stream().map(Person::getId).toList().contains(reader.getId()));

        PersonMinistryConsistencyReport report = consistencyService.audit(5);
        PersonMinistryConsistencyEntry detail = findDetail(report, reader.getId());

        assertNotNull(detail);
        assertFalse(detail.hasIssue());
        assertEquals(MinistryType.READER, detail.expectedMinistry());
        assertEquals(Set.of(MinistryType.READER, MinistryType.COMMENTATOR), detail.activeMinistries());
        assertEquals(Set.of(MinistryType.COMMENTATOR), detail.additionalMinistries());
        assertEquals(1, countPersonMinistry(reader.getId(), MinistryType.COMMENTATOR, true));
    }

    @Test
    void shouldReportMissingExpectedMinistryWithoutChangingData() {
        Reader reader = savePerson(new Reader(), "000 Missing Ministry Reader", "34973000008");
        saveMinistry(reader, MinistryType.READER, true);
        personMinistryRepository.deleteAllByPersonId(reader.getId());
        personMinistryRepository.flush();

        assertTrue(readerRepository.findAll().stream().map(Person::getId).toList().contains(reader.getId()));
        assertDoesNotContainPerson(readService.findActivePeopleByMinistry(MinistryType.READER, PageRequest.of(0, 20)), reader);

        PersonMinistryConsistencyReport report = consistencyService.audit(5);
        PersonMinistryConsistencyEntry issue = findIssue(report, reader.getId());

        assertEquals(PersonMinistryConsistencyIssueType.MISSING_EXPECTED_MINISTRY, issue.issueType());
        assertEquals(MinistryType.READER, issue.expectedMinistry());
        assertEquals(Set.of(), issue.activeMinistries());
        assertEquals(0, countPersonMinistries(reader.getId()));
    }

    @Test
    void shouldReportInactiveExpectedMinistryAndKeepAdditionalActive() {
        Reader reader = savePerson(new Reader(), "000 Inactive Ministry Reader", "34973000009");
        saveMinistry(reader, MinistryType.READER, false);
        saveMinistry(reader, MinistryType.COMMENTATOR, true);

        assertDoesNotContainPerson(readService.findActivePeopleByMinistry(MinistryType.READER, PageRequest.of(0, 20)), reader);
        assertContainsPerson(readService.findActivePeopleByMinistry(MinistryType.COMMENTATOR, PageRequest.of(0, 20)), reader);

        PersonMinistryConsistencyReport report = consistencyService.audit(5);
        PersonMinistryConsistencyEntry issue = findIssue(report, reader.getId());

        assertEquals(PersonMinistryConsistencyIssueType.EXPECTED_MINISTRY_INACTIVE, issue.issueType());
        assertEquals(MinistryType.READER, issue.expectedMinistry());
        assertEquals(Set.of(MinistryType.COMMENTATOR), issue.activeMinistries());
        assertEquals(Set.of(MinistryType.COMMENTATOR), issue.additionalMinistries());
        assertEquals(1, countPersonMinistry(reader.getId(), MinistryType.READER, false));
    }

    @Test
    void shouldRejectInvalidReadAndAuditArguments() {
        assertThrows(BusinessException.class,
                () -> readService.findActivePeopleByMinistry(null, PageRequest.of(0, 10)));
        assertThrows(BusinessException.class,
                () -> consistencyService.audit(0));
    }

    @Test
    void shouldNotExposeSensitiveDataInConsistencyReportTypes() {
        List<String> entryFields = recordComponentNames(PersonMinistryConsistencyEntry.class);
        List<String> reportFields = recordComponentNames(PersonMinistryConsistencyReport.class);

        assertFalse(entryFields.contains("password"));
        assertFalse(entryFields.contains("phoneNumber"));
        assertFalse(entryFields.contains("roles"));
        assertFalse(reportFields.contains("password"));
        assertFalse(reportFields.contains("phoneNumber"));
        assertFalse(reportFields.contains("roles"));
    }

    private List<Person> legacyPeople(MinistryType ministryType) {
        return switch (ministryType) {
            case READER -> List.copyOf(readerRepository.findAll());
            case COMMENTATOR -> List.copyOf(commentatorRepository.findAll());
            case PRIEST -> List.copyOf(priestRepository.findAll());
            case MINISTER_OF_THE_WORD -> List.copyOf(ministerOfTheWordRepository.findAll());
            case EUCHARISTIC_MINISTER -> List.copyOf(eucharisticMinisterRepository.findAll());
        };
    }

    private <T extends Person> T savePerson(T person, String name, String phoneNumber) {
        person.setName(name);
        person.setPhoneNumber(phoneNumber + UUID.randomUUID().toString().replace("-", "").substring(0, 4));
        person.setBirthdayDate(BIRTHDAY);
        person.setPassword("encoded-password");
        T saved = personRepository.saveAndFlush(person);
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

    private PersonMinistryConsistencyEntry findDetail(PersonMinistryConsistencyReport report, Long personId) {
        return report.details().stream()
                .filter(detail -> detail.personId().equals(personId))
                .findFirst()
                .orElse(null);
    }

    private PersonMinistryConsistencyEntry findIssue(PersonMinistryConsistencyReport report, Long personId) {
        return report.issues().stream()
                .filter(issue -> issue.personId().equals(personId))
                .findFirst()
                .orElseThrow();
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

    private List<String> recordComponentNames(Class<? extends Record> recordType) {
        return List.of(recordType.getRecordComponents()).stream()
                .map(RecordComponent::getName)
                .toList();
    }
}
