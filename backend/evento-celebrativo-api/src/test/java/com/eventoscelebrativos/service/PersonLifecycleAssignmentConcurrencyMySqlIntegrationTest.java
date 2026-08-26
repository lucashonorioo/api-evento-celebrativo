package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.CelebrationEventScaleRequestDTO;
import com.eventoscelebrativos.dto.request.PersonActiveRequestDTO;
import com.eventoscelebrativos.exception.exceptions.LifecycleConflictException;
import com.eventoscelebrativos.exception.exceptions.PersonHasActiveAssignmentsException;
import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.Location;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.repository.CelebrationEventRepository;
import com.eventoscelebrativos.repository.LocationRepository;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.MinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.repository.RoleRepository;
import com.eventoscelebrativos.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterAll;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.eventoscelebrativos.support.LegacyMinistryTestFactory.personMinistry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
class PersonLifecycleAssignmentConcurrencyMySqlIntegrationTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);

    private static String host;
    private static String port;
    private static String username;
    private static String password;
    private static String databaseName;
    private static boolean mysqlAvailable;

    @Autowired
    private CelebrationEventService celebrationEventService;

    @Autowired
    private UserAccountLifecycleService userAccountLifecycleService;

    @Autowired
    private CelebrationEventRepository celebrationEventRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private PersonMinistryRepository personMinistryRepository;

    @Autowired
    private MinistryRepository ministryRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void provisionIsolatedDatabase() throws SQLException {
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
            databaseName = "v15_pal_" + UUID.randomUUID().toString().replace("-", "");
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
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @AfterAll
    static void dropIsolatedDatabase() {
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
        assumeTrue(mysqlAvailable, "MySQL 8.4 real nao acessivel; teste ignorado.");
    }

    @Test
    void shouldRejectNewAssignmentWhenPersonDeactivationCommitsBeforeScaleValidation() throws Exception {
        Long personId = createReaderPerson();
        Long eventId = createFutureEvent();
        Long locationId = createLocation();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch personLocked = new CountDownLatch(1);
        CountDownLatch scaleAttemptStarted = new CountDownLatch(1);
        try {
            Future<Exception> deactivation = executor.submit(() -> deactivatePersonHoldingLock(personId, personLocked, scaleAttemptStarted));
            Future<Exception> scaleUpdate = executor.submit(() -> addReaderToScale(personId, eventId, locationId, personLocked, scaleAttemptStarted));

            Exception deactivationException = deactivation.get(30, TimeUnit.SECONDS);
            Exception scaleException = scaleUpdate.get(30, TimeUnit.SECONDS);

            assertEquals(null, deactivationException);
            assertInstanceOf(LifecycleConflictException.class, scaleException);
            assertEquals("PERSON_INACTIVE_FOR_ASSIGNMENT", ((LifecycleConflictException) scaleException).getErrorCode());
            assertFalse(personRepository.findById(personId).orElseThrow().isActive());
            assertEquals(0, countAssignments(eventId));
        } finally {
            executor.shutdownNow();
            cleanupEvent(eventId);
            cleanupPerson(personId);
            cleanupLocation(locationId);
        }
    }

    @Test
    void shouldRejectDeactivationWhenScaleCommitsFutureAssignmentFirst() throws Exception {
        Long actingAdminPersonId = createReaderPerson();
        Long personId = createReaderPerson();
        Long eventId = createFutureEvent();
        Long locationId = createLocation();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch scaleDone = new CountDownLatch(1);
        try {
            Future<Exception> scaleUpdate = executor.submit(() -> addReaderToScaleFirst(personId, eventId, locationId, scaleDone));
            Future<Exception> deactivation = executor.submit(() -> deactivateAfterScale(actingAdminPersonId, personId, scaleDone));

            Exception scaleException = scaleUpdate.get(30, TimeUnit.SECONDS);
            Exception deactivationException = deactivation.get(30, TimeUnit.SECONDS);

            assertEquals(null, scaleException);
            assertInstanceOf(PersonHasActiveAssignmentsException.class, deactivationException);
            assertEquals("PERSON_HAS_ACTIVE_ASSIGNMENTS", ((PersonHasActiveAssignmentsException) deactivationException).getErrorCode());
            assertTrue(personRepository.findById(personId).orElseThrow().isActive());
            assertEquals(1, countAssignments(eventId));
        } finally {
            executor.shutdownNow();
            cleanupEvent(eventId);
            cleanupPerson(personId);
            cleanupPerson(actingAdminPersonId);
            cleanupLocation(locationId);
        }
    }

    private Exception deactivatePersonHoldingLock(
            Long personId,
            CountDownLatch personLocked,
            CountDownLatch scaleAttemptStarted
    ) {
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                roleRepository.findByAuthorityForUpdate("ROLE_ADMIN").orElseThrow();
                Person person = personRepository.findByIdForUpdate(personId).orElseThrow();
                person.deactivate();
                personRepository.save(person);
                personLocked.countDown();
                await(scaleAttemptStarted);
            });
            return null;
        } catch (Exception exception) {
            return exception;
        }
    }

    private Exception addReaderToScale(
            Long personId,
            Long eventId,
            Long locationId,
            CountDownLatch personLocked,
            CountDownLatch scaleAttemptStarted
    ) {
        try {
            await(personLocked);
            scaleAttemptStarted.countDown();
            celebrationEventService.updateEventScale(eventId, scaleRequest(locationId, personId));
            return null;
        } catch (Exception exception) {
            return exception;
        }
    }

    private Exception addReaderToScaleFirst(Long personId, Long eventId, Long locationId, CountDownLatch scaleDone) {
        try {
            celebrationEventService.updateEventScale(eventId, scaleRequest(locationId, personId));
            return null;
        } catch (Exception exception) {
            return exception;
        } finally {
            scaleDone.countDown();
        }
    }

    private Exception deactivateAfterScale(Long actingAdminPersonId, Long personId, CountDownLatch scaleDone) {
        try {
            await(scaleDone);
            authenticateAsAdmin(actingAdminPersonId);
            userAccountLifecycleService.updatePersonActive(personId, activeRequest(false));
            return null;
        } catch (Exception exception) {
            return exception;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticateAsAdmin(Long personId) {
        Set<GrantedAuthority> authorities = Set.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(999_999L, personId, "admin-" + personId, 0L, authorities);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(authenticatedUser, null, authorities));
    }

    private PersonActiveRequestDTO activeRequest(boolean active) {
        PersonActiveRequestDTO request = new PersonActiveRequestDTO();
        request.setActive(active);
        return request;
    }

    private CelebrationEventScaleRequestDTO scaleRequest(Long locationId, Long readerId) {
        return new CelebrationEventScaleRequestDTO(locationId, null, List.of(readerId), null, null, null);
    }

    private Long createReaderPerson() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Person person = new Person("Concurrency Reader " + UUID.randomUUID(), uniquePhoneNumber(), BIRTHDAY);
            person.activate();
            Person saved = personRepository.save(person);
            personMinistryRepository.save(personMinistry(saved, MinistryType.READER, ministryRepository));
            return saved.getId();
        });
    }

    private Long createFutureEvent() {
        return celebrationEventRepository.saveAndFlush(new CelebrationEvent(
                null,
                "Concurrency Event " + UUID.randomUUID(),
                LocalDateTime.of(2026, 12, 1, 19, 0),
                LocalDateTime.of(2026, 12, 1, 20, 0),
                true
        )).getId();
    }

    private Long createLocation() {
        return locationRepository.saveAndFlush(new Location(
                null,
                "Concurrency Church " + UUID.randomUUID(),
                "Address"
        )).getId();
    }

    private int countAssignments(Long eventId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_event_assignment WHERE event_id = ?", Integer.class, eventId);
        return count == null ? 0 : count;
    }

    private void cleanupEvent(Long eventId) {
        jdbcTemplate.update("DELETE FROM tb_event_assignment WHERE event_id = ?", eventId);
        jdbcTemplate.update("DELETE FROM tb_event_location WHERE event_id = ?", eventId);
        jdbcTemplate.update("DELETE FROM tb_celebration_event WHERE id = ?", eventId);
    }

    private void cleanupPerson(Long personId) {
        jdbcTemplate.update("DELETE FROM tb_event_assignment WHERE person_id = ?", personId);
        jdbcTemplate.update("DELETE FROM tb_person_ministry WHERE person_id = ?", personId);        jdbcTemplate.update("DELETE FROM tb_person WHERE id = ?", personId);
    }

    private void cleanupLocation(Long locationId) {
        jdbcTemplate.update("DELETE FROM tb_event_location WHERE location_id = ?", locationId);
        jdbcTemplate.update("DELETE FROM tb_location WHERE id = ?", locationId);
    }

    private static String bootstrapUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    }

    private static String queryVersion(Statement statement) throws SQLException {
        try (var resultSet = statement.executeQuery("SELECT VERSION()")) {
            resultSet.next();
            return resultSet.getString(1);
        }
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

    private String uniquePhoneNumber() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return "3495" + String.format("%07d", suffix);
    }
}
