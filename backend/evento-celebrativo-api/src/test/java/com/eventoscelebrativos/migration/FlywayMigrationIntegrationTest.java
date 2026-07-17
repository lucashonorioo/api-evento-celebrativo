package com.eventoscelebrativos.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("flyway-test")
class FlywayMigrationIntegrationTest {

    private static final String[] CURRENT_TABLES = {
            "tb_celebration_event",
            "tb_location",
            "tb_person",
            "tb_role",
            "tb_event_location",
            "tb_event_person",
            "tb_person_role"
    };

    private static final String[] FUTURE_TABLES = {
            "person_ministry",
            "user_account",
            "user_account_role",
            "event_assignment"
    };

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldApplyCurrentSchemaAndRequiredRolesOnly() {
        assertSuccessfulMigration("1");
        assertSuccessfulMigration("2");
        assertTableExists("flyway_schema_history");

        for (String table : CURRENT_TABLES) {
            assertTableExists(table);
        }

        assertEquals(1, countRows("tb_role", "authority", "ROLE_OPERATOR"));
        assertEquals(1, countRows("tb_role", "authority", "ROLE_ADMIN"));
        assertEquals(0, countRows("tb_person"));
        assertEquals(0, countRows("tb_celebration_event"));

        for (String table : FUTURE_TABLES) {
            assertTableDoesNotExist(table);
        }

        Integer successfulVersions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version IN ('1', '2') AND success = TRUE",
                Integer.class
        );
        assertEquals(2, successfulVersions);
    }

    @Test
    void shouldKeepMigrationsStableWhenMigrateRunsAgain() {
        Map<String, Integer> checksumsBefore = migrationChecksums();

        MigrateResult result = flyway.migrate();

        assertEquals(0, result.migrationsExecuted);
        assertEquals(checksumsBefore, migrationChecksums());
        assertSuccessfulMigration("1");
        assertSuccessfulMigration("2");
        assertEquals(1, countRows("tb_role", "authority", "ROLE_OPERATOR"));
        assertEquals(1, countRows("tb_role", "authority", "ROLE_ADMIN"));
    }

    private void assertSuccessfulMigration(String version) {
        MigrationInfo migrationInfo = Arrays.stream(flyway.info().applied())
                .filter(info -> info.getVersion() != null)
                .filter(info -> version.equals(info.getVersion().getVersion()))
                .findFirst()
                .orElseThrow();

        assertEquals(MigrationState.SUCCESS, migrationInfo.getState());
    }

    private void assertTableExists(String tableName) {
        assertEquals(1, countInformationSchemaTables(tableName));
    }

    private void assertTableDoesNotExist(String tableName) {
        assertEquals(0, countInformationSchemaTables(tableName));
    }

    private int countInformationSchemaTables(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_name) = LOWER(?)",
                Integer.class,
                tableName
        );
        return count == null ? 0 : count;
    }

    private int countRows(String tableName) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
        return count == null ? 0 : count;
    }

    private int countRows(String tableName, String columnName, String value) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName + " WHERE " + columnName + " = ?",
                Integer.class,
                value
        );
        return count == null ? 0 : count;
    }

    private Map<String, Integer> migrationChecksums() {
        return Arrays.stream(flyway.info().applied())
                .filter(info -> info.getVersion() != null)
                .collect(Collectors.toMap(
                        info -> info.getVersion().getVersion(),
                        info -> Objects.requireNonNull(info.getChecksum())
                ));
    }
}
