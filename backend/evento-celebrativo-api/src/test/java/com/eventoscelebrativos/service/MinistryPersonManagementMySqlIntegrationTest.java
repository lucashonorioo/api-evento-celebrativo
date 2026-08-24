package com.eventoscelebrativos.service;

import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.EventAssignment;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.repository.CelebrationEventRepository;
import com.eventoscelebrativos.repository.EventAssignmentRepository;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Prova, contra MySQL 8.4 real (nao H2), a feature/ministry-scoped-person-management nos pontos que
 * dependem de dialeto ou de concorrencia real: a nova query
 * {@link EventAssignmentRepository#existsActiveOrFutureByPersonIdAndAssignmentType} (JPQL com
 * comparacao de enum e JOIN, usada por {@link PersonMinistryCommandServiceImpl#removeMinistry}
 * para decidir bloqueio de remocao de ministerio) e a serializacao real de
 * {@code addOrReactivateMinistry} concorrente com {@code removeMinistry} sobre a mesma
 * Person+MinistryType, usando Person (PESSIMISTIC_WRITE) como boundary. Ignorado automaticamente
 * quando MySQL 8.4 nao estiver acessivel (mesmas propriedades documentadas em
 * V13V14MigrationMySqlIntegrationTest).
 */
@SpringBootTest
class MinistryPersonManagementMySqlIntegrationTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);
    private static String host;
    private static String port;
    private static String username;
    private static String password;
    private static String databaseName;
    private static boolean mysqlAvailable;

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
            databaseName = "ministry_person_mgmt_" + UUID.randomUUID().toString().replace("-", "");
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
        registry.add("spring.datasource.url", () -> "jdbc:mysql://" + host + ":" + port + "/" + databaseName
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Sao_Paulo");
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("app.time-zone", () -> "America/Sao_Paulo");
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

    @Autowired
    private PersonMinistryCommandService personMinistryCommandService;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private PersonMinistryRepository personMinistryRepository;

    @Autowired
    private CelebrationEventRepository celebrationEventRepository;

    @Autowired
    private EventAssignmentRepository eventAssignmentRepository;

    @Test
    void shouldBlockRemovalWhenActiveFutureAssignmentExistsOnRealMySql() {
        Person person = savePerson("Ministry Removal Active Block " + UUID.randomUUID());
        personMinistryRepository.saveAndFlush(new PersonMinistry(person, MinistryType.READER));
        CelebrationEvent event = celebrationEventRepository.saveAndFlush(new CelebrationEvent(
                null, "Active Future Event " + UUID.randomUUID(),
                LocalDateTime.now().plusDays(15), LocalDateTime.now().plusDays(15).plusHours(1), true));
        eventAssignmentRepository.saveAndFlush(new EventAssignment(event, person, EventAssignmentType.READER));

        assertThrows(DatabaseException.class,
                () -> personMinistryCommandService.removeMinistry(person.getId(), MinistryType.READER, "Pessoa"));
        assertTrue(personMinistryRepository.findByPersonIdAndMinistryType(person.getId(), MinistryType.READER)
                .orElseThrow().getActive());
    }

    @Test
    void shouldNotBlockRemovalWhenOnlyCancelledFutureAssignmentExistsOnRealMySql() {
        Person person = savePerson("Ministry Removal Cancelled Only " + UUID.randomUUID());
        personMinistryRepository.saveAndFlush(new PersonMinistry(person, MinistryType.READER));
        CelebrationEvent event = celebrationEventRepository.saveAndFlush(new CelebrationEvent(
                null, "Cancelled Future Event " + UUID.randomUUID(),
                LocalDateTime.now().plusDays(16), LocalDateTime.now().plusDays(16).plusHours(1), true));
        event.cancel();
        celebrationEventRepository.saveAndFlush(event);
        eventAssignmentRepository.saveAndFlush(new EventAssignment(event, person, EventAssignmentType.READER));

        personMinistryCommandService.removeMinistry(person.getId(), MinistryType.READER, "Pessoa");

        assertFalse(personMinistryRepository.findByPersonIdAndMinistryType(person.getId(), MinistryType.READER)
                .orElseThrow().getActive());
    }

    @RepeatedTest(4)
    void addOrReactivateAndRemoveConcurrencyResultsInValidSerialOutcome() throws Exception {
        Person person = savePerson("Add Remove Race " + UUID.randomUUID());
        PersonMinistry ministry = new PersonMinistry(person, MinistryType.READER);
        ministry.deactivate();
        personMinistryRepository.saveAndFlush(ministry);
        Long personId = person.getId();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicReference<Throwable> addFailure = new AtomicReference<>();
        AtomicReference<Throwable> removeFailure = new AtomicReference<>();
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);

            Future<?> addFuture = executor.submit(() -> {
                ready.countDown();
                await(ready, start);
                try {
                    personMinistryCommandService.addOrReactivateMinistry(personId, MinistryType.READER);
                } catch (Throwable t) {
                    addFailure.set(t);
                }
            });
            Future<?> removeFuture = executor.submit(() -> {
                ready.countDown();
                await(ready, start);
                try {
                    personMinistryCommandService.removeMinistry(personId, MinistryType.READER, "Pessoa");
                } catch (Throwable t) {
                    removeFailure.set(t);
                }
            });

            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            addFuture.get(15, TimeUnit.SECONDS);
            removeFuture.get(15, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        // Remove pode falhar com ResourceNotFoundException se rodar antes do reativar concluir
        // (vinculo ainda inativo); e um resultado serial valido, nao uma falha de infraestrutura.
        if (removeFailure.get() != null) {
            assertTrue(removeFailure.get() instanceof com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException,
                    "Falha inesperada na remocao concorrente: " + removeFailure.get());
        }
        if (addFailure.get() != null) {
            fail("addOrReactivateMinistry nao deveria falhar nesta corrida: " + addFailure.get());
        }

        PersonMinistry finalState = personMinistryRepository.findByPersonIdAndMinistryType(personId, MinistryType.READER)
                .orElseThrow();
        if (removeFailure.get() == null) {
            assertFalse(finalState.getActive(),
                    "Quando ambas as operacoes concluem, a remocao deve ser o ultimo resultado serial");
        } else {
            assertTrue(finalState.getActive(),
                    "Quando a remocao perde a corrida, a reativacao deve prevalecer");
        }
        assertFalse(finalState.getCoordinator());
        List<PersonMinistry> allForPerson = personMinistryRepository.findAllByPersonId(personId);
        assertEquals(1, allForPerson.size(), "Nunca deve haver PersonMinistry duplicado para o mesmo par pessoa+ministerio");
    }

    private void await(CountDownLatch ready, CountDownLatch start) {
        try {
            start.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private Person savePerson(String name) {
        Person person = new Person(name, uniquePhoneNumber(), BIRTHDAY);
        return personRepository.saveAndFlush(person);
    }

    private String uniquePhoneNumber() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return "3499" + String.format("%07d", suffix);
    }

    private static String bootstrapUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Sao_Paulo";
    }

    private static String queryVersion(Statement statement) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery("SELECT VERSION()")) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }
}
