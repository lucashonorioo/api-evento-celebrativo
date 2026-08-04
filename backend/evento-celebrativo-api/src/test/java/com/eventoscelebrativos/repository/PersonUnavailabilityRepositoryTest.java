package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonUnavailability;
import com.eventoscelebrativos.projection.PersonUnavailabilityPersonProjection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Usa o profile "test" padrao (spring.profiles.active=test em application.properties de teste),
 * cujo application-test.properties aplica TODAS as migrations Flyway em classpath:db/migration
 * (V1 ate a mais recente, sem flyway.target) e valida o mapeamento JPA contra esse schema real
 * com spring.jpa.hibernate.ddl-auto=validate. Nao ha nenhum cap de versao aqui: se V11 nao
 * existisse ou estivesse incorreta, o contexto desta classe (e de toda a suite) falharia ao subir.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PersonUnavailabilityRepositoryTest {

    @Autowired
    private PersonUnavailabilityRepository personUnavailabilityRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldPersistWithTimestampsAndTrimmedReason() {
        Person person = savePerson("Unavailability Persistence Person", "34975000001");

        PersonUnavailability saved = personUnavailabilityRepository.saveAndFlush(
                new PersonUnavailability(person, at(2026, 8, 10, 0, 0), at(2026, 8, 12, 0, 0), "Viagem")
        );

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
        assertEquals("Viagem", saved.getReason());
    }

    @Test
    void shouldAllowNullReason() {
        Person person = savePerson("Unavailability Null Reason Person", "34975000002");

        PersonUnavailability saved = personUnavailabilityRepository.saveAndFlush(
                new PersonUnavailability(person, at(2026, 8, 10, 0, 0), at(2026, 8, 12, 0, 0), null)
        );

        assertNull(saved.getReason());
    }

    @Test
    void shouldEnforceCheckConstraintForInvertedRange() {
        // A traducao de excecao do Hibernate para o CHECK varia por dialeto: H2 produz
        // DataIntegrityViolationException, MySQL (via este driver/versao) produz JpaSystemException.
        // Ambas sao DataAccessException; o teste valida o comportamento do banco, nao a subclasse exata.
        Person person = savePerson("Unavailability Invalid Range Person", "34975000003");

        DataAccessException exception = assertThrows(DataAccessException.class, () ->
                personUnavailabilityRepository.saveAndFlush(
                        new PersonUnavailability(person, at(2026, 8, 12, 0, 0), at(2026, 8, 10, 0, 0), null)
                ));
        assertTrue(rootCauseMessage(exception).toLowerCase().contains("chk_tb_person_unavailability_range"));
    }

    @Test
    void shouldEnforceCheckConstraintForZeroDuration() {
        Person person = savePerson("Unavailability Zero Duration Person", "34975000018");
        LocalDateTime same = at(2026, 8, 10, 9, 0);

        assertThrows(DataAccessException.class, () ->
                personUnavailabilityRepository.saveAndFlush(new PersonUnavailability(person, same, same, null)));
    }

    @Test
    void shouldEnforceUniqueExactDuplicate() {
        Person person = savePerson("Unavailability Duplicate Person", "34975000004");
        personUnavailabilityRepository.saveAndFlush(
                new PersonUnavailability(person, at(2026, 8, 10, 0, 0), at(2026, 8, 12, 0, 0), null)
        );

        assertThrows(DataIntegrityViolationException.class, () ->
                personUnavailabilityRepository.saveAndFlush(
                        new PersonUnavailability(person, at(2026, 8, 10, 0, 0), at(2026, 8, 12, 0, 0), "Outro motivo")
                ));
    }

    @Test
    void shouldCascadeDeleteWhenPersonIsDeletedButNotTheOpposite() {
        Person person = savePerson("Unavailability Cascade Person", "34975000005");
        PersonUnavailability unavailability = personUnavailabilityRepository.saveAndFlush(
                new PersonUnavailability(person, at(2026, 8, 10, 0, 0), at(2026, 8, 12, 0, 0), null)
        );
        Long unavailabilityId = unavailability.getId();
        Long personId = person.getId();
        entityManager.clear();

        entityManager.remove(entityManager.find(Person.class, personId));
        entityManager.flush();
        entityManager.clear();

        assertNull(entityManager.find(PersonUnavailability.class, unavailabilityId));
    }

    @Test
    void shouldNotDeletePersonWhenDeletingUnavailability() {
        Person person = savePerson("Unavailability Reverse Cascade Person", "34975000006");
        PersonUnavailability unavailability = personUnavailabilityRepository.saveAndFlush(
                new PersonUnavailability(person, at(2026, 8, 10, 0, 0), at(2026, 8, 12, 0, 0), null)
        );
        Long personId = person.getId();

        personUnavailabilityRepository.delete(unavailability);
        personUnavailabilityRepository.flush();
        entityManager.clear();

        assertNotNull(entityManager.find(Person.class, personId));
    }

    @Test
    void shouldFindOverlappingSemiOpenRanges() {
        Person person = savePerson("Unavailability Overlap Person", "34975000007");
        saveUnavailability(person, at(2026, 8, 10, 0, 0), at(2026, 8, 12, 0, 0), null);

        List<PersonUnavailability> touching = personUnavailabilityRepository.findOverlapping(
                person.getId(), at(2026, 8, 11, 0, 0), at(2026, 8, 14, 0, 0));
        List<PersonUnavailability> adjacentAfter = personUnavailabilityRepository.findOverlapping(
                person.getId(), at(2026, 8, 12, 0, 0), at(2026, 8, 14, 0, 0));
        List<PersonUnavailability> containing = personUnavailabilityRepository.findOverlapping(
                person.getId(), at(2026, 8, 9, 0, 0), at(2026, 8, 20, 0, 0));

        assertEquals(1, touching.size());
        assertTrue(adjacentAfter.isEmpty(), "Intervalos adjacentes (endAt == startAt) nao devem ser conflitantes");
        assertEquals(1, containing.size());
    }

    @Test
    void shouldExcludeOwnIdWhenCheckingOverlapForUpdate() {
        Person person = savePerson("Unavailability Exclude Self Person", "34975000008");
        PersonUnavailability existing = saveUnavailability(person, at(2026, 8, 10, 0, 0), at(2026, 8, 12, 0, 0), null);

        List<PersonUnavailability> withoutExclusion = personUnavailabilityRepository.findOverlapping(
                person.getId(), at(2026, 8, 10, 0, 0), at(2026, 8, 12, 0, 0));
        List<PersonUnavailability> withExclusion = personUnavailabilityRepository.findOverlappingExcludingId(
                person.getId(), at(2026, 8, 10, 0, 0), at(2026, 8, 12, 0, 0), existing.getId());

        assertEquals(1, withoutExclusion.size());
        assertTrue(withExclusion.isEmpty());
    }

    @Test
    void shouldNotConsiderAdjacentNonOverlappingPeriodsAsConflicting() {
        Person person = savePerson("Unavailability Adjacent Person", "34975000009");
        saveUnavailability(person, at(2026, 8, 10, 0, 0), at(2026, 8, 12, 0, 0), null);

        List<PersonUnavailability> adjacent = personUnavailabilityRepository.findOverlapping(
                person.getId(), at(2026, 8, 12, 0, 0), at(2026, 8, 14, 0, 0));

        assertTrue(adjacent.isEmpty());
    }

    @Test
    void shouldFindIntersectingPeriodsPaginatedAndOrdered() {
        Person person = savePerson("Unavailability Intersect Person", "34975000010");
        saveUnavailability(person, at(2026, 8, 20, 0, 0), at(2026, 8, 22, 0, 0), null);
        saveUnavailability(person, at(2026, 8, 1, 0, 0), at(2026, 8, 3, 0, 0), null);
        saveUnavailability(person, at(2026, 9, 1, 0, 0), at(2026, 9, 2, 0, 0), null);

        Page<PersonUnavailability> page = personUnavailabilityRepository.findByPersonIdIntersecting(
                person.getId(), at(2026, 8, 1, 0, 0), at(2026, 9, 1, 0, 0), PageRequest.of(0, 10));

        assertEquals(2, page.getTotalElements());
        assertEquals(
                List.of(at(2026, 8, 1, 0, 0), at(2026, 8, 20, 0, 0)),
                page.getContent().stream().map(PersonUnavailability::getStartAt).toList()
        );
    }

    @Test
    void shouldReturnOverlappingWithMinimalIntersection() {
        Person person = savePerson("Unavailability Minimal Overlap Person", "34975000019");
        saveUnavailability(person, at(2026, 8, 10, 9, 59, 59), at(2026, 8, 10, 10, 0, 1), null);

        List<PersonUnavailability> result = personUnavailabilityRepository.findOverlapping(
                person.getId(), at(2026, 8, 10, 10, 0, 0), at(2026, 8, 10, 12, 0, 0));

        assertEquals(1, result.size());
    }

    @Test
    void shouldExcludeIntersectingUnavailabilityAdjacentToQueryRange() {
        Person person = savePerson("Unavailability Intersect Boundary Person", "34975000020");
        saveUnavailability(person, at(2026, 8, 10, 8, 0), at(2026, 8, 10, 10, 0), null);

        Page<PersonUnavailability> after = personUnavailabilityRepository.findByPersonIdIntersecting(
                person.getId(), at(2026, 8, 10, 10, 0), at(2026, 8, 10, 12, 0), PageRequest.of(0, 10));
        Page<PersonUnavailability> before = personUnavailabilityRepository.findByPersonIdIntersecting(
                person.getId(), at(2026, 8, 10, 4, 0), at(2026, 8, 10, 8, 0), PageRequest.of(0, 10));

        assertTrue(after.getContent().isEmpty());
        assertTrue(before.getContent().isEmpty());
    }

    @Test
    void shouldIncludeIntersectingUnavailabilityWithMinimalIntersection() {
        Person person = savePerson("Unavailability Intersect Minimal Person", "34975000021");
        saveUnavailability(person, at(2026, 8, 10, 9, 59, 59), at(2026, 8, 10, 10, 0, 1), null);

        Page<PersonUnavailability> result = personUnavailabilityRepository.findByPersonIdIntersecting(
                person.getId(), at(2026, 8, 10, 10, 0, 0), at(2026, 8, 10, 12, 0, 0), PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void shouldExcludeFromAdministrativeRangeQueryWhenAdjacentToRange() {
        Person person = savePerson("Unavailability Admin Boundary Person", "34975000022");
        saveUnavailability(person, at(2026, 8, 10, 8, 0), at(2026, 8, 10, 10, 0), null);

        List<PersonUnavailabilityPersonProjection> after = personUnavailabilityRepository.findAllByRange(
                at(2026, 8, 10, 10, 0), at(2026, 8, 10, 12, 0));
        List<PersonUnavailabilityPersonProjection> before = personUnavailabilityRepository.findAllByRange(
                at(2026, 8, 10, 4, 0), at(2026, 8, 10, 8, 0));

        assertTrue(after.isEmpty());
        assertTrue(before.isEmpty());
    }

    @Test
    void shouldIncludeInAdministrativeRangeQueryWithMinimalIntersection() {
        Person person = savePerson("Unavailability Admin Minimal Person", "34975000023");
        saveUnavailability(person, at(2026, 8, 10, 9, 59, 59), at(2026, 8, 10, 10, 0, 1), null);

        List<PersonUnavailabilityPersonProjection> result = personUnavailabilityRepository.findAllByRange(
                at(2026, 8, 10, 10, 0, 0), at(2026, 8, 10, 12, 0, 0));

        assertEquals(1, result.size());
    }

    @Test
    void shouldFindByIdAndPersonIdOnlyForOwner() {
        Person owner = savePerson("Unavailability Owner Person", "34975000011");
        Person other = savePerson("Unavailability Other Person", "34975000012");
        PersonUnavailability unavailability = saveUnavailability(owner, at(2026, 8, 10, 0, 0), at(2026, 8, 12, 0, 0), null);

        assertTrue(personUnavailabilityRepository.findByIdAndPersonId(unavailability.getId(), owner.getId()).isPresent());
        assertTrue(personUnavailabilityRepository.findByIdAndPersonId(unavailability.getId(), other.getId()).isEmpty());
    }

    @Test
    void shouldFindAllUnavailablePeopleOnRangeOrderedByNameThenId() {
        Person zelia = savePerson("Zelia Almeida", "34975000016");
        Person arthur = savePerson("Arthur Costa", "34975000017");
        saveUnavailability(zelia, at(2026, 8, 10, 0, 0), at(2026, 8, 12, 0, 0), null);
        saveUnavailability(arthur, at(2026, 8, 9, 0, 0), at(2026, 8, 15, 0, 0), null);

        List<PersonUnavailabilityPersonProjection> result =
                personUnavailabilityRepository.findAllByRange(at(2026, 8, 10, 0, 0), at(2026, 8, 10, 12, 0));

        assertEquals(
                List.of("Arthur Costa", "Zelia Almeida"),
                result.stream().map(PersonUnavailabilityPersonProjection::getPersonName).toList()
        );
    }

    @Test
    void shouldHaveNamedIndexesForTemporalRangeLookups() {
        assertIndexExists("tb_person_unavailability", "idx_tb_person_unavailability_person_end_start");
        assertIndexExists("tb_person_unavailability", "idx_tb_person_unavailability_range_person");
        assertConstraintExists("tb_person_unavailability", "uk_tb_person_unavailability_person_range");
    }

    @Test
    void shouldHavePrimaryKeyConstraint() {
        // MySQL sempre nomeia a constraint de PK como "PRIMARY", ignorando o nome dado na migration
        // (pk_tb_person_unavailability); por isso a verificacao e por tipo, nao por nome, para ser
        // portavel entre H2 e MySQL.
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints "
                        + "WHERE LOWER(table_name) = LOWER(?) AND constraint_type = 'PRIMARY KEY'",
                Integer.class,
                "tb_person_unavailability"
        );
        assertTrue(count != null && count > 0, "Expected a PRIMARY KEY constraint on tb_person_unavailability");
    }

    @Test
    void shouldHaveNamedForeignKeyConstraintToPerson() {
        assertConstraintExists("tb_person_unavailability", "fk_tb_person_unavailability_person");
    }

    @Test
    void shouldRejectInsertReferencingNonExistentPerson() {
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "INSERT INTO tb_person_unavailability (person_id, start_at, end_at) VALUES (?, ?, ?)",
                999999999L, at(2026, 8, 1, 0, 0), at(2026, 8, 2, 0, 0)
        ));
    }

    @Test
    void shouldLimitReasonColumnToFiveHundredCharacters() {
        Integer maxLength = jdbcTemplate.queryForObject(
                "SELECT character_maximum_length FROM information_schema.columns "
                        + "WHERE LOWER(table_name) = LOWER(?) AND LOWER(column_name) = LOWER(?)",
                Integer.class,
                "tb_person_unavailability",
                "reason"
        );
        assertEquals(500, maxLength);
    }

    @Test
    void shouldHaveAppliedFlywayMigrationThroughV11() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '11' AND success = TRUE",
                Integer.class
        );
        assertEquals(1, count, "Migration V11 deve estar aplicada com sucesso neste contexto de teste (sem flyway.target)");
    }

    private void assertIndexExists(String tableName, String indexName) {
        // Usa java.sql.DatabaseMetaData.getIndexInfo, que e a API JDBC padrao para metadados de
        // indice e funciona de forma identica no driver H2 e no driver MySQL, ao contrario das
        // tabelas information_schema (indexes/statistics), cujo nome e colunas variam por dialeto.
        boolean found = Boolean.TRUE.equals(jdbcTemplate.execute((java.sql.Connection connection) -> {
            try (java.sql.ResultSet rs = connection.getMetaData()
                    .getIndexInfo(connection.getCatalog(), null, tableName, false, false)) {
                while (rs.next()) {
                    String foundIndexName = rs.getString("INDEX_NAME");
                    if (foundIndexName != null && foundIndexName.equalsIgnoreCase(indexName)) {
                        return true;
                    }
                }
                return false;
            }
        }));
        assertTrue(found, "Expected index " + indexName + " to exist on " + tableName);
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return String.valueOf(current.getMessage());
    }

    private void assertConstraintExists(String tableName, String constraintName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints WHERE LOWER(table_name) = LOWER(?) AND LOWER(constraint_name) = LOWER(?)",
                Integer.class,
                tableName,
                constraintName
        );
        assertTrue(count != null && count > 0, "Expected constraint " + constraintName + " to exist on " + tableName);
    }

    private PersonUnavailability saveUnavailability(Person person, LocalDateTime startAt, LocalDateTime endAt, String reason) {
        PersonUnavailability unavailability = personUnavailabilityRepository.save(
                new PersonUnavailability(person, startAt, endAt, reason)
        );
        entityManager.flush();
        return unavailability;
    }

    private Person savePerson(String name, String phoneNumber) {
        Person person = new Person();
        person.setName(name);
        person.setPhoneNumber(phoneNumber);
        person.setBirthdayDate(LocalDate.of(1990, 1, 10));
        person.setPassword("encoded-password");
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }

    private LocalDateTime at(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute);
    }

    private LocalDateTime at(int year, int month, int day, int hour, int minute, int second) {
        return LocalDateTime.of(year, month, day, hour, minute, second);
    }
}
