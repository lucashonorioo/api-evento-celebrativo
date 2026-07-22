package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.CelebrationEventScaleRequestDTO;
import com.eventoscelebrativos.dto.request.CelebrationEventWithScaleRequestDTO;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.Location;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Priest;
import com.eventoscelebrativos.model.Reader;
import com.eventoscelebrativos.repository.LocationRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
class EventAssignmentWriteThroughRollbackIntegrationTest {

    @Autowired
    private CelebrationEventService celebrationEventService;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private EventAssignmentCompatibilityService eventAssignmentCompatibilityService;

    @Test
    void shouldRollbackEventCreationWhenAssignmentWriteThroughFails() {
        Long locationId = null;
        Long priestId = null;
        String eventName = "Assignment Rollback Create " + UUID.randomUUID();
        RuntimeException failure = new IllegalStateException("assignment write-through failed");
        doThrow(failure)
                .when(eventAssignmentCompatibilityService)
                .synchronizeAssignments(any(), anyCollection());
        try {
            Priest priest = savePriest("Assignment Rollback Create Priest");
            priestId = priest.getId();
            Location location = locationRepository.saveAndFlush(location("Assignment Rollback Create Church"));
            locationId = location.getId();

            Long savedLocationId = locationId;
            Long savedPriestId = priestId;
            RuntimeException result = assertThrows(RuntimeException.class, () ->
                    celebrationEventService.createEventWithScale(eventRequest(eventName, savedLocationId, savedPriestId)));

            assertSame(failure, result);
            assertEquals(0, countEventsByName(eventName));
            assertEquals(0, countRows("tb_event_person", "person_id", priestId));
            assertEquals(0, countRows("tb_event_assignment", "person_id", priestId));
        } finally {
            cleanupEventsByName(eventName);
            cleanupPerson(priestId);
            cleanupLocation(locationId);
        }
    }

    @Test
    void shouldRollbackLegacyScaleUpdateWhenAssignmentWriteThroughFails() {
        Long eventId = null;
        Long locationId = null;
        Long oldReaderId = null;
        Long newReaderId = null;
        try {
            Reader oldReader = saveReader("Assignment Rollback Update Old Reader");
            oldReaderId = oldReader.getId();
            Reader newReader = saveReader("Assignment Rollback Update New Reader");
            newReaderId = newReader.getId();
            Location location = locationRepository.saveAndFlush(location("Assignment Rollback Update Church"));
            locationId = location.getId();
            eventId = celebrationEventService.createEventWithScale(
                    eventRequest("Assignment Rollback Update " + UUID.randomUUID(), locationId, null, List.of(oldReaderId))
            ).getEventId();
            insertAssignment(eventId, oldReaderId, EventAssignmentType.READER);
            reset(eventAssignmentCompatibilityService);
            RuntimeException failure = new IllegalStateException("assignment update failed");
            doThrow(failure)
                    .when(eventAssignmentCompatibilityService)
                    .synchronizeAssignments(any(), anyCollection());

            Long savedEventId = eventId;
            Long savedLocationId = locationId;
            Long savedNewReaderId = newReaderId;
            RuntimeException result = assertThrows(RuntimeException.class, () ->
                    celebrationEventService.updateEventScale(
                            savedEventId,
                            new CelebrationEventScaleRequestDTO(savedLocationId, null, List.of(savedNewReaderId), null, null, null)
                    ));

            assertSame(failure, result);
            assertEquals(Set.of(oldReaderId), Set.copyOf(eventPersonIds(eventId)));
            assertEquals(Set.of(oldReaderId), Set.copyOf(eventAssignmentPersonIds(eventId)));
            assertEquals(1, countRows("tb_event_assignment", "person_id", oldReaderId));
            assertEquals(0, countRows("tb_event_assignment", "person_id", newReaderId));
        } finally {
            cleanupEvent(eventId);
            cleanupPerson(oldReaderId);
            cleanupPerson(newReaderId);
            cleanupLocation(locationId);
        }
    }

    @Test
    void shouldPreserveEventStateWhenAssignmentDeleteFailsBeforeEventDeletion() {
        Long eventId = null;
        Long locationId = null;
        Long priestId = null;
        try {
            Priest priest = savePriest("Assignment Rollback Delete Priest");
            priestId = priest.getId();
            Location location = locationRepository.saveAndFlush(location("Assignment Rollback Delete Church"));
            locationId = location.getId();
            eventId = celebrationEventService.createEventWithScale(
                    eventRequest("Assignment Rollback Delete " + UUID.randomUUID(), locationId, priestId)
            ).getEventId();
            insertAssignment(eventId, priestId, EventAssignmentType.PRIEST);
            reset(eventAssignmentCompatibilityService);
            RuntimeException failure = new IllegalStateException("assignment delete failed");
            doThrow(failure).when(eventAssignmentCompatibilityService).deleteAllForEvent(eventId);

            Long savedEventId = eventId;
            RuntimeException result = assertThrows(RuntimeException.class, () ->
                    celebrationEventService.deleteEventById(savedEventId));

            assertSame(failure, result);
            assertEquals(1, countRows("tb_celebration_event", "id", eventId));
            assertEquals(1, countRows("tb_event_person", "event_id", eventId));
            assertEquals(1, countRows("tb_event_assignment", "event_id", eventId));
        } finally {
            cleanupEvent(eventId);
            cleanupPerson(priestId);
            cleanupLocation(locationId);
        }
    }

