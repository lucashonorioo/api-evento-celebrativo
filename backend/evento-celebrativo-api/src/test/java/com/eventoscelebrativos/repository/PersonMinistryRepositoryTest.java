package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class PersonMinistryRepositoryTest {

    @Autowired
    private PersonMinistryRepository personMinistryRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldSaveAndFindPersonMinistry() {
        Person reader = saveReader("Ministry Person", "34971000001");
        PersonMinistry ministry = personMinistryRepository.saveAndFlush(new PersonMinistry(reader, MinistryType.READER));

        assertTrue(personMinistryRepository.existsByPersonIdAndMinistryType(reader.getId(), MinistryType.READER));
        assertEquals(
                ministry.getId(),
                personMinistryRepository.findByPersonIdAndMinistryType(reader.getId(), MinistryType.READER).orElseThrow().getId()
        );
    }

    @Test
    void shouldListAllMinistriesForPerson() {
        Person reader = saveReader("Multi Ministry Person", "34971000002");
        personMinistryRepository.save(new PersonMinistry(reader, MinistryType.READER));
        personMinistryRepository.save(new PersonMinistry(reader, MinistryType.COMMENTATOR));
        personMinistryRepository.flush();

        List<MinistryType> ministries = personMinistryRepository.findAllByPersonId(reader.getId()).stream()
                .map(PersonMinistry::getMinistryType)
                .toList();

        assertEquals(2, ministries.size());
        assertTrue(ministries.contains(MinistryType.READER));
        assertTrue(ministries.contains(MinistryType.COMMENTATOR));
    }

    @Test
    void shouldEnforceUniquePersonAndMinistryType() {
        Person reader = saveReader("Unique Ministry Person", "34971000003");
        personMinistryRepository.saveAndFlush(new PersonMinistry(reader, MinistryType.READER));

        assertThrows(DataIntegrityViolationException.class,
                () -> personMinistryRepository.saveAndFlush(new PersonMinistry(reader, MinistryType.READER)));
    }

    @Test
    void shouldPersistEnumAsConstraintValue() {
        Person reader = saveReader("Enum Ministry Person", "34971000004");
        PersonMinistry ministry = personMinistryRepository.saveAndFlush(new PersonMinistry(reader, MinistryType.EUCHARISTIC_MINISTER));

        String value = jdbcTemplate.queryForObject(
                "SELECT ministry_type FROM tb_person_ministry WHERE id = ?",
                String.class,
                ministry.getId()
        );

        assertEquals("EUCHARISTIC_MINISTER", value);
    }

    @Test
    void shouldFillTimestampsWhenSaving() {
        Person reader = saveReader("Timestamp Ministry Person", "34971000005");
        PersonMinistry ministry = personMinistryRepository.saveAndFlush(new PersonMinistry(reader, MinistryType.READER));

        assertNotNull(ministry.getCreatedAt());
        assertNotNull(ministry.getUpdatedAt());
    }

    @Test
    void shouldReactivateInactiveMinistryWhenUpdated() {
        Person reader = saveReader("Inactive Ministry Person", "34971000006");
        PersonMinistry ministry = new PersonMinistry(reader, MinistryType.READER);
        ministry.setActive(false);
        ministry = personMinistryRepository.saveAndFlush(ministry);

        ministry.activate();
        PersonMinistry reactivated = personMinistryRepository.saveAndFlush(ministry);

        assertTrue(reactivated.getActive());
    }

    @Test
    void shouldDeleteAllMinistriesByPersonId() {
        Person reader = saveReader("Delete Ministry Person", "34971000007");
        personMinistryRepository.save(new PersonMinistry(reader, MinistryType.READER));
        personMinistryRepository.save(new PersonMinistry(reader, MinistryType.COMMENTATOR));
        personMinistryRepository.flush();

        personMinistryRepository.deleteAllByPersonId(reader.getId());
        entityManager.flush();
        entityManager.clear();

        assertTrue(personMinistryRepository.findAllByPersonId(reader.getId()).isEmpty());
    }

    @Test
    void shouldNotCascadeDeletePersonWhenDeletingMinistry() {
        Person reader = saveReader("Cascade Ministry Person", "34971000008");
        PersonMinistry ministry = personMinistryRepository.saveAndFlush(new PersonMinistry(reader, MinistryType.READER));

        personMinistryRepository.delete(ministry);
        personMinistryRepository.flush();
        entityManager.clear();

        assertNotNull(entityManager.find(Person.class, reader.getId()));
    }

    @Test
    void shouldPageDistinctActivePersonIdsByMinistryOrderedByNameAndId() {
        Person first = saveReader("000 Ministry Page Same", "34971000101");
        Person second = saveReader("000 Ministry Page Same", "34971000102");
        Person inactive = saveReader("000 Ministry Page Inactive", "34971000103");

        personMinistryRepository.save(new PersonMinistry(first, MinistryType.READER));
        personMinistryRepository.save(new PersonMinistry(second, MinistryType.READER));
        personMinistryRepository.save(new PersonMinistry(second, MinistryType.COMMENTATOR));
        PersonMinistry inactiveMinistry = new PersonMinistry(inactive, MinistryType.READER);
        inactiveMinistry.setActive(false);
        personMinistryRepository.save(inactiveMinistry);
        personMinistryRepository.flush();

        Page<Long> result = personMinistryRepository.findActivePersonIdsByMinistryType(
                MinistryType.READER,
                PageRequest.of(0, 2)
        );

        assertEquals(List.of(first.getId(), second.getId()), result.getContent());
        assertEquals(countActivePeopleByMinistry(MinistryType.READER), result.getTotalElements());
        assertFalse(result.getContent().contains(inactive.getId()));
    }

    @Test
    void shouldLoadActiveMinistryTypesByPersonIdsInOneBatchProjection() {
        Person reader = saveReader("Batch Active Ministry Person", "34971000104");
        personMinistryRepository.save(new PersonMinistry(reader, MinistryType.READER));
        PersonMinistry inactiveCommentator = new PersonMinistry(reader, MinistryType.COMMENTATOR);
        inactiveCommentator.setActive(false);
        personMinistryRepository.save(inactiveCommentator);
        personMinistryRepository.flush();

        List<PersonMinistryRepository.PersonMinistryTypeView> result =
                personMinistryRepository.findActiveMinistryTypesByPersonIds(List.of(reader.getId()));

        assertEquals(1, result.size());
        assertEquals(reader.getId(), result.get(0).getPersonId());
        assertEquals(MinistryType.READER, result.get(0).getMinistryType());
    }

    @Test
    void shouldLoadAllMinistryStatusesByPersonIdsInOneBatchProjection() {
        Person reader = saveReader("Batch Status Ministry Person", "34971000105");
        personMinistryRepository.save(new PersonMinistry(reader, MinistryType.READER));
        PersonMinistry inactiveCommentator = new PersonMinistry(reader, MinistryType.COMMENTATOR);
        inactiveCommentator.setActive(false);
        personMinistryRepository.save(inactiveCommentator);
        personMinistryRepository.flush();

        List<PersonMinistryRepository.PersonMinistryStatusView> result =
                personMinistryRepository.findAllMinistryStatusesByPersonIds(List.of(reader.getId()));

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(row ->
                row.getPersonId().equals(reader.getId())
                        && row.getMinistryType() == MinistryType.READER
                        && Boolean.TRUE.equals(row.getActive())));
        assertTrue(result.stream().anyMatch(row ->
                row.getPersonId().equals(reader.getId())
                        && row.getMinistryType() == MinistryType.COMMENTATOR
                        && Boolean.FALSE.equals(row.getActive())));
    }

    @Test
    void shouldReturnMinistryTypeWhenActiveAndCoordinator() {
        Person person = saveReader("Coordinator Person", "34971000201");
        PersonMinistry ministry = new PersonMinistry(person, MinistryType.READER);
        ministry.grantCoordination();
        personMinistryRepository.saveAndFlush(ministry);

        List<MinistryType> result = personMinistryRepository.findActiveCoordinatedMinistryTypesByPersonId(person.getId());

        assertEquals(List.of(MinistryType.READER), result);
    }

    @Test
    void shouldNotReturnActiveMinistryThatIsNotCoordinated() {
        Person person = saveReader("Active Non Coordinator Person", "34971000202");
        personMinistryRepository.saveAndFlush(new PersonMinistry(person, MinistryType.READER));

        List<MinistryType> result = personMinistryRepository.findActiveCoordinatedMinistryTypesByPersonId(person.getId());

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldNotReturnInactiveNonCoordinatedMinistry() {
        Person person = saveReader("Inactive Non Coordinator Person", "34971000203");
        PersonMinistry ministry = new PersonMinistry(person, MinistryType.READER);
        ministry.setActive(false);
        personMinistryRepository.saveAndFlush(ministry);

        List<MinistryType> result = personMinistryRepository.findActiveCoordinatedMinistryTypesByPersonId(person.getId());

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnAllActiveCoordinatedMinistryTypesOrderedByMinistryTypeAscending() {
        Person person = saveReader("Multi Coordinator Person", "34971000204");

        PersonMinistry reader = new PersonMinistry(person, MinistryType.READER);
        reader.grantCoordination();
        PersonMinistry priest = new PersonMinistry(person, MinistryType.PRIEST);
        priest.grantCoordination();
        PersonMinistry commentator = new PersonMinistry(person, MinistryType.COMMENTATOR);
        commentator.grantCoordination();
        PersonMinistry eucharisticMinister = new PersonMinistry(person, MinistryType.EUCHARISTIC_MINISTER);

        personMinistryRepository.save(reader);
        personMinistryRepository.save(priest);
        personMinistryRepository.save(commentator);
        personMinistryRepository.save(eucharisticMinister);
        personMinistryRepository.flush();

        List<MinistryType> result = personMinistryRepository.findActiveCoordinatedMinistryTypesByPersonId(person.getId());

        assertEquals(List.of(MinistryType.COMMENTATOR, MinistryType.PRIEST, MinistryType.READER), result);
    }

    @Test
    void shouldNotReturnCoordinatedMinistryFromAnotherPerson() {
        Person target = saveReader("Target Person", "34971000205");
        Person other = saveReader("Other Coordinator Person", "34971000206");

        PersonMinistry otherMinistry = new PersonMinistry(other, MinistryType.READER);
        otherMinistry.grantCoordination();
        personMinistryRepository.saveAndFlush(otherMinistry);

        List<MinistryType> result = personMinistryRepository.findActiveCoordinatedMinistryTypesByPersonId(target.getId());

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyListWhenPersonHasNoCoordinatedMinistry() {
        Person person = saveReader("No Ministry Person", "34971000207");

        List<MinistryType> result = personMinistryRepository.findActiveCoordinatedMinistryTypesByPersonId(person.getId());

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldExistWhenPersonMinistryTypeActiveAndCoordinator() {
        Person person = saveReader("Exists Coordinator Person", "34971000301");
        PersonMinistry ministry = new PersonMinistry(person, MinistryType.READER);
        ministry.grantCoordination();
        personMinistryRepository.saveAndFlush(ministry);

        assertTrue(personMinistryRepository.existsByPersonIdAndMinistryTypeAndActiveTrueAndCoordinatorTrue(
                person.getId(), MinistryType.READER));
    }

    @Test
    void shouldNotExistWhenCoordinatorFalse() {
        Person person = saveReader("Exists Non Coordinator Person", "34971000302");
        personMinistryRepository.saveAndFlush(new PersonMinistry(person, MinistryType.READER));

        assertFalse(personMinistryRepository.existsByPersonIdAndMinistryTypeAndActiveTrueAndCoordinatorTrue(
                person.getId(), MinistryType.READER));
    }

    @Test
    void shouldNotExistForAnotherMinistryType() {
        Person person = saveReader("Exists Other Ministry Person", "34971000303");
        PersonMinistry ministry = new PersonMinistry(person, MinistryType.READER);
        ministry.grantCoordination();
        personMinistryRepository.saveAndFlush(ministry);

        assertFalse(personMinistryRepository.existsByPersonIdAndMinistryTypeAndActiveTrueAndCoordinatorTrue(
                person.getId(), MinistryType.COMMENTATOR));
    }

    @Test
    void shouldNotExistForAnotherPerson() {
        Person target = saveReader("Exists Target Person", "34971000304");
        Person other = saveReader("Exists Other Person", "34971000305");
        PersonMinistry ministry = new PersonMinistry(other, MinistryType.READER);
        ministry.grantCoordination();
        personMinistryRepository.saveAndFlush(ministry);

        assertFalse(personMinistryRepository.existsByPersonIdAndMinistryTypeAndActiveTrueAndCoordinatorTrue(
                target.getId(), MinistryType.READER));
    }

    private Person saveReader(String name, String phoneNumber) {
        Person reader = new Person(name, phoneNumber, LocalDate.of(1990, 1, 10));
        entityManager.persist(reader);
        entityManager.flush();
        return reader;
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
}
