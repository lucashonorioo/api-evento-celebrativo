package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.CelebrationEventScaleRequestDTO;
import com.eventoscelebrativos.dto.request.CelebrationEventWithScaleRequestDTO;
import com.eventoscelebrativos.dto.request.ParticipationResponseRequestDTO;
import com.eventoscelebrativos.model.EventParticipationResponse;
import com.eventoscelebrativos.model.Location;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.ParticipationStatus;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.repository.EventParticipationResponseRepository;
import com.eventoscelebrativos.repository.LocationRepository;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova, atraves do fluxo oficial de sincronizacao de escala (CelebrationEventService.updateEventScale),
 * as regras de preservacao/limpeza de EventParticipationResponse quando a escala de um evento e editada.
 */
@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
class EventParticipationSyncIntegrationTest {

    @Autowired
    private CelebrationEventService celebrationEventService;

    @Autowired
    private EventParticipationResponseService eventParticipationResponseService;

    @Autowired
    private EventParticipationResponseRepository eventParticipationResponseRepository;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private PersonMinistryRepository personMinistryRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldRemoveParticipationResponseWhenAllFunctionsAreRemoved() {
        Long eventId = null;
        Long locationId = null;
        Long personId = null;
        try {
            Person person = savePersonWithMinistries("Sync Remove All Person", MinistryType.READER);
            personId = person.getId();
            Location location = locationRepository.saveAndFlush(location("Sync Remove All Church"));
            locationId = location.getId();

            eventId = celebrationEventService.createEventWithScale(
                    eventRequest("Sync Remove All Event", locationId, List.of(personId), null)
            ).getEventId();
            respond(person.getId(), eventId, "DECLINED", "Viagem");
            assertTrue(findResponse(eventId, personId).isPresent());

            celebrationEventService.updateEventScale(
                    eventId, new CelebrationEventScaleRequestDTO(locationId, null, List.of(), null, null, null)
            );

            assertTrue(findResponse(eventId, personId).isEmpty());
        } finally {
            cleanupEvent(eventId);
            cleanupPerson(personId);
            cleanupLocation(locationId);
        }
    }

    @Test
    void shouldReturnToPendingWhenPersonReentersScaleAfterBeingFullyRemoved() {
        Long eventId = null;
        Long locationId = null;
        Long personId = null;
        try {
            Person person = savePersonWithMinistries("Sync Reentry Person", MinistryType.READER);
            personId = person.getId();
            Location location = locationRepository.saveAndFlush(location("Sync Reentry Church"));
            locationId = location.getId();

            eventId = celebrationEventService.createEventWithScale(
                    eventRequest("Sync Reentry Event", locationId, List.of(personId), null)
            ).getEventId();
            respond(person.getId(), eventId, "CONFIRMED", null);

            celebrationEventService.updateEventScale(
                    eventId, new CelebrationEventScaleRequestDTO(locationId, null, List.of(), null, null, null)
            );
            assertTrue(findResponse(eventId, personId).isEmpty());

            celebrationEventService.updateEventScale(
                    eventId, new CelebrationEventScaleRequestDTO(locationId, null, List.of(personId), null, null, null)
            );

            assertTrue(findResponse(eventId, personId).isEmpty(), "Ausencia de registro representa PENDING");
        } finally {
            cleanupEvent(eventId);
            cleanupPerson(personId);
            cleanupLocation(locationId);
        }
    }

    @Test
    void shouldPreserveParticipationResponseWhenFunctionIsSwappedButPersonRemains() {
        Long eventId = null;
        Long locationId = null;
        Long personId = null;
        try {
            Person person = savePersonWithMinistries("Sync Swap Person", MinistryType.READER, MinistryType.COMMENTATOR);
            personId = person.getId();
            Location location = locationRepository.saveAndFlush(location("Sync Swap Church"));
            locationId = location.getId();

            eventId = celebrationEventService.createEventWithScale(
                    eventRequest("Sync Swap Event", locationId, List.of(personId), null)
            ).getEventId();
            respond(person.getId(), eventId, "CONFIRMED", null);
            EventParticipationResponse before = findResponse(eventId, personId).orElseThrow();

            celebrationEventService.updateEventScale(
                    eventId, new CelebrationEventScaleRequestDTO(locationId, null, null, List.of(personId), null, null)
            );

            EventParticipationResponse after = findResponse(eventId, personId).orElseThrow();
            assertEquals(before.getId(), after.getId());
            assertEquals(ParticipationStatus.CONFIRMED, after.getStatus());
        } finally {
            cleanupEvent(eventId);
            cleanupPerson(personId);
            cleanupLocation(locationId);
        }
    }

