package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.Ministry;
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

import static com.eventoscelebrativos.support.LegacyMinistryTestFactory.personMinistry;
import static com.eventoscelebrativos.support.LegacyMinistryTestFactory.persistentMinistry;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class PersonMinistryRepositoryTest {

    @Autowired
    private PersonMinistryRepository personMinistryRepository;

    @Autowired
    private MinistryRepository ministryRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldSaveAndFindPersonMinistry() {
        Person reader = saveReader("Ministry Person", "34971000001");
        PersonMinistry ministry = personMinistryRepository.saveAndFlush(personMinistry(reader, MinistryType.READER, ministryRepository));

        assertTrue(personMinistryRepository.existsByPersonIdAndMinistryId(
                reader.getId(), ministry.getMinistry().getId()));
        assertEquals(
                ministry.getId(),
                personMinistryRepository.findByPersonIdAndMinistryId(
                        reader.getId(),
                        ministry.getMinistry().getId()
                ).orElseThrow().getId()
        );
        assertTrue(personMinistryRepository.existsByPersonIdAndMinistryType(reader.getId(), MinistryType.READER));
        assertEquals(
                ministry.getId(),
                personMinistryRepository.findByPersonIdAndMinistryType(reader.getId(), MinistryType.READER).orElseThrow().getId()
        );
    }

    @Test
    void shouldListAllMinistriesForPerson() {
        Person reader = saveReader("Multi Ministry Person", "34971000002");
        personMinistryRepository.save(personMinistry(reader, MinistryType.READER, ministryRepository));
        personMinistryRepository.save(personMinistry(reader, MinistryType.COMMENTATOR, ministryRepository));
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
        personMinistryRepository.saveAndFlush(personMinistry(reader, MinistryType.READER, ministryRepository));

        assertThrows(DataIntegrityViolationException.class,
                () -> personMinistryRepository.saveAndFlush(personMinistry(reader, MinistryType.READER, ministryRepository)));
    }

    @Test
    void shouldEnforceUniquePersonAndPersistentMinistryEvenWhenLegacyTypeDiffers() {
        Person reader = saveReader("Unique Persistent Ministry Person", "34971000013");
        Ministry readerMinistry = persistentMinistry(MinistryType.READER, ministryRepository);
        personMinistryRepository.saveAndFlush(new PersonMinistry(reader, readerMinistry, MinistryType.READER));

        assertThrows(DataIntegrityViolationException.class,
                () -> personMinistryRepository.saveAndFlush(
                        new PersonMinistry(reader, readerMinistry, MinistryType.COMMENTATOR)));
    }

    @Test
    void shouldPersistEnumAsConstraintValue() {
        Person reader = saveReader("Enum Ministry Person", "34971000004");
        PersonMinistry ministry = personMinistryRepository.saveAndFlush(personMinistry(reader, MinistryType.EUCHARISTIC_MINISTER, ministryRepository));

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
        PersonMinistry ministry = personMinistryRepository.saveAndFlush(personMinistry(reader, MinistryType.READER, ministryRepository));

        assertNotNull(ministry.getCreatedAt());
        assertNotNull(ministry.getUpdatedAt());
    }

    @Test
    void shouldReactivateInactiveMinistryWhenUpdated() {
        Person reader = saveReader("Inactive Ministry Person", "34971000006");
        PersonMinistry ministry = personMinistry(reader, MinistryType.READER, ministryRepository);
        ministry.setActive(false);
        ministry = personMinistryRepository.saveAndFlush(ministry);

        ministry.activate();
        PersonMinistry reactivated = personMinistryRepository.saveAndFlush(ministry);

        assertTrue(reactivated.getActive());
    }

    @Test
    void shouldDeleteAllMinistriesByPersonId() {
        Person reader = saveReader("Delete Ministry Person", "34971000007");
        personMinistryRepository.save(personMinistry(reader, MinistryType.READER, ministryRepository));
        personMinistryRepository.save(personMinistry(reader, MinistryType.COMMENTATOR, ministryRepository));
        personMinistryRepository.flush();

        personMinistryRepository.deleteAllByPersonId(reader.getId());
        entityManager.flush();
        entityManager.clear();

        assertTrue(personMinistryRepository.findAllByPersonId(reader.getId()).isEmpty());
    }

    @Test
    void shouldNotCascadeDeletePersonWhenDeletingMinistry() {
        Person reader = saveReader("Cascade Ministry Person", "34971000008");
        PersonMinistry ministry = personMinistryRepository.saveAndFlush(personMinistry(reader, MinistryType.READER, ministryRepository));

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

        personMinistryRepository.save(personMinistry(first, MinistryType.READER, ministryRepository));
        personMinistryRepository.save(personMinistry(second, MinistryType.READER, ministryRepository));
        personMinistryRepository.save(personMinistry(second, MinistryType.COMMENTATOR, ministryRepository));
        PersonMinistry inactiveMinistry = personMinistry(inactive, MinistryType.READER, ministryRepository);
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
    void shouldPageDistinctActivePersonIdsByPersistentMinistryOrderedByNameAndId() {
        Person first = saveReader("000 Catalog Page Same", "34971000111");
        Person second = saveReader("000 Catalog Page Same", "34971000112");
        Person inactive = saveReader("000 Catalog Page Inactive", "34971000113");
        Ministry readerMinistry = persistentMinistry(MinistryType.READER, ministryRepository);

        personMinistryRepository.save(personMinistry(first, MinistryType.READER, ministryRepository));
        personMinistryRepository.save(personMinistry(second, MinistryType.READER, ministryRepository));
        personMinistryRepository.save(personMinistry(second, MinistryType.COMMENTATOR, ministryRepository));
        PersonMinistry inactiveMinistry = personMinistry(inactive, MinistryType.READER, ministryRepository);
        inactiveMinistry.setActive(false);
        personMinistryRepository.save(inactiveMinistry);
        personMinistryRepository.flush();

        Page<Long> result = personMinistryRepository.findActivePersonIdsByMinistryId(
                readerMinistry.getId(),
                PageRequest.of(0, 2)
        );

        assertEquals(List.of(first.getId(), second.getId()), result.getContent());
        assertEquals(countActivePeopleByMinistryId(readerMinistry.getId()), result.getTotalElements());
        assertFalse(result.getContent().contains(inactive.getId()));
    }

    @Test
    void shouldLoadActiveMinistryTypesByPersonIdsInOneBatchProjection() {
        Person reader = saveReader("Batch Active Ministry Person", "34971000104");
        personMinistryRepository.save(personMinistry(reader, MinistryType.READER, ministryRepository));
        PersonMinistry inactiveCommentator = personMinistry(reader, MinistryType.COMMENTATOR, ministryRepository);
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
    void shouldLoadActivePersistentMinistriesByPersonIdsInOneBatchProjection() {
        Person reader = saveReader("Batch Active Catalog Ministry Person", "34971000114");
        personMinistryRepository.save(personMinistry(reader, MinistryType.READER, ministryRepository));
        PersonMinistry inactiveCommentator = personMinistry(reader, MinistryType.COMMENTATOR, ministryRepository);
        inactiveCommentator.setActive(false);
        personMinistryRepository.save(inactiveCommentator);
        personMinistryRepository.flush();

        List<PersonMinistryRepository.PersonMinistryCatalogView> result =
                personMinistryRepository.findActiveMinistriesByPersonIds(List.of(reader.getId()));

        assertEquals(1, result.size());
        assertEquals(reader.getId(), result.get(0).getPersonId());
        assertEquals(persistentMinistry(MinistryType.READER, ministryRepository).getId(), result.get(0).getMinistryId());
        assertEquals("LEITORES", result.get(0).getMinistryNormalizedName());
    }

    @Test
    void shouldLoadAllMinistryStatusesByPersonIdsInOneBatchProjection() {
        Person reader = saveReader("Batch Status Ministry Person", "34971000105");
        personMinistryRepository.save(personMinistry(reader, MinistryType.READER, ministryRepository));
        PersonMinistry inactiveCommentator = personMinistry(reader, MinistryType.COMMENTATOR, ministryRepository);
        inactiveCommentator.setActive(false);
        personMinistryRepository.save(inactiveCommentator);
        personMinistryRepository.flush();

        List<PersonMinistryRepository.PersonMinistryCatalogStatusView> result =
                personMinistryRepository.findAllMinistryStatusesByPersonIds(List.of(reader.getId()));

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(row ->
                row.getPersonId().equals(reader.getId())
                        && "LEITORES".equals(row.getMinistryNormalizedName())
                        && Boolean.TRUE.equals(row.getActive())));
        assertTrue(result.stream().anyMatch(row ->
                row.getPersonId().equals(reader.getId())
                        && "COMENTARISTAS".equals(row.getMinistryNormalizedName())
                        && Boolean.FALSE.equals(row.getActive())));
    }

    @Test
    void shouldReturnMinistryTypeWhenActiveAndCoordinator() {
        Person person = saveReader("Coordinator Person", "34971000201");
        PersonMinistry ministry = personMinistry(person, MinistryType.READER, ministryRepository);
        ministry.grantCoordination();
        personMinistryRepository.saveAndFlush(ministry);

        List<MinistryType> result = personMinistryRepository.findActiveCoordinatedMinistryTypesByPersonId(person.getId());

        assertEquals(List.of(MinistryType.READER), result);
    }

    @Test
    void shouldReturnPersistentMinistryWhenActiveAndCoordinator() {
        Person person = saveReader("Coordinator Catalog Person", "34971000211");
        PersonMinistry ministry = personMinistry(person, MinistryType.READER, ministryRepository);
        ministry.grantCoordination();
        personMinistryRepository.saveAndFlush(ministry);

        List<Ministry> result = personMinistryRepository.findActiveCoordinatedMinistriesByPersonId(person.getId());

        assertEquals(1, result.size());
        assertEquals(persistentMinistry(MinistryType.READER, ministryRepository).getId(), result.get(0).getId());
    }

    @Test
    void shouldNotReturnActiveMinistryThatIsNotCoordinated() {
        Person person = saveReader("Active Non Coordinator Person", "34971000202");
        personMinistryRepository.saveAndFlush(personMinistry(person, MinistryType.READER, ministryRepository));

        List<MinistryType> result = personMinistryRepository.findActiveCoordinatedMinistryTypesByPersonId(person.getId());

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldNotReturnInactiveNonCoordinatedMinistry() {
        Person person = saveReader("Inactive Non Coordinator Person", "34971000203");
        PersonMinistry ministry = personMinistry(person, MinistryType.READER, ministryRepository);
        ministry.setActive(false);
        personMinistryRepository.saveAndFlush(ministry);

        List<MinistryType> result = personMinistryRepository.findActiveCoordinatedMinistryTypesByPersonId(person.getId());

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnAllActiveCoordinatedMinistryTypesOrderedByMinistryTypeAscending() {
        Person person = saveReader("Multi Coordinator Person", "34971000204");

        PersonMinistry reader = personMinistry(person, MinistryType.READER, ministryRepository);
        reader.grantCoordination();
        PersonMinistry priest = personMinistry(person, MinistryType.PRIEST, ministryRepository);
        priest.grantCoordination();
        PersonMinistry commentator = personMinistry(person, MinistryType.COMMENTATOR, ministryRepository);
        commentator.grantCoordination();
        PersonMinistry eucharisticMinister = personMinistry(person, MinistryType.EUCHARISTIC_MINISTER, ministryRepository);

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

        PersonMinistry otherMinistry = personMinistry(other, MinistryType.READER, ministryRepository);
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
        PersonMinistry ministry = personMinistry(person, MinistryType.READER, ministryRepository);
        ministry.grantCoordination();
        personMinistryRepository.saveAndFlush(ministry);

        assertTrue(personMinistryRepository.existsByPersonIdAndMinistryTypeAndActiveTrueAndCoordinatorTrue(
                person.getId(), MinistryType.READER));
    }

    @Test
    void shouldNotExistWhenCoordinatorFalse() {
        Person person = saveReader("Exists Non Coordinator Person", "34971000302");
        personMinistryRepository.saveAndFlush(personMinistry(person, MinistryType.READER, ministryRepository));

        assertFalse(personMinistryRepository.existsByPersonIdAndMinistryTypeAndActiveTrueAndCoordinatorTrue(
                person.getId(), MinistryType.READER));
    }

    @Test
    void shouldNotExistForAnotherMinistryType() {
        Person person = saveReader("Exists Other Ministry Person", "34971000303");
        PersonMinistry ministry = personMinistry(person, MinistryType.READER, ministryRepository);
        ministry.grantCoordination();
        personMinistryRepository.saveAndFlush(ministry);

        assertFalse(personMinistryRepository.existsByPersonIdAndMinistryTypeAndActiveTrueAndCoordinatorTrue(
                person.getId(), MinistryType.COMMENTATOR));
    }

    @Test
    void shouldNotExistForAnotherPerson() {
        Person target = saveReader("Exists Target Person", "34971000304");
        Person other = saveReader("Exists Other Person", "34971000305");
        PersonMinistry ministry = personMinistry(other, MinistryType.READER, ministryRepository);
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

    private long countActivePeopleByMinistryId(Long ministryId) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(DISTINCT person_id)
                FROM tb_person_ministry
                WHERE ministry_id = ?
                  AND active = TRUE
                """,
                Long.class,
                ministryId
        );
        return count == null ? 0 : count;
    }
}
