package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.PersonActiveRequestDTO;
import com.eventoscelebrativos.exception.exceptions.LifecycleConflictException;
import com.eventoscelebrativos.exception.exceptions.ParishActivePastorAlreadyExistsException;
import com.eventoscelebrativos.exception.exceptions.PastorPriestMinistryRequiredException;
import com.eventoscelebrativos.exception.exceptions.PersonHasActiveParishResponsibilitiesException;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova, com transacoes reais e threads concorrentes contra MySQL 8.4 real, os tres invariantes
 * criticos de {@code ParishStaffAssignment} sob concorrencia genuina (nao apenas sob mock):
 * <ul>
 *     <li>nunca mais de um PASTOR ativo, mesmo com duas nomeacoes simultaneas (mutex
 *     {@code ParishProfile(id=1)});</li>
 *     <li>nunca {@code PASTOR active=true} com {@code PersonMinistry(PRIEST).active=false} na mesma
 *     Person, mesmo concedendo PASTOR e removendo PRIEST ao mesmo tempo (lock da Person);</li>
 *     <li>nunca {@code ParishStaffAssignment.active=true} com {@code Person.active=false}, mesmo
 *     concedendo responsabilidade e desativando a Person ao mesmo tempo (lock da Person).</li>
 * </ul>
 * Cada teste dispara as duas operacoes concorrentes com uma barreira ({@code CountDownLatch}) para
 * nao depender de scheduling especifico: qualquer um dos dois lados pode vencer a corrida pelo lock,
 * mas o estado final precisa ser sempre consistente. Ignorado automaticamente quando MySQL 8.4 nao
 * estiver acessivel (mesmo padrao de propriedades dos demais testes MySQL do projeto).
 */
@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
class ParishStaffAssignmentConcurrencyMySqlIntegrationTest {

    private static String host;
    private static String port;
    private static String username;
    private static String password;
    private static String databaseName;
    private static boolean mysqlAvailable;

    @Autowired
    private ParishStaffAssignmentService parishStaffAssignmentService;

    @Autowired
    private PersonMinistryCommandService personMinistryCommandService;

