package com.eventoscelebrativos.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonDiscriminatorRemovalMigrationIntegrationTest {

    @Test
    void shouldRemovePersonTypeWhenEveryHistoricalTypeHasMatchingActiveMinistry() {
        DataSource dataSource = createDataSource("person_discriminator_removal_valid");
        migrateUntil(dataSource, "7");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        long readerId = insertPerson(jdbcTemplate, "Removal Valid Reader", "reader");
        insertMinistry(jdbcTemplate, readerId, "READER", true);

        long commentatorId = insertPerson(jdbcTemplate, "Removal Valid Commentator", "commentator");
        insertMinistry(jdbcTemplate, commentatorId, "COMMENTATOR", true);

        long priestId = insertPerson(jdbcTemplate, "Removal Valid Priest", "priest");
        insertMinistry(jdbcTemplate, priestId, "PRIEST", true);

        long wordMinisterId = insertPerson(jdbcTemplate, "Removal Valid Word Minister", "minister_of_the_word");
        insertMinistry(jdbcTemplate, wordMinisterId, "MINISTER_OF_THE_WORD", true);

        long eucharisticMinisterId = insertPerson(jdbcTemplate, "Removal Valid Eucharistic Minister", "eucharistic_minister");
        insertMinistry(jdbcTemplate, eucharisticMinisterId, "EUCHARISTIC_MINISTER", true);

        long multiMinistryId = insertPerson(jdbcTemplate, "Removal Valid Multi Ministry", "reader");
        insertMinistry(jdbcTemplate, multiMinistryId, "READER", true);
        insertMinistry(jdbcTemplate, multiMinistryId, "COMMENTATOR", true);

        long roleId = existingRoleId(jdbcTemplate, "ROLE_OPERATOR");
        insertPersonRole(jdbcTemplate, readerId, roleId);

        long eventId = insertEvent(jdbcTemplate, "Removal Valid Event");
        insertAssignment(jdbcTemplate, eventId, readerId, "READER");

        MigrateResult result = migrateAll(dataSource);

        assertEquals(1, result.migrationsExecuted);
        assertSuccessfulMigration(jdbcTemplate, "8");
        assertFalse(columnExists(jdbcTemplate, "tb_person", "person_type"));
        assertEquals(6, countRows(jdbcTemplate, "tb_person"));
        assertEquals(1, countRows(jdbcTemplate, "tb_person_role"));
        assertEquals(1, countRows(jdbcTemplate, "tb_event_assignment"));
        assertEquals(
                "Removal Valid Reader",
                jdbcTemplate.queryForObject("SELECT name FROM tb_person WHERE id = ?", String.class, readerId)
        );
        assertEquals(2, countMinistries(jdbcTemplate, multiMinistryId));
    }

    @Test
    void shouldFailAndKeepColumnWhenHistoricalTypeHasNoMatchingActiveMinistry() {
        DataSource dataSource = createDataSource("person_discriminator_removal_divergent");
        migrateUntil(dataSource, "7");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        long readerWithoutMinistryId = insertPerson(jdbcTemplate, "Removal Divergent Reader", "reader");

        long priestWithInactiveMinistryId = insertPerson(jdbcTemplate, "Removal Divergent Priest", "priest");
        insertMinistry(jdbcTemplate, priestWithInactiveMinistryId, "PRIEST", false);

        FlywayException exception = assertThrows(FlywayException.class, () -> migrateAll(dataSource));

        assertTrue(hasMessageContaining(exception, "2 pessoa"));
        assertTrue(hasMessageContaining(exception, "id=" + readerWithoutMinistryId));
        assertTrue(hasMessageContaining(exception, "id=" + priestWithInactiveMinistryId));
        assertFailedMigrationRecorded(jdbcTemplate, "8");
        assertTrue(columnExists(jdbcTemplate, "tb_person", "person_type"));
        assertEquals(
                "reader",
                jdbcTemplate.queryForObject(
                        "SELECT person_type FROM tb_person WHERE id = ?", String.class, readerWithoutMinistryId
                )
        );
        assertEquals(0, countMinistries(jdbcTemplate, readerWithoutMinistryId));
        assertEquals(1, countMinistries(jdbcTemplate, priestWithInactiveMinistryId));
    }

    @Test
    void shouldReachV8FromNewDatabaseWithoutPersonTypeColumn() {
        DataSource dataSource = createDataSource("person_discriminator_removal_new_database");

        MigrateResult result = migrateAll(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertEquals(8, result.migrationsExecuted);
        assertSuccessfulMigration(jdbcTemplate, "8");
        assertFalse(columnExists(jdbcTemplate, "tb_person", "person_type"));
        assertTrue(tableExists(jdbcTemplate, "tb_person"));
        assertTrue(tableExists(jdbcTemplate, "tb_person_ministry"));
    }

    private DataSource createDataSource(String namePrefix) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:" + namePrefix + "_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
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
                .target("8")
                .load()
                .migrate();
    }

    private long insertPerson(JdbcTemplate jdbcTemplate, String name, String personType) {
        String phoneNumber = "3494" + Math.floorMod(UUID.randomUUID().hashCode(), 100_000_000);
        jdbcTemplate.update(
                """
                INSERT INTO tb_person(name, phone_number, birthday_date, password, person_type)
                VALUES (?, ?, '1990-01-01', 'encoded-password', ?)
                """,
                name,
                phoneNumber,
                personType
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tb_person WHERE phone_number = ?",
                Long.class,
                phoneNumber
        );
    }

    private void insertMinistry(JdbcTemplate jdbcTemplate, long personId, String ministryType, boolean active) {
        jdbcTemplate.update(
                """
                INSERT INTO tb_person_ministry(person_id, ministry_type, active, created_at, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """,
                personId,
                ministryType,
                active
        );
    }

    private long existingRoleId(JdbcTemplate jdbcTemplate, String authority) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tb_role WHERE authority = ?",
                Long.class,
                authority
        );
    }

    private void insertPersonRole(JdbcTemplate jdbcTemplate, long personId, long roleId) {
        jdbcTemplate.update(
                "INSERT INTO tb_person_role(person_id, role_id) VALUES (?, ?)",
                personId,
                roleId
        );
    }

    private long insertEvent(JdbcTemplate jdbcTemplate, String name) {
        String eventName = name + " " + UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO tb_celebration_event(name_mass_or_event, event_date, event_time, mass_or_celebration)
                VALUES (?, '2026-08-01', '19:00:00', TRUE)
                """,
                eventName
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tb_celebration_event WHERE name_mass_or_event = ?",
                Long.class,
                eventName
        );
    }

    private void insertAssignment(JdbcTemplate jdbcTemplate, long eventId, long personId, String assignmentType) {
        jdbcTemplate.update(
                """
                INSERT INTO tb_event_assignment(event_id, person_id, assignment_type, created_at, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """,
                eventId,
                personId,
                assignmentType
        );
    }

    private void assertSuccessfulMigration(JdbcTemplate jdbcTemplate, String version) {
        assertEquals(1, countSuccessfulMigration(jdbcTemplate, version));
    }

    private void assertFailedMigrationRecorded(JdbcTemplate jdbcTemplate, String version) {
        assertEquals(0, countSuccessfulMigration(jdbcTemplate, version));
        assertEquals(1, countFailedMigration(jdbcTemplate, version));
    }

    private int countSuccessfulMigration(JdbcTemplate jdbcTemplate, String version) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = ? AND success = TRUE",
                Integer.class,
                version
        );
        return count == null ? 0 : count;
    }

    private int countFailedMigration(JdbcTemplate jdbcTemplate, String version) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = ? AND success = FALSE",
                Integer.class,
                version
        );
        return count == null ? 0 : count;
    }

    private int countRows(JdbcTemplate jdbcTemplate, String tableName) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
        return count == null ? 0 : count;
    }

    private int countMinistries(JdbcTemplate jdbcTemplate, long personId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_person_ministry WHERE person_id = ?",
                Integer.class,
                personId
        );
        return count == null ? 0 : count;
    }

    private boolean tableExists(JdbcTemplate jdbcTemplate, String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_name) = LOWER(?)",
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }

    private boolean columnExists(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE LOWER(table_name) = LOWER(?) AND LOWER(column_name) = LOWER(?)",
                Integer.class,
                tableName,
                columnName
        );
        return count != null && count > 0;
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
