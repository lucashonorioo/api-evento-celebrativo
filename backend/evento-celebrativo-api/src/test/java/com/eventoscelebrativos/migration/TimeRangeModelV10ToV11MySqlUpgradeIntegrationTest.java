package com.eventoscelebrativos.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova a atualizacao real V10 -> V11 em MySQL 8.4, partindo de um schema V10 com dados
 * representativos ja persistidos (nao um banco vazio migrado V1..V11 do zero). Cada teste usa
 * uma database MySQL isolada e propria (criada e removida neste teste), sem compartilhar schema
 * com o restante da suite. E' automaticamente ignorado (Assumptions) quando MySQL nao estiver
 * acessivel via as propriedades de sistema/variaveis de ambiente abaixo, para que `mvnw test`
 * continue verde sem Docker.
 *
 * Propriedades/env vars opcionais (sem valor padrao para credenciais):
 *   mysql.validation.host (padrao localhost)
 *   mysql.validation.port (padrao 3307)
 *   mysql.validation.username (padrao root)
 *   mysql.validation.password / MYSQL_VALIDATION_PASSWORD (obrigatoria; sem ela o teste e ignorado)
 */
class TimeRangeModelV10ToV11MySqlUpgradeIntegrationTest {

    private static String host;
    private static String port;
    private static String username;
    private static String password;
    private static boolean mysqlAvailable;

    @BeforeAll
    static void checkMySqlAvailability() {
        host = System.getProperty("mysql.validation.host", "localhost");
        port = System.getProperty("mysql.validation.port", "3307");
        username = System.getProperty("mysql.validation.username", "root");
        password = System.getProperty("mysql.validation.password", System.getenv("MYSQL_VALIDATION_PASSWORD"));

        if (password == null || password.isBlank()) {
            mysqlAvailable = false;
            return;
        }
        try (Connection connection = DriverManager.getConnection(bootstrapUrl(), username, password)) {
            mysqlAvailable = connection.isValid(3);
        } catch (SQLException e) {
            mysqlAvailable = false;
        }
    }

