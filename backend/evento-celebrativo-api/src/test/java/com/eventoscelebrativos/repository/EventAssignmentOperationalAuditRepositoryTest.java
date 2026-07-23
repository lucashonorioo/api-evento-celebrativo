package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.projection.LegacyEventAssignmentProjection;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class EventAssignmentOperationalAuditRepositoryTest {

    @Autowired
    private CelebrationEventRepository eventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void shouldPageAuditEventIdsByDateTimeAndId() {
        Long first = insertEvent("Audit Same Time 1", LocalDate.of(2026, 1, 1), LocalTime.of(8, 0));
        Long second = insertEvent("Audit Same Time 2", LocalDate.of(2026, 1, 1), LocalTime.of(8, 0));

        Page<Long> result = eventRepository.findEventIdsForAssignmentAudit(
                PageRequest.of(0, 10),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 1)
        );

        assertEquals(List.of(first, second), result.getContent());
        assertEquals(2, result.getTotalElements());
    }

    @Test
    void shouldReturnEmptyAuditEventPageWhenPeriodHasNoEvents() {
        Page<Long> result = eventRepository.findEventIdsForAssignmentAudit(
                PageRequest.of(9, 10),
                LocalDate.of(2030, 1, 1),
                LocalDate.of(2030, 1, 31)
        );

        assertTrue(result.isEmpty());
        assertEquals(0, result.getTotalElements());
    }

    @Test
    void shouldFilterSpecificAuditEventByIdAndDates() {
        Page<Long> result = eventRepository.findEventIdForAssignmentAudit(
                PageRequest.of(0, 1),
                1L,
                LocalDate.of(2025, 7, 13),
                LocalDate.of(2025, 7, 13)
        );

        assertEquals(List.of(1L), result.getContent());
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void shouldFindLegacyAssignmentsForOneEventInBatchFormat() {
        List<LegacyEventAssignmentProjection> result = eventRepository.findLegacyEventAssignmentsForAudit(List.of(1L));

        assertEquals(7, result.size());
        assertTrue(result.stream().allMatch(row -> row.getEventId().equals(1L)));
        assertTrue(result.stream().map(LegacyEventAssignmentProjection::getPersonType).toList().contains("reader"));
    }

    @Test
    void shouldFindLegacyAssignmentsForSeveralEvents() {
        List<LegacyEventAssignmentProjection> result = eventRepository.findLegacyEventAssignmentsForAudit(List.of(2L, 1L));

        assertEquals(14, result.size());
        assertEquals(List.of(1L, 2L), result.stream()
                .map(LegacyEventAssignmentProjection::getEventId)
                .distinct()
                .toList());
    }

    @Test
    void shouldReturnNoLegacyAssignmentsForEventWithoutParticipants() {
        Long eventId = insertEvent("Audit No Participants", LocalDate.of(2026, 2, 1), LocalTime.of(9, 0));

        List<LegacyEventAssignmentProjection> result = eventRepository.findLegacyEventAssignmentsForAudit(List.of(eventId));

        assertEquals(List.of(), result);
    }

    @Test
    void shouldReadAllFiveLegacyPersonTypes() {
        List<String> personTypes = eventRepository.findLegacyEventAssignmentsForAudit(List.of(1L)).stream()
                .map(LegacyEventAssignmentProjection::getPersonType)
                .distinct()
                .toList();

        assertEquals(List.of(
                "priest",
                "reader",
                "commentator",
                "minister_of_the_word",
                "eucharistic_minister"
        ), personTypes);
    }

    @Test
    void shouldReturnSamePersonInDifferentEvents() {
        Long personId = firstPersonIdByType("reader");
        Long firstEventId = insertEvent("Audit Same Person 1", LocalDate.of(2026, 2, 2), LocalTime.of(8, 0));
        Long secondEventId = insertEvent("Audit Same Person 2", LocalDate.of(2026, 2, 3), LocalTime.of(8, 0));
        jdbcTemplate.update("INSERT INTO tb_event_person(event_id, person_id) VALUES (?, ?)", firstEventId, personId);
        jdbcTemplate.update("INSERT INTO tb_event_person(event_id, person_id) VALUES (?, ?)", secondEventId, personId);

        List<LegacyEventAssignmentProjection> result =
                eventRepository.findLegacyEventAssignmentsForAudit(List.of(secondEventId, firstEventId));

        assertEquals(List.of(firstEventId, secondEventId), result.stream()
                .map(LegacyEventAssignmentProjection::getEventId)
                .toList());
        assertEquals(List.of(personId, personId), result.stream()
                .map(LegacyEventAssignmentProjection::getPersonId)
                .toList());
    }

    @Test
    void shouldOrderLegacyAssignmentsByEventFunctionAndPersonId() {
        List<LegacyEventAssignmentProjection> result = eventRepository.findLegacyEventAssignmentsForAudit(List.of(1L));

        assertEquals(List.of(
                "priest",
                "reader",
                "reader",
                "commentator",
                "minister_of_the_word",
                "eucharistic_minister",
                "eucharistic_minister"
        ), result.stream().map(LegacyEventAssignmentProjection::getPersonType).toList());
        assertEquals(List.of(13L, 4L, 5L, 1L, 7L, 10L, 11L),
                result.stream().map(LegacyEventAssignmentProjection::getPersonId).toList());
    }

    @Test
    void shouldReturnEmptyCollectionWithoutExecutingSqlForEmptyInput() {
        Statistics statistics = statistics();
        statistics.clear();

        List<LegacyEventAssignmentProjection> result = eventRepository.findLegacyEventAssignmentsForAudit(List.of());

        assertEquals(List.of(), result);
        assertEquals(0L, statistics.getPrepareStatementCount());
    }

    @Test
    void shouldReadLegacyAssignmentsInOneBatchQueryWithoutNPlusOne() {
        Statistics statistics = statistics();
        statistics.clear();

        List<LegacyEventAssignmentProjection> result = eventRepository.findLegacyEventAssignmentsForAudit(List.of(1L, 2L, 3L));

        assertEquals(21, result.size());
        assertEquals(1L, statistics.getPrepareStatementCount());
    }

    private Long insertEvent(String name, LocalDate eventDate, LocalTime eventTime) {
        String eventName = name + " " + UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO tb_celebration_event(name_mass_or_event, event_date, event_time, mass_or_celebration)
                VALUES (?, ?, ?, TRUE)
                """,
                eventName,
                eventDate,
                eventTime
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tb_celebration_event WHERE name_mass_or_event = ?",
                Long.class,
                eventName
        );
    }

    private Long firstPersonIdByType(String personType) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tb_person WHERE person_type = ? ORDER BY id LIMIT 1",
                Long.class,
                personType
        );
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }
}
