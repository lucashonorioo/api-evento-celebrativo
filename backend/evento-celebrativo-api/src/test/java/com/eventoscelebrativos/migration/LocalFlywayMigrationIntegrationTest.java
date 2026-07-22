package com.eventoscelebrativos.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
@ActiveProfiles("local")
class LocalFlywayMigrationIntegrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldApplyCurrentSchemaRequiredRolesAndLocalDemoData() {
        assertSuccessfulVersionedMigration("1");
        assertSuccessfulVersionedMigration("2");
        assertSuccessfulVersionedMigration("3");
        assertSuccessfulVersionedMigration("4");
        assertSuccessfulVersionedMigration("5");
        assertSuccessfulScript("R__load_local_demo_data.sql");

        assertEquals(2, countRows("tb_role"));
        assertEquals(1, countRows("tb_role", "id", 1L, "authority", "ROLE_OPERATOR"));
        assertEquals(1, countRows("tb_role", "id", 2L, "authority", "ROLE_ADMIN"));

        assertEquals(15, countRows("tb_person"));
        assertEquals(3, countRows("tb_location"));
        assertEquals(3, countRows("tb_celebration_event"));

        assertEquals("Luana Odinson", queryString("SELECT name FROM tb_person WHERE id = 1"));
        assertEquals("Padre Miguel", queryString("SELECT name FROM tb_person WHERE id = 13"));
        assertEquals("Igreja Matriz Nossa Senhora do Rosário", queryString("SELECT church_name FROM tb_location WHERE id = 1"));
        assertEquals("Heloísa Ribeiro", queryString("SELECT name FROM tb_person WHERE id = 6"));
        assertEquals("Missa de Domingo da manhã", queryString("SELECT name_mass_or_event FROM tb_celebration_event WHERE id = 1"));
        assertEquals("Celebração da Palavra de Sábado", queryString("SELECT name_mass_or_event FROM tb_celebration_event WHERE id = 2"));

        assertEquals(20, countRows("tb_person_role"));
        assertEquals(21, countRows("tb_event_person"));
        assertEquals(3, countRows("tb_event_location"));
        assertEquals(15, countPeopleWithFilledParallelColumns());
        assertPersonMinistryFixtures();
        assertEventAssignmentFixtures();
        assertFutureUserTablesAreEmpty();
    }

    @Test
    void shouldNotDuplicateLocalDemoDataWhenMigrateRunsAgain() {
        int rolesBefore = countRows("tb_role");
        int peopleBefore = countRows("tb_person");
        int locationsBefore = countRows("tb_location");
        int eventsBefore = countRows("tb_celebration_event");
        int personRolesBefore = countRows("tb_person_role");
        int eventPeopleBefore = countRows("tb_event_person");
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
        assertEquals(eventPeopleBefore, countRows("tb_event_person"));
        assertEquals(eventLocationsBefore, countRows("tb_event_location"));
        assertEquals(personMinistriesBefore, countRows("tb_person_ministry"));
        assertEquals(userAccountsBefore, countRows("tb_user_account"));
        assertEquals(userAccountRolesBefore, countRows("tb_user_account_role"));
        assertEquals(eventAssignmentsBefore, countRows("tb_event_assignment"));
        assertEquals(1, countSuccessfulScript("R__load_local_demo_data.sql"));
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
        assertEquals(0, countPeopleWithoutExpectedMinistry());
    }

    private void assertEventAssignmentFixtures() {
        assertEquals(countRows("tb_event_person"), countRows("tb_event_assignment"));
        assertEquals(0, countEventPeopleWithoutAssignment());
        assertEquals(0, countAssignmentsWithoutLegacyEventPerson());
        assertEquals(0, countDuplicatedEventAssignments());
        assertEquals(0, countAssignmentsWithUnexpectedType());
    }

    private void assertFutureUserTablesAreEmpty() {
        assertEquals(0, countRows("tb_user_account"));
        assertEquals(0, countRows("tb_user_account_role"));
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

    private int countPeopleWithoutExpectedMinistry() {
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

    private int countEventPeopleWithoutAssignment() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM tb_event_person ep
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM tb_event_assignment ea
                    WHERE ea.event_id = ep.event_id
                      AND ea.person_id = ep.person_id
                )
                """,
                Integer.class
        );
        return count == null ? 0 : count;
    }

    private int countAssignmentsWithoutLegacyEventPerson() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM tb_event_assignment ea
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM tb_event_person ep
                    WHERE ep.event_id = ea.event_id
                      AND ep.person_id = ea.person_id
                )
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

    private int countAssignmentsWithUnexpectedType() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM tb_event_assignment ea
                INNER JOIN tb_person p ON p.id = ea.person_id
                WHERE ea.assignment_type <> CASE p.person_type
                    WHEN 'priest' THEN 'PRIEST'
                    WHEN 'reader' THEN 'READER'
                    WHEN 'commentator' THEN 'COMMENTATOR'
                    WHEN 'minister_of_the_word' THEN 'MINISTER_OF_THE_WORD'
                    WHEN 'eucharistic_minister' THEN 'EUCHARISTIC_MINISTER'
                END
                """,
                Integer.class
        );
        return count == null ? 0 : count;
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
}
