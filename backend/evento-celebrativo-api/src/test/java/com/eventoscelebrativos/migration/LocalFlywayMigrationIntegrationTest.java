package com.eventoscelebrativos.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
@ActiveProfiles("local")
class LocalFlywayMigrationIntegrationTest {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_OPERATOR = "ROLE_OPERATOR";

    private record MinistryDistribution(String ministryType, String admin, List<String> operators, String withoutAccount) {
    }

    private static final List<MinistryDistribution> MINISTRY_DISTRIBUTIONS = List.of(
            new MinistryDistribution("COMMENTATOR", "Luana Odinson", List.of("Miguel Souza", "Helena Oliveira"), "Camila Martins"),
            new MinistryDistribution("READER", "Alice Lima", List.of("Arthur Costa", "Heloísa Ribeiro"), "Gabriel Santos"),
            new MinistryDistribution("MINISTER_OF_THE_WORD", "Davi Gomes", List.of("Laura Alves", "Bernardo Ferreira"), "Rafael Moreira"),
            new MinistryDistribution("EUCHARISTIC_MINISTER", "Mariana Ferraz", List.of("Carlos Silva", "Fernanda Souza"), "Juliana Mendes"),
            new MinistryDistribution("PRIEST", "Padre Paulo", List.of("Padre Miguel", "Padre Roberto"), "Padre Antônio")
    );

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
        assertSuccessfulVersionedMigration("6");
        assertSuccessfulVersionedMigration("7");
        assertSuccessfulVersionedMigration("8");
        assertSuccessfulVersionedMigration("9");
        assertSuccessfulVersionedMigration("10");
        assertSuccessfulVersionedMigration("11");
        assertSuccessfulScript("R__load_local_demo_data.sql");
        assertTableDoesNotExist("tb_event_person");
        assertTableDoesNotExist("tb_person_role");
        assertColumnDoesNotExist("tb_person", "person_type");
        assertColumnDoesNotExist("tb_person", "password");

        assertEquals(2, countRows("tb_role"));
        assertEquals(1, countRows("tb_role", "id", 1L, "authority", "ROLE_OPERATOR"));
        assertEquals(1, countRows("tb_role", "id", 2L, "authority", "ROLE_ADMIN"));

        assertEquals(20, countRows("tb_person"));
        assertEquals(3, countRows("tb_location"));
        assertEquals(3, countRows("tb_celebration_event"));

        assertEquals("Luana Odinson", queryString("SELECT name FROM tb_person WHERE id = 1"));
        assertEquals("Padre Miguel", queryString("SELECT name FROM tb_person WHERE id = 13"));
        assertEquals("Igreja Matriz Nossa Senhora do Rosário", queryString("SELECT church_name FROM tb_location WHERE id = 1"));
        assertEquals("Heloísa Ribeiro", queryString("SELECT name FROM tb_person WHERE id = 6"));
        assertEquals("Missa de Domingo da manhã", queryString("SELECT name_mass_or_event FROM tb_celebration_event WHERE id = 1"));
        assertEquals("Celebração da Palavra de Sábado", queryString("SELECT name_mass_or_event FROM tb_celebration_event WHERE id = 2"));
        assertEventRange(1L, LocalDateTime.of(2025, 7, 13, 10, 0), LocalDateTime.of(2025, 7, 13, 11, 0));
        assertEventRange(2L, LocalDateTime.of(2025, 7, 12, 19, 30), LocalDateTime.of(2025, 7, 12, 20, 30));
        assertEventRange(3L, LocalDateTime.of(2025, 7, 20, 8, 0), LocalDateTime.of(2025, 7, 20, 9, 0));

