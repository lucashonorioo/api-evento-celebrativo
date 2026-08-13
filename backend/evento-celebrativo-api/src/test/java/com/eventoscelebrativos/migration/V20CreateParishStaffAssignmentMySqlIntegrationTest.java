package com.eventoscelebrativos.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.UncategorizedSQLException;
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
 * Prova, contra MySQL 8.4 real (nao H2), que a migration V20 cria {@code tb_parish_staff_assignment}
 * com as mesmas garantias do teste H2 equivalente ({@link V20CreateParishStaffAssignmentIntegrationTest}):
 * FK sem cascade, unicidade person_id+responsibility, CHECK de enum e upgrade a partir de V19 sem
 * alterar migrations anteriores. Ignorado automaticamente quando MySQL 8.4 nao estiver acessivel
 * (mesmas propriedades documentadas em V13V14MigrationMySqlIntegrationTest).
 */
class V20CreateParishStaffAssignmentMySqlIntegrationTest {

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
    void shouldCreateEmptyTableWhenUpgradingFromV19OnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v20my_upgrade");
        migrateUntil(dataSource, "19");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        MigrateResult result = migrateAll(dataSource, "20");

        assertEquals(1, result.migrationsExecuted);
        assertEquals(0, countRows(jdbcTemplate));
    }

    @Test
    void shouldRejectFkToNonexistentPersonOnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v20my_fk");
        migrateAll(dataSource, "20");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO tb_parish_staff_assignment (person_id, responsibility) VALUES (999999, 'PASTOR')"));
    }

    @Test
    void shouldRejectDuplicatePersonAndResponsibilityOnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v20my_unique");
        migrateAll(dataSource, "20");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long personId = insertPerson(jdbcTemplate, "Padre Miguel", "34988770001");
        jdbcTemplate.update(
                "INSERT INTO tb_parish_staff_assignment (person_id, responsibility) VALUES (?, 'PASTOR')", personId);

        // DuplicateKeyException e a traducao especifica do Spring para violacao de UNIQUE/PK no
        // MySQL (subtipo de DataIntegrityViolationException), confirmada empiricamente contra
        // MySQL 8.4 real.
        assertThrows(DuplicateKeyException.class, () -> jdbcTemplate.update(
                "INSERT INTO tb_parish_staff_assignment (person_id, responsibility) VALUES (?, 'PASTOR')", personId));
    }

    @Test
    void shouldRejectResponsibilityOutsideAllowedEnumOnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v20my_check");
        migrateAll(dataSource, "20");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long personId = insertPerson(jdbcTemplate, "Pessoa Qualquer", "34988770002");

        // Confirmado empiricamente contra MySQL 8.4 real: violacao de CHECK constraint (erro MySQL
        // 3819) NAO e traduzida para DataIntegrityViolationException pelo SQLErrorCodeSQLExceptionTranslator
        // padrao do Spring (sql-error-codes.xml nao mapeia esse codigo para MySQL) - o Spring recai no
        // fallback UncategorizedSQLException. Nao e uma classe interna do driver MySQL, e uma traducao
        // estavel do proprio spring-jdbc.
        assertThrows(UncategorizedSQLException.class, () -> jdbcTemplate.update(
                "INSERT INTO tb_parish_staff_assignment (person_id, responsibility) VALUES (?, 'DEACON')", personId));
    }

    @Test
    void shouldNotCascadeDeleteFromPersonOnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v20my_no_cascade");
        migrateAll(dataSource, "20");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long personId = insertPerson(jdbcTemplate, "Padre Sem Cascata", "34988770003");
        jdbcTemplate.update(
                "INSERT INTO tb_parish_staff_assignment (person_id, responsibility) VALUES (?, 'PASTOR')", personId);

        assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("DELETE FROM tb_person WHERE id = ?", personId));
        assertEquals(1, countRows(jdbcTemplate));
    }

    @Test
    void shouldNotAlterAnyPreviousMigrationOnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v20my_no_prior_change");
        MigrateResult result = migrateAll(dataSource, "20");

        assertEquals(20, result.migrationsExecuted);
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

    private int countRows(JdbcTemplate jdbcTemplate) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tb_parish_staff_assignment", Integer.class);
        return count == null ? 0 : count;
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
