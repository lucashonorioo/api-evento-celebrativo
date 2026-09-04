package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.EventScheduleType;
import com.eventoscelebrativos.projection.EventScheduleAssignmentProjection;
import com.eventoscelebrativos.projection.EventScheduleEventProjection;
import com.eventoscelebrativos.projection.EucharistScaleEventProjection;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@DataJpaTest
class CelebrationEventRepositoryTest {

    @Autowired
    private CelebrationEventRepository eventRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldFindEucharistScaleByParallelAssignmentsWhenEventsExistInPeriod() {
        Page<EucharistScaleEventProjection> result = eventRepository.findEucharistScaleByAssignments(
                PageRequest.of(0, 10),
                rs(LocalDate.of(2025, 7, 1)),
                re(LocalDate.of(2025, 12, 31))
        );

        Assertions.assertEquals(3, result.getTotalElements());
        Assertions.assertEquals(0, result.getNumber());
        Assertions.assertEquals(10, result.getSize());
        Assertions.assertFalse(result.getContent().isEmpty());
        Assertions.assertTrue(result.getContent().stream()
                .allMatch(event -> event.getMinisterNames() == null));
    }

    @Test
    void shouldFilterEucharistScaleByParallelAssignmentsByPeriod() {
        Page<EucharistScaleEventProjection> result = eventRepository.findEucharistScaleByAssignments(
                PageRequest.of(0, 10),
                rs(LocalDate.of(2025, 7, 12)),
                re(LocalDate.of(2025, 7, 13))
        );

        Assertions.assertEquals(2, result.getTotalElements());
        Assertions.assertTrue(result.getContent().stream()
                .allMatch(event -> !event.getStartAt().toLocalDate().isBefore(LocalDate.of(2025, 7, 12))
                        && !event.getStartAt().toLocalDate().isAfter(LocalDate.of(2025, 7, 13))));
    }

    @Test
    void shouldPaginateEucharistScaleByParallelAssignments() {
        Page<EucharistScaleEventProjection> firstPage = eventRepository.findEucharistScaleByAssignments(
                PageRequest.of(0, 2),
                rs(LocalDate.of(2025, 7, 1)),
                re(LocalDate.of(2025, 7, 31))
        );
        Page<EucharistScaleEventProjection> secondPage = eventRepository.findEucharistScaleByAssignments(
                PageRequest.of(1, 2),
                rs(LocalDate.of(2025, 7, 1)),
                re(LocalDate.of(2025, 7, 31))
        );

        Assertions.assertEquals(3, firstPage.getTotalElements());
        Assertions.assertEquals(2, firstPage.getNumberOfElements());
        Assertions.assertEquals(1, secondPage.getNumberOfElements());
    }

    @Test
    void shouldFindEucharistScaleAssignmentsInBatchForPageEvents() {
        Page<EucharistScaleEventProjection> result = eventRepository.findEucharistScaleByAssignments(
                PageRequest.of(0, 10),
                rs(LocalDate.of(2025, 7, 13)),
                re(LocalDate.of(2025, 7, 13))
        );
        List<Long> eventIds = result.getContent().stream()
                .map(EucharistScaleEventProjection::getEventId)
                .toList();

        List<EventScheduleAssignmentProjection> assignments =
                eventRepository.findEucharistScaleAssignmentsByEventIds(eventIds);

        Assertions.assertEquals(1, result.getTotalElements());
        Assertions.assertEquals(2, assignments.size());
        Assertions.assertTrue(assignments.stream().allMatch(assignment -> eventIds.contains(assignment.getEventId())));
        Assertions.assertTrue(assignments.stream().map(EventScheduleAssignmentProjection::getPersonName)
                .toList()
                .containsAll(List.of("Mariana Ferraz", "Carlos Silva")));
    }

