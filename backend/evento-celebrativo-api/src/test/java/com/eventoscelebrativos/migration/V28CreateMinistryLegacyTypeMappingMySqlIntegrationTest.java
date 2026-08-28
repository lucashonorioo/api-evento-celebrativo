package com.eventoscelebrativos.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.ByteBuffer;
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

class V28CreateMinistryLegacyTypeMappingMySqlIntegrationTest {

    private static final String VERSIONED_MIGRATIONS_LOCATION = "classpath:db/migration";

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
        } catch (SQLException exception) {
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
    void shouldNotHaveMappingTableBeforeV28OnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v28my_before");
        migrateUntil(dataSource, "27");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertThrows(BadSqlGrammarException.class,
                () -> jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tb_ministry_legacy_type_mapping", Integer.class));
    }

    @Test
    void shouldCreateFiveMappingsWhenUpgradingFromV27OnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v28my_upgrade");
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
    }

    @Test
    void shouldEnforceMappingConstraintsOnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v28my_constraints");
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
    void shouldKeepMappingStableWhenLegacyMinistryIsRenamedOnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v28my_rename");
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
    void shouldNotModifyPersonMinistryRowsWhenCreatingMappingOnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v28my_preserve_pm");
        migrateUntil(dataSource, "27");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long personId = insertPerson(jdbcTemplate, "Leitor V28 MySQL", "34988775010");
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
        assertTrue(isTrue(row.get("active")));
        assertTrue(isTrue(row.get("coordinator")));
        assertEquals(Timestamp.valueOf(createdAt), row.get("created_at"));
        assertEquals(Timestamp.valueOf(updatedAt), row.get("updated_at"));
    }

    @Test
    void shouldFailSafelyWhenRequiredCatalogRowIsMissingOnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v28my_missing_catalog");
        migrateUntil(dataSource, "27");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("DELETE FROM tb_ministry WHERE normalized_name = 'LEITORES'");

        assertThrows(FlywayException.class, () -> migrateAll(dataSource));
    }

    private Long insertPerson(JdbcTemplate jdbcTemplate, String name, String phoneNumber) {
        jdbcTemplate.update(
                "INSERT INTO tb_person(public_id, name, phone_number) VALUES (?, ?, ?)",
                newPublicId(),
                name,
                phoneNumber);
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
