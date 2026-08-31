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
            "tb_event_location"
    };

    private static final String[] PARALLEL_DOMAIN_TABLES = {
            "tb_person_ministry",
            "tb_user_account",
            "tb_user_account_role",
            "tb_event_assignment"
    };

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldApplyCurrentSchemaAndRequiredRolesOnly() {
        assertSuccessfulMigration("1");
        assertSuccessfulMigration("2");
        assertSuccessfulMigration("3");
        assertSuccessfulMigration("4");
        assertSuccessfulMigration("5");
        assertSuccessfulMigration("6");
        assertSuccessfulMigration("7");
        assertSuccessfulMigration("8");
        assertSuccessfulMigration("9");
        assertSuccessfulMigration("10");
        assertSuccessfulMigration("11");
        assertSuccessfulMigration("12");
        assertSuccessfulMigration("13");
        assertSuccessfulMigration("14");
        assertSuccessfulMigration("15");
        assertSuccessfulMigration("16");
        assertSuccessfulMigration("17");
        assertSuccessfulMigration("18");
        assertSuccessfulMigration("19");
        assertSuccessfulMigration("20");
        assertSuccessfulMigration("21");
        assertSuccessfulMigration("22");
        assertSuccessfulMigration("23");
        assertSuccessfulMigration("24");
        assertSuccessfulMigration("25");
        assertSuccessfulMigration("26");
        assertSuccessfulMigration("27");
        assertSuccessfulMigration("28");
        assertSuccessfulMigration("29");
        assertTableExists("flyway_schema_history");
        assertTableExists("tb_event_participation_response");
        assertTableExists("tb_person_unavailability");
        assertTableExists("tb_ministry");
        assertTableExists("tb_ministry_legacy_type_mapping");
        assertTableDoesNotExist("tb_event_person");
        assertTableDoesNotExist("tb_person_role");
        assertColumnDoesNotExist("tb_person", "person_type");
        assertColumnDoesNotExist("tb_person", "password");
        assertColumnDoesNotExist("tb_person_ministry", "ministry_type");
        assertColumnDoesNotExist("tb_celebration_event", "event_date");
        assertColumnDoesNotExist("tb_celebration_event", "event_time");
        assertColumnExists("tb_celebration_event", "start_at");
        assertColumnExists("tb_celebration_event", "end_at");

        for (String table : CURRENT_TABLES) {
            assertTableExists(table);
        }

        for (String table : PARALLEL_DOMAIN_TABLES) {
            assertTableExists(table);
            assertEquals(0, countRows(table));
        }

        assertColumnExists("tb_person", "active");
        assertColumnExists("tb_person", "created_at");
        assertColumnExists("tb_person", "updated_at");
        assertColumnExists("tb_user_account", "token_version");
        assertColumnNotNullable("tb_user_account", "token_version");
        assertColumnExists("tb_person_ministry", "ministry_id");
        assertColumnNotNullable("tb_person_ministry", "ministry_id");

        assertMainConstraintExists("tb_person_ministry", "pk_tb_person_ministry");
        assertMainConstraintExists("tb_person_ministry", "uk_tb_person_ministry_person_ministry");
        assertMainConstraintExists("tb_person_ministry", "fk_tb_person_ministry_person");
        assertMainConstraintExists("tb_person_ministry", "fk_tb_person_ministry_ministry");
        assertMainConstraintExists("tb_ministry_legacy_type_mapping", "pk_tb_ministry_legacy_type_mapping");
        assertMainConstraintExists("tb_ministry_legacy_type_mapping", "uk_tb_ministry_legacy_type_mapping_type");
        assertMainConstraintExists("tb_ministry_legacy_type_mapping", "fk_tb_ministry_legacy_type_mapping_ministry");
        assertMainConstraintExists("tb_user_account", "pk_tb_user_account");
        assertMainConstraintExists("tb_user_account", "uk_tb_user_account_person_id");
        assertMainConstraintExists("tb_user_account", "uk_tb_user_account_username");
        assertMainConstraintExists("tb_user_account", "fk_tb_user_account_person");
        assertMainConstraintExists("tb_user_account_role", "pk_tb_user_account_role");
        assertMainConstraintExists("tb_user_account_role", "uk_tb_user_account_role_user_account");
        assertMainConstraintExists("tb_user_account_role", "fk_tb_user_account_role_user_account");
        assertMainConstraintExists("tb_user_account_role", "fk_tb_user_account_role_role");
        assertMainConstraintExists("tb_event_assignment", "pk_tb_event_assignment");
        assertMainConstraintExists("tb_event_assignment", "uk_tb_event_assignment_event_person");
        assertMainConstraintExists("tb_event_assignment", "chk_tb_event_assignment_type");
        assertMainConstraintExists("tb_event_assignment", "fk_tb_event_assignment_event");
        assertMainConstraintExists("tb_event_assignment", "fk_tb_event_assignment_person");

        assertEquals(1, countRows("tb_role", "authority", "ROLE_OPERATOR"));
        assertEquals(1, countRows("tb_role", "authority", "ROLE_ADMIN"));
        assertEquals(5, countRows("tb_ministry"));
        assertEquals(5, countRows("tb_ministry_legacy_type_mapping"));
        assertEquals(0, countRows("tb_person"));
        assertEquals(0, countRows("tb_celebration_event"));

        Integer successfulVersions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history "
                        + "WHERE version IN ('1','2','3','4','5','6','7','8','9','10','11','12','13','14','15','16','17','18',"
                        + "'19','20','21','22','23','24','25','26','27','28','29') "
                        + "AND success = TRUE",
                Integer.class
        );
        assertEquals(29, successfulVersions);
    }

    @Test
    void shouldKeepMigrationsStableWhenMigrateRunsAgain() {
        Map<String, String> checksumsBefore = migrationChecksums();

        MigrateResult result = flyway.migrate();

        assertEquals(0, result.migrationsExecuted);
        assertEquals(checksumsBefore, migrationChecksums());
        assertSuccessfulMigration("1");
        assertSuccessfulMigration("2");
        assertSuccessfulMigration("3");
        assertSuccessfulMigration("4");
        assertSuccessfulMigration("5");
        assertSuccessfulMigration("6");
        assertSuccessfulMigration("7");
        assertSuccessfulMigration("8");
        assertSuccessfulMigration("9");
        assertSuccessfulMigration("10");
        assertSuccessfulMigration("11");
        assertSuccessfulMigration("12");
        assertSuccessfulMigration("13");
        assertSuccessfulMigration("14");
        assertSuccessfulMigration("15");
        assertSuccessfulMigration("16");
        assertSuccessfulMigration("17");
        assertSuccessfulMigration("18");
        assertSuccessfulMigration("19");
        assertSuccessfulMigration("20");
        assertSuccessfulMigration("21");
        assertSuccessfulMigration("22");
        assertSuccessfulMigration("23");
        assertSuccessfulMigration("24");
        assertSuccessfulMigration("25");
        assertSuccessfulMigration("26");
        assertSuccessfulMigration("27");
        assertSuccessfulMigration("28");
        assertSuccessfulMigration("29");
        assertTableDoesNotExist("tb_event_person");
        assertTableDoesNotExist("tb_person_role");
        assertColumnDoesNotExist("tb_person", "person_type");
        assertColumnDoesNotExist("tb_person", "password");
        assertColumnDoesNotExist("tb_person_ministry", "ministry_type");
        assertEquals(5, countRows("tb_ministry"));
        assertEquals(5, countRows("tb_ministry_legacy_type_mapping"));
        assertEquals(1, countRows("tb_role", "authority", "ROLE_OPERATOR"));
        assertEquals(1, countRows("tb_role", "authority", "ROLE_ADMIN"));
        for (String table : PARALLEL_DOMAIN_TABLES) {
            assertEquals(0, countRows(table));
        }
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

    private void assertColumnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE LOWER(table_name) = LOWER(?) AND LOWER(column_name) = LOWER(?)",
                Integer.class,
                tableName,
                columnName
        );
        assertEquals(1, count);
    }

    private void assertColumnNullable(String tableName, String columnName) {
        assertColumnNullability(tableName, columnName, "YES");
    }

    private void assertColumnNotNullable(String tableName, String columnName) {
        assertColumnNullability(tableName, columnName, "NO");
    }

    private void assertColumnNullability(String tableName, String columnName, String expected) {
        String nullable = jdbcTemplate.queryForObject(
                "SELECT is_nullable FROM information_schema.columns WHERE LOWER(table_name) = LOWER(?) AND LOWER(column_name) = LOWER(?)",
                String.class,
                tableName,
                columnName
        );
        assertEquals(expected, nullable);
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

    private void assertMainConstraintExists(String tableName, String constraintName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints WHERE LOWER(table_name) = LOWER(?) AND LOWER(constraint_name) = LOWER(?)",
                Integer.class,
                tableName,
                constraintName
        );
        assertEquals(1, count);
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

    private Map<String, String> migrationChecksums() {
        return Arrays.stream(flyway.info().applied())
                .filter(info -> info.getVersion() != null)
                .collect(Collectors.toMap(
                        info -> info.getVersion().getVersion(),
                        info -> String.valueOf(info.getChecksum())
                ));
    }
}
