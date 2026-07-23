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
import java.util.Arrays;
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
    void shouldFindEucharistScaleWhenEventsExistInPeriod() {
        Page<EucharistScaleEventProjection> result = eventRepository.findEucharistScale(
                PageRequest.of(0, 10),
                LocalDate.of(2025, 7, 1),
                LocalDate.of(2025, 12, 31)
        );

        Assertions.assertEquals(3, result.getTotalElements());
        Assertions.assertEquals(0, result.getNumber());
        Assertions.assertEquals(10, result.getSize());
        Assertions.assertFalse(result.getContent().isEmpty());
    }

    @Test
    void shouldFilterEucharistScaleByPeriod() {
        Page<EucharistScaleEventProjection> result = eventRepository.findEucharistScale(
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
    void shouldPaginateEucharistScale() {
        Page<EucharistScaleEventProjection> firstPage = eventRepository.findEucharistScale(
                PageRequest.of(0, 2),
                LocalDate.of(2025, 7, 1),
                LocalDate.of(2025, 7, 31)
        );
        Page<EucharistScaleEventProjection> secondPage = eventRepository.findEucharistScale(
                PageRequest.of(1, 2),
                LocalDate.of(2025, 7, 1),
                LocalDate.of(2025, 7, 31)
        );

        Assertions.assertEquals(3, firstPage.getTotalElements());
        Assertions.assertEquals(2, firstPage.getNumberOfElements());
        Assertions.assertEquals(1, secondPage.getNumberOfElements());
    }

    @Test
    void shouldGroupMinisterNamesByEvent() {
        Page<EucharistScaleEventProjection> result = eventRepository.findEucharistScale(
                PageRequest.of(0, 10),
                LocalDate.of(2025, 7, 13),
                LocalDate.of(2025, 7, 13)
        );

        Assertions.assertEquals(1, result.getTotalElements());
        EucharistScaleEventProjection event = result.getContent().get(0);
        List<String> ministerNames = Arrays.stream(event.getMinisterNames().split(","))
                .map(String::trim)
                .toList();

        Assertions.assertTrue(event.getNameMassOrEvent().contains("Domingo"));
        Assertions.assertEquals(2, ministerNames.size());
        Assertions.assertTrue(ministerNames.contains("Mariana Ferraz"));
        Assertions.assertTrue(ministerNames.contains("Carlos Silva"));
    }

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
        Long personId = insertPerson("reader", "Reader Serving Eucharist");
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
    void shouldNotUseLegacyEventPersonToSelectParallelEucharistScaleEvents() {
        Long personId = insertPerson("eucharistic_minister", "Legacy Only Minister");
        Long eventId = insertEvent("Legacy Only Eucharist", LocalDate.of(2026, 3, 9));
        Long locationId = firstLocationId();
        jdbcTemplate.update("INSERT INTO tb_event_location(event_id, location_id) VALUES (?, ?)", eventId, locationId);
        jdbcTemplate.update("INSERT INTO tb_event_person(event_id, person_id) VALUES (?, ?)", eventId, personId);

        Page<EucharistScaleEventProjection> result = eventRepository.findEucharistScaleByAssignments(
                PageRequest.of(0, 10),
                LocalDate.of(2026, 3, 9),
                LocalDate.of(2026, 3, 9)
        );

        Assertions.assertTrue(result.isEmpty());
        Assertions.assertTrue(eventRepository.findEucharistScaleAssignmentsByEventIds(List.of(eventId)).isEmpty());
    }

    @Test
    void shouldReturnEmptyPageWhenNoEventsExistInPeriod() {
        Page<EucharistScaleEventProjection> result = eventRepository.findEucharistScale(
                PageRequest.of(0, 10),
                LocalDate.of(2030, 1, 1),
                LocalDate.of(2030, 1, 31)
        );

        Assertions.assertTrue(result.isEmpty());
        Assertions.assertEquals(0, result.getTotalElements());
    }

    @Test
    void shouldFindPriestScheduleEvents() {
        Page<EventScheduleEventProjection> result = findSchedule(EventScheduleType.PRIEST, false);

        Assertions.assertEquals(2, result.getTotalElements());
        Assertions.assertTrue(result.getContent().stream()
                .allMatch(event -> !assignments(event, EventScheduleType.PRIEST).isEmpty()));
    }

    @Test
    void shouldFindReaderScheduleEvents() {
        Page<EventScheduleEventProjection> result = findSchedule(EventScheduleType.READER, false);

        Assertions.assertEquals(3, result.getTotalElements());
        Assertions.assertTrue(result.getContent().stream()
                .allMatch(event -> !assignments(event, EventScheduleType.READER).isEmpty()));
    }

    @Test
    void shouldFindCommentatorScheduleEvents() {
        Page<EventScheduleEventProjection> result = findSchedule(EventScheduleType.COMMENTATOR, false);

        Assertions.assertEquals(3, result.getTotalElements());
        Assertions.assertTrue(result.getContent().stream()
                .allMatch(event -> !assignments(event, EventScheduleType.COMMENTATOR).isEmpty()));
    }

    @Test
    void shouldFindMinisterOfTheWordScheduleEvents() {
        Page<EventScheduleEventProjection> result = findSchedule(EventScheduleType.MINISTER_OF_THE_WORD, false);

        Assertions.assertEquals(3, result.getTotalElements());
        Assertions.assertTrue(result.getContent().stream()
                .allMatch(event -> !assignments(event, EventScheduleType.MINISTER_OF_THE_WORD).isEmpty()));
    }

    @Test
    void shouldFindEucharisticMinisterScheduleEvents() {
        Page<EventScheduleEventProjection> result = findSchedule(EventScheduleType.EUCHARISTIC_MINISTER, false);

        Assertions.assertEquals(3, result.getTotalElements());
        Assertions.assertTrue(result.getContent().stream()
                .allMatch(event -> !assignments(event, EventScheduleType.EUCHARISTIC_MINISTER).isEmpty()));
    }

    @Test
    void shouldFilterScheduleEventsByPeriod() {
        Page<EventScheduleEventProjection> result = eventRepository.findEventScheduleEvents(
                PageRequest.of(0, 10),
                LocalDate.of(2025, 7, 12),
                LocalDate.of(2025, 7, 13),
                EventScheduleType.READER.getPersonType(),
                false
        );

        Assertions.assertEquals(2, result.getTotalElements());
        Assertions.assertTrue(result.getContent().stream()
                .allMatch(event -> !event.getEventDate().isBefore(LocalDate.of(2025, 7, 12))
                        && !event.getEventDate().isAfter(LocalDate.of(2025, 7, 13))));
    }

    @Test
    void shouldPaginateScheduleByEvent() {
        Page<EventScheduleEventProjection> firstPage = eventRepository.findEventScheduleEvents(
                PageRequest.of(0, 2),
                LocalDate.of(2025, 7, 1),
                LocalDate.of(2025, 7, 31),
                EventScheduleType.EUCHARISTIC_MINISTER.getPersonType(),
                false
        );
        Page<EventScheduleEventProjection> secondPage = eventRepository.findEventScheduleEvents(
                PageRequest.of(1, 2),
                LocalDate.of(2025, 7, 1),
                LocalDate.of(2025, 7, 31),
                EventScheduleType.EUCHARISTIC_MINISTER.getPersonType(),
                false
        );

        Assertions.assertEquals(3, firstPage.getTotalElements());
        Assertions.assertEquals(2, firstPage.getNumberOfElements());
        Assertions.assertEquals(1, secondPage.getNumberOfElements());
    }

    @Test
    void shouldGroupSeveralAssignmentsInSameEvent() {
        Page<EventScheduleEventProjection> result = eventRepository.findEventScheduleEvents(
                PageRequest.of(0, 10),
                LocalDate.of(2025, 7, 13),
                LocalDate.of(2025, 7, 13),
                EventScheduleType.EUCHARISTIC_MINISTER.getPersonType(),
                false
        );

        List<EventScheduleAssignmentProjection> assignments = eventRepository.findEventScheduleAssignments(
                result.getContent().stream().map(EventScheduleEventProjection::getEventId).toList(),
                EventScheduleType.EUCHARISTIC_MINISTER.getPersonType()
        );

        Assertions.assertEquals(1, result.getTotalElements());
        Assertions.assertEquals(2, assignments.size());
        Assertions.assertEquals(1L, assignments.get(0).getEventId());
        Assertions.assertEquals(1L, assignments.get(1).getEventId());
    }

    @Test
    void shouldOrderScheduleEventsByDateTimeAndId() {
        CelebrationEvent first = new CelebrationEvent(null, "Missa Teste 1", LocalDate.of(2026, 1, 1), LocalTime.of(8, 0), true);
        CelebrationEvent second = new CelebrationEvent(null, "Missa Teste 2", LocalDate.of(2026, 1, 1), LocalTime.of(8, 0), true);
        entityManager.persist(first);
        entityManager.persist(second);
        entityManager.flush();

        Page<EventScheduleEventProjection> result = eventRepository.findEventScheduleEvents(
                PageRequest.of(0, 10),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 1),
                EventScheduleType.READER.getPersonType(),
                true
        );

        Assertions.assertEquals(List.of(first.getId(), second.getId()),
                result.getContent().stream().map(EventScheduleEventProjection::getEventId).toList());
    }

    @Test
    void shouldNotDuplicateScheduleEvents() {
        Page<EventScheduleEventProjection> result = findSchedule(EventScheduleType.EUCHARISTIC_MINISTER, false);

        List<Long> ids = result.getContent().stream().map(EventScheduleEventProjection::getEventId).toList();
        Assertions.assertEquals(ids.size(), ids.stream().distinct().count());
    }

    @Test
    void shouldReturnOnlyAssignedEventsWhenIncludeUnassignedIsFalse() {
        Page<EventScheduleEventProjection> result = findSchedule(EventScheduleType.PRIEST, false);

        Assertions.assertEquals(2, result.getTotalElements());
    }

    @Test
    void shouldReturnAllEventsWhenIncludeUnassignedIsTrue() {
        Page<EventScheduleEventProjection> result = findSchedule(EventScheduleType.PRIEST, true);
        List<EventScheduleAssignmentProjection> assignments = eventRepository.findEventScheduleAssignments(
                result.getContent().stream().map(EventScheduleEventProjection::getEventId).toList(),
                EventScheduleType.PRIEST.getPersonType()
        );

        Assertions.assertEquals(3, result.getTotalElements());
        Assertions.assertTrue(result.getContent().stream()
                .anyMatch(event -> assignments.stream().noneMatch(assignment -> assignment.getEventId().equals(event.getEventId()))));
    }

    @Test
    void shouldReturnEmptySchedulePageWhenNoEventsExistInPeriod() {
        Page<EventScheduleEventProjection> result = eventRepository.findEventScheduleEvents(
                PageRequest.of(0, 10),
                LocalDate.of(2030, 1, 1),
                LocalDate.of(2030, 1, 31),
                EventScheduleType.READER.getPersonType(),
                false
        );

        Assertions.assertTrue(result.isEmpty());
        Assertions.assertEquals(0, result.getTotalElements());
    }

    @Test
    void shouldCountScheduleTotalElementsByEventsAndNotPeople() {
        Page<EventScheduleEventProjection> result = findSchedule(EventScheduleType.EUCHARISTIC_MINISTER, false);
        List<EventScheduleAssignmentProjection> assignments = eventRepository.findEventScheduleAssignments(
                result.getContent().stream().map(EventScheduleEventProjection::getEventId).toList(),
                EventScheduleType.EUCHARISTIC_MINISTER.getPersonType()
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
    void shouldFindEventWithPeopleWithoutDuplicatingEvent() {
        CelebrationEvent event = eventRepository.findByIdWithPeople(1L).orElseThrow();

        Assertions.assertEquals(1L, event.getId());
        Assertions.assertEquals(7, event.getPeople().size());
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

    @Test
    void shouldFindEventWithoutPeople() {
        CelebrationEvent event = new CelebrationEvent(null, "Evento sem pessoas", LocalDate.of(2026, 2, 2), LocalTime.of(9, 0), true);
        entityManager.persist(event);
        entityManager.flush();
        entityManager.clear();

        CelebrationEvent result = eventRepository.findByIdWithPeople(event.getId()).orElseThrow();

        Assertions.assertTrue(result.getPeople().isEmpty());
    }

    private Page<EventScheduleEventProjection> findSchedule(EventScheduleType type, boolean includeUnassigned) {
        return eventRepository.findEventScheduleEvents(
                PageRequest.of(0, 10),
                LocalDate.of(2025, 7, 1),
                LocalDate.of(2025, 7, 31),
                type.getPersonType(),
                includeUnassigned
        );
    }

    private List<EventScheduleAssignmentProjection> assignments(EventScheduleEventProjection event, EventScheduleType type) {
        return eventRepository.findEventScheduleAssignments(List.of(event.getEventId()), type.getPersonType());
    }

    private Long insertPerson(String personType, String name) {
        String phoneNumber = uniquePhoneNumber();
        jdbcTemplate.update(
                """
                INSERT INTO tb_person(name, phone_number, birthday_date, password, person_type)
                VALUES (?, ?, '1990-01-10', 'encoded-password', ?)
                """,
                name + " " + UUID.randomUUID(),
                phoneNumber,
                personType
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