    private Priest savePriest(String name) {
        Priest priest = new Priest();
        populatePerson(priest, name);
        return (Priest) personRepository.saveAndFlush(priest);
    }

    private Reader saveReader(String name) {
        Reader reader = new Reader();
        populatePerson(reader, name);
        return (Reader) personRepository.saveAndFlush(reader);
    }

    private void populatePerson(Person person, String name) {
        person.setName(name + " " + UUID.randomUUID());
        person.setPhoneNumber(uniquePhoneNumber());
        person.setBirthdayDate(LocalDate.of(1990, 1, 10));
        person.setPassword("encoded-password");
    }

    private Location location(String name) {
        return new Location(null, name + " " + UUID.randomUUID(), "Rua Teste, 123");
    }

    private CelebrationEventWithScaleRequestDTO eventRequest(String name, Long locationId, Long priestId) {
        return eventRequest(name, locationId, priestId, null);
    }

    private CelebrationEventWithScaleRequestDTO eventRequest(String name, Long locationId, Long priestId, List<Long> readerIds) {
        CelebrationEventWithScaleRequestDTO request = new CelebrationEventWithScaleRequestDTO();
        request.setNameMassOrEvent(name);
        request.setEventDate(LocalDate.now().plusDays(30));
        request.setEventTime(LocalTime.of(19, 0));
        request.setMassOrCelebration(true);
        request.setLocationId(locationId);
        request.setPriestId(priestId);
        request.setReaderIds(readerIds);
        return request;
    }

    private void insertAssignment(Long eventId, Long personId, EventAssignmentType assignmentType) {
        jdbcTemplate.update(
                """
                INSERT INTO tb_event_assignment(event_id, person_id, assignment_type, created_at, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                eventId,
                personId,
                assignmentType.name()
        );
    }

    private List<Long> eventPersonIds(Long eventId) {
        return jdbcTemplate.queryForList(
                "SELECT person_id FROM tb_event_person WHERE event_id = ? ORDER BY person_id",
                Long.class,
                eventId
        );
    }

    private List<Long> eventAssignmentPersonIds(Long eventId) {
        return jdbcTemplate.queryForList(
                "SELECT person_id FROM tb_event_assignment WHERE event_id = ? ORDER BY person_id",
                Long.class,
                eventId
        );
    }

    private int countEventsByName(String eventName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_celebration_event WHERE name_mass_or_event = ?",
                Integer.class,
                eventName
        );
        return count == null ? 0 : count;
    }

    private int countRows(String table, String column, Long id) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
                Integer.class,
                id
        );
        return count == null ? 0 : count;
    }

    private void cleanupEventsByName(String eventName) {
        jdbcTemplate.queryForList(
                "SELECT id FROM tb_celebration_event WHERE name_mass_or_event = ?",
                Long.class,
                eventName
        ).forEach(this::cleanupEvent);
    }

    private void cleanupEvent(Long eventId) {
        if (eventId == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM tb_event_assignment WHERE event_id = ?", eventId);
        jdbcTemplate.update("DELETE FROM tb_event_location WHERE event_id = ?", eventId);
        jdbcTemplate.update("DELETE FROM tb_event_person WHERE event_id = ?", eventId);
        jdbcTemplate.update("DELETE FROM tb_celebration_event WHERE id = ?", eventId);
    }

    private void cleanupPerson(Long personId) {
        if (personId == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM tb_event_assignment WHERE person_id = ?", personId);
        jdbcTemplate.update("DELETE FROM tb_event_person WHERE person_id = ?", personId);
        jdbcTemplate.update("DELETE FROM tb_person_ministry WHERE person_id = ?", personId);
        jdbcTemplate.update("DELETE FROM tb_person_role WHERE person_id = ?", personId);
        jdbcTemplate.update("DELETE FROM tb_person WHERE id = ?", personId);
    }

    private void cleanupLocation(Long locationId) {
        if (locationId == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM tb_event_location WHERE location_id = ?", locationId);
        jdbcTemplate.update("DELETE FROM tb_location WHERE id = ?", locationId);
    }

    private String uniquePhoneNumber() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return "3499" + String.format("%07d", suffix);
    }
}
