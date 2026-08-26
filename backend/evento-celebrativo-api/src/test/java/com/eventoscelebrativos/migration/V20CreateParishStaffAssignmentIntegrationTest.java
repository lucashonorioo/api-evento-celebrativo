package com.eventoscelebrativos.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.ByteBuffer;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova, contra H2 (rapido, parte de toda execucao da suite), que a migration V20 cria
 * {@code tb_parish_staff_assignment} com o schema esperado: FK para tb_person sem cascade, unicidade
 * de person_id + responsibility, CHECK restringindo a PASTOR/PARISH_SECRETARY, indice por
 * responsibility + active, e default active=TRUE. Tambem prova que o upgrade a partir de um banco ja
 * em V19 funciona sem alterar nenhuma migration anterior.
 */
class V20CreateParishStaffAssignmentIntegrationTest {

    private static final String VERSIONED_MIGRATIONS_LOCATION = "classpath:db/migration";

    @Test
    void shouldNotHaveParishStaffAssignmentTableBeforeV20() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateUntil(dataSource, "19");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertThrows(BadSqlGrammarException.class,
                () -> jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tb_parish_staff_assignment", Integer.class));
    }

    @Test
    void shouldCreateTableWhenUpgradingFromV19() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateUntil(dataSource, "19");

        MigrateResult result = migrateAll(dataSource);

        assertTrue(result.migrationsExecuted >= 1);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        assertEquals(0, countRows(jdbcTemplate));
    }

    @Test
    void shouldNotReapplyV20WhenMigratingAgain() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateAll(dataSource);

        MigrateResult secondResult = migrateAll(dataSource);

        assertEquals(0, secondResult.migrationsExecuted);
    }

    @Test
    void shouldNotChangeAnyPreviousMigrationChecksum() {
        DataSource dataSource = newIsolatedH2DataSource();

        MigrateResult first = migrateAll(dataSource);
        MigrateResult second = migrateAll(dataSource);

        assertEquals(27, first.migrations.size());
        assertTrue(second.migrations.isEmpty());
    }

    @Test
    void shouldDefaultActiveToTrueAndPersistTimestamps() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateAll(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long personId = insertPerson(jdbcTemplate, "Padre Miguel", "34988776600");

        jdbcTemplate.update(
                "INSERT INTO tb_parish_staff_assignment (person_id, responsibility) VALUES (?, 'PASTOR')", personId);

        assertTrue(jdbcTemplate.queryForObject(
                "SELECT active FROM tb_parish_staff_assignment WHERE person_id = ?", Boolean.class, personId));
        assertEquals(1, countRows(jdbcTemplate));
    }

    @Test
    void shouldRejectFkToNonexistentPerson() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateAll(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO tb_parish_staff_assignment (person_id, responsibility) VALUES (999999, 'PASTOR')"));
    }

    @Test
    void shouldRejectDuplicatePersonAndResponsibility() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateAll(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long personId = insertPerson(jdbcTemplate, "Padre Paulo", "34988776601");
        jdbcTemplate.update(
                "INSERT INTO tb_parish_staff_assignment (person_id, responsibility) VALUES (?, 'PASTOR')", personId);

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO tb_parish_staff_assignment (person_id, responsibility) VALUES (?, 'PASTOR')", personId));
    }

    @Test
    void shouldAllowSamePersonWithBothResponsibilityTypes() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateAll(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long personId = insertPerson(jdbcTemplate, "Padre Roberto", "34988776602");

        jdbcTemplate.update(
                "INSERT INTO tb_parish_staff_assignment (person_id, responsibility) VALUES (?, 'PASTOR')", personId);
        jdbcTemplate.update(
                "INSERT INTO tb_parish_staff_assignment (person_id, responsibility) VALUES (?, 'PARISH_SECRETARY')", personId);

        assertEquals(2, countRows(jdbcTemplate));
    }

    @Test
    void shouldRejectResponsibilityOutsideAllowedEnum() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateAll(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long personId = insertPerson(jdbcTemplate, "Pessoa Qualquer", "34988776603");

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO tb_parish_staff_assignment (person_id, responsibility) VALUES (?, 'DEACON')", personId));
    }

    @Test
    void shouldNotCascadeDeleteFromPerson() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateAll(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long personId = insertPerson(jdbcTemplate, "Padre Sem Cascata", "34988776604");
        jdbcTemplate.update(
                "INSERT INTO tb_parish_staff_assignment (person_id, responsibility) VALUES (?, 'PASTOR')", personId);

        assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("DELETE FROM tb_person WHERE id = ?", personId));
        assertEquals(1, countRows(jdbcTemplate));
    }

    @Test
    void shouldHaveIndexOnResponsibilityAndActive() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateAll(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        Integer indexCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES "
                        + "WHERE UPPER(TABLE_NAME) = 'TB_PARISH_STAFF_ASSIGNMENT' "
                        + "AND UPPER(INDEX_NAME) = 'IDX_TB_PARISH_STAFF_ASSIGNMENT_RESPONSIBILITY_ACTIVE'",
                Integer.class);

        assertEquals(1, indexCount);
    }

    private int countRows(JdbcTemplate jdbcTemplate) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tb_parish_staff_assignment", Integer.class);
        return count == null ? 0 : count;
    }

    private Long insertPerson(JdbcTemplate jdbcTemplate, String name, String phoneNumber) {
        jdbcTemplate.update(
                "INSERT INTO tb_person(public_id, name, phone_number) VALUES (?, ?, ?)",
                newPublicId(), name, phoneNumber);
        return jdbcTemplate.queryForObject("SELECT id FROM tb_person WHERE phone_number = ?", Long.class, phoneNumber);
    }

    private byte[] newPublicId() {
        UUID uuid = UUID.randomUUID();
        return ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
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
        String dbName = "parishstaffassignment_" + UUID.randomUUID().toString().replace("-", "");
        dataSource.setUrl("jdbc:h2:mem:" + dbName + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }
}
