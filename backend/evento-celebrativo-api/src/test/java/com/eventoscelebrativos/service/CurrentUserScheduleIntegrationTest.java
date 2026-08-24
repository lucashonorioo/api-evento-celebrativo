package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.EventAssignment;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.repository.CelebrationEventRepository;
import com.eventoscelebrativos.repository.EventAssignmentRepository;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prova, de ponta a ponta pelo endpoint real GET /pessoas/me/escalas, que a escala retornada e
 * determinada exclusivamente pelo phoneNumber do principal autenticado: a pessoa A nunca recebe
 * eventos ou funcoes de outra pessoa, mesmo quando ambas compartilham o mesmo evento, um
 * parametro personId informado pelo cliente e ignorado, e a desativacao posterior de um
 * PersonMinistry nao apaga o historico ja registrado em EventAssignment.
 *
 * A autenticacao e aplicada por requisicao via {@link SecurityMockMvcRequestPostProcessors#user},
 * em vez de SecurityContextHolder manual, pois o filtro de seguranca desta aplicacao persiste o
 * contexto na sessao HTTP (politica padrao IF_REQUIRED); usar SecurityContextHolder diretamente
 * quando duas pessoas diferentes autenticam dentro do mesmo metodo de teste vaza a sessao mockada
 * entre chamadas e reusa a identidade da requisicao anterior.
 */
@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
@AutoConfigureMockMvc
class CurrentUserScheduleIntegrationTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private PersonMinistryRepository personMinistryRepository;

    @Autowired
    private CelebrationEventRepository celebrationEventRepository;

    @Autowired
    private EventAssignmentRepository eventAssignmentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldReturnOnlyEventsOfAuthenticatedPersonAndExcludeEventOfAnotherPerson() throws Exception {
        Long personAId = null;
        Long personBId = null;
        Long ownEventId = null;
        Long otherEventId = null;
        try {
            String phoneA = uniquePhoneNumber();
            personAId = savePersonWithRole("Isolation Person A", phoneA);
            personBId = savePersonWithRole("Isolation Person B", uniquePhoneNumber());

            CelebrationEvent ownEvent = saveEvent("Isolation Own Event", LocalDate.of(2026, 8, 10));
            ownEventId = ownEvent.getId();
            saveAssignment(ownEvent, personAId, EventAssignmentType.READER);

            CelebrationEvent otherEvent = saveEvent("Isolation Other Event", LocalDate.of(2026, 8, 11));
            otherEventId = otherEvent.getId();
            saveAssignment(otherEvent, personBId, EventAssignmentType.READER);

            mockMvc.perform(scheduleRequest(personAId, phoneA, "2026-08-01", "2026-08-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].eventId").value(ownEventId));
        } finally {
            cleanupAssignments(ownEventId);
            cleanupAssignments(otherEventId);
            cleanupPerson(personAId);
            cleanupPerson(personBId);
        }
    }

    @Test
    void shouldReturnOnlyOwnFunctionsWhenBothPeopleShareTheSameEvent() throws Exception {
        Long personAId = null;
        Long personBId = null;
        Long sharedEventId = null;
        try {
            String phoneA = uniquePhoneNumber();
            String phoneB = uniquePhoneNumber();
            personAId = savePersonWithRole("Shared Event Person A", phoneA);
            personBId = savePersonWithRole("Shared Event Person B", phoneB);

            CelebrationEvent sharedEvent = saveEvent("Isolation Shared Event", LocalDate.of(2026, 8, 12));
            sharedEventId = sharedEvent.getId();
            saveAssignment(sharedEvent, personAId, EventAssignmentType.READER);
            saveAssignment(sharedEvent, personBId, EventAssignmentType.COMMENTATOR);

            mockMvc.perform(scheduleRequest(personAId, phoneA, "2026-08-01", "2026-08-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].eventId").value(sharedEventId))
                    .andExpect(jsonPath("$.content[0].assignments[0]").value("READER"))
                    .andExpect(jsonPath("$.content[0].assignments.length()").value(1));

            mockMvc.perform(scheduleRequest(personBId, phoneB, "2026-08-01", "2026-08-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].eventId").value(sharedEventId))
                    .andExpect(jsonPath("$.content[0].assignments[0]").value("COMMENTATOR"))
                    .andExpect(jsonPath("$.content[0].assignments.length()").value(1));
        } finally {
            cleanupAssignments(sharedEventId);
            cleanupPerson(personAId);
            cleanupPerson(personBId);
        }
    }

    @Test
    void shouldRejectPersistingReaderAndCommentatorForSamePersonInSameEvent() {
        Long personId = null;
        Long eventId = null;
        try {
            String phone = uniquePhoneNumber();
            personId = savePersonWithRole("MultiFunction Person", phone);

            CelebrationEvent event = saveEvent("Isolation MultiFunction Event", LocalDate.of(2026, 8, 13));
            eventId = event.getId();
            saveAssignment(event, personId, EventAssignmentType.READER);

            Long finalPersonId = personId;
            assertThrows(DataIntegrityViolationException.class,
                    () -> saveAssignment(event, finalPersonId, EventAssignmentType.COMMENTATOR));
        } finally {
            cleanupAssignments(eventId);
            cleanupPerson(personId);
        }
    }

    @Test
    void shouldKeepScheduleHistoryAfterMinistryIsLaterDeactivated() throws Exception {
        Long personId = null;
        Long eventId = null;
        try {
            String phone = uniquePhoneNumber();
            personId = savePersonWithRole("Ministry History Person", phone);
            PersonMinistry ministry = addMinistry(personId, MinistryType.READER);

            CelebrationEvent event = saveEvent("Isolation Ministry History Event", LocalDate.of(2026, 8, 14));
            eventId = event.getId();
            saveAssignment(event, personId, EventAssignmentType.READER);

            ministry.deactivate();
            personMinistryRepository.saveAndFlush(ministry);

            mockMvc.perform(scheduleRequest(personId, phone, "2026-08-01", "2026-08-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].assignments[0]").value("READER"));
        } finally {
            cleanupAssignments(eventId);
            cleanupPerson(personId);
        }
    }

    @Test
    void shouldIgnorePersonIdParameterAndNeverSelectAnotherPerson() throws Exception {
        Long personAId = null;
        Long personBId = null;
        Long ownEventId = null;
        Long otherEventId = null;
        try {
            String phoneA = uniquePhoneNumber();
            personAId = savePersonWithRole("Ignore PersonId A", phoneA);
            personBId = savePersonWithRole("Ignore PersonId B", uniquePhoneNumber());

            CelebrationEvent ownEvent = saveEvent("Ignore PersonId Own Event", LocalDate.of(2026, 8, 15));
            ownEventId = ownEvent.getId();
            saveAssignment(ownEvent, personAId, EventAssignmentType.READER);

            CelebrationEvent otherEvent = saveEvent("Ignore PersonId Other Event", LocalDate.of(2026, 8, 16));
            otherEventId = otherEvent.getId();
            saveAssignment(otherEvent, personBId, EventAssignmentType.READER);

            mockMvc.perform(scheduleRequest(personAId, phoneA, "2026-08-01", "2026-08-31")
                            .param("personId", String.valueOf(personBId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].eventId").value(ownEventId));
        } finally {
            cleanupAssignments(ownEventId);
            cleanupAssignments(otherEventId);
            cleanupPerson(personAId);
            cleanupPerson(personBId);
        }
    }

    private MockHttpServletRequestBuilder scheduleRequest(Long personId, String phoneNumber, String startDate, String endDate) {
        java.util.Set<org.springframework.security.core.GrantedAuthority> authorities = java.util.Set.of(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_OPERATOR"));
        com.eventoscelebrativos.security.AuthenticatedUser authenticatedUser =
                new com.eventoscelebrativos.security.AuthenticatedUser(1L, personId, phoneNumber, 0L, authorities);
        return get("/pessoas/me/escalas")
                .param("startDate", startDate)
                .param("endDate", endDate)
                .with(SecurityMockMvcRequestPostProcessors.authentication(
                        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                                authenticatedUser, null, authorities)));
    }

    private Long savePersonWithRole(String name, String phoneNumber) {
        Person person = new Person(name + " " + UUID.randomUUID(), phoneNumber, BIRTHDAY);
        return personRepository.saveAndFlush(person).getId();
    }

    private PersonMinistry addMinistry(Long personId, MinistryType ministryType) {
        Person person = personRepository.findById(personId).orElseThrow();
        PersonMinistry ministry = new PersonMinistry(person, ministryType);
        ministry.setActive(true);
        return personMinistryRepository.saveAndFlush(ministry);
    }

    private CelebrationEvent saveEvent(String name, LocalDate eventDate) {
        LocalDateTime startAt = LocalDateTime.of(eventDate, LocalTime.of(19, 0));
        CelebrationEvent event = new CelebrationEvent(
                null,
                name + " " + UUID.randomUUID(),
                startAt,
                startAt.plusHours(1),
                true
        );
        return celebrationEventRepository.saveAndFlush(event);
    }

    private void saveAssignment(CelebrationEvent event, Long personId, EventAssignmentType assignmentType) {
        Person person = personRepository.findById(personId).orElseThrow();
        eventAssignmentRepository.saveAndFlush(new EventAssignment(event, person, assignmentType));
    }

    private void cleanupAssignments(Long eventId) {
        if (eventId == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM tb_event_assignment WHERE event_id = ?", eventId);
        jdbcTemplate.update("DELETE FROM tb_celebration_event WHERE id = ?", eventId);
    }

    private void cleanupPerson(Long personId) {
        if (personId == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM tb_event_assignment WHERE person_id = ?", personId);
        jdbcTemplate.update("DELETE FROM tb_person_ministry WHERE person_id = ?", personId);
        jdbcTemplate.update("DELETE FROM tb_person WHERE id = ?", personId);
    }

    private String uniquePhoneNumber() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return "3495" + String.format("%07d", suffix);
    }
}
