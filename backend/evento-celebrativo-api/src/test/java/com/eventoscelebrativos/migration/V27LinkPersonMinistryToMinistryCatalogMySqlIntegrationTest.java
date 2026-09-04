package com.eventoscelebrativos.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class V27LinkPersonMinistryToMinistryCatalogMySqlIntegrationTest {

    private static String host;
    private static String port;
    private static String username;
    private static String password;
    private static String mysqlVersion;
    private static boolean mysqlAvailable;
    private static final List<String> CREATED_DATABASES = new CopyOnWriteArrayList<>();

    @BeforeAll
    static void checkAvailability() {
        host = System.getProperty("mysql.validation.host", "localhost");
        port = System.getProperty("mysql.validation.port", "3307");
        username = System.getProperty("mysql.validation.username", "root");
        password = System.getProperty("mysql.validation.password", System.getenv("MYSQL_VALIDATION_PASSWORD"));

        if (password == null || password.isBlank()) {
            mysqlAvailable = false;
            return;
        }
        try (Connection connection = DriverManager.getConnection(bootstrapUrl(), username, password);
             Statement statement = connection.createStatement()) {
            mysqlVersion = queryVersion(statement);
            mysqlAvailable = connection.isValid(3) && mysqlVersion.startsWith("8.4.");
        } catch (SQLException e) {
            mysqlAvailable = false;
        }
    }

    @BeforeEach
    void setUp() {
        assumeTrue(mysqlAvailable, "MySQL 8.4 real nao acessivel; teste ignorado.");
    }

    @AfterAll
    static void dropCreatedDatabases() {
        if (!mysqlAvailable) {
            return;
        }
        try (Connection connection = DriverManager.getConnection(bootstrapUrl(), username, password);
             Statement statement = connection.createStatement()) {
            for (String db : CREATED_DATABASES) {
                statement.execute("DROP DATABASE IF EXISTS `" + db + "`");
            }
        } catch (SQLException ignored) {
            // best-effort cleanup
        }
    }

    @Test
    void shouldReportMySql84Version() {
        assertTrue(mysqlVersion.startsWith("8.4."), "Versao inesperada: " + mysqlVersion);
    }

    @Test
    void shouldBackfillLegacyRowsAndPreserveExistingDataOnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v27my_backfill");
        migrateUntil(dataSource, "26");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long personId = insertPerson(jdbcTemplate, "Leitor V27 MySQL", "34988773010");
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

        MigrateResult result = migrateUntilResult(dataSource, "27");

        assertEquals(1, result.migrationsExecuted);
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
    void shouldMapAllFiveLegacyMinistryTypesOnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v27my_mapping");
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
            Long personId = insertPerson(jdbcTemplate, "Pessoa " + ministryType, "349887730" + phoneSuffix++);
            jdbcTemplate.update(
                    "INSERT INTO tb_person_ministry(person_id, ministry_type, active, coordinator) VALUES (?, ?, TRUE, FALSE)",
                    personId,
                    ministryType);
        }

        migrateUntil(dataSource, "27");

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
    }

    @Test
    void shouldEnforceMinistryIdConstraintsOnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v27my_constraints");
        migrateUntil(dataSource, "27");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long personId = insertPerson(jdbcTemplate, "Leitor Constraints MySQL", "34988773030");
        Long readerMinistryId = ministryId(jdbcTemplate, "LEITORES");

        DataAccessException missingMinistryId = assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "INSERT INTO tb_person_ministry(person_id, ministry_type) VALUES (?, 'READER')",
                personId));
        assertTrue(missingMinistryId.getMessage().contains("ministry_id"));
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
    void shouldFailSafelyWhenRequiredCatalogRowIsMissingOnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v27my_missing_catalog");
        migrateUntil(dataSource, "26");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("DELETE FROM tb_ministry WHERE normalized_name = 'LEITORES'");

        assertThrows(FlywayException.class, () -> migrateUntil(dataSource, "27"));
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

    private UUID newPublicId() {
        return UUID.randomUUID();
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

    private DataSource createDatabase(String namePrefix) throws SQLException {
        String dbName = namePrefix + "_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(bootstrapUrl(), username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + dbName + "`");
        }
        CREATED_DATABASES.add(dbName);

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl("jdbc:mysql://" + host + ":" + port + "/" + dbName
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return dataSource;
    }

    private void migrateUntil(DataSource dataSource, String target) {
        migrateUntilResult(dataSource, target);
    }

    private MigrateResult migrateUntilResult(DataSource dataSource, String target) {
        return Flyway.configure()
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
                .load()
                .migrate();
    }

    private static String bootstrapUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    }

    private static String queryVersion(Statement statement) throws SQLException {
        try (java.sql.ResultSet resultSet = statement.executeQuery("SELECT VERSION()")) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }
}
