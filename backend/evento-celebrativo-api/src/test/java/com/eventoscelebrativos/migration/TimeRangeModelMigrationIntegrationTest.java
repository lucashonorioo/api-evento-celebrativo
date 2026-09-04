package com.eventoscelebrativos.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova em H2 a atualizacao V10 -> V11 para o modelo definitivo de intervalos
 * semiabertos [start_at, end_at), com precisao de segundos e sem colunas temporais legadas.
 */
class TimeRangeModelMigrationIntegrationTest {

    private static final String PASSWORD_HASH = "$2a$10$BZEayVp6X1Ry93e44/Rnze0hpK5J3ThbAdUm2OzH.GSWjA4zmtGHW";

    @Test
    void shouldBackfillPersonUnavailabilityRangeExactlyAndDropLegacyColumns() {
        DataSource dataSource = createDataSource("v11_unavailability_backfill");
        migrateUntil(dataSource, "10");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        long personId = insertPerson(jdbcTemplate, "Legacy Unavailable Person", "34990001001");
        LocalDate startDate = LocalDate.of(2026, 8, 10);
        LocalDate endDate = LocalDate.of(2026, 8, 12);
        long unavailabilityId = insertLegacyUnavailability(jdbcTemplate, personId, startDate, endDate, "Viagem");

        MigrateResult result = migrateAll(dataSource);

        assertTrue(result.migrationsExecuted >= 1);
        assertSuccessfulMigration(jdbcTemplate, "11");

        LocalDateTime expectedStartAt = startDate.atStartOfDay();
        LocalDateTime expectedEndAt = endDate.plusDays(1).atStartOfDay();
        assertEquals(Timestamp.valueOf(expectedStartAt), queryTimestamp(jdbcTemplate,
                "SELECT start_at FROM tb_person_unavailability WHERE id = ?", unavailabilityId));
        assertEquals(Timestamp.valueOf(expectedEndAt), queryTimestamp(jdbcTemplate,
                "SELECT end_at FROM tb_person_unavailability WHERE id = ?", unavailabilityId));

        assertFalse(columnExists(jdbcTemplate, "tb_person_unavailability", "start_date"));
        assertFalse(columnExists(jdbcTemplate, "tb_person_unavailability", "end_date"));
        assertTrue(columnExists(jdbcTemplate, "tb_person_unavailability", "start_at"));
        assertTrue(columnExists(jdbcTemplate, "tb_person_unavailability", "end_at"));
        assertFalse(columnNullable(jdbcTemplate, "tb_person_unavailability", "start_at"));
        assertFalse(columnNullable(jdbcTemplate, "tb_person_unavailability", "end_at"));
    }

