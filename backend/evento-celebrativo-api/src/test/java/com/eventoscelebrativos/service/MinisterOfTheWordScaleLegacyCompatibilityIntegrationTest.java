package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.CelebrationEventWithScaleRequestDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventScaleDetailResponseDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventScaleResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.model.Location;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.repository.LocationRepository;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.MinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static com.eventoscelebrativos.support.LegacyMinistryTestFactory.personMinistry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
class MinisterOfTheWordScaleLegacyCompatibilityIntegrationTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);

    @Autowired
    private CelebrationEventService celebrationEventService;

    @Autowired
    private MinisterOfTheWordService ministerOfTheWordService;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private PersonMinistryRepository personMinistryRepository;

    @Autowired
    private MinistryRepository ministryRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldKeepLegacyMinisterOfTheWordAssociationForEventScale() {
        Long ministerId = null;
        Long locationId = null;
        Long eventId = null;
        try {
            Person minister = personRepository.saveAndFlush(ministerOfTheWord("Scale Legacy Word Minister"));
            ministerId = minister.getId();
            personMinistryRepository.saveAndFlush(personMinistry(minister, MinistryType.MINISTER_OF_THE_WORD, ministryRepository));
            Location location = locationRepository.saveAndFlush(location("Scale Legacy Church"));
            locationId = location.getId();

            CelebrationEventScaleResponseDTO createdEvent = celebrationEventService.createEventWithScale(
                    eventRequest("Scale Legacy Word Minister Mass", locationId, List.of(ministerId))
            );
            eventId = createdEvent.getEventId();

            assertNotNull(eventId);
            assertEquals(List.of(ministerId), createdEvent.getMinistersOfTheWord().stream()
                    .map(person -> person.getId())
                    .toList());

            CelebrationEventScaleDetailResponseDTO scaleDetail = celebrationEventService.findScaleByEventId(eventId);
            assertEquals(List.of(ministerId), scaleDetail.getMinistersOfTheWord().stream()
                    .map(person -> person.getId())
                    .toList());
        } finally {
            cleanupEvent(eventId);
            cleanupPerson(ministerId);
            cleanupLocation(locationId);
        }
    }

    @Test
    void shouldAcceptNonMinisterSubtypeWithAdditionalMinisterOfTheWordMinistryForEventScale() {
        Long readerId = null;
        Long locationId = null;
        Long eventId = null;
        String eventName = "Scale Accept Additional Word Minister " + UUID.randomUUID();
        try {
            Person reader = personRepository.saveAndFlush(reader("Reader With Word Minister Ministry"));
            readerId = reader.getId();
            personMinistryRepository.saveAndFlush(personMinistry(reader, MinistryType.READER, ministryRepository));
            personMinistryRepository.saveAndFlush(personMinistry(reader, MinistryType.MINISTER_OF_THE_WORD, ministryRepository));
            Location location = locationRepository.saveAndFlush(location("Scale Accept Church"));
            locationId = location.getId();

            Long savedReaderId = readerId;
            CelebrationEventScaleResponseDTO createdEvent = celebrationEventService.createEventWithScale(
                    eventRequest(eventName, locationId, List.of(savedReaderId))
            );
            eventId = createdEvent.getEventId();

            assertEquals(1, countEventsByName(eventName));
            assertEquals(List.of(savedReaderId), createdEvent.getMinistersOfTheWord().stream()
                    .map(person -> person.getId())
                    .toList());
            assertEquals(1, countMinistries(readerId, MinistryType.MINISTER_OF_THE_WORD));
        } finally {
            cleanupEvent(eventId);
            cleanupEventsByName(eventName);
            cleanupPerson(readerId);
            cleanupLocation(locationId);
        }
    }

    @Test
    void shouldRollbackMinistryRemovalWhenDeletingMinisterOfTheWordLinkedToEventFails() {
        Long ministerId = null;
        Long locationId = null;
        Long eventId = null;
        try {
            Person minister = personRepository.saveAndFlush(ministerOfTheWord("Linked Scale Word Minister"));
            ministerId = minister.getId();
            personMinistryRepository.saveAndFlush(personMinistry(minister, MinistryType.MINISTER_OF_THE_WORD, ministryRepository));
            Location location = locationRepository.saveAndFlush(location("Linked Scale Church"));
            locationId = location.getId();
            eventId = celebrationEventService.createEventWithScale(
                    eventRequest("Linked Scale Word Minister Mass", locationId, List.of(ministerId))
            ).getEventId();

            int ministriesBeforeDelete = countMinistries(ministerId);
            Long savedMinisterId = ministerId;

            assertThrows(DatabaseException.class, () -> ministerOfTheWordService.deleteMinisterOfTheWord(savedMinisterId));

            assertTrue(personRepository.existsById(ministerId));
            assertEquals(ministriesBeforeDelete, countMinistries(ministerId));
            assertEquals(1, countMinistries(ministerId, MinistryType.MINISTER_OF_THE_WORD));
        } finally {
            cleanupEvent(eventId);
            cleanupPerson(ministerId);
            cleanupLocation(locationId);
        }
    }

    private Person ministerOfTheWord(String name) {
        return person(name);
    }

    private Person reader(String name) {
        return person(name);
    }

    private Person person(String name) {
        return new Person(name, uniquePhoneNumber(), BIRTHDAY);
    }

    private Location location(String name) {
        return new Location(null, name, "Rua Teste, 123");
    }

    private CelebrationEventWithScaleRequestDTO eventRequest(
            String name,
            Long locationId,
            List<Long> ministerOfTheWordIds
    ) {
        CelebrationEventWithScaleRequestDTO request = new CelebrationEventWithScaleRequestDTO();
        request.setNameMassOrEvent(name);
        request.setStartAt(LocalDateTime.of(LocalDate.now().plusDays(15), LocalTime.of(19, 0)));
        request.setEndAt(LocalDateTime.of(LocalDate.now().plusDays(15), LocalTime.of(20, 0)));
        request.setMassOrCelebration(true);
        request.setLocationId(locationId);
        request.setMinisterOfTheWordIds(ministerOfTheWordIds);
        return request;
    }

    private int countEventsByName(String eventName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_celebration_event WHERE name_mass_or_event = ?",
                Integer.class,
                eventName
        );
        return count == null ? 0 : count;
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
        jdbcTemplate.update("DELETE FROM tb_celebration_event WHERE id = ?", eventId);
    }

    private void cleanupPerson(Long personId) {
        if (personId == null) {
            return;
        }
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
        return "3497" + String.format("%07d", suffix);
    }
}