    @Test
    void shouldUseAssignmentTypeInsteadOfLegacyPersonTypeForParallelEucharistScale() {
        Long personId = insertPerson("Reader Serving Eucharist");
        Long eventId = insertEvent("Parallel Eucharist By Assignment", LocalDate.of(2026, 3, 8));
        Long locationId = firstLocationId();
        jdbcTemplate.update("INSERT INTO tb_event_location(event_id, location_id) VALUES (?, ?)", eventId, locationId);
        jdbcTemplate.update(
                """
                INSERT INTO tb_event_assignment(event_id, person_id, assignment_type, created_at, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """,
                eventId,
                personId,
                EventAssignmentType.EUCHARISTIC_MINISTER.name()
        );

        Page<EucharistScaleEventProjection> result = eventRepository.findEucharistScaleByAssignments(
                PageRequest.of(0, 10),
                rs(LocalDate.of(2026, 3, 8)),
                re(LocalDate.of(2026, 3, 8))
        );
        List<EventScheduleAssignmentProjection> assignments =
                eventRepository.findEucharistScaleAssignmentsByEventIds(List.of(eventId));

        Assertions.assertEquals(1, result.getTotalElements());
        Assertions.assertEquals(eventId, result.getContent().get(0).getEventId());
        Assertions.assertEquals(List.of(personId), assignments.stream()
                .map(EventScheduleAssignmentProjection::getPersonId)
                .toList());
    }

    @Test
    void shouldExcludeCancelledEventFromEucharistScaleAndItsCount() {
        Long personId = insertPerson("Reader Serving Eucharist Cancelled");
        Long eventId = insertEvent("Parallel Eucharist Cancelled", LocalDate.of(2026, 3, 9));
        Long locationId = firstLocationId();
        jdbcTemplate.update("INSERT INTO tb_event_location(event_id, location_id) VALUES (?, ?)", eventId, locationId);
        jdbcTemplate.update(
                """
                INSERT INTO tb_event_assignment(event_id, person_id, assignment_type, created_at, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """,
                eventId,
                personId,
                EventAssignmentType.EUCHARISTIC_MINISTER.name()
        );
        jdbcTemplate.update("UPDATE tb_celebration_event SET status = 'CANCELLED' WHERE id = ?", eventId);

        Page<EucharistScaleEventProjection> result = eventRepository.findEucharistScaleByAssignments(
                PageRequest.of(0, 10),
                rs(LocalDate.of(2026, 3, 9)),
                re(LocalDate.of(2026, 3, 9))
        );

        Assertions.assertEquals(0, result.getTotalElements());
        Assertions.assertTrue(result.getContent().isEmpty());
    }

    @Test
    void shouldFindParallelScheduleEventsForEachAssignmentType() {
        for (EventScheduleType type : EventScheduleType.values()) {
            Page<EventScheduleEventProjection> result = findParallelSchedule(type, false);

            Assertions.assertEquals(expectedScheduleCount(type), result.getTotalElements());
            Assertions.assertTrue(result.getContent().stream()
                    .allMatch(event -> !parallelAssignments(event, type).isEmpty()));
        }
    }

    @Test
    void shouldFilterParallelScheduleEventsByPeriod() {
        Page<EventScheduleEventProjection> result = eventRepository.findEventScheduleEventsByAssignments(
                PageRequest.of(0, 10),
                rs(LocalDate.of(2025, 7, 12)),
                re(LocalDate.of(2025, 7, 13)),
                EventAssignmentType.READER.name(),
                false
        );

        Assertions.assertEquals(2, result.getTotalElements());
        Assertions.assertTrue(result.getContent().stream()
                .allMatch(event -> !event.getStartAt().toLocalDate().isBefore(LocalDate.of(2025, 7, 12))
                        && !event.getStartAt().toLocalDate().isAfter(LocalDate.of(2025, 7, 13))));
    }

    @Test
    void shouldPaginateParallelScheduleByEvent() {
        Page<EventScheduleEventProjection> firstPage = eventRepository.findEventScheduleEventsByAssignments(
                PageRequest.of(0, 2),
                rs(LocalDate.of(2025, 7, 1)),
                re(LocalDate.of(2025, 7, 31)),
                EventAssignmentType.EUCHARISTIC_MINISTER.name(),
                false
        );
        Page<EventScheduleEventProjection> secondPage = eventRepository.findEventScheduleEventsByAssignments(
                PageRequest.of(1, 2),
                rs(LocalDate.of(2025, 7, 1)),
                re(LocalDate.of(2025, 7, 31)),
                EventAssignmentType.EUCHARISTIC_MINISTER.name(),
                false
        );

        Assertions.assertEquals(3, firstPage.getTotalElements());
        Assertions.assertEquals(2, firstPage.getNumberOfElements());
        Assertions.assertEquals(1, secondPage.getNumberOfElements());
    }

