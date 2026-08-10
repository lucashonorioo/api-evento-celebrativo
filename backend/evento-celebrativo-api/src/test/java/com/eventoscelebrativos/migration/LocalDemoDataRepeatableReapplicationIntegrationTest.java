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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Simula o cenario central desta mudanca: um banco LOCAL ja tem a versao anterior (pre-tarefa)
 * de R__load_local_demo_data.sql aplicada; o checksum do repeatable muda com o novo conteudo; o
 * Flyway detecta a divergencia e reaplica o script inteiro sobre o banco ja populado. Usa duas
 * instancias de Flyway manuais (nao o contexto Spring) apontando para o mesmo datasource, porque
 * precisamos trocar as "locations" do repeatable entre a execucao antiga e a atual sem reiniciar
 * o banco. O cenario roda sempre contra H2 (rapido, parte de toda execucao da suite) e tambem
 * contra MySQL 8.4 real quando acessivel, seguindo a mesma convencao de sistema properties
 * (mysql.validation.host/port/username/password, ignorado automaticamente quando indisponivel)
 * ja usada por V13V14MigrationMySqlIntegrationTest e as demais *MySqlIntegrationTest do projeto.
 */
class LocalDemoDataRepeatableReapplicationIntegrationTest {

    private static final String VERSIONED_MIGRATIONS_LOCATION = "classpath:db/migration";
    private static final String LEGACY_LOCAL_SEED_LOCATION = "classpath:db/local-legacy";
    private static final String CURRENT_LOCAL_SEED_LOCATION = "classpath:db/local";

    private static String host;
    private static String port;
    private static String username;
    private static String password;
    private static boolean mysqlAvailable;
    private static final List<String> CREATED_DATABASES = new CopyOnWriteArrayList<>();

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
    void shouldReapplyLocalDemoSeedSafelyWhenRepeatableChecksumChangesOnH2() {
        assertReapplicationScenario(newIsolatedH2DataSource());
    }

    @Test
    void shouldReapplyLocalDemoSeedSafelyWhenRepeatableChecksumChangesOnRealMySql() throws SQLException {
        assumeTrue(mysqlAvailable, "MySQL 8.4 real nao acessivel; teste ignorado.");
        assertReapplicationScenario(createMySqlDatabase("local_seed_reapply"));
    }

    private void assertReapplicationScenario(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        Flyway legacyFlyway = Flyway.configure()
                .dataSource(dataSource)
                .locations(VERSIONED_MIGRATIONS_LOCATION, LEGACY_LOCAL_SEED_LOCATION)
                .load();
        legacyFlyway.migrate();

        assertEquals(15, countRows(jdbcTemplate, "tb_person"));
        assertEquals(15, countRows(jdbcTemplate, "tb_person_ministry"));
        assertEquals(15, countRows(jdbcTemplate, "tb_user_account"));
        assertEquals(15, countRows(jdbcTemplate, "tb_user_account_role"));
        assertEquals(3, countRows(jdbcTemplate, "tb_location"));
        assertEquals(3, countRows(jdbcTemplate, "tb_celebration_event"));
        assertEquals(21, countRows(jdbcTemplate, "tb_event_assignment"));
        assertEquals(3, countRows(jdbcTemplate, "tb_event_location"));
        assertEquals("ROLE_ADMIN", roleOf(jdbcTemplate, "Padre Paulo"));
        assertEquals("ROLE_OPERATOR", roleOf(jdbcTemplate, "Luana Odinson"));

        Flyway currentFlyway = Flyway.configure()
                .dataSource(dataSource)
                .locations(VERSIONED_MIGRATIONS_LOCATION, CURRENT_LOCAL_SEED_LOCATION)
                .load();
        MigrateResult reapplyResult = currentFlyway.migrate();

        assertTrue(reapplyResult.migrationsExecuted >= 1,
                "expected the repeatable seed to be re-executed after its checksum changed");

        assertEquals(20, countRows(jdbcTemplate, "tb_person"));
        assertEquals(20, countRows(jdbcTemplate, "tb_person_ministry"));
        assertEquals(15, countRows(jdbcTemplate, "tb_user_account"));
        assertEquals(15, countRows(jdbcTemplate, "tb_user_account_role"));
        assertEquals(3, countRows(jdbcTemplate, "tb_location"));
        assertEquals(3, countRows(jdbcTemplate, "tb_celebration_event"));
        assertEquals(21, countRows(jdbcTemplate, "tb_event_assignment"));
        assertEquals(3, countRows(jdbcTemplate, "tb_event_location"));
        assertEquals(0, countDuplicated(jdbcTemplate, "tb_person_ministry", "person_id, ministry_type"));
        assertEquals(0, countDuplicated(jdbcTemplate, "tb_event_assignment", "event_id, person_id"));
        assertEquals(0, countAccountsWithMoreThanOneRole(jdbcTemplate));

        assertEquals(5, countAccountsWithRole(jdbcTemplate, "ROLE_ADMIN"));
        assertEquals(10, countAccountsWithRole(jdbcTemplate, "ROLE_OPERATOR"));
        assertEquals("ROLE_ADMIN", roleOf(jdbcTemplate, "Padre Paulo"));
        assertEquals("ROLE_ADMIN", roleOf(jdbcTemplate, "Luana Odinson"));
        assertEquals("ROLE_ADMIN", roleOf(jdbcTemplate, "Alice Lima"));
        assertEquals("ROLE_ADMIN", roleOf(jdbcTemplate, "Davi Gomes"));
        assertEquals("ROLE_ADMIN", roleOf(jdbcTemplate, "Mariana Ferraz"));
        assertEquals("ROLE_OPERATOR", roleOf(jdbcTemplate, "Miguel Souza"));
        assertEquals("ROLE_OPERATOR", roleOf(jdbcTemplate, "Padre Miguel"));

        for (String newPersonName : new String[] {"Camila Martins", "Gabriel Santos", "Rafael Moreira", "Juliana Mendes", "Padre Antônio"}) {
            assertEquals(1, countRows(jdbcTemplate, "tb_person", "name", newPersonName));
            assertEquals(0, countAccountsForPersonName(jdbcTemplate, newPersonName));
        }

        MigrateResult stableResult = currentFlyway.migrate();
        assertEquals(0, stableResult.migrationsExecuted);
        assertEquals(20, countRows(jdbcTemplate, "tb_person"));
        assertEquals(20, countRows(jdbcTemplate, "tb_person_ministry"));
        assertEquals(15, countRows(jdbcTemplate, "tb_user_account"));
        assertEquals(15, countRows(jdbcTemplate, "tb_user_account_role"));
        assertEquals(3, countRows(jdbcTemplate, "tb_location"));
        assertEquals(3, countRows(jdbcTemplate, "tb_celebration_event"));
        assertEquals(21, countRows(jdbcTemplate, "tb_event_assignment"));
        assertEquals(3, countRows(jdbcTemplate, "tb_event_location"));
    }

