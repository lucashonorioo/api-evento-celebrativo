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
                LocalDate.of(2025, 7, 1),
                LocalDate.of(2025, 12, 31)
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
                LocalDate.of(2025, 7, 12),
                LocalDate.of(2025, 7, 13)
        );

        Assertions.assertEquals(2, result.getTotalElements());
        Assertions.assertTrue(result.getContent().stream()
                .allMatch(event -> !event.getEventDate().isBefore(LocalDate.of(2025, 7, 12))
                        && !event.getEventDate().isAfter(LocalDate.of(2025, 7, 13))));
    }

    @Test
    void shouldPaginateEucharistScaleByParallelAssignments() {
        Page<EucharistScaleEventProjection> firstPage = eventRepository.findEucharistScaleByAssignments(
                PageRequest.of(0, 2),
                LocalDate.of(2025, 7, 1),
                LocalDate.of(2025, 7, 31)
        );
        Page<EucharistScaleEventProjection> secondPage = eventRepository.findEucharistScaleByAssignments(
                PageRequest.of(1, 2),
                LocalDate.of(2025, 7, 1),
                LocalDate.of(2025, 7, 31)
        );

        Assertions.assertEquals(3, firstPage.getTotalElements());
        Assertions.assertEquals(2, firstPage.getNumberOfElements());
        Assertions.assertEquals(1, secondPage.getNumberOfElements());
    }

    @Test
    void shouldFindEucharistScaleAssignmentsInBatchForPageEvents() {
        Page<EucharistScaleEventProjection> result = eventRepository.findEucharistScaleByAssignments(
                PageRequest.of(0, 10),
                LocalDate.of(2025, 7, 13),
                LocalDate.of(2025, 7, 13)
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
                LocalDate.of(2026, 3, 8),
                LocalDate.of(2026, 3, 8)
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
                LocalDate.of(2025, 7, 12),
                LocalDate.of(2025, 7, 13),
                EventAssignmentType.READER.name(),
                false
        );

        Assertions.assertEquals(2, result.getTotalElements());
        Assertions.assertTrue(result.getContent().stream()
                .allMatch(event -> !event.getEventDate().isBefore(LocalDate.of(2025, 7, 12))
                        && !event.getEventDate().isAfter(LocalDate.of(2025, 7, 13))));
    }

    @Test
    void shouldPaginateParallelScheduleByEvent() {
        Page<EventScheduleEventProjection> firstPage = eventRepository.findEventScheduleEventsByAssignments(
                PageRequest.of(0, 2),
                LocalDate.of(2025, 7, 1),
                LocalDate.of(2025, 7, 31),
                EventAssignmentType.EUCHARISTIC_MINISTER.name(),
                false
        );
        Page<EventScheduleEventProjection> secondPage = eventRepository.findEventScheduleEventsByAssignments(
                PageRequest.of(1, 2),
                LocalDate.of(2025, 7, 1),
                LocalDate.of(2025, 7, 31),
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
                LocalDate.of(2026, 3, 10),
                LocalDate.of(2026, 3, 10),
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
    void shouldFindEventWithLocations() {
        CelebrationEvent event = eventRepository.findByIdWithLocations(1L).orElseThrow();

        Assertions.assertEquals(1L, event.getId());
        Assertions.assertFalse(event.getLocations().isEmpty());
    }

    @Test
    void shouldFindEventWithoutLocation() {
        CelebrationEvent event = new CelebrationEvent(null, "Evento sem local", LocalDate.of(2026, 2, 1), LocalTime.of(9, 0), true);
        entityManager.persist(event);
        entityManager.flush();
        entityManager.clear();

        CelebrationEvent result = eventRepository.findByIdWithLocations(event.getId()).orElseThrow();

        Assertions.assertTrue(result.getLocations().isEmpty());
    }

    private Page<EventScheduleEventProjection> findParallelSchedule(EventScheduleType type, boolean includeUnassigned) {
        return eventRepository.findEventScheduleEventsByAssignments(
                PageRequest.of(0, 10),
                LocalDate.of(2025, 7, 1),
                LocalDate.of(2025, 7, 31),
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
                INSERT INTO tb_person(name, phone_number, birthday_date, password)
                VALUES (?, ?, '1990-01-10', 'encoded-password')
                """,
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
        jdbcTemplate.update(
                """
                INSERT INTO tb_celebration_event(name_mass_or_event, event_date, event_time, mass_or_celebration)
                VALUES (?, ?, ?, TRUE)
                """,
                eventName,
                eventDate,
                LocalTime.of(19, 0)
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

    private String uniquePhoneNumber() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return "3498" + String.format("%07d", suffix);
    }
}