    @Test
    void shouldReturnAllParallelScheduleEventsWhenIncludeUnassignedIsTrue() {
        Page<EventScheduleEventProjection> result = findParallelSchedule(EventScheduleType.PRIEST, true);
        List<EventScheduleAssignmentProjection> assignments =
                eventRepository.findEventScheduleAssignmentsByAssignmentType(
                        result.getContent().stream().map(EventScheduleEventProjection::getEventId).toList(),
                        EventAssignmentType.PRIEST.name()
                );

        Assertions.assertEquals(3, result.getTotalElements());
        Assertions.assertTrue(result.getContent().stream()
                .anyMatch(event -> assignments.stream().noneMatch(assignment -> assignment.getEventId().equals(event.getEventId()))));
    }

    @Test
    void shouldExcludeCancelledEventFromParallelScheduleAndItsCount() {
        Long personId = insertPerson("Commentator Cancelled Schedule");
        Long eventId = insertEvent("Parallel Monthly Cancelled", LocalDate.of(2026, 3, 11));
        Long locationId = firstLocationId();
        jdbcTemplate.update("INSERT INTO tb_event_location(event_id, location_id) VALUES (?, ?)", eventId, locationId);
        jdbcTemplate.update(
                """
                INSERT INTO tb_event_assignment(event_id, person_id, assignment_type, created_at, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """,
                eventId,
                personId,
                EventAssignmentType.COMMENTATOR.name()
        );
        jdbcTemplate.update("UPDATE tb_celebration_event SET status = 'CANCELLED' WHERE id = ?", eventId);

        Page<EventScheduleEventProjection> result = eventRepository.findEventScheduleEventsByAssignments(
                PageRequest.of(0, 10),
                rs(LocalDate.of(2026, 3, 11)),
                re(LocalDate.of(2026, 3, 11)),
                EventAssignmentType.COMMENTATOR.name(),
                false
        );

        Assertions.assertEquals(0, result.getTotalElements());
        Assertions.assertTrue(result.getContent().isEmpty());
    }

    @Test
    void shouldUseAssignmentTypeInsteadOfLegacyPersonTypeForParallelMonthlySchedule() {
        Long personId = insertPerson("Reader Serving As Commentator");
        Long eventId = insertEvent("Parallel Monthly By Assignment", LocalDate.of(2026, 3, 10));
        Long locationId = firstLocationId();
        jdbcTemplate.update("INSERT INTO tb_event_location(event_id, location_id) VALUES (?, ?)", eventId, locationId);
        jdbcTemplate.update(
                """
                INSERT INTO tb_event_assignment(event_id, person_id, assignment_type, created_at, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """,
                eventId,
                personId,
                EventAssignmentType.COMMENTATOR.name()
        );

        Page<EventScheduleEventProjection> result = eventRepository.findEventScheduleEventsByAssignments(
                PageRequest.of(0, 10),
                rs(LocalDate.of(2026, 3, 10)),
                re(LocalDate.of(2026, 3, 10)),
                EventAssignmentType.COMMENTATOR.name(),
                false
        );
        List<EventScheduleAssignmentProjection> assignments =
                eventRepository.findEventScheduleAssignmentsByAssignmentType(List.of(eventId), EventAssignmentType.COMMENTATOR.name());

        Assertions.assertEquals(1, result.getTotalElements());
        Assertions.assertEquals(eventId, result.getContent().get(0).getEventId());
        Assertions.assertEquals(List.of(personId), assignments.stream()
                .map(EventScheduleAssignmentProjection::getPersonId)
                .toList());
    }

    @Test
    void shouldCountParallelScheduleTotalElementsByEventsAndNotAssignments() {
        Page<EventScheduleEventProjection> result = findParallelSchedule(EventScheduleType.EUCHARISTIC_MINISTER, false);
        List<EventScheduleAssignmentProjection> assignments =
                eventRepository.findEventScheduleAssignmentsByAssignmentType(
                        result.getContent().stream().map(EventScheduleEventProjection::getEventId).toList(),
                        EventAssignmentType.EUCHARISTIC_MINISTER.name()
                );

        Assertions.assertEquals(3, result.getTotalElements());
        Assertions.assertTrue(assignments.size() > result.getTotalElements());
    }

