package com.eventoscelebrativos.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
@ActiveProfiles("test")
class TestProfileFlywayIntegrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldApplyCurrentSchemaRequiredRolesAndTestFixtures() {
        assertSuccessfulVersionedMigration("1");
        assertSuccessfulVersionedMigration("2");
        assertSuccessfulVersionedMigration("3");
        assertSuccessfulVersionedMigration("4");
        assertSuccessfulVersionedMigration("5");
        assertSuccessfulVersionedMigration("6");
        assertSuccessfulVersionedMigration("7");
        assertSuccessfulVersionedMigration("8");
        assertSuccessfulVersionedMigration("9");
        assertSuccessfulVersionedMigration("10");
        assertSuccessfulVersionedMigration("11");
        assertSuccessfulScript("R__load_test_fixtures.sql");
        assertTableDoesNotExist("tb_event_person");
        assertColumnDoesNotExist("tb_person", "person_type");

        assertEquals(2, countRows("tb_role"));
        assertEquals(1, countRows("tb_role", "id", 1L, "authority", "ROLE_OPERATOR"));
        assertEquals(1, countRows("tb_role", "id", 2L, "authority", "ROLE_ADMIN"));

        assertEquals(15, countRows("tb_person"));
        assertEquals(3, countRows("tb_location"));
        assertEquals(3, countRows("tb_celebration_event"));

        assertEquals("Luana Odinson", queryString("SELECT name FROM tb_person WHERE id = 1"));
        assertEquals("Heloísa Ribeiro", queryString("SELECT name FROM tb_person WHERE id = 6"));
        assertEquals("Padre Miguel", queryString("SELECT name FROM tb_person WHERE id = 13"));
        assertEquals("Igreja Matriz Nossa Senhora do Rosário", queryString("SELECT church_name FROM tb_location WHERE id = 1"));
        assertEquals("Missa de Domingo da manhã", queryString("SELECT name_mass_or_event FROM tb_celebration_event WHERE id = 1"));
        assertEquals("Celebração da Palavra de Sábado", queryString("SELECT name_mass_or_event FROM tb_celebration_event WHERE id = 2"));
        assertEquals("Missa de Ação de Graças", queryString("SELECT name_mass_or_event FROM tb_celebration_event WHERE id = 3"));
        assertEventRange(1L, LocalDateTime.of(2025, 7, 13, 10, 0), LocalDateTime.of(2025, 7, 13, 11, 0));
        assertEventRange(2L, LocalDateTime.of(2025, 7, 12, 19, 30), LocalDateTime.of(2025, 7, 12, 20, 30));
        assertEventRange(3L, LocalDateTime.of(2025, 7, 20, 8, 0), LocalDateTime.of(2025, 7, 20, 9, 0));

