package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.model.Reader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
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
        Reader reader = saveReader("Ministry Reader", "34971000001");
        PersonMinistry ministry = personMinistryRepository.saveAndFlush(new PersonMinistry(reader, MinistryType.READER));

        assertTrue(personMinistryRepository.existsByPersonIdAndMinistryType(reader.getId(), MinistryType.READER));
        assertEquals(
                ministry.getId(),
                personMinistryRepository.findByPersonIdAndMinistryType(reader.getId(), MinistryType.READER).orElseThrow().getId()
        );
    }

    @Test
    void shouldListAllMinistriesForPerson() {
        Reader reader = saveReader("Multi Ministry Reader", "34971000002");
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
        Reader reader = saveReader("Unique Ministry Reader", "34971000003");
        personMinistryRepository.saveAndFlush(new PersonMinistry(reader, MinistryType.READER));

        assertThrows(DataIntegrityViolationException.class,
                () -> personMinistryRepository.saveAndFlush(new PersonMinistry(reader, MinistryType.READER)));
    }

    @Test
    void shouldPersistEnumAsConstraintValue() {
        Reader reader = saveReader("Enum Ministry Reader", "34971000004");
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
        Reader reader = saveReader("Timestamp Ministry Reader", "34971000005");
        PersonMinistry ministry = personMinistryRepository.saveAndFlush(new PersonMinistry(reader, MinistryType.READER));

        assertNotNull(ministry.getCreatedAt());
        assertNotNull(ministry.getUpdatedAt());
    }

    @Test
    void shouldReactivateInactiveMinistryWhenUpdated() {
        Reader reader = saveReader("Inactive Ministry Reader", "34971000006");
        PersonMinistry ministry = new PersonMinistry(reader, MinistryType.READER);
        ministry.setActive(false);
        ministry = personMinistryRepository.saveAndFlush(ministry);

        ministry.activate();
        PersonMinistry reactivated = personMinistryRepository.saveAndFlush(ministry);

        assertTrue(reactivated.getActive());
    }

    @Test
    void shouldDeleteAllMinistriesByPersonId() {
        Reader reader = saveReader("Delete Ministry Reader", "34971000007");
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
        Reader reader = saveReader("Cascade Ministry Reader", "34971000008");
        PersonMinistry ministry = personMinistryRepository.saveAndFlush(new PersonMinistry(reader, MinistryType.READER));

        personMinistryRepository.delete(ministry);
        personMinistryRepository.flush();
        entityManager.clear();

        assertNotNull(entityManager.find(Reader.class, reader.getId()));
    }

    private Reader saveReader(String name, String phoneNumber) {
        Reader reader = new Reader();
        reader.setName(name);
        reader.setPhoneNumber(phoneNumber);
        reader.setBirthdayDate(LocalDate.of(1990, 1, 10));
        reader.setPassword("encoded-password");
        entityManager.persist(reader);
        entityManager.flush();
        return reader;
    }
}
