package com.eventoscelebrativos.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V29RemovePersonMinistryLegacyTypeIntegrationTest {

    private static final String VERSIONED_MIGRATIONS_LOCATION = "classpath:db/migration";

    @Test
    void shouldNotHaveLegacyColumnAfterFreshSchemaThroughV29() {
        DataSource dataSource = newIsolatedH2DataSource();
        MigrateResult result = migrateAll(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertEquals(29, result.migrationsExecuted);
        assertColumnDoesNotExist(jdbcTemplate, "tb_person_ministry", "ministry_type");
        assertConstraintDoesNotExist(jdbcTemplate, "tb_person_ministry", "chk_tb_person_ministry_type");
        assertConstraintDoesNotExist(jdbcTemplate, "tb_person_ministry", "uk_tb_person_ministry_person_type");
        assertConstraintExists(jdbcTemplate, "tb_person_ministry", "uk_tb_person_ministry_person_ministry");
        assertConstraintExists(jdbcTemplate, "tb_person_ministry", "fk_tb_person_ministry_ministry");
        assertEquals(5, countRows(jdbcTemplate, "tb_ministry_legacy_type_mapping"));
        assertEquals(1, countSuccessfulMigration(jdbcTemplate, "29"));
    }

    @Test
    void shouldUpgradeFromV28DropLegacyColumnAndPreserveExistingPersonMinistryData() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateUntil(dataSource, "28");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long personId = insertPerson(jdbcTemplate, "Leitor V29", "34988776010");
        Long readerMinistryId = ministryId(jdbcTemplate, "LEITORES");
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 2, 3, 4, 5);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 1, 3, 4, 5, 6);
        jdbcTemplate.update(
                """
                INSERT INTO tb_person_ministry(person_id, ministry_type, ministry_id, active, coordinator, created_at, updated_at)
                VALUES (?, 'READER', ?, TRUE, TRUE, ?, ?)
                """,
                personId,
                readerMinistryId,
                Timestamp.valueOf(createdAt),
                Timestamp.valueOf(updatedAt));
        Long personMinistryId = jdbcTemplate.queryForObject(
                "SELECT id FROM tb_person_ministry WHERE person_id = ?",
                Long.class,
                personId);

        MigrateResult result = migrateAll(dataSource);

        assertEquals(1, result.migrationsExecuted);
        assertColumnDoesNotExist(jdbcTemplate, "tb_person_ministry", "ministry_type");
        assertConstraintDoesNotExist(jdbcTemplate, "tb_person_ministry", "chk_tb_person_ministry_type");
        assertConstraintDoesNotExist(jdbcTemplate, "tb_person_ministry", "uk_tb_person_ministry_person_type");
        assertConstraintExists(jdbcTemplate, "tb_person_ministry", "uk_tb_person_ministry_person_ministry");
        assertConstraintExists(jdbcTemplate, "tb_person_ministry", "fk_tb_person_ministry_ministry");

        var row = jdbcTemplate.queryForMap(
                """
                SELECT id, person_id, ministry_id, active, coordinator, created_at, updated_at
                FROM tb_person_ministry
                WHERE person_id = ?
                """,
                personId);
        assertEquals(personMinistryId, ((Number) row.get("id")).longValue());
        assertEquals(personId, ((Number) row.get("person_id")).longValue());
        assertEquals(readerMinistryId, ((Number) row.get("ministry_id")).longValue());
        assertTrue(isTrue(row.get("active")));
        assertTrue(isTrue(row.get("coordinator")));
        assertEquals(Timestamp.valueOf(createdAt), row.get("created_at"));
        assertEquals(Timestamp.valueOf(updatedAt), row.get("updated_at"));
        assertEquals(5, countRows(jdbcTemplate, "tb_ministry_legacy_type_mapping"));
    }

    @Test
    void shouldAllowArbitraryMinistryMembershipAfterV29WithoutLegacyTypeValue() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateAll(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long personId = insertPerson(jdbcTemplate, "Acolito V29", "34988776011");
        Long acolyteMinistryId = insertMinistry(jdbcTemplate, "Acolitos", "ACOLITOS");

        jdbcTemplate.update(
                """
                INSERT INTO tb_person_ministry(person_id, ministry_id, active, coordinator)
                VALUES (?, ?, TRUE, TRUE)
                """,
                personId,
                acolyteMinistryId);

        assertEquals(1, jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM tb_person_ministry
                WHERE person_id = ?
                  AND ministry_id = ?
                  AND active = TRUE
                  AND coordinator = TRUE
                """,
                Integer.class,
                personId,
                acolyteMinistryId));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_ministry_legacy_type_mapping WHERE ministry_id = ?",
                Integer.class,
                acolyteMinistryId));
    }

    @Test
    void shouldPreserveCanonicalUniqueAndForeignKeyConstraintsAfterV29() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateAll(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long personId = insertPerson(jdbcTemplate, "Constraints V29", "34988776012");
        Long readerMinistryId = ministryId(jdbcTemplate, "LEITORES");

        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "INSERT INTO tb_person_ministry(person_id, ministry_id) VALUES (?, 999999)",
                personId));

        jdbcTemplate.update(
                "INSERT INTO tb_person_ministry(person_id, ministry_id) VALUES (?, ?)",
                personId,
                readerMinistryId);
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "INSERT INTO tb_person_ministry(person_id, ministry_id) VALUES (?, ?)",
                personId,
                readerMinistryId));
        assertThrows(BadSqlGrammarException.class, () -> jdbcTemplate.update(
                "INSERT INTO tb_person_ministry(person_id, ministry_type, ministry_id) VALUES (?, 'READER', ?)",
                personId,
                readerMinistryId));
    }

    @Test
    void shouldRejectV28RowsWhoseLegacyTypeDoesNotMatchMappedMinistryIdBeforeDroppingColumn() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateUntil(dataSource, "28");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long personId = insertPerson(jdbcTemplate, "Inconsistent V29", "34988776013");
        Long commentatorMinistryId = ministryId(jdbcTemplate, "COMENTARISTAS");
        jdbcTemplate.update(
                """
                INSERT INTO tb_person_ministry(person_id, ministry_type, ministry_id, active, coordinator, created_at, updated_at)
                VALUES (?, 'READER', ?, TRUE, FALSE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """,
                personId,
                commentatorMinistryId);

        FlywayException exception = assertThrows(FlywayException.class, () -> migrateAll(dataSource));

        assertTrue(containsMessage(exception, "ministry_type inconsistente com ministry_id"));
        assertColumnExists(jdbcTemplate, "tb_person_ministry", "ministry_type");
    }

    private Long insertPerson(JdbcTemplate jdbcTemplate, String name, String phoneNumber) {
        jdbcTemplate.update(
                "INSERT INTO tb_person(public_id, name, phone_number) VALUES (?, ?, ?)",
                newPublicId(), name, phoneNumber);
        return jdbcTemplate.queryForObject("SELECT id FROM tb_person WHERE phone_number = ?", Long.class, phoneNumber);
    }

    private Long insertMinistry(JdbcTemplate jdbcTemplate, String name, String normalizedName) {
        jdbcTemplate.update(
                """
                INSERT INTO tb_ministry(name, normalized_name, active, created_at, updated_at)
                VALUES (?, ?, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """,
                name,
                normalizedName);
        return ministryId(jdbcTemplate, normalizedName);
    }

    private Long ministryId(JdbcTemplate jdbcTemplate, String normalizedName) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tb_ministry WHERE normalized_name = ?",
                Long.class,
                normalizedName);
    }

    private UUID newPublicId() {
        return UUID.randomUUID();
    }

    private boolean isTrue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() == 1;
        }
        return false;
    }

    private boolean containsMessage(Throwable exception, String expected) {
        Throwable current = exception;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(expected)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private int countRows(JdbcTemplate jdbcTemplate, String tableName) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
        return count == null ? 0 : count;
    }

    private int countSuccessfulMigration(JdbcTemplate jdbcTemplate, String version) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = ? AND success = TRUE",
                Integer.class,
                version
        );
        return count == null ? 0 : count;
    }

    private void assertColumnExists(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE LOWER(table_name) = LOWER(?) AND LOWER(column_name) = LOWER(?)",
                Integer.class,
                tableName,
                columnName
        );
        assertEquals(1, count == null ? 0 : count);
    }

    private void assertColumnDoesNotExist(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE LOWER(table_name) = LOWER(?) AND LOWER(column_name) = LOWER(?)",
                Integer.class,
                tableName,
                columnName
        );
        assertEquals(0, count == null ? 0 : count);
    }

    private void assertConstraintExists(JdbcTemplate jdbcTemplate, String tableName, String constraintName) {
        assertEquals(1, countConstraint(jdbcTemplate, tableName, constraintName));
    }

    private void assertConstraintDoesNotExist(JdbcTemplate jdbcTemplate, String tableName, String constraintName) {
        assertEquals(0, countConstraint(jdbcTemplate, tableName, constraintName));
    }

    private int countConstraint(JdbcTemplate jdbcTemplate, String tableName, String constraintName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.table_constraints
                WHERE LOWER(table_name) = LOWER(?)
                  AND LOWER(constraint_name) = LOWER(?)
                """,
                Integer.class,
                tableName,
                constraintName
        );
        return count == null ? 0 : count;
    }

    private void migrateUntil(DataSource dataSource, String target) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations(VERSIONED_MIGRATIONS_LOCATION)
                .target(target)
                .load()
                .migrate();
    }

    private MigrateResult migrateAll(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations(VERSIONED_MIGRATIONS_LOCATION)
                .load()
                .migrate();
    }

    private DataSource newIsolatedH2DataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        String dbName = "personministryv29_" + UUID.randomUUID().toString().replace("-", "");
        dataSource.setUrl("jdbc:h2:mem:" + dbName + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }
}