        assertEquals(3, countRows("tb_event_location"));
        assertEquals(20, countPeopleWithFilledParallelColumns());
        assertPersonMinistryFixtures();
        assertEventAssignmentFixtures();
        assertUserAccountFixturesMirrorPersonFixtures();
        assertMinistryRoleMatrix();
        assertNominalRoleAssignments();
    }

    @Test
    void shouldNotDuplicateLocalDemoDataWhenMigrateRunsAgain() {
        int rolesBefore = countRows("tb_role");
        int peopleBefore = countRows("tb_person");
        int locationsBefore = countRows("tb_location");
        int eventsBefore = countRows("tb_celebration_event");
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
        assertEquals(20, countRows("tb_person_ministry"));
        assertEquals(20, countActivePersonMinistries());
        assertEquals(0, countDuplicatedPersonMinistries());
    }

    private void assertEventAssignmentFixtures() {
        assertEquals(21, countRows("tb_event_assignment"));
        assertEquals(0, countDuplicatedEventAssignments());
    }

    private void assertUserAccountFixturesMirrorPersonFixtures() {
        assertEquals(15, countRows("tb_user_account"));
        assertEquals(15, countRows("tb_user_account_role"));
        assertEquals(0, countUsernameMismatches());
        assertEquals(15, countAccountsEnabled());
        assertEquals(5, countAccountsWithRole(ROLE_ADMIN));
        assertEquals(10, countAccountsWithRole(ROLE_OPERATOR));
        assertEquals(0, countAccountsWithMoreThanOneRole());
        assertEquals(0, countPersonsWithMoreThanOneAccount());
        assertEquals(5, countPersonsWithoutAccount());
    }

    private void assertMinistryRoleMatrix() {
        for (MinistryDistribution distribution : MINISTRY_DISTRIBUTIONS) {
            assertEquals(1, countPeopleWithMinistryAndRole(distribution.ministryType(), ROLE_ADMIN),
                    "expected exactly 1 ROLE_ADMIN for " + distribution.ministryType());
            assertEquals(2, countPeopleWithMinistryAndRole(distribution.ministryType(), ROLE_OPERATOR),
                    "expected exactly 2 ROLE_OPERATOR for " + distribution.ministryType());
            assertEquals(1, countPeopleWithMinistryAndNoAccount(distribution.ministryType()),
                    "expected exactly 1 person without account for " + distribution.ministryType());
        }
    }

    private void assertNominalRoleAssignments() {
        for (MinistryDistribution distribution : MINISTRY_DISTRIBUTIONS) {
            assertEquals(ROLE_ADMIN, personRole(distribution.admin()));
            for (String operator : distribution.operators()) {
                assertEquals(ROLE_OPERATOR, personRole(operator));
            }
            assertEquals(0, countAccountsForPersonName(distribution.withoutAccount()));
        }
    }

    private String personRole(String personName) {
        return jdbcTemplate.queryForObject(
                """
                SELECT r.authority
                FROM tb_person p
                JOIN tb_user_account ua ON ua.person_id = p.id
                JOIN tb_user_account_role uar ON uar.user_account_id = ua.id
                JOIN tb_role r ON r.id = uar.role_id
                WHERE p.name = ?
                """,
                String.class,
                personName
        );
    }

    private int countAccountsForPersonName(String personName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM tb_person p
                JOIN tb_user_account ua ON ua.person_id = p.id
                WHERE p.name = ?
                """,
                Integer.class,
                personName
        );
        return count == null ? 0 : count;
    }

    private int countPeopleWithMinistryAndRole(String ministryType, String authority) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM tb_person_ministry pm
                JOIN tb_user_account ua ON ua.person_id = pm.person_id
                JOIN tb_user_account_role uar ON uar.user_account_id = ua.id
                JOIN tb_role r ON r.id = uar.role_id
                WHERE pm.ministry_type = ? AND r.authority = ?
                """,
                Integer.class,
                ministryType,
                authority
        );
        return count == null ? 0 : count;
    }

    private int countPeopleWithMinistryAndNoAccount(String ministryType) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM tb_person_ministry pm
                WHERE pm.ministry_type = ?
                  AND NOT EXISTS (SELECT 1 FROM tb_user_account ua WHERE ua.person_id = pm.person_id)
                """,
                Integer.class,
                ministryType
        );
        return count == null ? 0 : count;
    }

    private int countAccountsWithRole(String authority) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM tb_user_account_role uar
                JOIN tb_role r ON r.id = uar.role_id
                WHERE r.authority = ?
                """,
                Integer.class,
                authority
        );
        return count == null ? 0 : count;
    }

    private int countAccountsWithMoreThanOneRole() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM (
                    SELECT user_account_id
                    FROM tb_user_account_role
                    GROUP BY user_account_id
                    HAVING COUNT(*) > 1
                ) duplicated
                """,
                Integer.class
        );
        return count == null ? 0 : count;
    }

    private int countPersonsWithMoreThanOneAccount() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM (
                    SELECT person_id
                    FROM tb_user_account
                    GROUP BY person_id
                    HAVING COUNT(*) > 1
                ) duplicated
                """,
                Integer.class
        );
        return count == null ? 0 : count;
    }

    private int countPersonsWithoutAccount() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM tb_person p
                WHERE NOT EXISTS (SELECT 1 FROM tb_user_account ua WHERE ua.person_id = p.id)
                """,
                Integer.class
        );
        return count == null ? 0 : count;
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