    @Test
    void shouldNotAffectOtherPeopleOrOtherEventsParticipationDuringSync() {
        Long eventOneId = null;
        Long eventTwoId = null;
        Long locationId = null;
        Long removedPersonId = null;
        Long keptPersonId = null;
        try {
            Person removedPerson = savePersonWithMinistries("Sync Isolation Removed Person", MinistryType.READER);
            removedPersonId = removedPerson.getId();
            Person keptPerson = savePersonWithMinistries("Sync Isolation Kept Person", MinistryType.READER);
            keptPersonId = keptPerson.getId();
            Location location = locationRepository.saveAndFlush(location("Sync Isolation Church"));
            locationId = location.getId();

            eventOneId = celebrationEventService.createEventWithScale(
                    eventRequest("Sync Isolation Event One", locationId, List.of(removedPersonId, keptPersonId), null)
            ).getEventId();
            eventTwoId = celebrationEventService.createEventWithScale(
                    eventRequest("Sync Isolation Event Two", locationId, List.of(removedPersonId), null)
            ).getEventId();
            respond(removedPerson.getId(), eventOneId, "CONFIRMED", null);
            respond(keptPerson.getId(), eventOneId, "DECLINED", "Motivo");
            respond(removedPerson.getId(), eventTwoId, "CONFIRMED", null);

            celebrationEventService.updateEventScale(
                    eventOneId, new CelebrationEventScaleRequestDTO(locationId, null, List.of(keptPersonId), null, null, null)
            );

            assertTrue(findResponse(eventOneId, removedPersonId).isEmpty());
            assertTrue(findResponse(eventOneId, keptPersonId).isPresent(), "Outra pessoa no mesmo evento nao deve ser afetada");
            assertTrue(findResponse(eventTwoId, removedPersonId).isPresent(), "Outro evento da mesma pessoa nao deve ser afetado");
        } finally {
            cleanupEvent(eventOneId);
            cleanupEvent(eventTwoId);
            cleanupPerson(removedPersonId);
            cleanupPerson(keptPersonId);
            cleanupLocation(locationId);
        }
    }

    private void respond(Long personId, Long eventId, String status, String declineReason) {
        eventParticipationResponseService.respond(personId, eventId, new ParticipationResponseRequestDTO(status, declineReason));
    }

    private Optional<EventParticipationResponse> findResponse(Long eventId, Long personId) {
        return eventParticipationResponseRepository.findByEventIdAndPersonId(eventId, personId);
    }

    private Person savePersonWithMinistries(String name, MinistryType... ministryTypes) {
        Person person = new Person();
        person.setName(name + " " + UUID.randomUUID());
        person.setPhoneNumber(uniquePhoneNumber());
        person.setBirthdayDate(LocalDate.of(1990, 1, 10));
        person.setPassword("encoded-password");
        person = personRepository.saveAndFlush(person);
        for (MinistryType ministryType : ministryTypes) {
            personMinistryRepository.saveAndFlush(new PersonMinistry(person, ministryType));
        }
        return person;
    }

    private Location location(String name) {
        return new Location(null, name + " " + UUID.randomUUID(), "Rua Teste, 123");
    }

    private CelebrationEventWithScaleRequestDTO eventRequest(
            String name, Long locationId, List<Long> readerIds, List<Long> commentatorIds
    ) {
        CelebrationEventWithScaleRequestDTO request = new CelebrationEventWithScaleRequestDTO();
        request.setNameMassOrEvent(name + " " + UUID.randomUUID());
        request.setStartAt(LocalDateTime.of(LocalDate.now().plusDays(30), LocalTime.of(19, 0)));
        request.setEndAt(LocalDateTime.of(LocalDate.now().plusDays(30), LocalTime.of(20, 0)));
        request.setMassOrCelebration(true);
        request.setLocationId(locationId);
        request.setReaderIds(readerIds);
        request.setCommentatorIds(commentatorIds);
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
        return "3497" + String.format("%07d", suffix);
    }
}
