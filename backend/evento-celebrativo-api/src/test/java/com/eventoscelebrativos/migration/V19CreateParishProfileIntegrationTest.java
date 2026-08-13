package com.eventoscelebrativos.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova, contra H2 (rapido, parte de toda execucao da suite), que a migration V19 cria
 * {@code tb_parish_profile} como singleton real: a linha id=1 ja existe apos a migration
 * (configured=false), nenhuma segunda linha pode ser inserida, e o registro so pode ficar
 * configured=true quando name e diocese estao preenchidos. Tambem prova que o upgrade a partir de
 * um banco ja em V18 funciona sem alterar as migrations anteriores.
 */
class V19CreateParishProfileIntegrationTest {

    private static final String VERSIONED_MIGRATIONS_LOCATION = "classpath:db/migration";

    @Test
    void shouldNotHaveParishProfileTableBeforeV19() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateUntil(dataSource, "18");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertThrows(BadSqlGrammarException.class,
                () -> jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tb_parish_profile", Integer.class));
    }

    @Test
    void shouldCreateSingletonRowUnconfiguredWhenUpgradingFromV18() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateUntil(dataSource, "18");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        MigrateResult result = migrateAll(dataSource);

        assertTrue(result.migrationsExecuted >= 1);
        assertEquals(1, countRows(jdbcTemplate));
        assertEquals(1L, jdbcTemplate.queryForObject("SELECT id FROM tb_parish_profile", Long.class));
        assertFalse(jdbcTemplate.queryForObject("SELECT configured FROM tb_parish_profile WHERE id = 1", Boolean.class));
        assertEquals(null, jdbcTemplate.queryForObject("SELECT name FROM tb_parish_profile WHERE id = 1", String.class));
        assertEquals(null, jdbcTemplate.queryForObject("SELECT diocese FROM tb_parish_profile WHERE id = 1", String.class));
    }

    @Test
    void shouldNotReapplyV19WhenMigratingAgain() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateAll(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        MigrateResult secondResult = migrateAll(dataSource);

        assertEquals(0, secondResult.migrationsExecuted);
        assertEquals(1, countRows(jdbcTemplate));
        assertEquals(1L, jdbcTemplate.queryForObject("SELECT id FROM tb_parish_profile", Long.class));
    }

    @Test
    void shouldRejectSecondRowWithDifferentId() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateAll(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO tb_parish_profile (id, configured) VALUES (2, FALSE)"));
        assertEquals(1, countRows(jdbcTemplate));
    }

    @Test
    void shouldRejectDuplicateIdOne() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateAll(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO tb_parish_profile (id, configured) VALUES (1, FALSE)"));
        assertEquals(1, countRows(jdbcTemplate));
    }

    @Test
    void shouldRejectConfiguredTrueWithoutNameOrDiocese() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateAll(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "UPDATE tb_parish_profile SET configured = TRUE WHERE id = 1"));
    }

    @Test
    void shouldAllowConfiguredTrueWhenNameAndDioceseArePresent() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateAll(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        jdbcTemplate.update(
                "UPDATE tb_parish_profile SET configured = TRUE, name = ?, diocese = ? WHERE id = 1",
                "Paróquia São José", "Diocese de Uberlândia");

        assertTrue(jdbcTemplate.queryForObject("SELECT configured FROM tb_parish_profile WHERE id = 1", Boolean.class));
        assertEquals(1, countRows(jdbcTemplate));
    }

    private int countRows(JdbcTemplate jdbcTemplate) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tb_parish_profile", Integer.class);
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
        String dbName = "parishprofile_" + UUID.randomUUID().toString().replace("-", "");
        dataSource.setUrl("jdbc:h2:mem:" + dbName + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }
}
