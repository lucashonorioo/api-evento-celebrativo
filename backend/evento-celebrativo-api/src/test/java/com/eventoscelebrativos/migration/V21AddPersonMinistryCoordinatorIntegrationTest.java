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
 * Prova, contra H2 (rapido, parte de toda execucao da suite), que a migration V21 adiciona a coluna
 * {@code coordinator} em {@code tb_person_ministry} com o invariante estrutural
 * {@code coordinator=FALSE OR active=TRUE}, sem criar nova tabela nem alterar V1-V20.
 */
class V21AddPersonMinistryCoordinatorIntegrationTest {

    private static final String VERSIONED_MIGRATIONS_LOCATION = "classpath:db/migration";

    @Test
    void shouldNotHaveCoordinatorColumnBeforeV21() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateUntil(dataSource, "20");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertThrows(BadSqlGrammarException.class,
                () -> jdbcTemplate.queryForObject("SELECT coordinator FROM tb_person_ministry", Boolean.class));
    }

    @Test
    void shouldBackfillExistingRowsWithCoordinatorFalseWhenUpgradingFromV20() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateUntil(dataSource, "20");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long personId = insertPerson(jdbcTemplate, "Leitor Preexistente", "34988770010");
        jdbcTemplate.update(
                "INSERT INTO tb_person_ministry(person_id, ministry_type, active) VALUES (?, 'READER', TRUE)", personId);

        // migrateAll() migra ate a mais recente disponivel no classpath, nao apenas V21;
        // a partir de V20, isso executa V21 (esta migration) e V22-V29.
        MigrateResult result = migrateAll(dataSource);

        assertEquals(9, result.migrationsExecuted);
        assertFalse(jdbcTemplate.queryForObject(
                "SELECT coordinator FROM tb_person_ministry WHERE person_id = ?", Boolean.class, personId));
    }

    @Test
    void shouldDefaultCoordinatorToFalseOnNewRowWithoutExplicitValue() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateAll(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long personId = insertPerson(jdbcTemplate, "Leitor Novo", "34988770011");

        jdbcTemplate.update(
                "INSERT INTO tb_person_ministry(person_id, ministry_id) VALUES (?, ?)",
                personId, ministryId(jdbcTemplate, "LEITORES"));

        assertFalse(jdbcTemplate.queryForObject(
                "SELECT coordinator FROM tb_person_ministry WHERE person_id = ?", Boolean.class, personId));
    }

    @Test
    void shouldAllowActiveTrueWithCoordinatorTrue() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateAll(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long personId = insertPerson(jdbcTemplate, "Leitor Coordenador", "34988770012");

        jdbcTemplate.update(
                """
                INSERT INTO tb_person_ministry(person_id, ministry_id, active, coordinator)
                VALUES (?, ?, TRUE, TRUE)
                """,
                personId, ministryId(jdbcTemplate, "LEITORES"));

        assertTrue(jdbcTemplate.queryForObject(
                "SELECT coordinator FROM tb_person_ministry WHERE person_id = ?", Boolean.class, personId));
    }

    @Test
    void shouldAllowActiveFalseWithCoordinatorFalse() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateAll(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long personId = insertPerson(jdbcTemplate, "Leitor Inativo", "34988770013");

        jdbcTemplate.update(
                """
                INSERT INTO tb_person_ministry(person_id, ministry_id, active, coordinator)
                VALUES (?, ?, FALSE, FALSE)
                """,
                personId, ministryId(jdbcTemplate, "LEITORES"));

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_person_ministry WHERE person_id = ?", Integer.class, personId));
    }

    @Test
    void shouldRejectActiveFalseWithCoordinatorTrue() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateAll(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long personId = insertPerson(jdbcTemplate, "Leitor Invalido", "34988770014");

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                """
                INSERT INTO tb_person_ministry(person_id, ministry_id, active, coordinator)
                VALUES (?, ?, FALSE, TRUE)
                """,
                personId, ministryId(jdbcTemplate, "LEITORES")));
    }

    @Test
    void shouldRejectUpdatingActiveToFalseWhileCoordinatorRemainsTrue() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateAll(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long personId = insertPerson(jdbcTemplate, "Leitor Coordenador Dois", "34988770015");
        jdbcTemplate.update(
                """
                INSERT INTO tb_person_ministry(person_id, ministry_id, active, coordinator)
                VALUES (?, ?, TRUE, TRUE)
                """,
                personId, ministryId(jdbcTemplate, "LEITORES"));

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "UPDATE tb_person_ministry SET active = FALSE WHERE person_id = ?", personId));
    }

    @Test
    void shouldNotAlterAnyPreviousMigrationAndApplyAllVersionedMigrationsFromEmptySchema() {
        DataSource dataSource = newIsolatedH2DataSource();

        MigrateResult first = migrateAll(dataSource);
        MigrateResult second = migrateAll(dataSource);

        assertEquals(29, first.migrations.size());
        assertTrue(second.migrations.isEmpty());
    }

    @Test
    void shouldNotCreateNewTable() {
        DataSource dataSource = newIsolatedH2DataSource();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        migrateAll(dataSource);

        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_NAME) = 'TB_PERSON_MINISTRY'",
                Integer.class);
        assertEquals(1, tableCount);
    }

    private Long insertPerson(JdbcTemplate jdbcTemplate, String name, String phoneNumber) {
        if (hasPublicIdColumn(jdbcTemplate)) {
            jdbcTemplate.update(
                    "INSERT INTO tb_person(public_id, name, phone_number) VALUES (?, ?, ?)",
                    newPublicId(), name, phoneNumber);
        } else {
            jdbcTemplate.update("INSERT INTO tb_person(name, phone_number) VALUES (?, ?)", name, phoneNumber);
        }
        return jdbcTemplate.queryForObject("SELECT id FROM tb_person WHERE phone_number = ?", Long.class, phoneNumber);
    }

    private boolean hasPublicIdColumn(JdbcTemplate jdbcTemplate) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE UPPER(TABLE_NAME) = 'TB_PERSON'
                  AND UPPER(COLUMN_NAME) = 'PUBLIC_ID'
                """,
                Integer.class);
        return count != null && count > 0;
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
        String dbName = "personministrycoordinator_" + UUID.randomUUID().toString().replace("-", "");
        dataSource.setUrl("jdbc:h2:mem:" + dbName + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }
}
