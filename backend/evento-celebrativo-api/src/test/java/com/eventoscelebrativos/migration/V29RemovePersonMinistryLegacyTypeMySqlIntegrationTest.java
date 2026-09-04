package com.eventoscelebrativos.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class V29RemovePersonMinistryLegacyTypeMySqlIntegrationTest {

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
    void shouldApplyFreshSchemaThroughV29OnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v29my_fresh");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        MigrateResult result = migrateAll(dataSource);

        assertEquals(29, result.migrationsExecuted);
        assertColumnDoesNotExist(jdbcTemplate, "tb_person_ministry", "ministry_type");
        assertConstraintDoesNotExist(jdbcTemplate, "tb_person_ministry", "chk_tb_person_ministry_type");
        assertIndexDoesNotExist(jdbcTemplate, "tb_person_ministry", "uk_tb_person_ministry_person_type");
        assertIndexExists(jdbcTemplate, "tb_person_ministry", "uk_tb_person_ministry_person_ministry");
        assertConstraintExists(jdbcTemplate, "tb_person_ministry", "fk_tb_person_ministry_ministry");
        assertEquals(5, countRows(jdbcTemplate, "tb_ministry_legacy_type_mapping"));
    }

    @Test
    void shouldUpgradeFromV28AndPreservePersonMinistryDataOnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v29my_upgrade");
        migrateUntil(dataSource, "28");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long personId = insertPerson(jdbcTemplate, "Leitor V29 MySQL", "34988777010");
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
        Long personMinistryId = jdbcTemplate.queryForObject(
                "SELECT id FROM tb_person_ministry WHERE person_id = ?",
                Long.class,
                personId);

        MigrateResult result = migrateAll(dataSource);

        assertEquals(1, result.migrationsExecuted);
        assertColumnDoesNotExist(jdbcTemplate, "tb_person_ministry", "ministry_type");
        var row = jdbcTemplate.queryForMap(
                """
                SELECT id, person_id, ministry_id, active, coordinator, created_at, updated_at
                FROM tb_person_ministry
                WHERE person_id = ?
                """,
                personId);
        assertEquals(personMinistryId, ((Number) row.get("id")).longValue());
        assertEquals(personId, ((Number) row.get("person_id")).longValue());
        assertEquals(readerMinistryId, ((Number) row.get("ministry_id")).longValue());
        assertTrue(isTrue(row.get("active")));
        assertTrue(isTrue(row.get("coordinator")));
        assertEquals(Timestamp.valueOf(createdAt), row.get("created_at"));
        assertEquals(Timestamp.valueOf(updatedAt), row.get("updated_at"));
        assertEquals(5, countRows(jdbcTemplate, "tb_ministry_legacy_type_mapping"));
    }

    @Test
    void shouldAllowArbitraryMinistryMembershipAfterV29OnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v29my_arbitrary");
        migrateAll(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long personId = insertPerson(jdbcTemplate, "Acolito V29 MySQL", "34988777011");
        Long acolyteMinistryId = insertMinistry(jdbcTemplate, "Acolitos", "ACOLITOS");

        jdbcTemplate.update(
                """
                INSERT INTO tb_person_ministry(person_id, ministry_id, active, coordinator)
                VALUES (?, ?, TRUE, TRUE)
                """,
                personId,
                acolyteMinistryId);

        assertEquals(1, jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM tb_person_ministry
                WHERE person_id = ?
                  AND ministry_id = ?
                  AND active = TRUE
                  AND coordinator = TRUE
                """,
                Integer.class,
                personId,
                acolyteMinistryId));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_ministry_legacy_type_mapping WHERE ministry_id = ?",
                Integer.class,
                acolyteMinistryId));
    }

    @Test
    void shouldPreserveCanonicalUniqueAndForeignKeyConstraintsOnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v29my_constraints");
        migrateAll(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long personId = insertPerson(jdbcTemplate, "Constraints V29 MySQL", "34988777012");
        Long readerMinistryId = ministryId(jdbcTemplate, "LEITORES");

        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "INSERT INTO tb_person_ministry(person_id, ministry_id) VALUES (?, 999999)",
                personId));

        jdbcTemplate.update(
                "INSERT INTO tb_person_ministry(person_id, ministry_id) VALUES (?, ?)",
                personId,
                readerMinistryId);
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "INSERT INTO tb_person_ministry(person_id, ministry_id) VALUES (?, ?)",
                personId,
                readerMinistryId));
        assertThrows(BadSqlGrammarException.class, () -> jdbcTemplate.update(
                "INSERT INTO tb_person_ministry(person_id, ministry_type, ministry_id) VALUES (?, 'READER', ?)",
                personId,
                readerMinistryId));
    }

    @Test
    void shouldRejectV28RowsWhoseLegacyTypeDoesNotMatchMappedMinistryIdOnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v29my_inconsistent");
        migrateUntil(dataSource, "28");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long personId = insertPerson(jdbcTemplate, "Inconsistent V29 MySQL", "34988777013");
        Long commentatorMinistryId = ministryId(jdbcTemplate, "COMENTARISTAS");
        jdbcTemplate.update(
                """
                INSERT INTO tb_person_ministry(person_id, ministry_type, ministry_id, active, coordinator, created_at, updated_at)
                VALUES (?, 'READER', ?, TRUE, FALSE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """,
                personId,
                commentatorMinistryId);

        FlywayException exception = assertThrows(FlywayException.class, () -> migrateAll(dataSource));

        assertTrue(containsMessage(exception, "ministry_type inconsistente com ministry_id"));
        assertColumnExists(jdbcTemplate, "tb_person_ministry", "ministry_type");
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

    private boolean containsMessage(Throwable exception, String expected) {
        Throwable current = exception;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(expected)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private int countRows(JdbcTemplate jdbcTemplate, String tableName) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
        return count == null ? 0 : count;
    }

    private void assertColumnExists(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                """,
                Integer.class,
                tableName,
                columnName
        );
        assertEquals(1, count == null ? 0 : count);
    }

    private void assertColumnDoesNotExist(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                """,
                Integer.class,
                tableName,
                columnName
        );
        assertEquals(0, count == null ? 0 : count);
    }

    private void assertConstraintExists(JdbcTemplate jdbcTemplate, String tableName, String constraintName) {
        assertEquals(1, countConstraint(jdbcTemplate, tableName, constraintName));
    }

    private void assertConstraintDoesNotExist(JdbcTemplate jdbcTemplate, String tableName, String constraintName) {
        assertEquals(0, countConstraint(jdbcTemplate, tableName, constraintName));
    }

    private int countConstraint(JdbcTemplate jdbcTemplate, String tableName, String constraintName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.table_constraints
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND constraint_name = ?
                """,
                Integer.class,
                tableName,
                constraintName
        );
        return count == null ? 0 : count;
    }

    private void assertIndexExists(JdbcTemplate jdbcTemplate, String tableName, String indexName) {
        assertEquals(1, countIndex(jdbcTemplate, tableName, indexName));
    }

    private void assertIndexDoesNotExist(JdbcTemplate jdbcTemplate, String tableName, String indexName) {
        assertEquals(0, countIndex(jdbcTemplate, tableName, indexName));
    }

    private int countIndex(JdbcTemplate jdbcTemplate, String tableName, String indexName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(DISTINCT index_name)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND index_name = ?
                """,
                Integer.class,
                tableName,
                indexName
        );
        return count == null ? 0 : count;
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