    private static String bootstrapUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    }

    private DataSource createMySqlDatabase(String namePrefix) throws SQLException {
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

    private DataSource newIsolatedH2DataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        String dbName = "localseed_" + UUID.randomUUID().toString().replace("-", "");
        dataSource.setUrl("jdbc:h2:mem:" + dbName + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private int countRows(JdbcTemplate jdbcTemplate, String tableName) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
        return count == null ? 0 : count;
    }

    private int countRows(JdbcTemplate jdbcTemplate, String tableName, String columnName, String value) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName + " WHERE " + columnName + " = ?",
                Integer.class,
                value
        );
        return count == null ? 0 : count;
    }

    private int countDuplicated(JdbcTemplate jdbcTemplate, String tableName, String naturalKeyColumns) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM (SELECT " + naturalKeyColumns + " FROM " + tableName
                        + " GROUP BY " + naturalKeyColumns + " HAVING COUNT(*) > 1) duplicated",
                Integer.class
        );
        return count == null ? 0 : count;
    }

    private int countAccountsWithMoreThanOneRole(JdbcTemplate jdbcTemplate) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM (
                    SELECT user_account_id
                    FROM tb_user_account_role
                    GROUP BY user_account_id
                    HAVING COUNT(*) > 1
                ) duplicated
                """,
                Integer.class
        );
        return count == null ? 0 : count;
    }

    private int countAccountsWithRole(JdbcTemplate jdbcTemplate, String authority) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM tb_user_account_role uar
                JOIN tb_role r ON r.id = uar.role_id
                WHERE r.authority = ?
                """,
                Integer.class,
                authority
        );
        return count == null ? 0 : count;
    }

    private int countAccountsForPersonName(JdbcTemplate jdbcTemplate, String personName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM tb_person p
                JOIN tb_user_account ua ON ua.person_id = p.id
                WHERE p.name = ?
                """,
                Integer.class,
                personName
        );
        return count == null ? 0 : count;
    }

    private String roleOf(JdbcTemplate jdbcTemplate, String personName) {
        return jdbcTemplate.queryForObject(
                """
                SELECT r.authority
                FROM tb_person p
                JOIN tb_user_account ua ON ua.person_id = p.id
                JOIN tb_user_account_role uar ON uar.user_account_id = ua.id
                JOIN tb_role r ON r.id = uar.role_id
                WHERE p.name = ?
                """,
                String.class,
                personName
        );
    }
}
