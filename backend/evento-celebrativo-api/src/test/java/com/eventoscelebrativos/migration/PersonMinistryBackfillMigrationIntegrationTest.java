package com.eventoscelebrativos.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonMinistryBackfillMigrationIntegrationTest {

    private static final String PASSWORD_HASH = "$2a$10$BZEayVp6X1Ry93e44/Rnze0hpK5J3ThbAdUm2OzH.GSWjA4zmtGHW";

    @Test
    void shouldBackfillLegacyPersonMinistriesWithoutDuplicatingOrRemovingAdditionalFunctions() {
        DataSource dataSource = createDataSource("person_ministry_backfill");
        migrateUntil(dataSource, "3");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        long readerId = insertPerson(jdbcTemplate, "Legacy Reader", "34990000001", "reader");
        long commentatorId = insertPerson(jdbcTemplate, "Legacy Commentator", "34990000002", "commentator");
        long priestId = insertPerson(jdbcTemplate, "Legacy Priest", "34990000003", "priest");
        long ministerOfTheWordId = insertPerson(jdbcTemplate, "Legacy Minister Word", "34990000004", "minister_of_the_word");
        long eucharisticMinisterId = insertPerson(jdbcTemplate, "Legacy Eucharistic Minister", "34990000005", "eucharistic_minister");

        LocalDateTime activeCreatedAt = LocalDateTime.of(2026, 1, 10, 8, 0);
        LocalDateTime activeUpdatedAt = LocalDateTime.of(2026, 1, 10, 8, 30);
        long activeCommentatorMinistryId = insertPersonMinistry(
                jdbcTemplate, commentatorId, "COMMENTATOR", true, activeCreatedAt, activeUpdatedAt
        );

        LocalDateTime inactiveCreatedAt = LocalDateTime.of(2026, 1, 11, 8, 0);
        LocalDateTime inactiveUpdatedAt = LocalDateTime.of(2026, 1, 11, 8, 30);
        long inactivePriestMinistryId = insertPersonMinistry(
                jdbcTemplate, priestId, "PRIEST", false, inactiveCreatedAt, inactiveUpdatedAt
        );

        LocalDateTime additionalCreatedAt = LocalDateTime.of(2026, 1, 12, 8, 0);
        LocalDateTime additionalUpdatedAt = LocalDateTime.of(2026, 1, 12, 8, 30);
        long additionalCommentatorMinistryId = insertPersonMinistry(
                jdbcTemplate, ministerOfTheWordId, "COMMENTATOR", true, additionalCreatedAt, additionalUpdatedAt
        );

        MigrateResult result = migrateAll(dataSource);

        assertEquals(1, result.migrationsExecuted);
        assertSuccessfulMigration(jdbcTemplate, "4");
        assertEquals(5, countRows(jdbcTemplate, "tb_person"));
        assertEquals(6, countRows(jdbcTemplate, "tb_person_ministry"));
        assertEquals(0, countDuplicatedPersonMinistries(jdbcTemplate));
        assertEquals(0, countPeopleWithoutExpectedMinistry(jdbcTemplate));

        assertEquals(1, countPersonMinistry(jdbcTemplate, readerId, "READER"));
        assertEquals(1, countPersonMinistry(jdbcTemplate, commentatorId, "COMMENTATOR"));
        assertEquals(activeCommentatorMinistryId, queryPersonMinistryId(jdbcTemplate, commentatorId, "COMMENTATOR"));
        assertEquals(Timestamp.valueOf(activeCreatedAt), queryTimestamp(jdbcTemplate, commentatorId, "COMMENTATOR", "created_at"));
        assertEquals(Timestamp.valueOf(activeUpdatedAt), queryTimestamp(jdbcTemplate, commentatorId, "COMMENTATOR", "updated_at"));

        assertEquals(1, countPersonMinistry(jdbcTemplate, priestId, "PRIEST"));
        assertEquals(inactivePriestMinistryId, queryPersonMinistryId(jdbcTemplate, priestId, "PRIEST"));
        assertTrue(queryActive(jdbcTemplate, priestId, "PRIEST"));
        assertEquals(Timestamp.valueOf(inactiveCreatedAt), queryTimestamp(jdbcTemplate, priestId, "PRIEST", "created_at"));
        assertNotEquals(Timestamp.valueOf(inactiveUpdatedAt), queryTimestamp(jdbcTemplate, priestId, "PRIEST", "updated_at"));

        assertEquals(1, countPersonMinistry(jdbcTemplate, ministerOfTheWordId, "MINISTER_OF_THE_WORD"));
        assertEquals(1, countPersonMinistry(jdbcTemplate, ministerOfTheWordId, "COMMENTATOR"));
        assertEquals(additionalCommentatorMinistryId, queryPersonMinistryId(jdbcTemplate, ministerOfTheWordId, "COMMENTATOR"));

        assertEquals(1, countPersonMinistry(jdbcTemplate, eucharisticMinisterId, "EUCHARISTIC_MINISTER"));
    }

    @Test
    void shouldFailBackfillWhenPersonTypeIsInvalid() {
        DataSource dataSource = createDataSource("person_ministry_invalid_type");
        migrateUntil(dataSource, "3");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        insertPerson(jdbcTemplate, "Invalid Person", "34990000100", "invalid_type");

        FlywayException exception = assertThrows(FlywayException.class, () -> migrateAll(dataSource));

        assertTrue(hasMessageContaining(exception, "invalid_type"));
        assertEquals(0, countSuccessfulMigration(jdbcTemplate, "4"));
        assertEquals(0, countRows(jdbcTemplate, "tb_person_ministry"));
    }

    private DataSource createDataSource(String namePrefix) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:" + namePrefix + "_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private void migrateUntil(DataSource dataSource, String target) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate();
    }

    private MigrateResult migrateAll(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private long insertPerson(JdbcTemplate jdbcTemplate, String name, String phoneNumber, String personType) {
        jdbcTemplate.update(
                """
                INSERT INTO tb_person(name, phone_number, birthday_date, password, person_type)
                VALUES (?, ?, '1990-01-01', ?, ?)
                """,
                name,
                phoneNumber,
                PASSWORD_HASH,
                personType
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tb_person WHERE phone_number = ?",
                Long.class,
                phoneNumber
        );
    }

    private long insertPersonMinistry(
            JdbcTemplate jdbcTemplate,
            long personId,
            String ministryType,
            boolean active,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO tb_person_ministry(person_id, ministry_type, active, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                personId,
                ministryType,
                active,
                Timestamp.valueOf(createdAt),
                Timestamp.valueOf(updatedAt)
        );
        return queryPersonMinistryId(jdbcTemplate, personId, ministryType);
    }

    private void assertSuccessfulMigration(JdbcTemplate jdbcTemplate, String version) {
        assertEquals(1, countSuccessfulMigration(jdbcTemplate, version));
    }

    private int countSuccessfulMigration(JdbcTemplate jdbcTemplate, String version) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = ? AND success = TRUE",
                Integer.class,
                version
        );
        return count == null ? 0 : count;
    }

    private int countRows(JdbcTemplate jdbcTemplate, String tableName) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
        return count == null ? 0 : count;
    }

    private int countPersonMinistry(JdbcTemplate jdbcTemplate, long personId, String ministryType) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_person_ministry WHERE person_id = ? AND ministry_type = ?",
                Integer.class,
                personId,
                ministryType
        );
        return count == null ? 0 : count;
    }

    private long queryPersonMinistryId(JdbcTemplate jdbcTemplate, long personId, String ministryType) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tb_person_ministry WHERE person_id = ? AND ministry_type = ?",
                Long.class,
                personId,
                ministryType
        );
    }

    private boolean queryActive(JdbcTemplate jdbcTemplate, long personId, String ministryType) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT active FROM tb_person_ministry WHERE person_id = ? AND ministry_type = ?",
                Boolean.class,
                personId,
                ministryType
        ));
    }

    private Timestamp queryTimestamp(JdbcTemplate jdbcTemplate, long personId, String ministryType, String columnName) {
        return jdbcTemplate.queryForObject(
                "SELECT " + columnName + " FROM tb_person_ministry WHERE person_id = ? AND ministry_type = ?",
                Timestamp.class,
                personId,
                ministryType
        );
    }

    private int countDuplicatedPersonMinistries(JdbcTemplate jdbcTemplate) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM (
                    SELECT person_id, ministry_type
                    FROM tb_person_ministry
                    GROUP BY person_id, ministry_type
                    HAVING COUNT(*) > 1
                ) duplicated
                """,
                Integer.class
        );
        return count == null ? 0 : count;
    }

    private int countPeopleWithoutExpectedMinistry(JdbcTemplate jdbcTemplate) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM tb_person p
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM tb_person_ministry pm
                    WHERE pm.person_id = p.id
                      AND pm.ministry_type = CASE p.person_type
                          WHEN 'reader' THEN 'READER'
                          WHEN 'commentator' THEN 'COMMENTATOR'
                          WHEN 'priest' THEN 'PRIEST'
                          WHEN 'minister_of_the_word' THEN 'MINISTER_OF_THE_WORD'
                          WHEN 'eucharistic_minister' THEN 'EUCHARISTIC_MINISTER'
                      END
                )
                """,
                Integer.class
        );
        return count == null ? 0 : count;
    }

    private boolean hasMessageContaining(Throwable throwable, String expectedText) {
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(expectedText)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
