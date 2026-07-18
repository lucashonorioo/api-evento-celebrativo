package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.MinistryType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.person-ministry.read-source.reader=PARALLEL",
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
@AutoConfigureMockMvc
class ReaderParallelCutoverIsolatedLifecycleIntegrationTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 3, 12);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldKeepReaderLifecycleConsistentWithParallelSourceWithoutSeedAssumptions() throws Exception {
        Long readerId = null;
        Long commentatorId = null;
        try {
            readerId = createPerson("/leitores", "Isolated Reader", uniquePhoneNumber());

            assertEquals("reader", personType(readerId));
            assertEquals(1, countMinistries(readerId, MinistryType.READER));
            assertTrue(isMinistryActive(readerId, MinistryType.READER));
            assertContainsOnce(getReaders(), readerId);

            putPerson("/leitores/{id}", readerId, "Isolated Reader Updated", uniquePhoneNumber())
                    .andExpect(status().isOk());

            assertEquals(1, countMinistries(readerId, MinistryType.READER));
            assertContainsOnce(getReaders(), readerId);

            deactivateMinistry(readerId, MinistryType.READER);
            assertFalse(isMinistryActive(readerId, MinistryType.READER));
            assertFalse(containsReader(getReaders(), readerId));

            putPerson("/leitores/{id}", readerId, "Isolated Reader Reactivated", uniquePhoneNumber())
                    .andExpect(status().isOk());

            assertEquals(1, countMinistries(readerId, MinistryType.READER));
            assertTrue(isMinistryActive(readerId, MinistryType.READER));
            assertContainsOnce(getReaders(), readerId);

            commentatorId = createPerson("/comentaristas", "Isolated Commentator Reader", uniquePhoneNumber());
            addMinistry(commentatorId, MinistryType.READER);

            assertEquals("commentator", personType(commentatorId));
            assertContainsOnce(getReaders(), commentatorId);
            assertEquals(1, countMinistries(commentatorId, MinistryType.COMMENTATOR));
            assertEquals(1, countMinistries(commentatorId, MinistryType.READER));

            deletePerson("/leitores/{id}", readerId)
                    .andExpect(status().isNoContent());

            assertEquals(0, countPersonRows(readerId));
            assertEquals(0, countMinistries(readerId));
            assertFalse(containsReader(getReaders(), readerId));
            readerId = null;
        } finally {
            cleanupPerson(readerId);
            cleanupPerson(commentatorId);
        }
    }

    private Long createPerson(String path, String name, String phoneNumber) throws Exception {
        MvcResult result = mockMvc.perform(post(path)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(personPayload(name, phoneNumber)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions putPerson(
            String path,
            Long id,
            String name,
            String phoneNumber
    ) throws Exception {
        return mockMvc.perform(put(path, id)
                .with(user("admin").roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(personPayload(name, phoneNumber)));
    }

    private org.springframework.test.web.servlet.ResultActions deletePerson(String path, Long id) throws Exception {
        return mockMvc.perform(delete(path, id)
                .with(user("admin").roles("ADMIN"))
                .with(csrf()));
    }

    private List<Long> getReaders() throws Exception {
        MvcResult result = mockMvc.perform(get("/leitores").with(user("operator").roles("OPERATOR")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return StreamSupport.stream(root.spliterator(), false)
                .map(node -> node.path("id").asLong())
                .toList();
    }

    private void assertContainsOnce(List<Long> readerIds, Long readerId) {
        assertEquals(1, readerIds.stream().filter(readerId::equals).count());
    }

    private boolean containsReader(List<Long> readerIds, Long readerId) {
        return readerIds.stream().anyMatch(readerId::equals);
    }

    private String personType(Long personId) {
        return jdbcTemplate.queryForObject(
                "SELECT person_type FROM tb_person WHERE id = ?",
                String.class,
                personId
        );
    }

    private boolean isMinistryActive(Long personId, MinistryType ministryType) {
        Boolean active = jdbcTemplate.queryForObject(
                """
                SELECT active
                FROM tb_person_ministry
                WHERE person_id = ?
                  AND ministry_type = ?
                """,
                Boolean.class,
                personId,
                ministryType.name()
        );
        return Boolean.TRUE.equals(active);
    }

    private void deactivateMinistry(Long personId, MinistryType ministryType) {
        assertEquals(1, jdbcTemplate.update(
                """
                UPDATE tb_person_ministry
                SET active = FALSE,
                    updated_at = CURRENT_TIMESTAMP(6)
                WHERE person_id = ?
                  AND ministry_type = ?
                """,
                personId,
                ministryType.name()
        ));
    }

    private void addMinistry(Long personId, MinistryType ministryType) {
        assertEquals(1, jdbcTemplate.update(
                """
                INSERT INTO tb_person_ministry(person_id, ministry_type, active, created_at, updated_at)
                VALUES (?, ?, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """,
                personId,
                ministryType.name()
        ));
    }

    private int countPersonRows(Long personId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_person WHERE id = ?",
                Integer.class,
                personId
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

    private String personPayload(String name, String phoneNumber) {
        return """
                {
                  "name": "%s",
                  "phoneNumber": "%s",
                  "birthdayDate": "%s",
                  "password": "123456"
                }
                """.formatted(name, phoneNumber, BIRTHDAY);
    }

    private String uniquePhoneNumber() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return "3497" + String.format("%07d", suffix);
    }
}