        assertEquals(20, countRows("tb_person_role"));
        assertEquals(3, countRows("tb_event_location"));
        assertEquals(15, countPeopleWithFilledParallelColumns());
        assertPersonMinistryFixtures();
        assertEventAssignmentFixtures();
        assertUserAccountFixturesMirrorPersonFixtures();
    }

    @Test
    void shouldNotDuplicateTestFixturesWhenMigrateRunsAgain() {
        int rolesBefore = countRows("tb_role");
        int peopleBefore = countRows("tb_person");
        int locationsBefore = countRows("tb_location");
        int eventsBefore = countRows("tb_celebration_event");
        int personRolesBefore = countRows("tb_person_role");
        int eventLocationsBefore = countRows("tb_event_location");
        int personMinistriesBefore = countRows("tb_person_ministry");
        int userAccountsBefore = countRows("tb_user_account");
        int userAccountRolesBefore = countRows("tb_user_account_role");
        int eventAssignmentsBefore = countRows("tb_event_assignment");

        MigrateResult result = flyway.migrate();

        assertEquals(0, result.migrationsExecuted);
        assertEquals(rolesBefore, countRows("tb_role"));
        assertEquals(peopleBefore, countRows("tb_person"));
        assertEquals(locationsBefore, countRows("tb_location"));
        assertEquals(eventsBefore, countRows("tb_celebration_event"));
        assertEquals(personRolesBefore, countRows("tb_person_role"));
        assertEquals(eventLocationsBefore, countRows("tb_event_location"));
        assertEquals(personMinistriesBefore, countRows("tb_person_ministry"));
        assertEquals(userAccountsBefore, countRows("tb_user_account"));
        assertEquals(userAccountRolesBefore, countRows("tb_user_account_role"));
        assertEquals(eventAssignmentsBefore, countRows("tb_event_assignment"));
        assertEquals(1, countSuccessfulScript("R__load_test_fixtures.sql"));
    }

    private void assertSuccessfulVersionedMigration(String version) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = ? AND success = TRUE",
                Integer.class,
                version
        );
        assertEquals(1, count);
    }

    private void assertSuccessfulScript(String script) {
        assertEquals(1, countSuccessfulScript(script));
    }

    private int countSuccessfulScript(String script) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE script = ? AND success = TRUE",
                Integer.class,
                script
        );
        return count == null ? 0 : count;
    }

    private int countRows(String tableName) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
        return count == null ? 0 : count;
    }

    private int countPeopleWithFilledParallelColumns() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_person WHERE active = TRUE AND created_at IS NOT NULL AND updated_at IS NOT NULL",
                Integer.class
        );
        return count == null ? 0 : count;
    }

    private void assertPersonMinistryFixtures() {
        assertEquals(15, countRows("tb_person_ministry"));
        assertEquals(15, countActivePersonMinistries());
        assertEquals(0, countDuplicatedPersonMinistries());
    }

    private void assertEventAssignmentFixtures() {
        assertEquals(21, countRows("tb_event_assignment"));
        assertEquals(0, countDuplicatedEventAssignments());
    }

    private void assertUserAccountFixturesMirrorPersonFixtures() {
        assertEquals(15, countRows("tb_user_account"));
        assertEquals(20, countRows("tb_user_account_role"));
        assertEquals(0, countUsernameMismatches());
        assertEquals(0, countPasswordHashMismatches());
        assertEquals(15, countAccountsEnabled());
    }

    private int countUsernameMismatches() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM tb_person p
                JOIN tb_user_account ua ON ua.person_id = p.id
                WHERE ua.username <> p.phone_number
                """,
                Integer.class
        );
        return count == null ? 0 : count;
    }

    private int countPasswordHashMismatches() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM tb_person p
                JOIN tb_user_account ua ON ua.person_id = p.id
                WHERE ua.password_hash <> p.password
                """,
                Integer.class
        );
        return count == null ? 0 : count;
    }

    private int countAccountsEnabled() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_user_account WHERE enabled = TRUE",
                Integer.class
        );
        return count == null ? 0 : count;
    }

    private void assertTableDoesNotExist(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_name) = LOWER(?)",
                Integer.class,
                tableName
        );
        assertEquals(0, count == null ? 0 : count);
    }

    private int countActivePersonMinistries() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_person_ministry WHERE active = TRUE",
                Integer.class
        );
        return count == null ? 0 : count;
    }

    private int countDuplicatedPersonMinistries() {
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

    private int countDuplicatedEventAssignments() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM (
                    SELECT event_id, person_id
                    FROM tb_event_assignment
                    GROUP BY event_id, person_id
                    HAVING COUNT(*) > 1
                ) duplicated
                """,
                Integer.class
        );
        return count == null ? 0 : count;
    }

    private void assertColumnDoesNotExist(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE LOWER(table_name) = LOWER(?) AND LOWER(column_name) = LOWER(?)",
                Integer.class,
                tableName,
                columnName
        );
        assertEquals(0, count == null ? 0 : count);
    }

    private int countRows(String tableName, String idColumn, Long id, String valueColumn, String value) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName + " WHERE " + idColumn + " = ? AND " + valueColumn + " = ?",
                Integer.class,
                id,
                value
        );
        return count == null ? 0 : count;
    }

    private String queryString(String sql) {
        return jdbcTemplate.queryForObject(sql, String.class);
    }

    private void assertEventRange(Long eventId, LocalDateTime expectedStartAt, LocalDateTime expectedEndAt) {
        assertEquals(expectedStartAt, jdbcTemplate.queryForObject(
                "SELECT start_at FROM tb_celebration_event WHERE id = ?",
                LocalDateTime.class,
                eventId
        ));
        assertEquals(expectedEndAt, jdbcTemplate.queryForObject(
                "SELECT end_at FROM tb_celebration_event WHERE id = ?",
                LocalDateTime.class,
                eventId
        ));
    }
}