    @Autowired
    private UserAccountLifecycleService userAccountLifecycleService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void provisionIsolatedMySqlDatabase() throws SQLException {
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
            String version = queryVersion(statement);
            mysqlAvailable = connection.isValid(3) && version.startsWith("8.4.");
            if (!mysqlAvailable) {
                return;
            }
            databaseName = "parish_staff_concurrency_" + UUID.randomUUID().toString().replace("-", "");
            statement.execute("CREATE DATABASE `" + databaseName + "`");
        } catch (SQLException exception) {
            mysqlAvailable = false;
        }
    }

    @DynamicPropertySource
    static void registerMySqlProperties(DynamicPropertyRegistry registry) {
        if (!mysqlAvailable) {
            return;
        }
        registry.add(
                "spring.datasource.url",
                () -> "jdbc:mysql://" + host + ":" + port + "/" + databaseName
                        + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
        );
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @AfterAll
    static void dropIsolatedMySqlDatabase() {
        if (!mysqlAvailable || databaseName == null) {
            return;
        }
        try (Connection connection = DriverManager.getConnection(bootstrapUrl(), username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS `" + databaseName + "`");
        } catch (SQLException ignored) {
            // best-effort cleanup
        }
    }

    @BeforeEach
    void requireMySql84() {
        Assumptions.assumeTrue(mysqlAvailable, "MySQL 8.4 real nao acessivel; teste ignorado.");
    }

    @AfterEach
    void cleanUpBetweenTests() {
        if (!mysqlAvailable) {
            return;
        }
        jdbcTemplate.update("DELETE FROM tb_parish_staff_assignment");
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAllowExactlyOnePastorWhenTwoPeopleAreNominatedSimultaneously() throws Exception {
        long personA = insertPerson("Padre Concorrente A");
        long personB = insertPerson("Padre Concorrente B");
        insertActivePriestMinistry(personA);
        insertActivePriestMinistry(personB);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);

            Future<Exception> resultA = executor.submit(grantPastorTask(personA, ready, start));
            Future<Exception> resultB = executor.submit(grantPastorTask(personB, ready, start));

            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            Exception exceptionA = resultA.get(20, TimeUnit.SECONDS);
            Exception exceptionB = resultB.get(20, TimeUnit.SECONDS);

            long successes = countNulls(exceptionA, exceptionB);
            long conflicts = countInstancesOf(ParishActivePastorAlreadyExistsException.class, exceptionA, exceptionB);
            assertEquals(1, successes, "Exatamente uma nomeacao deve ter sucesso");
            assertEquals(1, conflicts, "Exatamente uma nomeacao deve ser rejeitada com PARISH_ACTIVE_PASTOR_ALREADY_EXISTS");

            Integer activePastorCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM tb_parish_staff_assignment WHERE responsibility = 'PASTOR' AND active = TRUE",
                    Integer.class);
            assertEquals(1, activePastorCount, "Deve existir exatamente 1 PASTOR ativo no banco apos a corrida");

            Integer totalPastorRows = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM tb_parish_staff_assignment WHERE responsibility = 'PASTOR'", Integer.class);
            assertEquals(1, totalPastorRows, "Nao deve haver duplicacao de linha de PASTOR");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldNeverEndWithActivePastorAndInactivePriest() throws Exception {
        long personId = insertPerson("Padre Concorrente C");
        insertActivePriestMinistry(personId);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);

            Future<Exception> grantResult = executor.submit(grantPastorTask(personId, ready, start));
            Future<Exception> removeResult = executor.submit(removePriestTask(personId, ready, start));

            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            Exception grantException = grantResult.get(20, TimeUnit.SECONDS);
            Exception removeException = removeResult.get(20, TimeUnit.SECONDS);

            // Uma das duas operacoes deve necessariamente falhar (nunca as duas tem sucesso juntas),
            // pois sao mutuamente exclusivas: PASTOR exige PRIEST ativo.
            boolean grantSucceeded = grantException == null;
            boolean removeSucceeded = removeException == null;
            assertTrue(grantSucceeded != removeSucceeded, "Exatamente uma das duas operacoes deve ter sucesso");
            if (!grantSucceeded) {
                assertInstanceOf(PastorPriestMinistryRequiredException.class, grantException);
            }
            if (!removeSucceeded) {
                assertInstanceOf(PastorPriestMinistryRequiredException.class, removeException);
            }

            Boolean pastorActive = jdbcTemplate.query(
                    "SELECT active FROM tb_parish_staff_assignment WHERE person_id = ? AND responsibility = 'PASTOR'",
                    rs -> rs.next() ? rs.getBoolean(1) : null,
                    personId);
            Boolean priestActive = jdbcTemplate.queryForObject(
                    "SELECT active FROM tb_person_ministry WHERE person_id = ? AND ministry_type = 'PRIEST'",
                    Boolean.class, personId);

            boolean invariantViolated = Boolean.TRUE.equals(pastorActive) && Boolean.FALSE.equals(priestActive);
            assertTrue(!invariantViolated, "Nunca pode existir PASTOR ativo com PRIEST inativo na mesma pessoa. "
                    + "pastorActive=" + pastorActive + " priestActive=" + priestActive);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldNeverEndWithActiveResponsibilityAndInactivePerson() throws Exception {
        long adminActorId = insertPerson("Admin Ator Concorrencia");
        long personId = insertPerson("Secretaria Concorrente");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);

            Future<Exception> grantResult = executor.submit(grantSecretaryTask(personId, ready, start));
            Future<Exception> deactivateResult = executor.submit(deactivatePersonTask(adminActorId, personId, ready, start));

            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            Exception grantException = grantResult.get(20, TimeUnit.SECONDS);
            Exception deactivateException = deactivateResult.get(20, TimeUnit.SECONDS);

            boolean grantSucceeded = grantException == null;
            boolean deactivateSucceeded = deactivateException == null;
            assertTrue(grantSucceeded != deactivateSucceeded, "Exatamente uma das duas operacoes deve ter sucesso");
            if (!grantSucceeded) {
                assertInstanceOf(LifecycleConflictException.class, grantException);
                assertEquals("PERSON_INACTIVE", ((LifecycleConflictException) grantException).getErrorCode());
            }
            if (!deactivateSucceeded) {
                assertInstanceOf(PersonHasActiveParishResponsibilitiesException.class, deactivateException);
            }

            Boolean responsibilityActive = jdbcTemplate.query(
                    "SELECT active FROM tb_parish_staff_assignment WHERE person_id = ? AND responsibility = 'PARISH_SECRETARY'",
                    rs -> rs.next() ? rs.getBoolean(1) : null,
                    personId);
            Boolean personActive = jdbcTemplate.queryForObject(
                    "SELECT active FROM tb_person WHERE id = ?", Boolean.class, personId);

            boolean invariantViolated = Boolean.TRUE.equals(responsibilityActive) && Boolean.FALSE.equals(personActive);
            assertTrue(!invariantViolated, "Nunca pode existir responsabilidade ativa com Person inativa. "
                    + "responsibilityActive=" + responsibilityActive + " personActive=" + personActive);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldAllowConcurrentGrantSecretaryForDifferentPersonsWithoutContention() throws Exception {
        // Objetivo principal da remocao do PESSIMISTIC_WRITE em ParishStaffAssignment: duas Persons
        // sem nenhuma relacao logica entre si concedendo PARISH_SECRETARY ao mesmo tempo NAO podem
        // disputar um mutex institucional que nao deveriam compartilhar (grantSecretary nao adquire
        // ParishProfile; cada lado serializa apenas pelo proprio lock de Person). Repete a disputa em
        // varias rodadas, cada uma com Persons novas, para aumentar a chance de expor uma regressao.
        int rounds = 3;
        for (int round = 1; round <= rounds; round++) {
            long personA = insertPerson("Secretaria Concorrente A r" + round);
            long personB = insertPerson("Secretaria Concorrente B r" + round);

            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch start = new CountDownLatch(1);

                Future<Exception> resultA = executor.submit(grantSecretaryTask(personA, ready, start));
                Future<Exception> resultB = executor.submit(grantSecretaryTask(personB, ready, start));

                ready.await(5, TimeUnit.SECONDS);
                start.countDown();
                Exception exceptionA = resultA.get(20, TimeUnit.SECONDS);
                Exception exceptionB = resultB.get(20, TimeUnit.SECONDS);

                assertNull(exceptionA, "Rodada " + round + ": grant de A deveria ter sucesso, mas lancou " + exceptionA);
                assertNull(exceptionB, "Rodada " + round + ": grant de B deveria ter sucesso, mas lancou " + exceptionB);

                Boolean activeA = jdbcTemplate.queryForObject(
                        "SELECT active FROM tb_parish_staff_assignment WHERE person_id = ? AND responsibility = 'PARISH_SECRETARY'",
                        Boolean.class, personA);
                Boolean activeB = jdbcTemplate.queryForObject(
                        "SELECT active FROM tb_parish_staff_assignment WHERE person_id = ? AND responsibility = 'PARISH_SECRETARY'",
                        Boolean.class, personB);
                assertTrue(Boolean.TRUE.equals(activeA), "Rodada " + round + ": Person A deveria estar ativa como secretaria");
                assertTrue(Boolean.TRUE.equals(activeB), "Rodada " + round + ": Person B deveria estar ativa como secretaria");
            } finally {
                executor.shutdownNow();
            }
        }

        Integer totalActiveSecretaries = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_parish_staff_assignment WHERE responsibility = 'PARISH_SECRETARY' AND active = TRUE",
                Integer.class);
        assertEquals(2 * rounds, totalActiveSecretaries,
                rounds + " rodadas x 2 pessoas = " + (2 * rounds) + " secretarios ativos, sem duplicacao nem perda");
    }

    private Callable<Exception> grantPastorTask(long personId, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            await(start);
            try {
                parishStaffAssignmentService.grantPastor(personId);
                return null;
            } catch (Exception exception) {
                return exception;
            }
        };
    }

    private Callable<Exception> removePriestTask(long personId, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            await(start);
            try {
                personMinistryCommandService.removeMinistry(personId, MinistryType.PRIEST, "Padre");
                return null;
            } catch (Exception exception) {
                return exception;
            }
        };
    }

    private Callable<Exception> grantSecretaryTask(long personId, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            await(start);
            try {
                parishStaffAssignmentService.grantSecretary(personId);
                return null;
            } catch (Exception exception) {
                return exception;
            }
        };
    }

    private Callable<Exception> deactivatePersonTask(long adminActorId, long personId, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            await(start);
            try {
                authenticateAsAdmin(adminActorId);
                PersonActiveRequestDTO request = new PersonActiveRequestDTO();
                request.setActive(false);
                userAccountLifecycleService.updatePersonActive(personId, request);
                return null;
            } catch (Exception exception) {
                return exception;
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
    }

    private void authenticateAsAdmin(long personId) {
        Set<GrantedAuthority> authorities = Set.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(999_000L + personId, personId, "admin-" + personId, 0L, authorities);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(authenticatedUser, null, authorities));
    }

    private long insertPerson(String name) {
        String phoneNumber = uniquePhoneNumber();
        jdbcTemplate.update(
                "INSERT INTO tb_person(public_id, name, phone_number, active) VALUES (?, ?, ?, TRUE)",
                newPublicId(), name, phoneNumber);
        return jdbcTemplate.queryForObject("SELECT id FROM tb_person WHERE phone_number = ?", Long.class, phoneNumber);
    }

    private byte[] newPublicId() {
        UUID uuid = UUID.randomUUID();
        return ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }

    private void insertActivePriestMinistry(long personId) {
        jdbcTemplate.update(
                "INSERT INTO tb_person_ministry(person_id, ministry_type, active, created_at, updated_at) "
                        + "VALUES (?, 'PRIEST', TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))",
                personId);
    }

    private String uniquePhoneNumber() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return "3496" + String.format("%07d", suffix);
    }

    private long countNulls(Exception... exceptions) {
        return java.util.Arrays.stream(exceptions).filter(e -> e == null).count();
    }

    private long countInstancesOf(Class<? extends Exception> type, Exception... exceptions) {
        return java.util.Arrays.stream(exceptions).filter(type::isInstance).count();
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Latch nao liberado a tempo");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private static String bootstrapUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    }

    private static String queryVersion(Statement statement) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery("SELECT VERSION()")) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }
}