    @Test
    void shouldUpgradeV10DatasetToV11WithExactBackfillAndPreservedIdentity() throws SQLException {
        Assumptions.assumeTrue(mysqlAvailable, "MySQL 8.4 real nao acessivel; teste ignorado.");

        String databaseName = createIsolatedDatabase("v11_upgrade");
        try {
            DataSource dataSource = dataSourceFor(databaseName);
            migrateUntil(dataSource, "10");
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            String mysqlVersion = jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
            assertTrue(mysqlVersion != null && mysqlVersion.startsWith("8.4."),
                    "A validacao exige MySQL 8.4 real, mas encontrou: " + mysqlVersion);

            // --- Dataset representativo em schema V10 ---
            long personId = insertPerson(jdbcTemplate, "Ana Beatriz Ferreira", "34991230001");

            LocalDate singleDay = LocalDate.of(2026, 8, 9);
            Timestamp singleDayCreatedAt = Timestamp.valueOf(LocalDateTime.of(2026, 1, 5, 9, 0));
            Timestamp singleDayUpdatedAt = Timestamp.valueOf(LocalDateTime.of(2026, 1, 5, 9, 0));
            long singleDayUnavailabilityId = insertLegacyUnavailability(
                    jdbcTemplate, personId, singleDay, singleDay, null, singleDayCreatedAt, singleDayUpdatedAt);

            LocalDate multiDayStart = LocalDate.of(2026, 8, 10);
            LocalDate multiDayEnd = LocalDate.of(2026, 8, 12);
            Timestamp multiDayCreatedAt = Timestamp.valueOf(LocalDateTime.of(2026, 1, 6, 14, 30));
            Timestamp multiDayUpdatedAt = Timestamp.valueOf(LocalDateTime.of(2026, 1, 7, 8, 15));
            long multiDayUnavailabilityId = insertLegacyUnavailability(
                    jdbcTemplate, personId, multiDayStart, multiDayEnd, "Viagem de familia",
                    multiDayCreatedAt, multiDayUpdatedAt);

            // Regra autorizada apenas para os dados legados descartaveis: end_at = start_at + 1 hora.
            LocalDate eventDate = LocalDate.of(2026, 8, 9);
            LocalTime eventTime = LocalTime.of(19, 0);
            long eventId = insertLegacyEvent(jdbcTemplate, "Missa Legada V10", eventDate, eventTime);

            long assignmentId = insertEventAssignment(jdbcTemplate, eventId, personId, "READER");
            Timestamp respondedAt = Timestamp.valueOf(LocalDateTime.of(2026, 1, 8, 10, 0));
            String declineReason = "Compromisso familiar";
            long participationId = insertEventParticipationResponse(
                    jdbcTemplate, eventId, personId, "DECLINED", declineReason, respondedAt);

            MigrateResult result = migrateAll(dataSource);
            assertTrue(result.migrationsExecuted >= 1);
            assertSuccessfulMigration(jdbcTemplate, "11");

            // --- Conversao exata das indisponibilidades ---
            assertEquals(Timestamp.valueOf(LocalDateTime.of(2026, 8, 9, 0, 0)), queryTimestamp(
                    jdbcTemplate, "SELECT start_at FROM tb_person_unavailability WHERE id = ?", singleDayUnavailabilityId));
            assertEquals(Timestamp.valueOf(LocalDateTime.of(2026, 8, 10, 0, 0)), queryTimestamp(
                    jdbcTemplate, "SELECT end_at FROM tb_person_unavailability WHERE id = ?", singleDayUnavailabilityId));

            assertEquals(Timestamp.valueOf(LocalDateTime.of(2026, 8, 10, 0, 0)), queryTimestamp(
                    jdbcTemplate, "SELECT start_at FROM tb_person_unavailability WHERE id = ?", multiDayUnavailabilityId));
            assertEquals(Timestamp.valueOf(LocalDateTime.of(2026, 8, 13, 0, 0)), queryTimestamp(
                    jdbcTemplate, "SELECT end_at FROM tb_person_unavailability WHERE id = ?", multiDayUnavailabilityId));

            // --- Identidade preservada: id, person_id, reason, created_at, updated_at ---
            assertEquals(personId, jdbcTemplate.queryForObject(
                    "SELECT person_id FROM tb_person_unavailability WHERE id = ?", Long.class, singleDayUnavailabilityId));
            assertNull(jdbcTemplate.queryForObject(
                    "SELECT reason FROM tb_person_unavailability WHERE id = ?", String.class, singleDayUnavailabilityId));
            assertEquals(singleDayCreatedAt, queryTimestamp(
                    jdbcTemplate, "SELECT created_at FROM tb_person_unavailability WHERE id = ?", singleDayUnavailabilityId));
            assertEquals(singleDayUpdatedAt, queryTimestamp(
                    jdbcTemplate, "SELECT updated_at FROM tb_person_unavailability WHERE id = ?", singleDayUnavailabilityId));

            assertEquals(personId, jdbcTemplate.queryForObject(
                    "SELECT person_id FROM tb_person_unavailability WHERE id = ?", Long.class, multiDayUnavailabilityId));
            assertEquals("Viagem de familia", jdbcTemplate.queryForObject(
                    "SELECT reason FROM tb_person_unavailability WHERE id = ?", String.class, multiDayUnavailabilityId));
            assertEquals(multiDayCreatedAt, queryTimestamp(
                    jdbcTemplate, "SELECT created_at FROM tb_person_unavailability WHERE id = ?", multiDayUnavailabilityId));
            assertEquals(multiDayUpdatedAt, queryTimestamp(
                    jdbcTemplate, "SELECT updated_at FROM tb_person_unavailability WHERE id = ?", multiDayUnavailabilityId));

            // --- Conversao exata do evento: start_at = date+time; end_at = start_at + 1 hora ---
            assertEquals(Timestamp.valueOf(LocalDateTime.of(eventDate, eventTime)), queryTimestamp(
                    jdbcTemplate, "SELECT start_at FROM tb_celebration_event WHERE id = ?", eventId));
            assertEquals(Timestamp.valueOf(LocalDateTime.of(eventDate, eventTime).plusHours(1)), queryTimestamp(
                    jdbcTemplate, "SELECT end_at FROM tb_celebration_event WHERE id = ?", eventId));

            // --- EventAssignment e EventParticipationResponse permanecem intactos (nao sao alvo da V11) ---
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM tb_event_assignment WHERE id = ? AND event_id = ? AND person_id = ? AND assignment_type = 'READER'",
                    Integer.class, assignmentId, eventId, personId));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM tb_event_participation_response WHERE id = ? AND event_id = ? AND person_id = ? AND status = 'DECLINED'",
                    Integer.class, participationId, eventId, personId));
            assertEquals(declineReason, jdbcTemplate.queryForObject(
                    "SELECT decline_reason FROM tb_event_participation_response WHERE id = ?",
                    String.class, participationId));
            assertEquals(respondedAt, queryTimestamp(
                    jdbcTemplate,
                    "SELECT responded_at FROM tb_event_participation_response WHERE id = ?",
                    participationId
            ));

            // --- Schema final: PersonUnavailability ---
            assertFalse(columnExists(jdbcTemplate, "tb_person_unavailability", "start_date"));
            assertFalse(columnExists(jdbcTemplate, "tb_person_unavailability", "end_date"));
            assertFalse(columnNullable(jdbcTemplate, "tb_person_unavailability", "start_at"));
            assertFalse(columnNullable(jdbcTemplate, "tb_person_unavailability", "end_at"));
            assertTrue(constraintExists(jdbcTemplate, "uk_tb_person_unavailability_person_range", "UNIQUE"));
            assertTrue(constraintExists(jdbcTemplate, "chk_tb_person_unavailability_range", "CHECK"));
            assertTrue(indexExists(jdbcTemplate, "tb_person_unavailability",
                    "idx_tb_person_unavailability_person_end_start"));
            assertTrue(indexExists(jdbcTemplate, "tb_person_unavailability", "idx_tb_person_unavailability_range_person"));
            assertFalse(indexExists(jdbcTemplate, "tb_person_unavailability",
                    "idx_tb_person_unavailability_person_range"));
            assertTrue(foreignKeyExists(jdbcTemplate, "tb_person_unavailability", "fk_tb_person_unavailability_person"));
            assertEquals("CASCADE", foreignKeyDeleteRule(jdbcTemplate, "fk_tb_person_unavailability_person"));

            // --- Schema final: CelebrationEvent ---
            assertFalse(columnNullable(jdbcTemplate, "tb_celebration_event", "start_at"));
            assertFalse(columnNullable(jdbcTemplate, "tb_celebration_event", "end_at"));
            assertFalse(columnExists(jdbcTemplate, "tb_celebration_event", "event_date"));
            assertFalse(columnExists(jdbcTemplate, "tb_celebration_event", "event_time"));
            assertTrue(constraintExists(jdbcTemplate, "chk_tb_celebration_event_range", "CHECK"));
            assertEquals(
                    List.of("start_at", "end_at", "id"),
                    indexColumns(jdbcTemplate, "tb_celebration_event", "idx_tb_celebration_event_start_end")
            );
            assertEquals(
                    List.of("end_at", "start_at", "id"),
                    indexColumns(jdbcTemplate, "tb_celebration_event", "idx_tb_celebration_event_end_start")
            );
            assertEquals(
                    List.of("person_id", "start_at", "end_at"),
                    indexColumns(
                            jdbcTemplate,
                            "tb_person_unavailability",
                            "uk_tb_person_unavailability_person_range"
                    )
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

            assertBusinessDateTimeColumn(jdbcTemplate, "tb_celebration_event", "start_at");
            assertBusinessDateTimeColumn(jdbcTemplate, "tb_celebration_event", "end_at");
            assertBusinessDateTimeColumn(jdbcTemplate, "tb_person_unavailability", "start_at");
            assertBusinessDateTimeColumn(jdbcTemplate, "tb_person_unavailability", "end_at");

            // --- CHECK constraints funcionais ---
            long thirdPersonId = insertPerson(jdbcTemplate, "Bruno Castro", "34991230002");
            LocalDateTime sameInstant = LocalDateTime.of(2026, 9, 1, 10, 0);
            assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                    """
                    INSERT INTO tb_person_unavailability(person_id, start_at, end_at, created_at, updated_at)
                    VALUES (?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                    """,
                    thirdPersonId, Timestamp.valueOf(sameInstant), Timestamp.valueOf(sameInstant)));

            assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                    """
                    INSERT INTO tb_celebration_event(name_mass_or_event, start_at, end_at, mass_or_celebration)
                    VALUES (?, ?, NULL, TRUE)
                    """,
                    "Evento sem end_at", Timestamp.valueOf(sameInstant)));

            assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                    """
                    INSERT INTO tb_celebration_event(name_mass_or_event, start_at, end_at, mass_or_celebration)
                    VALUES (?, ?, ?, TRUE)
                    """,
                    "Evento invertido",
                    Timestamp.valueOf(sameInstant),
                    Timestamp.valueOf(sameInstant.minusHours(1))));

            assertExplainConsidersIndex(
                    jdbcTemplate,
                    "EXPLAIN SELECT * FROM tb_person_unavailability "
                            + "WHERE person_id = " + personId
                            + " AND start_at < '2026-08-13 00:00:00'"
                            + " AND end_at > '2026-08-10 00:00:00'",
                    "idx_tb_person_unavailability_person_end_start"
            );
            assertExplainConsidersIndex(
                    jdbcTemplate,
                    "EXPLAIN SELECT person_id, start_at, end_at FROM tb_person_unavailability "
                            + "WHERE person_id IN (" + personId + "," + thirdPersonId + ")"
                            + " AND start_at < '2026-08-13 00:00:00'"
                            + " AND end_at > '2026-08-10 00:00:00'",
                    "idx_tb_person_unavailability_person_end_start"
            );
            assertExplainConsidersIndex(
                    jdbcTemplate,
                    "EXPLAIN SELECT person_id, start_at, end_at FROM tb_person_unavailability "
                            + "WHERE start_at < '2026-08-13 00:00:00'"
                            + " AND end_at > '2026-08-10 00:00:00'",
                    "idx_tb_person_unavailability_range_person"
            );
            assertExplainConsidersIndex(
                    jdbcTemplate,
                    "EXPLAIN SELECT id, start_at, end_at FROM tb_celebration_event "
                            + "WHERE start_at < '2026-08-10 00:00:00'"
                            + " AND end_at > '2026-08-09 00:00:00'"
                            + " ORDER BY start_at, id",
                    "idx_tb_celebration_event_start_end"
            );
        } finally {
            dropIsolatedDatabase(databaseName);
        }
    }

    private String createIsolatedDatabase(String prefix) throws SQLException {
        String databaseName = prefix + "_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(bootstrapUrl(), username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + databaseName + "`");
        }
        return databaseName;
    }

    private void dropIsolatedDatabase(String databaseName) {
        try (Connection connection = DriverManager.getConnection(bootstrapUrl(), username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS `" + databaseName + "`");
        } catch (SQLException ignored) {
            // best-effort cleanup
        }
    }

    private static String bootstrapUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    }

    private DataSource dataSourceFor(String databaseName) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl("jdbc:mysql://" + host + ":" + port + "/" + databaseName
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        dataSource.setUsername(username);
        dataSource.setPassword(password);
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
                VALUES (?, ?, '1990-01-01', 'encoded-password')
                """,
                name, phoneNumber
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tb_person WHERE phone_number = ?", Long.class, phoneNumber);
    }

    private long insertLegacyUnavailability(
            JdbcTemplate jdbcTemplate, long personId, LocalDate startDate, LocalDate endDate,
            String reason, Timestamp createdAt, Timestamp updatedAt
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO tb_person_unavailability(person_id, start_date, end_date, reason, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                personId, java.sql.Date.valueOf(startDate), java.sql.Date.valueOf(endDate), reason, createdAt, updatedAt
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tb_person_unavailability WHERE person_id = ? AND start_date = ? AND end_date = ?",
                Long.class, personId, java.sql.Date.valueOf(startDate), java.sql.Date.valueOf(endDate));
    }

    private long insertLegacyEvent(JdbcTemplate jdbcTemplate, String name, LocalDate eventDate, LocalTime eventTime) {
        String eventName = name + " " + UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO tb_celebration_event(name_mass_or_event, event_date, event_time, mass_or_celebration)
                VALUES (?, ?, ?, TRUE)
                """,
                eventName, java.sql.Date.valueOf(eventDate), java.sql.Time.valueOf(eventTime)
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tb_celebration_event WHERE name_mass_or_event = ?", Long.class, eventName);
    }

    private long insertEventAssignment(JdbcTemplate jdbcTemplate, long eventId, long personId, String assignmentType) {
        jdbcTemplate.update(
                """
                INSERT INTO tb_event_assignment(event_id, person_id, assignment_type, created_at, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """,
                eventId, personId, assignmentType
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tb_event_assignment WHERE event_id = ? AND person_id = ?",
                Long.class, eventId, personId);
    }

    private long insertEventParticipationResponse(
            JdbcTemplate jdbcTemplate, long eventId, long personId, String status, String declineReason, Timestamp respondedAt
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO tb_event_participation_response(event_id, person_id, status, decline_reason, responded_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """,
                eventId, personId, status, declineReason, respondedAt
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tb_event_participation_response WHERE event_id = ? AND person_id = ?",
                Long.class, eventId, personId);
    }

    private void assertSuccessfulMigration(JdbcTemplate jdbcTemplate, String version) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = ? AND success = TRUE",
                Integer.class, version);
        assertEquals(1, count == null ? 0 : count);
    }

    private Timestamp queryTimestamp(JdbcTemplate jdbcTemplate, String sql, long id) {
        return jdbcTemplate.queryForObject(sql, Timestamp.class, id);
    }

    private boolean columnExists(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND UPPER(table_name) = UPPER(?) AND UPPER(column_name) = UPPER(?)
                """,
                Integer.class, tableName, columnName);
        return count != null && count > 0;
    }

    private boolean columnNullable(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
        String isNullable = jdbcTemplate.queryForObject(
                """
                SELECT is_nullable FROM information_schema.columns
                WHERE table_schema = DATABASE() AND UPPER(table_name) = UPPER(?) AND UPPER(column_name) = UPPER(?)
                """,
                String.class, tableName, columnName);
        return "YES".equalsIgnoreCase(isNullable);
    }

    private boolean constraintExists(JdbcTemplate jdbcTemplate, String constraintName, String constraintType) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE table_schema = DATABASE() AND UPPER(constraint_name) = UPPER(?) AND constraint_type = ?
                """,
                Integer.class, constraintName, constraintType);
        return count != null && count > 0;
    }

    private boolean indexExists(JdbcTemplate jdbcTemplate, String tableName, String indexName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND UPPER(table_name) = UPPER(?) AND UPPER(index_name) = UPPER(?)
                """,
                Integer.class, tableName, indexName);
        return count != null && count > 0;
    }

    private boolean foreignKeyExists(JdbcTemplate jdbcTemplate, String tableName, String constraintName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE table_schema = DATABASE() AND UPPER(table_name) = UPPER(?)
                  AND UPPER(constraint_name) = UPPER(?) AND constraint_type = 'FOREIGN KEY'
                """,
                Integer.class, tableName, constraintName);
        return count != null && count > 0;
    }

    private String foreignKeyDeleteRule(JdbcTemplate jdbcTemplate, String constraintName) {
        return jdbcTemplate.queryForObject(
                """
                SELECT delete_rule FROM information_schema.referential_constraints
                WHERE constraint_schema = DATABASE() AND UPPER(constraint_name) = UPPER(?)
                """,
                String.class, constraintName);
    }

    private List<String> indexColumns(JdbcTemplate jdbcTemplate, String tableName, String indexName) {
        return jdbcTemplate.queryForList(
                """
                SELECT LOWER(column_name)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND UPPER(table_name) = UPPER(?)
                  AND UPPER(index_name) = UPPER(?)
                ORDER BY seq_in_index
                """,
                String.class,
                tableName,
                indexName
        );
    }

    private void assertBusinessDateTimeColumn(
            JdbcTemplate jdbcTemplate,
            String tableName,
            String columnName
    ) {
        Map<String, Object> metadata = jdbcTemplate.queryForMap(
                """
                SELECT data_type, datetime_precision
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND UPPER(table_name) = UPPER(?)
                  AND UPPER(column_name) = UPPER(?)
                """,
                tableName,
                columnName
        );
        assertEquals("datetime", String.valueOf(metadata.get("data_type")).toLowerCase(Locale.ROOT));
        assertEquals(0, ((Number) metadata.get("datetime_precision")).intValue());
    }

    private void assertExplainConsidersIndex(
            JdbcTemplate jdbcTemplate,
            String explainSql,
            String expectedIndex
    ) {
        List<Map<String, Object>> plan = jdbcTemplate.queryForList(explainSql);
        String possibleKeys = plan.stream()
                .map(row -> row.get("possible_keys"))
                .filter(value -> value != null)
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
        assertTrue(
                possibleKeys.toLowerCase(Locale.ROOT).contains(expectedIndex.toLowerCase(Locale.ROOT)),
                () -> "EXPLAIN nao considerou " + expectedIndex + ": " + plan
        );
    }
}
