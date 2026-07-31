package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.CelebrationEventRequestDTO;
import com.eventoscelebrativos.dto.request.CelebrationEventWithScaleRequestDTO;
import com.eventoscelebrativos.dto.request.ParticipationResponseRequestDTO;
import com.eventoscelebrativos.dto.request.PersonUnavailabilityRequestDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventScaleResponseDTO;
import com.eventoscelebrativos.dto.response.PersonUnavailabilityResponseDTO;
import com.eventoscelebrativos.dto.response.ScheduleUnavailabilityConflictResponseDTO;
import com.eventoscelebrativos.model.Location;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.ParticipationStatus;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.repository.LocationRepository;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova, de ponta a ponta e com contexto Spring real (H2), que os conflitos entre EventAssignment
 * e PersonUnavailability sao inteiramente derivados: nenhum estado e persistido, o conflito aparece
 * assim que os dados o justificam e desaparece automaticamente assim que deixam de existir, sem
 * qualquer mutacao manual de "resolucao".
 */
@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
class ScheduleUnavailabilityConflictIntegrationTest {

    @Autowired
    private CelebrationEventService celebrationEventService;

    @Autowired
    private PersonUnavailabilityService personUnavailabilityService;

    @Autowired
    private ScheduleUnavailabilityConflictService scheduleUnavailabilityConflictService;

    @Autowired
    private EventParticipationResponseService eventParticipationResponseService;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private PersonMinistryRepository personMinistryRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldShowConflictAfterCreatingFutureUnavailabilityOverAssignedPersonAndHideItAfterDeletion() {
        Long eventId = null;
        Long locationId = null;
        Long personId = null;
        Long unavailabilityId = null;
        try {
            Person reader = savePersonWithMinistries("Conflict Round Trip Reader", MinistryType.READER);
            personId = reader.getId();
            Location location = locationRepository.saveAndFlush(location("Conflict Round Trip Church"));
            locationId = location.getId();

            LocalDateTime eventStartAt = LocalDateTime.of(LocalDate.now().plusDays(30), LocalTime.of(19, 0));
            LocalDateTime eventEndAt = eventStartAt.plusHours(1);
            CelebrationEventScaleResponseDTO scaleResponse = celebrationEventService.createEventWithScale(
                    eventRequest("Conflict Round Trip Event", locationId, eventStartAt, eventEndAt, List.of(reader.getId())));
            eventId = scaleResponse.getEventId();

            assertTrue(scheduleUnavailabilityConflictService.findByEventId(eventId).isEmpty(),
                    "Sem indisponibilidade cadastrada, nao deve haver conflito");

            LocalDateTime unavailabilityStartAt = eventStartAt.plusMinutes(30);
            LocalDateTime unavailabilityEndAt = eventEndAt.plusHours(1);
            PersonUnavailabilityResponseDTO unavailability = personUnavailabilityService.create(
                    reader.getPhoneNumber(), new PersonUnavailabilityRequestDTO(unavailabilityStartAt, unavailabilityEndAt, null));
            unavailabilityId = unavailability.getId();

            List<ScheduleUnavailabilityConflictResponseDTO> conflictsByEvent =
                    scheduleUnavailabilityConflictService.findByEventId(eventId);
            assertEquals(1, conflictsByEvent.size());
            ScheduleUnavailabilityConflictResponseDTO conflict = conflictsByEvent.get(0);
            assertEquals(eventId, conflict.getEventId());
            assertEquals(reader.getId(), conflict.getPersonId());
            assertEquals("READER", conflict.getAssignmentType());
            assertEquals(1, conflict.getUnavailabilities().size());
            assertEquals(unavailabilityId, conflict.getUnavailabilities().get(0).getId());

            Page<ScheduleUnavailabilityConflictResponseDTO> conflictsByRange = scheduleUnavailabilityConflictService.findByRange(
                    eventStartAt.toLocalDate().atStartOfDay(), eventStartAt.toLocalDate().plusDays(1).atStartOfDay(), 0, 10);
            assertEquals(1, conflictsByRange.getTotalElements());

            personUnavailabilityService.delete(reader.getPhoneNumber(), unavailabilityId);
            unavailabilityId = null;

            assertTrue(scheduleUnavailabilityConflictService.findByEventId(eventId).isEmpty(),
                    "Apos a exclusao da indisponibilidade, o conflito derivado deve desaparecer automaticamente");
        } finally {
            cleanupUnavailability(unavailabilityId);
            cleanupEvent(eventId);
            cleanupPerson(personId);
            cleanupLocation(locationId);
        }
    }

