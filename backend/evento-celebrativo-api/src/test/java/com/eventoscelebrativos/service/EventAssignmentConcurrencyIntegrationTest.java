package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.CelebrationEventScaleRequestDTO;
import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.EventAssignment;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.Location;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.repository.CelebrationEventRepository;
import com.eventoscelebrativos.repository.EventAssignmentRepository;
import com.eventoscelebrativos.repository.LocationRepository;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova, com transacoes reais e threads concorrentes contra MySQL 8.4 real, que o invariante
 * "no maximo um EventAssignment por (event_id, person_id)" (V12) se mantem sob concorrencia,
 * tanto na protecao direta do banco (Cenario A) quanto em atualizacoes completas concorrentes
 * da escala (Cenario B), onde a politica desta branch e: atualizacoes completas concorrentes sao
 * serializadas pelo lock do evento; o ultimo estado valido confirmado vence. Cada execucao usa uma
 * database isolada. Sem MySQL 8.4 acessivel, os testes sao ignorados.
 */
@SpringBootTest
class EventAssignmentConcurrencyIntegrationTest {

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
            databaseName = "v12_assignment_concurrency_" + UUID.randomUUID().toString().replace("-", "");
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
    private CelebrationEventService celebrationEventService;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private PersonMinistryRepository personMinistryRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private CelebrationEventRepository celebrationEventRepository;

    @Autowired
    private EventAssignmentRepository eventAssignmentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long cleanupPersonId;
    private Long cleanupEventId;
    private Long cleanupLocationId;

    @AfterEach
    void cleanup() {
        if (cleanupEventId != null) {
            jdbcTemplate.update("DELETE FROM tb_event_assignment WHERE event_id = ?", cleanupEventId);
            jdbcTemplate.update("DELETE FROM tb_event_location WHERE event_id = ?", cleanupEventId);
            jdbcTemplate.update("DELETE FROM tb_celebration_event WHERE id = ?", cleanupEventId);
        }
        if (cleanupPersonId != null) {
            jdbcTemplate.update("DELETE FROM tb_event_assignment WHERE person_id = ?", cleanupPersonId);
            jdbcTemplate.update("DELETE FROM tb_person_ministry WHERE person_id = ?", cleanupPersonId);            jdbcTemplate.update("DELETE FROM tb_person WHERE id = ?", cleanupPersonId);
        }
        if (cleanupLocationId != null) {
            jdbcTemplate.update("DELETE FROM tb_location WHERE id = ?", cleanupLocationId);
        }
        cleanupPersonId = null;
        cleanupEventId = null;
        cleanupLocationId = null;
    }

    @Test
    void shouldAllowOnlyOneConcurrentInsertForSamePersonAndEventWithDifferentTypes() throws Exception {
        Person person = savePerson("Concurrent Assignment Insert Person", uniquePhoneNumber());
        cleanupPersonId = person.getId();
        CelebrationEvent event = celebrationEventRepository.saveAndFlush(new CelebrationEvent(
                null, "Concurrent Assignment Insert Event " + UUID.randomUUID(),
                LocalDateTime.of(2026, 10, 25, 19, 0), LocalDateTime.of(2026, 10, 25, 20, 0), true));
        cleanupEventId = event.getId();

        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        runConcurrently(
                () -> eventAssignmentRepository.saveAndFlush(new EventAssignment(event, person, EventAssignmentType.READER)),
                () -> eventAssignmentRepository.saveAndFlush(new EventAssignment(event, person, EventAssignmentType.COMMENTATOR)),
                successes,
                conflicts
        );

        assertEquals(1, successes.get(), "Exatamente um dos dois inserts concorrentes deve persistir");
        assertEquals(1, conflicts.get());
        List<EventAssignment> finalAssignments = eventAssignmentRepository.findAllByEventId(event.getId());
        assertEquals(1, finalAssignments.size(), "Nunca podem existir duas funcoes para a mesma pessoa no mesmo evento");
    }

