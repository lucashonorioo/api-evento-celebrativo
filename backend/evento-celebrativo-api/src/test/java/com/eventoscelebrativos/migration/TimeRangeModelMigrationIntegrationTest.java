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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova o backfill exato da V11 (migracao para o modelo de intervalo [start_at, end_at)):
 * fase unica para PersonUnavailability (colunas antigas removidas, NOT NULL/CHECK/UNIQUE
 * definitivos) e fase 1 aditiva para CelebrationEvent (start_at exato e NOT NULL, end_at
 * propositalmente NULL para eventos ja existentes, colunas antigas preservadas e relaxadas
 * para nullable).
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
    void shouldBackfillCelebrationEventStartAtExactlyAndLeaveEndAtNullForExistingRows() {
        DataSource dataSource = createDataSource("v11_event_backfill");
        migrateUntil(dataSource, "10");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        LocalDate eventDate = LocalDate.of(2026, 9, 20);
        LocalTime eventTime = LocalTime.of(19, 30);
        long eventId = insertLegacyEvent(jdbcTemplate, "Missa Legada", eventDate, eventTime);

        MigrateResult result = migrateAll(dataSource);

        assertTrue(result.migrationsExecuted >= 1);
        assertSuccessfulMigration(jdbcTemplate, "11");

        LocalDateTime expectedStartAt = LocalDateTime.of(eventDate, eventTime);
        assertEquals(Timestamp.valueOf(expectedStartAt), queryTimestamp(jdbcTemplate,
                "SELECT start_at FROM tb_celebration_event WHERE id = ?", eventId));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT end_at FROM tb_celebration_event WHERE id = ?", Timestamp.class, eventId));

        assertTrue(columnExists(jdbcTemplate, "tb_celebration_event", "event_date"));
        assertTrue(columnExists(jdbcTemplate, "tb_celebration_event", "event_time"));
        assertTrue(columnNullable(jdbcTemplate, "tb_celebration_event", "event_date"));
        assertTrue(columnNullable(jdbcTemplate, "tb_celebration_event", "event_time"));
        assertFalse(columnNullable(jdbcTemplate, "tb_celebration_event", "start_at"));
        assertTrue(columnNullable(jdbcTemplate, "tb_celebration_event", "end_at"));

        assertEquals(java.sql.Date.valueOf(eventDate), jdbcTemplate.queryForObject(
                "SELECT event_date FROM tb_celebration_event WHERE id = ?", java.sql.Date.class, eventId));
        assertEquals(java.sql.Time.valueOf(eventTime), jdbcTemplate.queryForObject(
                "SELECT event_time FROM tb_celebration_event WHERE id = ?", java.sql.Time.class, eventId));
    }

    @Test
    void shouldAllowNullEndAtOnCelebrationEventAfterMigrationWithoutViolatingPermissiveCheck() {
        DataSource dataSource = createDataSource("v11_event_permissive_check");
        migrateAll(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        LocalDateTime startAt = LocalDateTime.of(2026, 10, 1, 8, 0);
        jdbcTemplate.update(
                """
                INSERT INTO tb_celebration_event(name_mass_or_event, start_at, end_at, mass_or_celebration)
                VALUES (?, ?, NULL, TRUE)
                """,
                "Evento sem end_at",
                Timestamp.valueOf(startAt)
        );

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_celebration_event WHERE name_mass_or_event = ?",
                Integer.class,
                "Evento sem end_at"
        );
        assertEquals(1, count);
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

    private DataSource createDataSource(String namePrefix) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:" + namePrefix + "_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
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
}