    @Test
    void shouldMakeConflictAppearWhenAdminChangesEventTimeOverExistingUnavailability() {
        Long eventId = null;
        Long locationId = null;
        Long personId = null;
        Long unavailabilityId = null;
        try {
            Person reader = savePersonWithMinistries("Conflict Time Change Reader", MinistryType.READER);
            personId = reader.getId();
            Location location = locationRepository.saveAndFlush(location("Conflict Time Change Church"));
            locationId = location.getId();

            LocalDateTime eventStartAt = LocalDateTime.of(LocalDate.now().plusDays(40), LocalTime.of(19, 0));
            LocalDateTime eventEndAt = eventStartAt.plusHours(1);
            CelebrationEventScaleResponseDTO scaleResponse = celebrationEventService.createEventWithScale(
                    eventRequest("Conflict Time Change Event", locationId, eventStartAt, eventEndAt, List.of(reader.getId())));
            eventId = scaleResponse.getEventId();

            // Indisponibilidade adjacente ao evento original: nao conflita ainda.
            PersonUnavailabilityResponseDTO unavailability = personUnavailabilityService.create(
                    reader.getPhoneNumber(), new PersonUnavailabilityRequestDTO(eventEndAt, eventEndAt.plusHours(1), null));
            unavailabilityId = unavailability.getId();

            assertTrue(scheduleUnavailabilityConflictService.findByEventId(eventId).isEmpty(),
                    "Indisponibilidade adjacente nao deve gerar conflito");

            LocalDateTime extendedEndAt = eventEndAt.plusMinutes(30);
            celebrationEventService.updateEvent(eventId, new CelebrationEventRequestDTO(
                    scaleResponse.getNameMassOrEvent(), eventStartAt, extendedEndAt, true));

            List<ScheduleUnavailabilityConflictResponseDTO> conflicts = scheduleUnavailabilityConflictService.findByEventId(eventId);
            assertEquals(1, conflicts.size(),
                    "Apos estender o endAt do evento sobre a indisponibilidade existente, o conflito deve aparecer");
            assertEquals(reader.getId(), conflicts.get(0).getPersonId());
        } finally {
            cleanupUnavailability(unavailabilityId);
            cleanupEvent(eventId);
            cleanupPerson(personId);
            cleanupLocation(locationId);
        }
    }

    @Test
    void shouldAllowUpdatingUnavailabilityToConflictWithFutureAssignedEventPreservingAssignmentAndParticipation() {
        Long eventId = null;
        Long locationId = null;
        Long personId = null;
        Long unavailabilityId = null;
        try {
            Person reader = savePersonWithMinistries("Update Conflict Reader", MinistryType.READER);
            personId = reader.getId();
            Location location = locationRepository.saveAndFlush(location("Update Conflict Church"));
            locationId = location.getId();

            LocalDateTime eventStartAt = LocalDateTime.of(LocalDate.now().plusDays(50), LocalTime.of(19, 0));
            LocalDateTime eventEndAt = eventStartAt.plusHours(1);
            CelebrationEventScaleResponseDTO scaleResponse = celebrationEventService.createEventWithScale(
                    eventRequest("Update Conflict Event", locationId, eventStartAt, eventEndAt, List.of(reader.getId())));
            eventId = scaleResponse.getEventId();

            eventParticipationResponseService.respond(
                    reader.getPhoneNumber(), eventId, new ParticipationResponseRequestDTO("CONFIRMED", null));

            // Indisponibilidade inicial fora do intervalo do evento: ainda nao conflita.
            PersonUnavailabilityResponseDTO unavailability = personUnavailabilityService.create(
                    reader.getPhoneNumber(),
                    new PersonUnavailabilityRequestDTO(eventEndAt.plusDays(1), eventEndAt.plusDays(2), null));
            unavailabilityId = unavailability.getId();

            assertTrue(scheduleUnavailabilityConflictService.findByEventId(eventId).isEmpty());

            // Atualizacao passa a conflitar com o evento futuro (ainda nao iniciado): deve ser aceita.
            PersonUnavailabilityResponseDTO updated = personUnavailabilityService.update(
                    reader.getPhoneNumber(), unavailabilityId,
                    new PersonUnavailabilityRequestDTO(eventStartAt.plusMinutes(30), eventEndAt.plusHours(1), null));
            assertEquals(eventStartAt.plusMinutes(30), updated.getStartAt());

            List<ScheduleUnavailabilityConflictResponseDTO> conflicts =
                    scheduleUnavailabilityConflictService.findByEventId(eventId);
            assertEquals(1, conflicts.size());
            assertEquals(reader.getId(), conflicts.get(0).getPersonId());
            assertEquals("READER", conflicts.get(0).getAssignmentType(), "Assignment deve ser preservado apos a atualizacao");

            Map<Long, ParticipationResponseSnapshot> participation =
                    eventParticipationResponseService.findByPersonIdAndEventIds(reader.getId(), List.of(eventId));
            assertTrue(participation.containsKey(eventId), "Participacao deve ser preservada apos o conflito derivado surgir");
            assertEquals(ParticipationStatus.CONFIRMED, participation.get(eventId).status());
        } finally {
            cleanupUnavailability(unavailabilityId);
            cleanupEvent(eventId);
            cleanupPerson(personId);
            cleanupLocation(locationId);
        }
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
            String name, Long locationId, LocalDateTime startAt, LocalDateTime endAt, List<Long> readerIds
    ) {
        CelebrationEventWithScaleRequestDTO request = new CelebrationEventWithScaleRequestDTO();
        request.setNameMassOrEvent(name + " " + UUID.randomUUID());
        request.setStartAt(startAt);
        request.setEndAt(endAt);
        request.setMassOrCelebration(true);
        request.setLocationId(locationId);
        request.setReaderIds(readerIds);
        return request;
    }

    private void cleanupUnavailability(Long unavailabilityId) {
        if (unavailabilityId == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM tb_person_unavailability WHERE id = ?", unavailabilityId);
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
        jdbcTemplate.update("DELETE FROM tb_person_unavailability WHERE person_id = ?", personId);
        jdbcTemplate.update("DELETE FROM tb_event_assignment WHERE person_id = ?", personId);
        jdbcTemplate.update("DELETE FROM tb_person_ministry WHERE person_id = ?", personId);
        jdbcTemplate.update("DELETE FROM tb_person WHERE id = ?", personId);
    }

    private void cleanupLocation(Long locationId) {
        if (locationId == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM tb_location WHERE id = ?", locationId);
    }

    private String uniquePhoneNumber() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return "3499" + String.format("%07d", suffix);
    }
}
