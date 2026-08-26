package com.eventoscelebrativos.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova, contra H2 (rapido, parte de toda execucao da suite), que a migration V22 adiciona a coluna
 * {@code status} em {@code tb_celebration_event} com default ACTIVE, NOT NULL e a CHECK
 * {@code status IN ('ACTIVE', 'CANCELLED')}, sem criar nova tabela nem alterar V1-V21.
 */
class V22AddCelebrationEventStatusIntegrationTest {

    private static final String VERSIONED_MIGRATIONS_LOCATION = "classpath:db/migration";

    @Test
    void shouldNotHaveStatusColumnBeforeV22() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateUntil(dataSource, "21");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertThrows(BadSqlGrammarException.class,
                () -> jdbcTemplate.queryForObject("SELECT status FROM tb_celebration_event", String.class));
    }

    @Test
    void shouldBackfillExistingRowsWithStatusActiveWhenUpgradingFromV21() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateUntil(dataSource, "21");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long eventId = insertEvent(jdbcTemplate, "Missa Preexistente");

        MigrateResult result = migrateAll(dataSource);

        assertEquals(5, result.migrationsExecuted);
        assertEquals("ACTIVE", jdbcTemplate.queryForObject(
                "SELECT status FROM tb_celebration_event WHERE id = ?", String.class, eventId));
    }

    @Test
    void shouldDefaultStatusToActiveOnNewRowWithoutExplicitValue() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateAll(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long eventId = insertEvent(jdbcTemplate, "Missa Nova");

        assertEquals("ACTIVE", jdbcTemplate.queryForObject(
                "SELECT status FROM tb_celebration_event WHERE id = ?", String.class, eventId));
    }

    @Test
    void shouldRejectNullStatus() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateAll(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                """
                INSERT INTO tb_celebration_event(name_mass_or_event, start_at, end_at, mass_or_celebration, status)
                VALUES (?, ?, ?, TRUE, NULL)
                """,
                "Missa Status Nulo", LocalDateTime.of(2027, 1, 1, 10, 0), LocalDateTime.of(2027, 1, 1, 11, 0)));
    }

    @Test
    void shouldAllowStatusCancelled() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateAll(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long eventId = insertEventWithStatus(jdbcTemplate, "Missa Cancelada", "CANCELLED");

        assertEquals("CANCELLED", jdbcTemplate.queryForObject(
                "SELECT status FROM tb_celebration_event WHERE id = ?", String.class, eventId));
    }

    @Test
    void shouldRejectInvalidStatusValue() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateAll(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertThrows(DataIntegrityViolationException.class,
                () -> insertEventWithStatus(jdbcTemplate, "Missa Status Invalido", "DELETED"));
    }

    @Test
    void shouldNotAlterAnyPreviousMigrationAndApplyAllVersionedMigrationsFromEmptySchema() {
        DataSource dataSource = newIsolatedH2DataSource();

        MigrateResult first = migrateAll(dataSource);
        MigrateResult second = migrateAll(dataSource);

        assertEquals(26, first.migrations.size());
        assertTrue(second.migrations.isEmpty());
    }

    @Test
    void shouldNotCreateNewTable() {
        DataSource dataSource = newIsolatedH2DataSource();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        migrateAll(dataSource);

        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_NAME) = 'TB_CELEBRATION_EVENT'",
                Integer.class);
        assertEquals(1, tableCount);
    }

    private Long insertEvent(JdbcTemplate jdbcTemplate, String name) {
        jdbcTemplate.update(
                "INSERT INTO tb_celebration_event(name_mass_or_event, start_at, end_at, mass_or_celebration) VALUES (?, ?, ?, TRUE)",
                name, LocalDateTime.of(2027, 1, 1, 10, 0), LocalDateTime.of(2027, 1, 1, 11, 0));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tb_celebration_event WHERE name_mass_or_event = ?", Long.class, name);
    }

    private Long insertEventWithStatus(JdbcTemplate jdbcTemplate, String name, String status) {
        jdbcTemplate.update(
                """
                INSERT INTO tb_celebration_event(name_mass_or_event, start_at, end_at, mass_or_celebration, status)
                VALUES (?, ?, ?, TRUE, ?)
                """,
                name, LocalDateTime.of(2027, 1, 1, 10, 0), LocalDateTime.of(2027, 1, 1, 11, 0), status);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tb_celebration_event WHERE name_mass_or_event = ?", Long.class, name);
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
        String dbName = "celebrationeventstatus_" + UUID.randomUUID().toString().replace("-", "");
        dataSource.setUrl("jdbc:h2:mem:" + dbName + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }
}
