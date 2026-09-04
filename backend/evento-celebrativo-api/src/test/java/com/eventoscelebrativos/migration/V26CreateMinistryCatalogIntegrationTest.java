package com.eventoscelebrativos.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V26CreateMinistryCatalogIntegrationTest {

    private static final String VERSIONED_MIGRATIONS_LOCATION = "classpath:db/migration";

    @Test
    void shouldNotHaveMinistryTableBeforeV26() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateUntil(dataSource, "25");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertThrows(BadSqlGrammarException.class,
                () -> jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tb_ministry", Integer.class));
    }

    @Test
    void shouldCreateMinistryTableWhenUpgradingFromV25() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateUntil(dataSource, "25");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        MigrateResult result = migrateAll(dataSource);

        assertEquals(4, result.migrationsExecuted);
        assertEquals(1, tableCount(jdbcTemplate, "TB_MINISTRY"));
    }

    @Test
    void shouldCreateExpectedColumnsAndConstraints() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateAll(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertColumnNotNullable(jdbcTemplate, "ID");
        assertColumnNotNullable(jdbcTemplate, "NAME");
        assertColumnNotNullable(jdbcTemplate, "NORMALIZED_NAME");
        assertColumnNotNullable(jdbcTemplate, "ACTIVE");
        assertColumnNotNullable(jdbcTemplate, "CREATED_AT");
        assertColumnNotNullable(jdbcTemplate, "UPDATED_AT");
        assertEquals(1, constraintCount(jdbcTemplate, "TB_MINISTRY", "PRIMARY KEY"));
        assertEquals(1, constraintCount(jdbcTemplate, "TB_MINISTRY", "UNIQUE"));
    }

    @Test
    void shouldSeedFiveLegacyMinistriesAsActive() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateAll(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT name, normalized_name, active FROM tb_ministry ORDER BY id"
        );

        assertEquals(5, rows.size());
        assertSeedRow(rows.get(0), "Presbíteros", "PRESBITEROS");
        assertSeedRow(rows.get(1), "Leitores", "LEITORES");
        assertSeedRow(rows.get(2), "Comentaristas", "COMENTARISTAS");
        assertSeedRow(rows.get(3), "Ministros da Palavra", "MINISTROS DA PALAVRA");
        assertSeedRow(rows.get(4), "Ministros da Eucaristia", "MINISTROS DA EUCARISTIA");
    }

    @Test
    void shouldRejectDuplicateNormalizedName() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateAll(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO tb_ministry(name, normalized_name, active) VALUES (' leitores ', 'LEITORES', TRUE)"
        ));
    }

    @Test
    void shouldRejectNullRequiredFields() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateAll(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO tb_ministry(name, normalized_name, active) VALUES (NULL, 'ACOLITOS', TRUE)"
        ));
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO tb_ministry(name, normalized_name, active) VALUES ('Acólitos', NULL, TRUE)"
        ));
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO tb_ministry(name, normalized_name, active) VALUES ('Acólitos', 'ACOLITOS', NULL)"
        ));
    }

    @Test
    void shouldKeepSeedStableWhenFlywayMigratesAgain() {
        DataSource dataSource = newIsolatedH2DataSource();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        MigrateResult first = migrateAll(dataSource);
        MigrateResult second = migrateAll(dataSource);

        assertEquals(29, first.migrations.size());
        assertTrue(second.migrations.isEmpty());
        assertEquals(5, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tb_ministry", Integer.class));
    }

    @Test
    void shouldRecordV26AsSuccessfulMigration() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateAll(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '26' AND success = TRUE",
                Integer.class
        ));
    }

    private void assertSeedRow(Map<String, Object> row, String expectedName, String expectedNormalizedName) {
        assertEquals(expectedName, row.get("name"));
        assertEquals(expectedNormalizedName, row.get("normalized_name"));
        assertTrue(isTrue(row.get("active")));
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

    private int tableCount(JdbcTemplate jdbcTemplate, String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.TABLES
                WHERE UPPER(TABLE_NAME) = ?
                """,
                Integer.class,
                tableName
        );
        return count == null ? 0 : count;
    }

    private void assertColumnNotNullable(JdbcTemplate jdbcTemplate, String columnName) {
        assertEquals("NO", jdbcTemplate.queryForObject(
                """
                SELECT IS_NULLABLE
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE UPPER(TABLE_NAME) = 'TB_MINISTRY'
                  AND UPPER(COLUMN_NAME) = ?
                """,
                String.class,
                columnName
        ));
    }

    private int constraintCount(JdbcTemplate jdbcTemplate, String tableName, String constraintType) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                WHERE UPPER(TABLE_NAME) = ?
                  AND CONSTRAINT_TYPE = ?
                """,
                Integer.class,
                tableName,
                constraintType
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
        String dbName = "ministrycatalog_" + UUID.randomUUID().toString().replace("-", "");
        dataSource.setUrl("jdbc:h2:mem:" + dbName + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }
}