    @Test
    void shouldFindEucharistScaleEventCrossingMidnightAcrossAllRangeScenarios() {
        Long personId = insertPerson("Eucharist Midnight Minister");
        Long eventId = insertEventWithRange(
                "Eucharist Midnight Event",
                LocalDateTime.of(2026, 8, 9, 23, 0),
                LocalDateTime.of(2026, 8, 10, 1, 0));
        attachEucharistAssignment(eventId, personId);

        assertEucharistScaleContainsEvent(eventId, LocalDateTime.of(2026, 8, 9, 0, 0), LocalDateTime.of(2026, 8, 10, 0, 0));
        assertEucharistScaleContainsEvent(eventId, LocalDateTime.of(2026, 8, 10, 0, 0), LocalDateTime.of(2026, 8, 11, 0, 0));
        assertEucharistScaleContainsEvent(eventId, LocalDateTime.of(2026, 8, 9, 0, 0), LocalDateTime.of(2026, 8, 11, 0, 0));
        assertEucharistScaleContainsEvent(eventId, LocalDateTime.of(2026, 8, 9, 23, 30), LocalDateTime.of(2026, 8, 10, 0, 30));
        assertEucharistScaleContainsEvent(eventId, LocalDateTime.of(2026, 8, 9, 22, 0), LocalDateTime.of(2026, 8, 10, 2, 0));
    }

    @Test
    void shouldNotReturnEucharistScaleEventEndingExactlyAtRangeStart() {
        Long personId = insertPerson("Eucharist Boundary Minister End");
        Long eventId = insertEventWithRange(
                "Eucharist Boundary Event Ends At Range Start",
                LocalDateTime.of(2026, 8, 20, 8, 0),
                LocalDateTime.of(2026, 8, 20, 10, 0));
        attachEucharistAssignment(eventId, personId);

        assertEucharistScaleExcludesEvent(eventId, LocalDateTime.of(2026, 8, 20, 10, 0), LocalDateTime.of(2026, 8, 20, 12, 0));
    }

    @Test
    void shouldNotReturnEucharistScaleEventStartingExactlyAtRangeEnd() {
        Long personId = insertPerson("Eucharist Boundary Minister Start");
        Long eventId = insertEventWithRange(
                "Eucharist Boundary Event Starts At Range End",
                LocalDateTime.of(2026, 8, 20, 12, 0),
                LocalDateTime.of(2026, 8, 20, 14, 0));
        attachEucharistAssignment(eventId, personId);

        assertEucharistScaleExcludesEvent(eventId, LocalDateTime.of(2026, 8, 20, 10, 0), LocalDateTime.of(2026, 8, 20, 12, 0));
    }

    @Test
    void shouldReturnEucharistScaleEventWithMinimalIntersection() {
        Long personId = insertPerson("Eucharist Boundary Minister Minimal");
        Long eventId = insertEventWithRange(
                "Eucharist Boundary Event Minimal Intersection",
                LocalDateTime.of(2026, 8, 20, 9, 59, 59),
                LocalDateTime.of(2026, 8, 20, 10, 0, 1));
        attachEucharistAssignment(eventId, personId);

        assertEucharistScaleContainsEvent(eventId, LocalDateTime.of(2026, 8, 20, 10, 0, 0), LocalDateTime.of(2026, 8, 20, 12, 0, 0));
    }

    @Test
    void shouldFindParallelScheduleEventCrossingMidnightAcrossAllRangeScenarios() {
        Long personId = insertPerson("Parallel Schedule Midnight Reader");
        Long eventId = insertEventWithRange(
                "Parallel Schedule Midnight Event",
                LocalDateTime.of(2026, 8, 9, 23, 0),
                LocalDateTime.of(2026, 8, 10, 1, 0));
        attachAssignment(eventId, personId, EventAssignmentType.READER);

        assertParallelScheduleContainsEvent(eventId, LocalDateTime.of(2026, 8, 9, 0, 0), LocalDateTime.of(2026, 8, 10, 0, 0));
        assertParallelScheduleContainsEvent(eventId, LocalDateTime.of(2026, 8, 10, 0, 0), LocalDateTime.of(2026, 8, 11, 0, 0));
        assertParallelScheduleContainsEvent(eventId, LocalDateTime.of(2026, 8, 9, 0, 0), LocalDateTime.of(2026, 8, 11, 0, 0));
        assertParallelScheduleContainsEvent(eventId, LocalDateTime.of(2026, 8, 9, 23, 30), LocalDateTime.of(2026, 8, 10, 0, 30));
        assertParallelScheduleContainsEvent(eventId, LocalDateTime.of(2026, 8, 9, 22, 0), LocalDateTime.of(2026, 8, 10, 2, 0));
    }