    @Test
    void shouldSerializeConcurrentFullScaleUpdatesForSamePersonAndKeepExactlyOneFunction() throws Exception {
        Person person = savePerson("Concurrent Scale Update Person", uniquePhoneNumber());
        cleanupPersonId = person.getId();
        personMinistryRepository.saveAndFlush(new PersonMinistry(person, MinistryType.COMMENTATOR));
        personMinistryRepository.saveAndFlush(new PersonMinistry(person, MinistryType.EUCHARISTIC_MINISTER));

        Location location = locationRepository.saveAndFlush(new Location(null, "Concurrent Scale Update Church", "Address"));
        cleanupLocationId = location.getId();
        CelebrationEvent event = celebrationEventRepository.saveAndFlush(new CelebrationEvent(
                null, "Concurrent Scale Update Event " + UUID.randomUUID(),
                LocalDateTime.of(2026, 10, 26, 19, 0), LocalDateTime.of(2026, 10, 26, 20, 0), true));
        cleanupEventId = event.getId();
        eventAssignmentRepository.saveAndFlush(new EventAssignment(event, person, EventAssignmentType.READER));

        Long eventId = event.getId();
        Long locationId = location.getId();
        Long personId = person.getId();
        CelebrationEventScaleRequestDTO toCommentator =
                new CelebrationEventScaleRequestDTO(locationId, null, null, List.of(personId), null, null);
        CelebrationEventScaleRequestDTO toEucharisticMinister =
                new CelebrationEventScaleRequestDTO(locationId, null, null, null, null, List.of(personId));

        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        runConcurrently(
                () -> celebrationEventService.updateEventScale(eventId, toCommentator),
                () -> celebrationEventService.updateEventScale(eventId, toEucharisticMinister),
                successes,
                conflicts
        );

        assertEquals(2, successes.get(),
                "Atualizacoes completas concorrentes sao serializadas pelo lock do evento; ambas devem concluir sem erro");
        assertEquals(0, conflicts.get());

        List<EventAssignment> finalAssignments = eventAssignmentRepository.findAllByEventId(eventId);
        assertEquals(1, finalAssignments.size(), "O estado final deve conter exatamente uma funcao para a pessoa");
        assertTrue(
                Set.of(EventAssignmentType.COMMENTATOR, EventAssignmentType.EUCHARISTIC_MINISTER)
                        .contains(finalAssignments.get(0).getAssignmentType()),
                "O ultimo estado valido confirmado deve prevalecer"
        );
    }

    private void runConcurrently(
            Runnable first, Runnable second, AtomicInteger successes, AtomicInteger conflicts
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);

            Runnable wrappedFirst = wrap(first, ready, start, successes, conflicts);
            Runnable wrappedSecond = wrap(second, ready, start, successes, conflicts);

            var futureOne = executor.submit(wrappedFirst);
            var futureTwo = executor.submit(wrappedSecond);
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            futureOne.get(15, TimeUnit.SECONDS);
            futureTwo.get(15, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private Runnable wrap(
            Runnable task, CountDownLatch ready, CountDownLatch start, AtomicInteger successes, AtomicInteger conflicts
    ) {
        return () -> {
            ready.countDown();
            await(ready, start);
            try {
                task.run();
                successes.incrementAndGet();
            } catch (DataIntegrityViolationException | com.eventoscelebrativos.exception.exceptions.ErrorResponseException e) {
                conflicts.incrementAndGet();
            }
        };
    }

    private void await(CountDownLatch ready, CountDownLatch start) {
        try {
            start.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private Person savePerson(String name, String phoneNumber) {
        Person person = new Person(name, phoneNumber, BIRTHDAY);
        return personRepository.saveAndFlush(person);
    }

    private String uniquePhoneNumber() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return "3499" + String.format("%07d", suffix);
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
