package com.eventoscelebrativos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.eventoscelebrativos.dto.request.CommentatorRequestDTO;
import com.eventoscelebrativos.dto.request.CommentatorUpdateRequestDTO;
import com.eventoscelebrativos.dto.request.ReaderRequestDTO;
import com.eventoscelebrativos.dto.request.ReaderUpdateRequestDTO;
import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.EventAssignment;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.repository.CelebrationEventRepository;
import com.eventoscelebrativos.repository.EventAssignmentRepository;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prova, de ponta a ponta pelos endpoints reais, que Person + PersonMinistry sao a fonte oficial
 * de escrita dos cinco CRUDs ministeriais: multiplos ministerios da mesma pessoa sao preservados
 * em update/delete, subtipo legado divergente nao bloqueia find/update, e a exclusao ministerial
 * remove apenas o vinculo solicitado - nunca a Person - respeitando conflito com EventAssignment
 * apenas quando do mesmo tipo.
 */
@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
@AutoConfigureMockMvc
@WithMockUser(roles = "ADMIN")
class PersonMinistryOfficialWriteIntegrationTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private PersonMinistryRepository personMinistryRepository;

    @Autowired
    private EventAssignmentRepository eventAssignmentRepository;

    @Autowired
    private CelebrationEventRepository celebrationEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldUpdateSharedPersonDataAndPreserveOtherMinistryFromAnotherEndpoint() throws Exception {
        Long personId = null;
        try {
            personId = createReader("Shared Person Reader");
            addExtraMinistry(personId, MinistryType.COMMENTATOR);

            String updatedName = "Shared Person Reader Updated";
            mockMvc.perform(put("/comentaristas/{id}", personId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(commentatorUpdatePayload(updatedName)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(personId))
                    .andExpect(jsonPath("$.name").value(updatedName));

            mockMvc.perform(get("/leitores/{id}", personId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(personId))
                    .andExpect(jsonPath("$.name").value(updatedName));
        } finally {
            cleanupPerson(personId);
        }
    }

    @Test
    void shouldPreservePersonAndOtherMinistryWhenDeletingOneMinistry() throws Exception {
        Long personId = null;
        try {
            personId = createReader("Multi Ministry Reader");
            addExtraMinistry(personId, MinistryType.COMMENTATOR);

            mockMvc.perform(delete("/leitores/{id}", personId))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/leitores/{id}", personId))
                    .andExpect(status().isNotFound());

            mockMvc.perform(get("/comentaristas/{id}", personId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(personId));

            assertTrue(personRepository.existsById(personId));
        } finally {
            cleanupPerson(personId);
        }
    }

    @Test
    void shouldAcceptDivergentLegacySubtypeOnFindAndUpdate() throws Exception {
        Long personId = null;
        try {
            personId = createCommentator("Divergent Subtype Person");
            addExtraMinistry(personId, MinistryType.READER);

            mockMvc.perform(get("/leitores/{id}", personId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(personId));

            String updatedName = "Divergent Subtype Person Updated";
            mockMvc.perform(put("/leitores/{id}", personId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(readerUpdatePayload(updatedName)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value(updatedName));

            mockMvc.perform(get("/comentaristas/{id}", personId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value(updatedName));
        } finally {
            cleanupPerson(personId);
        }
    }

    @Test
    void shouldBlockMinistryRemovalWhenEventAssignmentOfSameTypeExists() throws Exception {
        Long personId = null;
        Long eventId = null;
        try {
            personId = createReader("Assigned Reader");
            CelebrationEvent event = saveEvent("Assigned Reader Mass");
            eventId = event.getId();
            saveAssignment(event, personId, EventAssignmentType.READER);

            mockMvc.perform(delete("/leitores/{id}", personId))
                    .andExpect(status().isConflict());

            assertTrue(personMinistryRepository.findByPersonIdAndMinistryType(personId, MinistryType.READER)
                    .orElseThrow().getActive());
        } finally {
            cleanupEvent(eventId);
            cleanupPerson(personId);
        }
    }

    @Test
    void shouldAllowMinistryRemovalWhenEventAssignmentIsOfDifferentType() throws Exception {
        Long personId = null;
        Long eventId = null;
        try {
            personId = createReader("Cross Type Reader");
            CelebrationEvent event = saveEvent("Cross Type Reader Mass");
            eventId = event.getId();
            saveAssignment(event, personId, EventAssignmentType.COMMENTATOR);

            mockMvc.perform(delete("/leitores/{id}", personId))
                    .andExpect(status().isNoContent());

            assertTrue(personMinistryRepository.findByPersonIdAndMinistryType(personId, MinistryType.READER)
                    .map(pm -> !pm.getActive())
                    .orElse(false));
        } finally {
            cleanupEvent(eventId);
            cleanupPerson(personId);
        }
    }

    private Long createReader(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/leitores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(readerPayload(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private Long createCommentator(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/comentaristas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentatorPayload(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void addExtraMinistry(Long personId, MinistryType ministryType) {
        var person = personRepository.findById(personId).orElseThrow();
        personMinistryRepository.saveAndFlush(new PersonMinistry(person, ministryType));
    }

    private String readerPayload(String name) throws Exception {
        return objectMapper.writeValueAsString(
                new ReaderRequestDTO(name, uniquePhoneNumber(), BIRTHDAY, "123456"));
    }

    private String commentatorPayload(String name) throws Exception {
        return objectMapper.writeValueAsString(
                new CommentatorRequestDTO(name, uniquePhoneNumber(), BIRTHDAY, "123456"));
    }

    private String readerUpdatePayload(String name) throws Exception {
        return objectMapper.writeValueAsString(
                new ReaderUpdateRequestDTO(name, uniquePhoneNumber(), BIRTHDAY));
    }

    private String commentatorUpdatePayload(String name) throws Exception {
        return objectMapper.writeValueAsString(
                new CommentatorUpdateRequestDTO(name, uniquePhoneNumber(), BIRTHDAY));
    }

    private CelebrationEvent saveEvent(String name) {
        LocalDateTime startAt = LocalDateTime.of(LocalDate.now().plusDays(30), LocalTime.of(19, 0));
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
        var person = personRepository.findById(personId).orElseThrow();
        eventAssignmentRepository.saveAndFlush(new EventAssignment(event, person, assignmentType));
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
        jdbcTemplate.update("DELETE FROM tb_person_ministry WHERE person_id = ?", personId);
        jdbcTemplate.update(
                "DELETE FROM tb_user_account_role WHERE user_account_id IN (SELECT id FROM tb_user_account WHERE person_id = ?)",
                personId);
        jdbcTemplate.update("DELETE FROM tb_user_account WHERE person_id = ?", personId);
        jdbcTemplate.update("DELETE FROM tb_person WHERE id = ?", personId);
    }

    private String uniquePhoneNumber() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return "3495" + String.format("%07d", suffix);
    }
}