    @Test
    void shouldNotReturnParallelScheduleEventEndingExactlyAtRangeStart() {
        Long personId = insertPerson("Parallel Boundary Reader End");
        Long eventId = insertEventWithRange(
                "Parallel Boundary Event Ends At Range Start",
                LocalDateTime.of(2026, 8, 21, 8, 0),
                LocalDateTime.of(2026, 8, 21, 10, 0));
        attachAssignment(eventId, personId, EventAssignmentType.READER);

        assertParallelScheduleExcludesEvent(eventId, LocalDateTime.of(2026, 8, 21, 10, 0), LocalDateTime.of(2026, 8, 21, 12, 0));
    }

    @Test
    void shouldNotReturnParallelScheduleEventStartingExactlyAtRangeEnd() {
        Long personId = insertPerson("Parallel Boundary Reader Start");
        Long eventId = insertEventWithRange(
                "Parallel Boundary Event Starts At Range End",
                LocalDateTime.of(2026, 8, 21, 12, 0),
                LocalDateTime.of(2026, 8, 21, 14, 0));
        attachAssignment(eventId, personId, EventAssignmentType.READER);

        assertParallelScheduleExcludesEvent(eventId, LocalDateTime.of(2026, 8, 21, 10, 0), LocalDateTime.of(2026, 8, 21, 12, 0));
    }

    @Test
    void shouldReturnParallelScheduleEventWithMinimalIntersection() {
        Long personId = insertPerson("Parallel Boundary Reader Minimal");
        Long eventId = insertEventWithRange(
                "Parallel Boundary Event Minimal Intersection",
                LocalDateTime.of(2026, 8, 21, 9, 59, 59),
                LocalDateTime.of(2026, 8, 21, 10, 0, 1));
        attachAssignment(eventId, personId, EventAssignmentType.READER);

        assertParallelScheduleContainsEvent(eventId, LocalDateTime.of(2026, 8, 21, 10, 0, 0), LocalDateTime.of(2026, 8, 21, 12, 0, 0));
    }

    @Test
    void shouldFindEventWithLocations() {
        CelebrationEvent event = eventRepository.findByIdWithLocations(1L).orElseThrow();

        Assertions.assertEquals(1L, event.getId());
        Assertions.assertFalse(event.getLocations().isEmpty());
    }

    @Test
    void shouldFindEventWithoutLocation() {
        CelebrationEvent event = new CelebrationEvent(
                null, "Evento sem local", LocalDateTime.of(2026, 2, 1, 9, 0), LocalDateTime.of(2026, 2, 1, 10, 0), true);
        entityManager.persist(event);
        entityManager.flush();
        entityManager.clear();

        CelebrationEvent result = eventRepository.findByIdWithLocations(event.getId()).orElseThrow();

        Assertions.assertTrue(result.getLocations().isEmpty());
    }

    private Page<EventScheduleEventProjection> findParallelSchedule(EventScheduleType type, boolean includeUnassigned) {
        return eventRepository.findEventScheduleEventsByAssignments(
                PageRequest.of(0, 10),
                rs(LocalDate.of(2025, 7, 1)),
                re(LocalDate.of(2025, 7, 31)),
                toAssignmentType(type).name(),
                includeUnassigned
        );
    }

    private List<EventScheduleAssignmentProjection> parallelAssignments(EventScheduleEventProjection event, EventScheduleType type) {
        return eventRepository.findEventScheduleAssignmentsByAssignmentType(
                List.of(event.getEventId()),
                toAssignmentType(type).name()
        );
    }

    private EventAssignmentType toAssignmentType(EventScheduleType type) {
        return EventAssignmentType.valueOf(type.name());
    }

    private int expectedScheduleCount(EventScheduleType type) {
        return type == EventScheduleType.PRIEST ? 2 : 3;
    }

    private Long insertPerson(String name) {
        String phoneNumber = uniquePhoneNumber();
        jdbcTemplate.update(
                """
                INSERT INTO tb_person(public_id, name, phone_number, birthday_date)
                VALUES (?, ?, ?, '1990-01-10')
                """,
                newPublicId(),
                name + " " + UUID.randomUUID(),
                phoneNumber
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tb_person WHERE phone_number = ?",
                Long.class,
                phoneNumber
        );
    }

