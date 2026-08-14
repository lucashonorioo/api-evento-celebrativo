package com.eventoscelebrativos.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prova, contra MySQL 8.4 real (nao H2), que a migration V21 adiciona a coluna {@code coordinator}
 * com as mesmas garantias do teste H2 equivalente ({@link V21AddPersonMinistryCoordinatorIntegrationTest}),
 * incluindo a CHECK do invariante coordinator-&gt;active realmente aplicada pelo MySQL. Ignorado
 * automaticamente quando MySQL 8.4 nao estiver acessivel (mesmas propriedades documentadas em
 * V13V14MigrationMySqlIntegrationTest).
 */
class V21AddPersonMinistryCoordinatorMySqlIntegrationTest {

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
            mysqlAvailable = connection.isValid(3);
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
    void shouldBackfillExistingRowsWithCoordinatorFalseWhenUpgradingFromV20OnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v21my_backfill");
        migrateUntil(dataSource, "20");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long personId = insertPerson(jdbcTemplate, "Leitor Preexistente", "34988771010");
        jdbcTemplate.update(
                "INSERT INTO tb_person_ministry(person_id, ministry_type, active) VALUES (?, 'READER', TRUE)", personId);

        MigrateResult result = migrateAll(dataSource, "21");

        assertEquals(1, result.migrationsExecuted);
        assertEquals(Boolean.FALSE, jdbcTemplate.queryForObject(
                "SELECT coordinator FROM tb_person_ministry WHERE person_id = ?", Boolean.class, personId));
    }

    @Test
    void shouldAllowActiveTrueWithCoordinatorTrueOnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v21my_valid_true");
        migrateAll(dataSource, "21");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long personId = insertPerson(jdbcTemplate, "Leitor Coordenador", "34988771011");

        jdbcTemplate.update(
                "INSERT INTO tb_person_ministry(person_id, ministry_type, active, coordinator) VALUES (?, 'READER', TRUE, TRUE)",
                personId);

        assertEquals(Boolean.TRUE, jdbcTemplate.queryForObject(
                "SELECT coordinator FROM tb_person_ministry WHERE person_id = ?", Boolean.class, personId));
    }

    @Test
    void shouldRejectActiveFalseWithCoordinatorTrueOnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v21my_check");
        migrateAll(dataSource, "21");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long personId = insertPerson(jdbcTemplate, "Leitor Invalido", "34988771012");

        // Confirmado empiricamente contra MySQL 8.4 real (mesmo achado do V20): violacao de CHECK
        // (erro 3819) nao e traduzida para DataIntegrityViolationException pelo SQLErrorCodeSQLExceptionTranslator
        // padrao do Spring - o fallback e UncategorizedSQLException, uma classe estavel do proprio
        // spring-jdbc, nao do driver MySQL.
        assertThrows(org.springframework.jdbc.UncategorizedSQLException.class, () -> jdbcTemplate.update(
                "INSERT INTO tb_person_ministry(person_id, ministry_type, active, coordinator) VALUES (?, 'READER', FALSE, TRUE)",
                personId));
    }

    @Test
    void shouldRejectUpdatingActiveToFalseWhileCoordinatorRemainsTrueOnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v21my_update_check");
        migrateAll(dataSource, "21");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long personId = insertPerson(jdbcTemplate, "Leitor Coordenador Dois", "34988771013");
        jdbcTemplate.update(
                "INSERT INTO tb_person_ministry(person_id, ministry_type, active, coordinator) VALUES (?, 'READER', TRUE, TRUE)",
                personId);

        assertThrows(org.springframework.jdbc.UncategorizedSQLException.class, () -> jdbcTemplate.update(
                "UPDATE tb_person_ministry SET active = FALSE WHERE person_id = ?", personId));
    }

    @Test
    void shouldNotAlterAnyPreviousMigrationOnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v21my_no_prior_change");
        MigrateResult result = migrateAll(dataSource, "21");

        assertEquals(21, result.migrationsExecuted);
    }

    private Long insertPerson(JdbcTemplate jdbcTemplate, String name, String phoneNumber) {
        jdbcTemplate.update("INSERT INTO tb_person(name, phone_number) VALUES (?, ?)", name, phoneNumber);
        return jdbcTemplate.queryForObject("SELECT id FROM tb_person WHERE phone_number = ?", Long.class, phoneNumber);
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
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate();
    }

    private MigrateResult migrateAll(DataSource dataSource, String target) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(target)
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
