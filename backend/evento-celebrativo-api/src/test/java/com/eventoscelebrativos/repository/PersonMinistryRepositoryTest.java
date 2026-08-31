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

import java.time.LocalDate;
import java.util.List;

import static com.eventoscelebrativos.support.LegacyMinistryTestFactory.personMinistry;
import static com.eventoscelebrativos.support.LegacyMinistryTestFactory.persistentMinistry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class PersonMinistryRepositoryTest {

    @Autowired
    private PersonMinistryRepository personMinistryRepository;

    @Autowired
    private MinistryRepository ministryRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void shouldSaveAndFindPersonMinistryByPersistentMinistry() {
        Person reader = savePerson("Ministry Person", "34971000001");
        PersonMinistry ministry = personMinistryRepository.saveAndFlush(
                personMinistry(reader, MinistryType.READER, ministryRepository)
        );

        assertTrue(personMinistryRepository.existsByPersonIdAndMinistryId(
                reader.getId(), ministry.getMinistry().getId()));
        assertEquals(
                ministry.getId(),
                personMinistryRepository.findByPersonIdAndMinistryId(
                        reader.getId(),
                        ministry.getMinistry().getId()
                ).orElseThrow().getId()
        );
    }

    @Test
    void shouldPersistArbitraryMinistryWithoutLegacyMapping() {
        Person person = savePerson("Arbitrary Ministry Person", "34971000014");
        Ministry acolytes = saveMinistry("Acolitos");

        PersonMinistry ministry = personMinistryRepository.saveAndFlush(new PersonMinistry(person, acolytes));

        assertEquals(acolytes.getId(), ministry.getMinistry().getId());
        assertTrue(personMinistryRepository.existsByPersonIdAndMinistryId(person.getId(), acolytes.getId()));
    }

    @Test
    void shouldListAllMinistriesForPerson() {
        Person reader = savePerson("Multi Ministry Person", "34971000002");
        Ministry readerMinistry = persistentMinistry(MinistryType.READER, ministryRepository);
        Ministry commentatorMinistry = persistentMinistry(MinistryType.COMMENTATOR, ministryRepository);
        personMinistryRepository.save(new PersonMinistry(reader, readerMinistry));
        personMinistryRepository.save(new PersonMinistry(reader, commentatorMinistry));
        personMinistryRepository.flush();

        List<Long> ministryIds = personMinistryRepository.findAllByPersonId(reader.getId()).stream()
                .map(personMinistry -> personMinistry.getMinistry().getId())
                .toList();

        assertEquals(2, ministryIds.size());
        assertTrue(ministryIds.contains(readerMinistry.getId()));
        assertTrue(ministryIds.contains(commentatorMinistry.getId()));
    }

    @Test
    void shouldEnforceUniquePersonAndPersistentMinistry() {
        Person reader = savePerson("Unique Ministry Person", "34971000003");
        Ministry readerMinistry = saveMinistry("Catalog Page Arbitrary");
        personMinistryRepository.saveAndFlush(new PersonMinistry(reader, readerMinistry));

        assertThrows(DataIntegrityViolationException.class,
                () -> personMinistryRepository.saveAndFlush(new PersonMinistry(reader, readerMinistry)));
    }

    @Test
    void shouldFillTimestampsWhenSaving() {
        Person reader = savePerson("Timestamp Ministry Person", "34971000005");
        PersonMinistry ministry = personMinistryRepository.saveAndFlush(
                personMinistry(reader, MinistryType.READER, ministryRepository)
        );

        assertNotNull(ministry.getCreatedAt());
        assertNotNull(ministry.getUpdatedAt());
    }

    @Test
    void shouldReactivateInactiveMinistryWhenUpdated() {
        Person reader = savePerson("Inactive Ministry Person", "34971000006");
        PersonMinistry ministry = personMinistry(reader, MinistryType.READER, ministryRepository);
        ministry.setActive(false);
        ministry = personMinistryRepository.saveAndFlush(ministry);

        ministry.activate();
        PersonMinistry reactivated = personMinistryRepository.saveAndFlush(ministry);

        assertTrue(reactivated.getActive());
    }

    @Test
    void shouldDeleteAllMinistriesByPersonId() {
        Person reader = savePerson("Delete Ministry Person", "34971000007");
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
        Person reader = savePerson("Cascade Ministry Person", "34971000008");
        PersonMinistry ministry = personMinistryRepository.saveAndFlush(
                personMinistry(reader, MinistryType.READER, ministryRepository)
        );

        personMinistryRepository.delete(ministry);
        personMinistryRepository.flush();
        entityManager.clear();

        assertNotNull(entityManager.find(Person.class, reader.getId()));
    }

    @Test
    void shouldPageDistinctActivePersonIdsByPersistentMinistryOrderedByNameAndId() {
        Person first = savePerson("000 Catalog Page Same", "34971000111");
        Person second = savePerson("000 Catalog Page Same", "34971000112");
        Person inactive = savePerson("000 Catalog Page Inactive", "34971000113");
        Ministry readerMinistry = saveMinistry("Catalog Page Arbitrary");

        personMinistryRepository.save(new PersonMinistry(first, readerMinistry));
        personMinistryRepository.save(new PersonMinistry(second, readerMinistry));
        personMinistryRepository.save(personMinistry(second, MinistryType.COMMENTATOR, ministryRepository));
        PersonMinistry inactiveMinistry = new PersonMinistry(inactive, readerMinistry);
        inactiveMinistry.setActive(false);
        personMinistryRepository.save(inactiveMinistry);
        personMinistryRepository.flush();

        Page<Long> result = personMinistryRepository.findActivePersonIdsByMinistryId(
                readerMinistry.getId(),
                PageRequest.of(0, 2)
        );

        assertEquals(List.of(first.getId(), second.getId()), result.getContent());
        assertEquals(2, result.getTotalElements());
        assertFalse(result.getContent().contains(inactive.getId()));
    }

    @Test
    void shouldLoadActivePersistentMinistriesByPersonIdsInOneBatchProjection() {
        Person reader = savePerson("Batch Active Catalog Ministry Person", "34971000114");
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
        assertEquals("Leitores", result.get(0).getMinistryName());
        assertFalse(result.get(0).getCoordinator());
    }

    @Test
    void shouldLoadAllMinistryStatusesByPersonIdsInOneBatchProjection() {
        Person reader = savePerson("Batch Status Ministry Person", "34971000105");
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
    void shouldReturnPersistentMinistryWhenActiveAndCoordinator() {
        Person person = savePerson("Coordinator Catalog Person", "34971000211");
        PersonMinistry ministry = personMinistry(person, MinistryType.READER, ministryRepository);
        ministry.grantCoordination();
        personMinistryRepository.saveAndFlush(ministry);

        List<Ministry> result = personMinistryRepository.findActiveCoordinatedMinistriesByPersonId(person.getId());

        assertEquals(1, result.size());
        assertEquals(persistentMinistry(MinistryType.READER, ministryRepository).getId(), result.get(0).getId());
    }

    @Test
    void shouldReturnArbitraryPersistentMinistryWhenActiveAndCoordinator() {
        Person person = savePerson("Arbitrary Coordinator Person", "34971000212");
        Ministry acolytes = saveMinistry("Acolitos Coordenacao");
        PersonMinistry ministry = new PersonMinistry(person, acolytes);
        ministry.grantCoordination();
        personMinistryRepository.saveAndFlush(ministry);

        List<Ministry> result = personMinistryRepository.findActiveCoordinatedMinistriesByPersonId(person.getId());

        assertEquals(1, result.size());
        assertEquals(acolytes.getId(), result.get(0).getId());
    }

    @Test
    void shouldNotReturnActiveMinistryThatIsNotCoordinated() {
        Person person = savePerson("Active Non Coordinator Person", "34971000202");
        personMinistryRepository.saveAndFlush(personMinistry(person, MinistryType.READER, ministryRepository));

        List<Ministry> result = personMinistryRepository.findActiveCoordinatedMinistriesByPersonId(person.getId());

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldNotReturnInactiveCoordinatedMinistry() {
        Person person = savePerson("Inactive Coordinator Person", "34971000203");
        PersonMinistry ministry = personMinistry(person, MinistryType.READER, ministryRepository);
        ministry.grantCoordination();
        ministry.deactivate();
        personMinistryRepository.saveAndFlush(ministry);

        List<Ministry> result = personMinistryRepository.findActiveCoordinatedMinistriesByPersonId(person.getId());

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnAllActiveCoordinatedMinistriesOrderedByMinistryIdAscending() {
        Person person = savePerson("Multi Coordinator Person", "34971000204");

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

        List<Long> result = personMinistryRepository.findActiveCoordinatedMinistriesByPersonId(person.getId()).stream()
                .map(Ministry::getId)
                .toList();

        assertEquals(List.of(
                persistentMinistry(MinistryType.PRIEST, ministryRepository).getId(),
                persistentMinistry(MinistryType.READER, ministryRepository).getId(),
                persistentMinistry(MinistryType.COMMENTATOR, ministryRepository).getId()
        ), result);
    }

    @Test
    void shouldNotReturnCoordinatedMinistryFromAnotherPerson() {
        Person target = savePerson("Target Person", "34971000205");
        Person other = savePerson("Other Coordinator Person", "34971000206");

        PersonMinistry otherMinistry = personMinistry(other, MinistryType.READER, ministryRepository);
        otherMinistry.grantCoordination();
        personMinistryRepository.saveAndFlush(otherMinistry);

        List<Ministry> result = personMinistryRepository.findActiveCoordinatedMinistriesByPersonId(target.getId());

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyListWhenPersonHasNoCoordinatedMinistry() {
        Person person = savePerson("No Ministry Person", "34971000207");

        List<Ministry> result = personMinistryRepository.findActiveCoordinatedMinistriesByPersonId(person.getId());

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldExistWhenPersonMinistryActiveAndCoordinator() {
        Person person = savePerson("Exists Coordinator Person", "34971000301");
        Ministry readerMinistry = persistentMinistry(MinistryType.READER, ministryRepository);
        PersonMinistry ministry = new PersonMinistry(person, readerMinistry);
        ministry.grantCoordination();
        personMinistryRepository.saveAndFlush(ministry);

        assertTrue(personMinistryRepository.existsByPersonIdAndMinistryIdAndActiveTrueAndCoordinatorTrue(
                person.getId(), readerMinistry.getId()));
    }

    @Test
    void shouldNotExistWhenCoordinatorFalse() {
        Person person = savePerson("Exists Non Coordinator Person", "34971000302");
        Ministry readerMinistry = persistentMinistry(MinistryType.READER, ministryRepository);
        personMinistryRepository.saveAndFlush(new PersonMinistry(person, readerMinistry));

        assertFalse(personMinistryRepository.existsByPersonIdAndMinistryIdAndActiveTrueAndCoordinatorTrue(
                person.getId(), readerMinistry.getId()));
    }

    @Test
    void shouldNotExistForAnotherMinistry() {
        Person person = savePerson("Exists Other Ministry Person", "34971000303");
        Ministry readerMinistry = persistentMinistry(MinistryType.READER, ministryRepository);
        Ministry commentatorMinistry = persistentMinistry(MinistryType.COMMENTATOR, ministryRepository);
        PersonMinistry ministry = new PersonMinistry(person, readerMinistry);
        ministry.grantCoordination();
        personMinistryRepository.saveAndFlush(ministry);

        assertFalse(personMinistryRepository.existsByPersonIdAndMinistryIdAndActiveTrueAndCoordinatorTrue(
                person.getId(), commentatorMinistry.getId()));
    }

    @Test
    void shouldNotExistForAnotherPerson() {
        Person target = savePerson("Exists Target Person", "34971000304");
        Person other = savePerson("Exists Other Person", "34971000305");
        Ministry readerMinistry = persistentMinistry(MinistryType.READER, ministryRepository);
        PersonMinistry ministry = new PersonMinistry(other, readerMinistry);
        ministry.grantCoordination();
        personMinistryRepository.saveAndFlush(ministry);

        assertFalse(personMinistryRepository.existsByPersonIdAndMinistryIdAndActiveTrueAndCoordinatorTrue(
                target.getId(), readerMinistry.getId()));
    }

    private Person savePerson(String name, String phoneNumber) {
        Person person = new Person(name, phoneNumber, LocalDate.of(1990, 1, 10));
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }

    private Ministry saveMinistry(String name) {
        Ministry ministry = new Ministry(name);
        entityManager.persist(ministry);
        entityManager.flush();
        return ministry;
    }
}
