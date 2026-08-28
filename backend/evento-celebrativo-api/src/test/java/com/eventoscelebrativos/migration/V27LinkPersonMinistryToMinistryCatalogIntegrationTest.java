package com.eventoscelebrativos.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V27LinkPersonMinistryToMinistryCatalogIntegrationTest {

    private static final String VERSIONED_MIGRATIONS_LOCATION = "classpath:db/migration";

    @Test
    void shouldNotHaveMinistryIdBeforeV27() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateUntil(dataSource, "26");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertThrows(BadSqlGrammarException.class,
                () -> jdbcTemplate.queryForObject("SELECT ministry_id FROM tb_person_ministry", Long.class));
    }

    @Test
    void shouldBackfillLegacyRowsAndPreserveExistingDataWhenUpgradingFromV26() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateUntil(dataSource, "26");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long personId = insertPerson(jdbcTemplate, "Leitor V27", "34988772010");
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 2, 3, 4, 5);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 1, 3, 4, 5, 6);
        jdbcTemplate.update(
                """
                INSERT INTO tb_person_ministry(person_id, ministry_type, active, coordinator, created_at, updated_at)
                VALUES (?, 'READER', TRUE, TRUE, ?, ?)
                """,
                personId,
                Timestamp.valueOf(createdAt),
                Timestamp.valueOf(updatedAt));
        Long personMinistryId = jdbcTemplate.queryForObject(
                "SELECT id FROM tb_person_ministry WHERE person_id = ?",
                Long.class,
                personId);

        MigrateResult result = migrateAll(dataSource);

        assertEquals(2, result.migrationsExecuted);
        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                SELECT pm.id, pm.person_id, pm.ministry_type, pm.ministry_id, pm.active, pm.coordinator,
                       pm.created_at, pm.updated_at, m.normalized_name
                FROM tb_person_ministry pm
                JOIN tb_ministry m ON m.id = pm.ministry_id
                WHERE pm.person_id = ?
                """,
                personId);
        assertEquals(personMinistryId, ((Number) row.get("id")).longValue());
        assertEquals(personId, ((Number) row.get("person_id")).longValue());
        assertEquals("READER", row.get("ministry_type"));
        assertEquals("LEITORES", row.get("normalized_name"));
        assertTrue(isTrue(row.get("active")));
        assertTrue(isTrue(row.get("coordinator")));
        assertEquals(Timestamp.valueOf(createdAt), row.get("created_at"));
        assertEquals(Timestamp.valueOf(updatedAt), row.get("updated_at"));
    }

    @Test
    void shouldMapAllFiveLegacyMinistryTypesToCatalogRows() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateUntil(dataSource, "26");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Map<String, String> expectedByType = new LinkedHashMap<>();
        expectedByType.put("PRIEST", "PRESBITEROS");
        expectedByType.put("READER", "LEITORES");
        expectedByType.put("COMMENTATOR", "COMENTARISTAS");
        expectedByType.put("MINISTER_OF_THE_WORD", "MINISTROS DA PALAVRA");
        expectedByType.put("EUCHARISTIC_MINISTER", "MINISTROS DA EUCARISTIA");
        int phoneSuffix = 20;
        for (String ministryType : expectedByType.keySet()) {
            Long personId = insertPerson(jdbcTemplate, "Pessoa " + ministryType, "349887720" + phoneSuffix++);
            jdbcTemplate.update(
                    "INSERT INTO tb_person_ministry(person_id, ministry_type, active, coordinator) VALUES (?, ?, TRUE, FALSE)",
                    personId,
                    ministryType);
        }

        migrateAll(dataSource);

        Map<String, String> actualByType = new LinkedHashMap<>();
        jdbcTemplate.queryForList(
                """
                SELECT pm.ministry_type, m.normalized_name
                FROM tb_person_ministry pm
                JOIN tb_ministry m ON m.id = pm.ministry_id
                ORDER BY pm.id
                """
        ).forEach(row -> actualByType.put((String) row.get("ministry_type"), (String) row.get("normalized_name")));
        assertEquals(expectedByType, actualByType);
        assertEquals(
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tb_person_ministry", Integer.class),
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tb_person_ministry WHERE ministry_id IS NOT NULL", Integer.class)
        );
    }

    @Test
    void shouldCreateNotNullForeignKeyAndUniqueConstraint() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateAll(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long personId = insertPerson(jdbcTemplate, "Leitor Constraints", "34988772030");
        Long readerMinistryId = ministryId(jdbcTemplate, "LEITORES");

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO tb_person_ministry(person_id, ministry_type) VALUES (?, 'READER')",
                personId));
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO tb_person_ministry(person_id, ministry_type, ministry_id) VALUES (?, 'READER', 999999)",
                personId));

        jdbcTemplate.update(
                "INSERT INTO tb_person_ministry(person_id, ministry_type, ministry_id) VALUES (?, 'READER', ?)",
                personId,
                readerMinistryId);
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO tb_person_ministry(person_id, ministry_type, ministry_id) VALUES (?, 'COMMENTATOR', ?)",
                personId,
                readerMinistryId));
    }

    @Test
    void shouldFailSafelyWhenRequiredCatalogRowIsMissing() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateUntil(dataSource, "26");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("DELETE FROM tb_ministry WHERE normalized_name = 'LEITORES'");

        assertThrows(FlywayException.class, () -> migrateAll(dataSource));
    }

    @Test
    void shouldFailSafelyWhenLegacyPersonMinistryTypeHasNoMapping() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateUntil(dataSource, "26");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long personId = insertPerson(jdbcTemplate, "Ministerio Desconhecido", "34988772031");
        jdbcTemplate.execute("ALTER TABLE tb_person_ministry DROP CONSTRAINT chk_tb_person_ministry_type");
        jdbcTemplate.update(
                "INSERT INTO tb_person_ministry(person_id, ministry_type, active, coordinator) VALUES (?, 'UNKNOWN', TRUE, FALSE)",
                personId);

        assertThrows(FlywayException.class, () -> migrateAll(dataSource));
    }

    @Test
    void shouldRecordV27AsSuccessfulMigrationAndRemainIdempotent() {
        DataSource dataSource = newIsolatedH2DataSource();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        MigrateResult first = migrateAll(dataSource);
        MigrateResult second = migrateAll(dataSource);

        assertEquals(28, first.migrationsExecuted);
        assertTrue(second.migrations.isEmpty());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '27' AND success = TRUE",
                Integer.class
        ));
    }

    private Long insertPerson(JdbcTemplate jdbcTemplate, String name, String phoneNumber) {
        jdbcTemplate.update(
                "INSERT INTO tb_person(public_id, name, phone_number) VALUES (?, ?, ?)",
                newPublicId(), name, phoneNumber);
        return jdbcTemplate.queryForObject("SELECT id FROM tb_person WHERE phone_number = ?", Long.class, phoneNumber);
    }

    private Long ministryId(JdbcTemplate jdbcTemplate, String normalizedName) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tb_ministry WHERE normalized_name = ?",
                Long.class,
                normalizedName);
    }

    private byte[] newPublicId() {
        UUID uuid = UUID.randomUUID();
        return ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
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
        String dbName = "personministrycatalog_" + UUID.randomUUID().toString().replace("-", "");
        dataSource.setUrl("jdbc:h2:mem:" + dbName + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }
}
