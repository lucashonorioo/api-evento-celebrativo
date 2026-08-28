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

class V28CreateMinistryLegacyTypeMappingIntegrationTest {

    private static final String VERSIONED_MIGRATIONS_LOCATION = "classpath:db/migration";

    @Test
    void shouldNotHaveMappingTableBeforeV28() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateUntil(dataSource, "27");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertThrows(BadSqlGrammarException.class,
                () -> jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tb_ministry_legacy_type_mapping", Integer.class));
    }

    @Test
    void shouldCreateFiveMappingsWhenUpgradingFromV27() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateUntil(dataSource, "27");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        MigrateResult result = migrateAll(dataSource);

        assertEquals(1, result.migrationsExecuted);
        assertEquals(5, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_ministry_legacy_type_mapping", Integer.class));
        assertEquals(5, jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT ministry_id) FROM tb_ministry_legacy_type_mapping", Integer.class));
        assertEquals(5, jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT ministry_type) FROM tb_ministry_legacy_type_mapping", Integer.class));

        Map<String, String> actualByType = new LinkedHashMap<>();
        jdbcTemplate.queryForList(
                """
                SELECT lm.ministry_type, m.normalized_name
                FROM tb_ministry_legacy_type_mapping lm
                JOIN tb_ministry m ON m.id = lm.ministry_id
                ORDER BY lm.ministry_type
                """
        ).forEach(row -> actualByType.put((String) row.get("ministry_type"), (String) row.get("normalized_name")));

        Map<String, String> expectedByType = new LinkedHashMap<>();
        expectedByType.put("COMMENTATOR", "COMENTARISTAS");
        expectedByType.put("EUCHARISTIC_MINISTER", "MINISTROS DA EUCARISTIA");
        expectedByType.put("MINISTER_OF_THE_WORD", "MINISTROS DA PALAVRA");
        expectedByType.put("PRIEST", "PRESBITEROS");
        expectedByType.put("READER", "LEITORES");
        assertEquals(expectedByType, actualByType);
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '28' AND success = TRUE",
                Integer.class));
    }

    @Test
    void shouldEnforcePrimaryKeyUniqueTypeForeignKeyAndCheckConstraints() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateAll(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long readerMinistryId = ministryId(jdbcTemplate, "LEITORES");
        Long arbitraryMinistryId = insertMinistry(jdbcTemplate, "Acolitos", "ACOLITOS");

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO tb_ministry_legacy_type_mapping(ministry_id, ministry_type) VALUES (?, 'COMMENTATOR')",
                readerMinistryId));
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO tb_ministry_legacy_type_mapping(ministry_id, ministry_type) VALUES (?, 'READER')",
                arbitraryMinistryId));
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO tb_ministry_legacy_type_mapping(ministry_id, ministry_type) VALUES (999999, 'READER')"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO tb_ministry_legacy_type_mapping(ministry_id, ministry_type) VALUES (?, 'ACOLYTE')",
                arbitraryMinistryId));
    }

    @Test
    void shouldKeepMappingStableWhenMinistryIsRenamedAfterMigration() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateAll(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long readerMinistryId = ministryId(jdbcTemplate, "LEITORES");

        jdbcTemplate.update(
                """
                UPDATE tb_ministry
                SET name = 'Leitores e Salmistas',
                    normalized_name = 'LEITORES E SALMISTAS',
                    updated_at = CURRENT_TIMESTAMP(6)
                WHERE id = ?
                """,
                readerMinistryId);

        assertEquals(readerMinistryId, jdbcTemplate.queryForObject(
                """
                SELECT ministry_id
                FROM tb_ministry_legacy_type_mapping
                WHERE ministry_type = 'READER'
                """,
                Long.class));
    }

    @Test
    void shouldNotModifyPersonMinistryRowsWhenCreatingMapping() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateUntil(dataSource, "27");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long personId = insertPerson(jdbcTemplate, "Leitor V28", "34988774010");
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

        migrateAll(dataSource);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                SELECT ministry_type, ministry_id, active, coordinator, created_at, updated_at
                FROM tb_person_ministry
                WHERE person_id = ?
                """,
                personId);
        assertEquals("READER", row.get("ministry_type"));
        assertEquals(readerMinistryId, ((Number) row.get("ministry_id")).longValue());
        assertEquals(true, isTrue(row.get("active")));
        assertEquals(true, isTrue(row.get("coordinator")));
        assertEquals(Timestamp.valueOf(createdAt), row.get("created_at"));
        assertEquals(Timestamp.valueOf(updatedAt), row.get("updated_at"));
    }

    @Test
    void shouldFailSafelyWhenRequiredCatalogRowIsMissing() {
        DataSource dataSource = newIsolatedH2DataSource();
        migrateUntil(dataSource, "27");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("DELETE FROM tb_ministry WHERE normalized_name = 'LEITORES'");

        assertThrows(FlywayException.class, () -> migrateAll(dataSource));
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
        String dbName = "legacytypemapping_" + UUID.randomUUID().toString().replace("-", "");
        dataSource.setUrl("jdbc:h2:mem:" + dbName + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }
}