    private Long insertEvent(String name, LocalDate eventDate) {
        String eventName = name + " " + UUID.randomUUID();
        LocalDateTime startAt = LocalDateTime.of(eventDate, LocalTime.of(19, 0));
        jdbcTemplate.update(
                """
                INSERT INTO tb_celebration_event(name_mass_or_event, start_at, end_at, mass_or_celebration)
                VALUES (?, ?, ?, TRUE)
                """,
                eventName,
                startAt,
                startAt.plusHours(1)
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tb_celebration_event WHERE name_mass_or_event = ?",
                Long.class,
                eventName
        );
    }

    private Long firstLocationId() {
        return jdbcTemplate.queryForObject("SELECT id FROM tb_location ORDER BY id LIMIT 1", Long.class);
    }

    private Long insertEventWithRange(String name, LocalDateTime startAt, LocalDateTime endAt) {
        String eventName = name + " " + UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO tb_celebration_event(name_mass_or_event, start_at, end_at, mass_or_celebration)
                VALUES (?, ?, ?, TRUE)
                """,
                eventName,
                startAt,
                endAt
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tb_celebration_event WHERE name_mass_or_event = ?",
                Long.class,
                eventName
        );
    }

    private void attachAssignment(Long eventId, Long personId, EventAssignmentType assignmentType) {
        jdbcTemplate.update(
                """
                INSERT INTO tb_event_assignment(event_id, person_id, assignment_type, created_at, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """,
                eventId,
                personId,
                assignmentType.name()
        );
    }

    private void attachEucharistAssignment(Long eventId, Long personId) {
        Long locationId = firstLocationId();
        jdbcTemplate.update("INSERT INTO tb_event_location(event_id, location_id) VALUES (?, ?)", eventId, locationId);
        attachAssignment(eventId, personId, EventAssignmentType.EUCHARISTIC_MINISTER);
    }

    private void assertEucharistScaleContainsEvent(Long eventId, LocalDateTime rangeStart, LocalDateTime rangeEnd) {
        Page<EucharistScaleEventProjection> result = eventRepository.findEucharistScaleByAssignments(
                PageRequest.of(0, 10), rangeStart, rangeEnd);

        Assertions.assertTrue(
                result.getContent().stream().anyMatch(event -> event.getEventId().equals(eventId)),
                "Esperava encontrar o evento " + eventId + " no intervalo [" + rangeStart + ", " + rangeEnd + ")");
    }

    private void assertEucharistScaleExcludesEvent(Long eventId, LocalDateTime rangeStart, LocalDateTime rangeEnd) {
        Page<EucharistScaleEventProjection> result = eventRepository.findEucharistScaleByAssignments(
                PageRequest.of(0, 10), rangeStart, rangeEnd);

        Assertions.assertTrue(
                result.getContent().stream().noneMatch(event -> event.getEventId().equals(eventId)),
                "Nao esperava encontrar o evento " + eventId + " no intervalo [" + rangeStart + ", " + rangeEnd + ")");
    }

    private void assertParallelScheduleContainsEvent(Long eventId, LocalDateTime rangeStart, LocalDateTime rangeEnd) {
        Page<EventScheduleEventProjection> result = eventRepository.findEventScheduleEventsByAssignments(
                PageRequest.of(0, 10), rangeStart, rangeEnd, EventAssignmentType.READER.name(), false);

        Assertions.assertTrue(
                result.getContent().stream().anyMatch(event -> event.getEventId().equals(eventId)),
                "Esperava encontrar o evento " + eventId + " no intervalo [" + rangeStart + ", " + rangeEnd + ")");
    }

    private void assertParallelScheduleExcludesEvent(Long eventId, LocalDateTime rangeStart, LocalDateTime rangeEnd) {
        Page<EventScheduleEventProjection> result = eventRepository.findEventScheduleEventsByAssignments(
                PageRequest.of(0, 10), rangeStart, rangeEnd, EventAssignmentType.READER.name(), false);

        Assertions.assertTrue(
                result.getContent().stream().noneMatch(event -> event.getEventId().equals(eventId)),
                "Nao esperava encontrar o evento " + eventId + " no intervalo [" + rangeStart + ", " + rangeEnd + ")");
    }

    private String uniquePhoneNumber() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return "3498" + String.format("%07d", suffix);
    }

    private UUID newPublicId() {
        return UUID.randomUUID();
    }

    private static LocalDateTime rs(LocalDate date) {
        return date.atStartOfDay();
    }

    private static LocalDateTime re(LocalDate date) {
        return date.plusDays(1).atStartOfDay();
    }
}
