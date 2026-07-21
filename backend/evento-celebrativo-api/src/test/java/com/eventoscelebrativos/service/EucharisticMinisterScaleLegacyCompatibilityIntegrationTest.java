package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.CelebrationEventScaleRequestDTO;
import com.eventoscelebrativos.dto.request.CelebrationEventWithScaleRequestDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventScaleDetailResponseDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventScaleResponseDTO;
import com.eventoscelebrativos.dto.response.EucharisticMinisterResponseDTO;
import com.eventoscelebrativos.dto.response.EventScheduleQueryResponseDTO;
import com.eventoscelebrativos.dto.response.EucharistScaleEventResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.model.EucharisticMinister;
import com.eventoscelebrativos.model.EventScheduleType;
import com.eventoscelebrativos.model.Location;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.model.Reader;
import com.eventoscelebrativos.repository.LocationRepository;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "app.person-ministry.read-source.eucharistic-minister=PARALLEL",
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
class EucharisticMinisterScaleLegacyCompatibilityIntegrationTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);

    @Autowired
    private CelebrationEventService celebrationEventService;

    @Autowired
    private EucharisticMinisterService eucharisticMinisterService;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private PersonMinistryRepository personMinistryRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldKeepLegacyEucharisticMinisterAssociationForEventScaleQueriesAndUpdate() {
        Long firstMinisterId = null;
        Long secondMinisterId = null;
        Long locationId = null;
        Long eventId = null;
        LocalDate eventDate = LocalDate.now().plusDays(15);
        String eventName = "Scale Legacy Eucharistic Minister Mass " + UUID.randomUUID();
        try {
            Person firstMinister = personRepository.saveAndFlush(eucharisticMinister("Scale Legacy Eucharistic Minister A"));
            firstMinisterId = firstMinister.getId();
            personMinistryRepository.saveAndFlush(new PersonMinistry(firstMinister, MinistryType.EUCHARISTIC_MINISTER));
            Person secondMinister = personRepository.saveAndFlush(eucharisticMinister("Scale Legacy Eucharistic Minister B"));
            secondMinisterId = secondMinister.getId();
            personMinistryRepository.saveAndFlush(new PersonMinistry(secondMinister, MinistryType.EUCHARISTIC_MINISTER));
            Location location = locationRepository.saveAndFlush(location("Scale Legacy Eucharistic Church"));
            locationId = location.getId();

            CelebrationEventScaleResponseDTO createdEvent = celebrationEventService.createEventWithScale(
                    eventRequest(eventName, eventDate, locationId, List.of(firstMinisterId))
            );
            eventId = createdEvent.getEventId();

            assertNotNull(eventId);
            assertEquals(List.of(firstMinisterId), createdEvent.getEucharisticMinisters().stream()
                    .map(person -> person.getId())
                    .toList());

            CelebrationEventScaleDetailResponseDTO firstDetail = celebrationEventService.findScaleByEventId(eventId);
            assertEquals(List.of(firstMinisterId), firstDetail.getEucharisticMinisters().stream()
                    .map(person -> person.getId())
                    .toList());

            assertEucharistScaleContains(eventName, eventDate, "Scale Legacy Eucharistic Minister A");
            assertEventScheduleContains(eventId, firstMinisterId);

            CelebrationEventScaleResponseDTO updatedEvent = celebrationEventService.updateEventScale(
                    eventId,
                    new CelebrationEventScaleRequestDTO(locationId, null, null, null, null, List.of(secondMinisterId))
            );

            assertEquals(List.of(secondMinisterId), updatedEvent.getEucharisticMinisters().stream()
                    .map(person -> person.getId())
                    .toList());

            CelebrationEventScaleDetailResponseDTO secondDetail = celebrationEventService.findScaleByEventId(eventId);
            assertEquals(List.of(secondMinisterId), secondDetail.getEucharisticMinisters().stream()
                    .map(person -> person.getId())
                    .toList());
            assertEucharistScaleContains(eventName, eventDate, "Scale Legacy Eucharistic Minister B");
            assertEventScheduleContains(eventId, secondMinisterId);
        } finally {
            cleanupEvent(eventId);
            cleanupPerson(firstMinisterId);
            cleanupPerson(secondMinisterId);
            cleanupLocation(locationId);
        }
    }

    @Test
    void shouldRejectNonEucharisticMinisterSubtypeWithAdditionalEucharisticMinisterMinistryForEventScale() {
        Long readerId = null;
        Long locationId = null;
        String eventName = "Scale Reject Additional Eucharistic Minister " + UUID.randomUUID();
        try {
            Person reader = personRepository.saveAndFlush(reader("Reader With Eucharistic Minister Ministry"));
            readerId = reader.getId();
            personMinistryRepository.saveAndFlush(new PersonMinistry(reader, MinistryType.READER));
            personMinistryRepository.saveAndFlush(new PersonMinistry(reader, MinistryType.EUCHARISTIC_MINISTER));
            Location location = locationRepository.saveAndFlush(location("Scale Reject Eucharistic Church"));
            locationId = location.getId();

            Long savedReaderId = readerId;
            Long savedLocationId = locationId;
            assertTrue(eucharisticMinisterService.findAllEucharisticMinisters().stream()
                    .map(EucharisticMinisterResponseDTO::getId)
                    .toList()
                    .contains(savedReaderId));
            assertThrows(BusinessException.class, () ->
                    celebrationEventService.createEventWithScale(eventRequest(
                            eventName,
                            LocalDate.now().plusDays(16),
                            savedLocationId,
                            List.of(savedReaderId)
                    )));

            assertEquals(0, countEventsByName(eventName));
            assertEquals("reader", personType(readerId));
            assertEquals(1, countMinistries(readerId, MinistryType.EUCHARISTIC_MINISTER));
        } finally {
            cleanupEventsByName(eventName);
            cleanupPerson(readerId);
            cleanupLocation(locationId);
        }
    }

    @Test
    void shouldRollbackMinistryRemovalWhenDeletingEucharisticMinisterLinkedToEventFails() {
        Long ministerId = null;
        Long locationId = null;
        Long eventId = null;
        try {
            Person minister = personRepository.saveAndFlush(eucharisticMinister("Linked Scale Eucharistic Minister"));
            ministerId = minister.getId();
            personMinistryRepository.saveAndFlush(new PersonMinistry(minister, MinistryType.EUCHARISTIC_MINISTER));
            Location location = locationRepository.saveAndFlush(location("Linked Scale Eucharistic Church"));
            locationId = location.getId();
            eventId = celebrationEventService.createEventWithScale(
                    eventRequest("Linked Scale Eucharistic Minister Mass", LocalDate.now().plusDays(17), locationId, List.of(ministerId))
            ).getEventId();

            int ministriesBeforeDelete = countMinistries(ministerId);
            Long savedMinisterId = ministerId;

            assertThrows(DatabaseException.class, () -> eucharisticMinisterService.deleteEucharisticMinisterById(savedMinisterId));

            assertTrue(personRepository.existsById(ministerId));
            assertEquals(ministriesBeforeDelete, countMinistries(ministerId));
            assertEquals(1, countMinistries(ministerId, MinistryType.EUCHARISTIC_MINISTER));
            assertEquals(1, countEventPeople(eventId, ministerId));
        } finally {
            cleanupEvent(eventId);
            cleanupPerson(ministerId);
            cleanupLocation(locationId);
        }
    }

    private EucharisticMinister eucharisticMinister(String name) {
        EucharisticMinister minister = new EucharisticMinister();
        populatePerson(minister, name);
        return minister;
    }

    private Reader reader(String name) {
        Reader reader = new Reader();
        populatePerson(reader, name);
        return reader;
    }

    private void populatePerson(Person person, String name) {
        person.setName(name);
        person.setPhoneNumber(uniquePhoneNumber());
        person.setBirthdayDate(BIRTHDAY);
        person.setPassword("encoded-password");
    }

    private Location location(String name) {
        return new Location(null, name, "Rua Teste, 123");
    }

    private CelebrationEventWithScaleRequestDTO eventRequest(
            String name,
            LocalDate eventDate,
            Long locationId,
            List<Long> eucharisticMinisterIds
    ) {
        CelebrationEventWithScaleRequestDTO request = new CelebrationEventWithScaleRequestDTO();
        request.setNameMassOrEvent(name);
        request.setEventDate(eventDate);
        request.setEventTime(LocalTime.of(19, 0));
        request.setMassOrCelebration(true);
        request.setLocationId(locationId);
        request.setEucharisticMinisterIds(eucharisticMinisterIds);
        return request;
    }

    private void assertEucharistScaleContains(String eventName, LocalDate eventDate, String ministerName) {
        Page<EucharistScaleEventResponseDTO> page = celebrationEventService.findEucharistScale(
                PageRequest.of(0, 20),
                eventDate,
                eventDate
        );

        EucharistScaleEventResponseDTO event = page.getContent().stream()
                .filter(item -> item.getNameMassOrEvent().equals(eventName))
                .findFirst()
                .orElseThrow();
        assertTrue(event.getNameMinisters().contains(ministerName));
    }

    private void assertEventScheduleContains(Long eventId, Long ministerId) {
        Page<EventScheduleQueryResponseDTO> page = celebrationEventService.findEventSchedules(
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(30),
                EventScheduleType.EUCHARISTIC_MINISTER,
                0,
                50,
                false
        );

        EventScheduleQueryResponseDTO event = page.getContent().stream()
                .filter(item -> item.getEventId().equals(eventId))
                .findFirst()
                .orElseThrow();
        assertTrue(event.getAssignments().stream().anyMatch(assignment -> assignment.getPersonId().equals(ministerId)));
    }

    private int countEventsByName(String eventName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_celebration_event WHERE name_mass_or_event = ?",
                Integer.class,
                eventName
        );
        return count == null ? 0 : count;
    }

    private String personType(Long personId) {
        return jdbcTemplate.queryForObject(
                "SELECT person_type FROM tb_person WHERE id = ?",
                String.class,
                personId
        );
    }

    private int countMinistries(Long personId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_person_ministry WHERE person_id = ?",
                Integer.class,
                personId
        );
        return count == null ? 0 : count;
    }

    private int countMinistries(Long personId, MinistryType ministryType) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM tb_person_ministry
                WHERE person_id = ?
                  AND ministry_type = ?
                """,
                Integer.class,
                personId,
                ministryType.name()
        );
        return count == null ? 0 : count;
    }

    private int countEventPeople(Long eventId, Long personId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM tb_event_person
                WHERE event_id = ?
                  AND person_id = ?
                """,
                Integer.class,
                eventId,
                personId
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
        return "3496" + String.format("%07d", suffix);
    }
}
