package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.CelebrationEventScaleRequestDTO;
import com.eventoscelebrativos.dto.request.CelebrationEventWithScaleRequestDTO;
import com.eventoscelebrativos.model.Location;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.repository.EventParticipationResponseRepository;
import com.eventoscelebrativos.repository.LocationRepository;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.MinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static com.eventoscelebrativos.support.LegacyMinistryTestFactory.personMinistry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doThrow;

/**
 * Prova que, quando a limpeza de EventParticipationResponse falha durante a sincronizacao oficial
 * de escala, as alteracoes de EventAssignment feitas na mesma transacao tambem sao revertidas.
 */
@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
class EventParticipationSyncRollbackIntegrationTest {

    @Autowired
    private CelebrationEventService celebrationEventService;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private PersonMinistryRepository personMinistryRepository;

    @Autowired
    private MinistryRepository ministryRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private EventParticipationResponseRepository eventParticipationResponseRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private EventParticipationResponseService eventParticipationResponseService;

    @Test
    void shouldRollbackAssignmentChangesWhenParticipationCleanupFailsDuringSync() {
        Long eventId = null;
        Long locationId = null;
        Long oldPersonId = null;
        Long newPersonId = null;
        try {
            Person oldPerson = savePersonWithMinistry("Rollback Cleanup Old Person", MinistryType.READER);
            oldPersonId = oldPerson.getId();
            Person newPerson = savePersonWithMinistry("Rollback Cleanup New Person", MinistryType.READER);
            newPersonId = newPerson.getId();
            Location location = locationRepository.saveAndFlush(location("Rollback Cleanup Church"));
            locationId = location.getId();

            eventId = celebrationEventService.createEventWithScale(
                    eventRequest("Rollback Cleanup Event", locationId, List.of(oldPersonId))
            ).getEventId();

            RuntimeException failure = new IllegalStateException("participation cleanup failed");
            Long finalEventId = eventId;
            doThrow(failure).when(eventParticipationResponseService).retainOnlyForPersonIds(any(), anyCollection());

            Long savedEventId = eventId;
            Long savedLocationId = locationId;
            Long savedNewPersonId = newPersonId;
            RuntimeException result = assertThrows(RuntimeException.class, () ->
                    celebrationEventService.updateEventScale(
                            savedEventId,
                            new CelebrationEventScaleRequestDTO(savedLocationId, null, List.of(savedNewPersonId), null, null, null)
                    ));

            assertSame(failure, result);
            assertEquals(
                    List.of(oldPersonId),
                    jdbcTemplate.queryForList(
                            "SELECT person_id FROM tb_event_assignment WHERE event_id = ?", Long.class, finalEventId
                    )
            );
        } finally {
            cleanupEvent(eventId);
            cleanupPerson(oldPersonId);
            cleanupPerson(newPersonId);
            cleanupLocation(locationId);
        }
    }

    private Person savePersonWithMinistry(String name, MinistryType ministryType) {
        Person person = new Person(name + " " + UUID.randomUUID(), uniquePhoneNumber(), LocalDate.of(1990, 1, 10));
        person = personRepository.saveAndFlush(person);
        personMinistryRepository.saveAndFlush(personMinistry(person, ministryType, ministryRepository));
        return person;
    }

    private Location location(String name) {
        return new Location(null, name + " " + UUID.randomUUID(), "Rua Teste, 123");
    }

    private CelebrationEventWithScaleRequestDTO eventRequest(String name, Long locationId, List<Long> readerIds) {
        CelebrationEventWithScaleRequestDTO request = new CelebrationEventWithScaleRequestDTO();
        request.setNameMassOrEvent(name + " " + UUID.randomUUID());
        request.setStartAt(LocalDateTime.of(LocalDate.now().plusDays(30), LocalTime.of(19, 0)));
        request.setEndAt(LocalDateTime.of(LocalDate.now().plusDays(30), LocalTime.of(20, 0)));
        request.setMassOrCelebration(true);
        request.setLocationId(locationId);
        request.setReaderIds(readerIds);
        return request;
    }

    private void cleanupEvent(Long eventId) {
        if (eventId == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM tb_event_participation_response WHERE event_id = ?", eventId);
        jdbcTemplate.update("DELETE FROM tb_event_assignment WHERE event_id = ?", eventId);
        jdbcTemplate.update("DELETE FROM tb_event_location WHERE event_id = ?", eventId);
        jdbcTemplate.update("DELETE FROM tb_celebration_event WHERE id = ?", eventId);
    }

    private void cleanupPerson(Long personId) {
        if (personId == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM tb_event_participation_response WHERE person_id = ?", personId);
        jdbcTemplate.update("DELETE FROM tb_event_assignment WHERE person_id = ?", personId);
        jdbcTemplate.update("DELETE FROM tb_person_ministry WHERE person_id = ?", personId);        jdbcTemplate.update("DELETE FROM tb_person WHERE id = ?", personId);
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
        return "3498" + String.format("%07d", suffix);
    }
}
