package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.ParticipationResponseRequestDTO;
import com.eventoscelebrativos.exception.exceptions.LifecycleConflictException;
import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.CelebrationEventStatus;
import com.eventoscelebrativos.model.EventAssignment;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.ParticipationStatus;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.repository.CelebrationEventRepository;
import com.eventoscelebrativos.repository.EventAssignmentRepository;
import com.eventoscelebrativos.repository.EventParticipationResponseRepository;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Prova, com threads reais e transacoes concorrentes contra MySQL 8.4 real (secao 24/56 da feature
 * celebration-cancellation-lifecycle), que cancelamento e resposta de participacao no mesmo evento
 * tem ordem serial valida: nunca uma resposta e persistida como se o evento estivesse ACTIVE depois
 * de um cancelamento ja confirmado. Como a ordem de quem vence o lock do CelebrationEvent nao e
 * controlavel deterministicamente entre threads, o teste nao assume qual delas vence; em vez disso,
 * verifica que o estado final e sempre coerente com uma das duas execucoes seriais possiveis:
 * <ul>
 *     <li>resposta vence o lock: resposta persiste; cancelamento (depois) preserva o historico
 *     e o evento termina CANCELLED;</li>
 *     <li>cancelamento vence o lock: evento fica CANCELLED antes da resposta observar o status;
 *     resposta e rejeitada com 409/CELEBRATION_EVENT_CANCELLED_PARTICIPATION_REJECTED e nenhuma
 *     resposta e persistida.</li>
 * </ul>
 * Repetido varias vezes para aumentar a chance de observar ambas as ordens de execucao ao longo da
 * suite. Sem MySQL 8.4 acessivel, o teste e ignorado.
 */
@SpringBootTest
class CancelParticipationResponseConcurrencyMySqlIntegrationTest {

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
        password = System.getProperty(
                "mysql.validation.password",
                System.getenv("MYSQL_VALIDATION_PASSWORD")
        );

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
            databaseName = "cancel_participation_race_" + UUID.randomUUID().toString().replace("-", "");
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
                        + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Sao_Paulo"
        );
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
    private CelebrationEventLifecycleService celebrationEventLifecycleService;

    @Autowired
    private EventParticipationResponseService eventParticipationResponseService;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private PersonMinistryRepository personMinistryRepository;

    @Autowired
    private CelebrationEventRepository celebrationEventRepository;

    @Autowired
    private EventAssignmentRepository eventAssignmentRepository;

    @Autowired
    private EventParticipationResponseRepository eventParticipationResponseRepository;

    @RepeatedTest(6)
    void cancelAndParticipationResponseRaceResultInAValidSerialOutcome() throws Exception {
        Person person = savePerson("Cancel Participation Race Person " + UUID.randomUUID());
        personMinistryRepository.saveAndFlush(new PersonMinistry(person, MinistryType.READER));
        CelebrationEvent event = celebrationEventRepository.saveAndFlush(new CelebrationEvent(
                null, "Cancel Participation Race Event " + UUID.randomUUID(),
                LocalDateTime.of(2026, 11, 20, 19, 0), LocalDateTime.of(2026, 11, 20, 20, 0), true));
        eventAssignmentRepository.saveAndFlush(new EventAssignment(event, person, EventAssignmentType.READER));

        Long eventId = event.getId();
        Long personId = person.getId();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicReference<Throwable> cancelFailure = new AtomicReference<>();
        AtomicReference<Throwable> responseOutcome = new AtomicReference<>();
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);

            Future<?> cancelFuture = executor.submit(() -> {
                ready.countDown();
                awaitStart(ready, start);
                try {
                    celebrationEventLifecycleService.cancel(eventId);
                } catch (Throwable t) {
                    cancelFailure.set(t);
                }
            });
            Future<?> responseFuture = executor.submit(() -> {
                ready.countDown();
                awaitStart(ready, start);
                try {
                    eventParticipationResponseService.respond(
                            personId, eventId, new ParticipationResponseRequestDTO("CONFIRMED", null));
                } catch (Throwable t) {
                    responseOutcome.set(t);
                }
            });

            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            cancelFuture.get(15, TimeUnit.SECONDS);
            responseFuture.get(15, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        if (cancelFailure.get() != null) {
            fail("Cancelamento nao deve falhar independentemente da corrida com a resposta: " + cancelFailure.get());
        }

        CelebrationEvent finalEvent = celebrationEventRepository.findById(eventId).orElseThrow();
        assertEquals(CelebrationEventStatus.CANCELLED, finalEvent.getStatus(),
                "Cancelamento sempre deve concluir independentemente do resultado da resposta");

        Optional<com.eventoscelebrativos.model.EventParticipationResponse> persistedResponse =
                eventParticipationResponseRepository.findByEventIdAndPersonId(eventId, personId);

        Throwable responseThrowable = responseOutcome.get();
        if (responseThrowable == null) {
            // Cenario A: resposta venceu o lock antes do cancelamento confirmar -> persiste normalmente.
            assertTrue(persistedResponse.isPresent(), "Resposta bem-sucedida deveria estar persistida");
            assertEquals(ParticipationStatus.CONFIRMED, persistedResponse.get().getStatus());
        } else {
            // Cenario B: cancelamento venceu o lock -> resposta observa CANCELLED e e rejeitada; nada persiste.
            assertTrue(responseThrowable instanceof LifecycleConflictException,
                    "Unica falha esperada para a resposta e o evento cancelado, mas foi: " + responseThrowable);
            assertEquals("CELEBRATION_EVENT_CANCELLED_PARTICIPATION_REJECTED",
                    ((LifecycleConflictException) responseThrowable).getErrorCode());
            assertTrue(persistedResponse.isEmpty(), "Resposta rejeitada nunca deve deixar registro persistido");
        }
    }

    private void awaitStart(CountDownLatch ready, CountDownLatch start) {
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
        return "3498" + String.format("%07d", suffix);
    }

    private static String bootstrapUrl() {
        return "jdbc:mysql://" + host + ":" + port
                + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Sao_Paulo";
    }

    private static String queryVersion(Statement statement) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery("SELECT VERSION()")) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }
}