    @Test
    void shouldEnforceRangeCheckConstraintOnPersonUnavailabilityAfterMigration() {
        DataSource dataSource = createDataSource("v11_unavailability_check");
        migrateAll(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        long personId = insertPerson(jdbcTemplate, "Post Migration Person", "34990001002");

        LocalDateTime sameInstant = LocalDateTime.of(2026, 9, 1, 10, 0);

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                """
                INSERT INTO tb_person_unavailability(person_id, start_at, end_at, created_at, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """,
                personId,
                Timestamp.valueOf(sameInstant),
                Timestamp.valueOf(sameInstant)
        ));
    }

    @Test
    void shouldBackfillCelebrationEventRangeExactlyAndDropLegacyColumns() {
        DataSource dataSource = createDataSource("v11_event_backfill");
        migrateUntil(dataSource, "10");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        LocalDate eventDate = LocalDate.of(2026, 8, 9);
        LocalTime eventTime = LocalTime.of(19, 0);
        long eventId = insertLegacyEvent(jdbcTemplate, "Missa Legada", eventDate, eventTime);

        MigrateResult result = migrateAll(dataSource);

        assertTrue(result.migrationsExecuted >= 1);
        assertSuccessfulMigration(jdbcTemplate, "11");

        LocalDateTime expectedStartAt = LocalDateTime.of(eventDate, eventTime);
        LocalDateTime expectedEndAt = expectedStartAt.plusHours(1);
        assertEquals(Timestamp.valueOf(expectedStartAt), queryTimestamp(jdbcTemplate,
                "SELECT start_at FROM tb_celebration_event WHERE id = ?", eventId));
        assertEquals(Timestamp.valueOf(expectedEndAt), queryTimestamp(jdbcTemplate,
                "SELECT end_at FROM tb_celebration_event WHERE id = ?", eventId));

        assertFalse(columnExists(jdbcTemplate, "tb_celebration_event", "event_date"));
        assertFalse(columnExists(jdbcTemplate, "tb_celebration_event", "event_time"));
        assertFalse(columnNullable(jdbcTemplate, "tb_celebration_event", "start_at"));
        assertFalse(columnNullable(jdbcTemplate, "tb_celebration_event", "end_at"));
    }

    @Test
    void shouldRejectNullEndAtOnCelebrationEventAfterMigration() {
        DataSource dataSource = createDataSource("v11_event_not_null");
        migrateAll(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        LocalDateTime startAt = LocalDateTime.of(2026, 10, 1, 8, 0);
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                """
                INSERT INTO tb_celebration_event(name_mass_or_event, start_at, end_at, mass_or_celebration)
                VALUES (?, ?, NULL, TRUE)
                """,
                "Evento sem end_at",
                Timestamp.valueOf(startAt)
        ));
    }

    @Test
    void shouldRejectInvertedRangeOnCelebrationEventAfterMigrationWhenEndAtIsProvided() {
        DataSource dataSource = createDataSource("v11_event_inverted_check");
        migrateAll(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        LocalDateTime startAt = LocalDateTime.of(2026, 10, 5, 20, 0);
        LocalDateTime invertedEndAt = startAt.minusHours(1);

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                """
                INSERT INTO tb_celebration_event(name_mass_or_event, start_at, end_at, mass_or_celebration)
                VALUES (?, ?, ?, TRUE)
                """,
                "Evento com intervalo invertido",
                Timestamp.valueOf(startAt),
                Timestamp.valueOf(invertedEndAt)
        ));
    }

    @Test
    void shouldExposeFinalSecondPrecisionConstraintsAndTemporalIndexes() {
        DataSource dataSource = createDataSource("v11_final_schema");
        migrateAll(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertEquals(0, columnDateTimePrecision(jdbcTemplate, "tb_celebration_event", "start_at"));
        assertEquals(0, columnDateTimePrecision(jdbcTemplate, "tb_celebration_event", "end_at"));
        assertEquals(0, columnDateTimePrecision(jdbcTemplate, "tb_person_unavailability", "start_at"));
        assertEquals(0, columnDateTimePrecision(jdbcTemplate, "tb_person_unavailability", "end_at"));

        assertTrue(constraintExists(
                jdbcTemplate, "tb_celebration_event", "chk_tb_celebration_event_range", "CHECK"));
        assertTrue(constraintExists(
                jdbcTemplate, "tb_person_unavailability", "chk_tb_person_unavailability_range", "CHECK"));
        assertTrue(constraintExists(
                jdbcTemplate, "tb_person_unavailability",
                "uk_tb_person_unavailability_person_range", "UNIQUE"));

        assertEquals(
                List.of("start_at", "end_at", "id"),
                indexColumns(jdbcTemplate, "tb_celebration_event", "idx_tb_celebration_event_start_end")
        );
        assertEquals(
                List.of("end_at", "start_at", "id"),
                indexColumns(jdbcTemplate, "tb_celebration_event", "idx_tb_celebration_event_end_start")
        );
        assertEquals(
                List.of("person_id", "end_at", "start_at"),
                indexColumns(
                        jdbcTemplate,
                        "tb_person_unavailability",
                        "idx_tb_person_unavailability_person_end_start"
                )
        );
        assertEquals(
                List.of("start_at", "end_at", "person_id"),
                indexColumns(
                        jdbcTemplate,
                        "tb_person_unavailability",
                        "idx_tb_person_unavailability_range_person"
                )
        );
        assertFalse(indexExists(
                jdbcTemplate, "tb_person_unavailability", "idx_tb_person_unavailability_person_range"));
    }

    private DataSource createDataSource(String namePrefix) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:" + namePrefix + "_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private void migrateUntil(DataSource dataSource, String target) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate();
    }

    private MigrateResult migrateAll(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("11")
                .load()
                .migrate();
    }

    private long insertPerson(JdbcTemplate jdbcTemplate, String name, String phoneNumber) {
        jdbcTemplate.update(
                """
                INSERT INTO tb_person(name, phone_number, birthday_date, password)
                VALUES (?, ?, '1990-01-01', ?)
                """,
                name,
                phoneNumber,
                PASSWORD_HASH
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tb_person WHERE phone_number = ?",
                Long.class,
                phoneNumber
        );
    }

    private long insertLegacyUnavailability(
            JdbcTemplate jdbcTemplate, long personId, LocalDate startDate, LocalDate endDate, String reason
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO tb_person_unavailability(person_id, start_date, end_date, reason, created_at, updated_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """,
                personId,
                java.sql.Date.valueOf(startDate),
                java.sql.Date.valueOf(endDate),
                reason
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tb_person_unavailability WHERE person_id = ?",
                Long.class,
                personId
        );
    }

    private long insertLegacyEvent(JdbcTemplate jdbcTemplate, String name, LocalDate eventDate, LocalTime eventTime) {
        String eventName = name + " " + UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO tb_celebration_event(name_mass_or_event, event_date, event_time, mass_or_celebration)
                VALUES (?, ?, ?, TRUE)
                """,
                eventName,
                java.sql.Date.valueOf(eventDate),
                java.sql.Time.valueOf(eventTime)
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tb_celebration_event WHERE name_mass_or_event = ?",
                Long.class,
                eventName
        );
    }

    private void assertSuccessfulMigration(JdbcTemplate jdbcTemplate, String version) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = ? AND success = TRUE",
                Integer.class,
                version
        );
        assertEquals(1, count == null ? 0 : count);
    }

    private Timestamp queryTimestamp(JdbcTemplate jdbcTemplate, String sql, long id) {
        return jdbcTemplate.queryForObject(sql, Timestamp.class, id);
    }

    private boolean columnExists(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE UPPER(table_name) = UPPER(?) AND UPPER(column_name) = UPPER(?)
                """,
                Integer.class,
                tableName,
                columnName
        );
        return count != null && count > 0;
    }

    private boolean columnNullable(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
        String isNullable = jdbcTemplate.queryForObject(
                """
                SELECT is_nullable FROM information_schema.columns
                WHERE UPPER(table_name) = UPPER(?) AND UPPER(column_name) = UPPER(?)
                """,
                String.class,
                tableName,
                columnName
        );
        return "YES".equalsIgnoreCase(isNullable);
    }

    private int columnDateTimePrecision(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
        Integer precision = jdbcTemplate.queryForObject(
                """
                SELECT datetime_precision FROM information_schema.columns
                WHERE UPPER(table_name) = UPPER(?) AND UPPER(column_name) = UPPER(?)
                """,
                Integer.class,
                tableName,
                columnName
        );
        return precision == null ? -1 : precision;
    }

    private boolean constraintExists(
            JdbcTemplate jdbcTemplate,
            String tableName,
            String constraintName,
            String constraintType
    ) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE UPPER(table_name) = UPPER(?)
                  AND UPPER(constraint_name) = UPPER(?)
                  AND UPPER(constraint_type) = UPPER(?)
                """,
                Integer.class,
                tableName,
                constraintName,
                constraintType
        );
        return count != null && count > 0;
    }

    private boolean indexExists(JdbcTemplate jdbcTemplate, String tableName, String indexName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.indexes
                WHERE UPPER(table_name) = UPPER(?) AND UPPER(index_name) = UPPER(?)
                """,
                Integer.class,
                tableName,
                indexName
        );
        return count != null && count > 0;
    }

    private List<String> indexColumns(JdbcTemplate jdbcTemplate, String tableName, String indexName) {
        return jdbcTemplate.queryForList(
                        """
                        SELECT LOWER(column_name) FROM information_schema.index_columns
                        WHERE UPPER(table_name) = UPPER(?) AND UPPER(index_name) = UPPER(?)
                        ORDER BY ordinal_position
                        """,
                        String.class,
                        tableName,
                        indexName
                );
    }
}
