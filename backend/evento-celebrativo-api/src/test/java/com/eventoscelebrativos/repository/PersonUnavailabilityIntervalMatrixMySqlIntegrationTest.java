package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.EventAssignment;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.projection.PersonUnavailabilityAssignmentConflictProjection;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova a matriz completa de intervalo [startAt, endAt) semiaberto entre PersonUnavailability e
 * CelebrationEvent (via EventAssignment) contra MySQL 8.4 real, alem de validar o mapeamento
 * Hibernate (ddl-auto=validate, ja ativo no profile "test") no schema V11 migrado em MySQL.
 * Usa uma database MySQL isolada e propria (@DynamicPropertySource + @AutoConfigureTestDatabase
 * Replace.NONE), nunca a database compartilhada pelo restante da suite. E' automaticamente
 * ignorado quando MySQL nao estiver acessivel (mesmas propriedades/env vars documentadas em
 * TimeRangeModelV10ToV11MySqlUpgradeIntegrationTest), para que `mvnw test` continue verde sem Docker.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PersonUnavailabilityIntervalMatrixMySqlIntegrationTest {

    private static String host;
    private static String port;
    private static String username;
    private static String password;
    private static String databaseName;
    private static boolean mysqlAvailable;

    @Autowired
    private PersonUnavailabilityRepository personUnavailabilityRepository;

    @Autowired
    private EventAssignmentRepository eventAssignmentRepository;

    @Autowired
    private CelebrationEventRepository celebrationEventRepository;

    @Autowired
    private PersonRepository personRepository;

    private Person person;
    private CelebrationEvent event;

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
        try (Connection connection = DriverManager.getConnection(bootstrapUrl(), username, password)) {
            mysqlAvailable = connection.isValid(3);
        } catch (SQLException e) {
            mysqlAvailable = false;
            return;
        }

        databaseName = "v11_interval_matrix_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(bootstrapUrl(), username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + databaseName + "`");
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
    }

    private static String bootstrapUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
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
    void setUp() {
        Assumptions.assumeTrue(mysqlAvailable, "MySQL 8.4 real nao acessivel; teste ignorado.");

        person = new Person();
        person.setName("Interval Matrix Person");
        person.setPhoneNumber(uniquePhoneNumber());
        person.setPassword("encoded-password");
        person = personRepository.saveAndFlush(person);

        // Evento de referencia: 19h00 as 20h00 do dia 2026-09-10.
        LocalDateTime eventStartAt = LocalDateTime.of(2026, 9, 10, 19, 0);
        LocalDateTime eventEndAt = LocalDateTime.of(2026, 9, 10, 20, 0);
        event = new CelebrationEvent(null, "Missa Matriz de Intervalo", eventStartAt, eventEndAt, true);
        event = celebrationEventRepository.saveAndFlush(event);

        eventAssignmentRepository.saveAndFlush(new EventAssignment(event, person, EventAssignmentType.READER));
    }

    @Test
    void shouldNotConflictWhenUnavailabilityIsBeforeEventOnSameDay() {
        assertNoConflict(at(2026, 9, 10, 6, 0), at(2026, 9, 10, 12, 0));
    }

    @Test
    void shouldConflictWhenUnavailabilityCoversEventStart() {
        assertConflict(at(2026, 9, 10, 18, 30), at(2026, 9, 10, 19, 30));
    }

    @Test
    void shouldConflictWhenUnavailabilityCoversEventMiddle() {
        assertConflict(at(2026, 9, 10, 19, 20), at(2026, 9, 10, 19, 40));
    }

    @Test
    void shouldConflictWhenUnavailabilityCoversEventEnd() {
        assertConflict(at(2026, 9, 10, 19, 30), at(2026, 9, 10, 20, 30));
    }

    @Test
    void shouldNotConflictWhenUnavailabilityEndsExactlyWhenEventStarts() {
        assertNoConflict(at(2026, 9, 10, 18, 0), at(2026, 9, 10, 19, 0));
    }

    @Test
    void shouldNotConflictWhenEventEndsExactlyWhenUnavailabilityStarts() {
        assertNoConflict(at(2026, 9, 10, 20, 0), at(2026, 9, 10, 21, 0));
    }

    @Test
    void shouldConflictWhenEventIsFullyContainedInUnavailability() {
        assertConflict(at(2026, 9, 10, 0, 0), at(2026, 9, 11, 0, 0));
    }

    @Test
    void shouldConflictWhenUnavailabilityIsFullyContainedInEvent() {
        assertConflict(at(2026, 9, 10, 19, 15), at(2026, 9, 10, 19, 45));
    }

    @Test
    void shouldConflictWithMinimalOneSecondIntersectionAtEventEnd() {
        assertConflict(at(2026, 9, 10, 19, 59, 59), at(2026, 9, 10, 20, 0, 1));
    }

    @Test
    void shouldConflictWhenEventCrossesMidnightAndUnavailabilityOverlapsIt() throws SQLException {
        LocalDateTime crossMidnightStart = LocalDateTime.of(2026, 9, 15, 23, 0);
        LocalDateTime crossMidnightEnd = LocalDateTime.of(2026, 9, 16, 1, 0);
        replaceEventRange(crossMidnightStart, crossMidnightEnd);

        assertConflict(at(2026, 9, 15, 23, 30), at(2026, 9, 16, 0, 30));
    }

    @Test
    void shouldConflictWhenUnavailabilityCrossesMidnightAndOverlapsEvent() {
        assertConflict(at(2026, 9, 9, 22, 0), at(2026, 9, 10, 19, 30));
    }

    @Test
    void shouldConflictWhenUnavailabilityIsWholeDayCoveringEvent() {
        assertConflict(at(2026, 9, 10, 0, 0), at(2026, 9, 11, 0, 0));
    }

    @Test
    void shouldConflictWhenUnavailabilitySpansMultipleDaysCoveringEvent() {
        assertConflict(at(2026, 9, 8, 0, 0), at(2026, 9, 12, 0, 0));
    }

    @Test
    void shouldRejectZeroDurationUnavailability() {
        LocalDateTime instant = at(2026, 9, 10, 19, 30);
        assertThrows(DataAccessException.class,
                () -> personUnavailabilityRepository.saveAndFlush(
                        new com.eventoscelebrativos.model.PersonUnavailability(person, instant, instant, null)));
    }

    @Test
    void shouldRejectInvertedUnavailabilityRange() {
        LocalDateTime start = at(2026, 9, 10, 20, 0);
        LocalDateTime end = at(2026, 9, 10, 19, 0);
        assertThrows(DataAccessException.class,
                () -> personUnavailabilityRepository.saveAndFlush(
                        new com.eventoscelebrativos.model.PersonUnavailability(person, start, end, null)));
    }

    private void assertConflict(LocalDateTime unavailabilityStartAt, LocalDateTime unavailabilityEndAt) {
        List<PersonUnavailabilityAssignmentConflictProjection> conflicts =
                eventAssignmentRepository.findAssignmentConflictsByPersonIdAndRange(
                        person.getId(), unavailabilityStartAt, unavailabilityEndAt);
        assertEquals(1, conflicts.size());
        assertEquals(event.getId(), conflicts.get(0).getEventId());
    }

    private void assertNoConflict(LocalDateTime unavailabilityStartAt, LocalDateTime unavailabilityEndAt) {
        List<PersonUnavailabilityAssignmentConflictProjection> conflicts =
                eventAssignmentRepository.findAssignmentConflictsByPersonIdAndRange(
                        person.getId(), unavailabilityStartAt, unavailabilityEndAt);
        assertTrue(conflicts.isEmpty());
    }

    private void replaceEventRange(LocalDateTime startAt, LocalDateTime endAt) {
        event.setStartAt(startAt);
        event.setEndAt(endAt);
        celebrationEventRepository.saveAndFlush(event);
    }

    private LocalDateTime at(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute);
    }

    private LocalDateTime at(int year, int month, int day, int hour, int minute, int second) {
        return LocalDateTime.of(year, month, day, hour, minute, second);
    }

    private String uniquePhoneNumber() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return "3498" + String.format("%07d", suffix);
    }
}
