package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.repository.CommentatorRepository;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
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

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.person-ministry.read-source.commentator=PARALLEL",
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
@AutoConfigureMockMvc
class CommentatorParallelCutoverConsistencyIntegrationTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CommentatorRepository commentatorRepository;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private PersonMinistryRepository personMinistryRepository;

    @Test
    void shouldKeepCommentatorLifecycleConsistentWhenParallelReadIsOfficialSource() throws Exception {
        Long commentatorId = null;
        try {
            List<PersonPayload> initialCommentators = getCommentators();
            Set<Long> initialCommentatorIds = idsFrom(initialCommentators);
            String createPhoneNumber = uniquePhoneNumber();
            String updatePhoneNumber = uniquePhoneNumber();

            assertFalse(initialCommentators.stream().anyMatch(commentator ->
                    commentator.phoneNumber().equals(createPhoneNumber)));
            assertEquivalentLegacyAndParallelCommentatorSources();

            MvcResult createResult = postCommentator("Commentator Cutover Alpha", createPhoneNumber)
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("Location"))
                    .andReturn();

            PersonPayload createdCommentator = payloadFromObject(createResult);
            commentatorId = createdCommentator.id();
            Long createdCommentatorId = commentatorId;
            assertFalse(initialCommentatorIds.contains(commentatorId));
            assertEquals("Commentator Cutover Alpha", createdCommentator.name());
            assertEquals(createPhoneNumber, createdCommentator.phoneNumber());
            assertEquals(BIRTHDAY.toString(), createdCommentator.birthdayDate());
            assertJsonContract(createResult);

            assertPersonRow(commentatorId, "Commentator Cutover Alpha", createPhoneNumber, "commentator");
            MinistrySnapshot createdMinistry = assertSingleMinistry(commentatorId, MinistryType.COMMENTATOR, true);
            assertNotNull(createdMinistry.createdAt());
            assertNotNull(createdMinistry.updatedAt());
            assertEquals(1, countMinistries(commentatorId));

            int ministryRowsBeforeRead = countAllMinistries();
            List<PersonPayload> commentatorsAfterCreate = getCommentators();
            assertEquals(ministryRowsBeforeRead, countAllMinistries());
            assertContainsOnce(commentatorsAfterCreate, commentatorId);
            assertEquals(
                    idsOrderedByActiveCommentatorMinistry(),
                    commentatorsAfterCreate.stream().map(PersonPayload::id).toList()
            );
            assertEquivalentLegacyAndParallelCommentatorSources();

            MvcResult updateResult = putCommentator(commentatorId, "Commentator Cutover Beta", updatePhoneNumber)
                    .andExpect(status().isOk())
                    .andReturn();

            PersonPayload updatedCommentator = payloadFromObject(updateResult);
            assertEquals(commentatorId, updatedCommentator.id());
            assertEquals("Commentator Cutover Beta", updatedCommentator.name());
            assertEquals(updatePhoneNumber, updatedCommentator.phoneNumber());
            assertJsonContract(updateResult);

            assertPersonRow(commentatorId, "Commentator Cutover Beta", updatePhoneNumber, "commentator");
            MinistrySnapshot updatedMinistry = assertSingleMinistry(commentatorId, MinistryType.COMMENTATOR, true);
            assertEquals(createdMinistry.id(), updatedMinistry.id());
            assertEquals(createdMinistry.createdAt(), updatedMinistry.createdAt());
            assertEquals(1, countMinistries(commentatorId, MinistryType.COMMENTATOR));

            List<PersonPayload> commentatorsAfterUpdate = getCommentators();
            assertContainsOnce(commentatorsAfterUpdate, commentatorId);
            assertTrue(commentatorsAfterUpdate.stream().anyMatch(commentator ->
                    commentator.id().equals(createdCommentatorId)
                            && commentator.name().equals("Commentator Cutover Beta")
                            && commentator.phoneNumber().equals(updatePhoneNumber)));
            assertFalse(commentatorsAfterUpdate.stream().anyMatch(commentator ->
                    commentator.phoneNumber().equals(createPhoneNumber)));
            assertEquals(
                    idsOrderedByActiveCommentatorMinistry(),
                    commentatorsAfterUpdate.stream().map(PersonPayload::id).toList()
            );
            assertEquivalentLegacyAndParallelCommentatorSources();

            deactivateCommentatorMinistry(commentatorId);
            MinistrySnapshot inactiveMinistry = assertSingleMinistry(commentatorId, MinistryType.COMMENTATOR, false);
            assertTrue(legacyCommentatorIds().contains(commentatorId));
            assertFalse(activeCommentatorIds().contains(commentatorId));
            assertFalse(getCommentators().stream().anyMatch(commentator -> commentator.id().equals(createdCommentatorId)));

            Thread.sleep(25);
            putCommentator(commentatorId, "Commentator Cutover Gamma", updatePhoneNumber)
                    .andExpect(status().isOk());

            MinistrySnapshot reactivatedMinistry = assertSingleMinistry(commentatorId, MinistryType.COMMENTATOR, true);
            assertEquals(createdMinistry.id(), reactivatedMinistry.id());
            assertEquals(createdMinistry.createdAt(), reactivatedMinistry.createdAt());
            assertNotEquals(inactiveMinistry.updatedAt(), reactivatedMinistry.updatedAt());
            assertContainsOnce(getCommentators(), commentatorId);
            assertEquivalentLegacyAndParallelCommentatorSources();

            addMinistry(commentatorId, MinistryType.READER);
            putCommentator(commentatorId, "Commentator Cutover Delta", updatePhoneNumber)
                    .andExpect(status().isOk());

            assertEquals(
                    Set.of(MinistryType.COMMENTATOR, MinistryType.READER),
                    new LinkedHashSet<>(ministryTypes(commentatorId))
            );
            assertSingleMinistry(commentatorId, MinistryType.COMMENTATOR, true);
            assertContainsOnce(getCommentators(), commentatorId);
            assertEquivalentLegacyAndParallelCommentatorSources();

            deleteCommentator(commentatorId)
                    .andExpect(status().isNoContent());

            assertFalse(personRepository.existsById(commentatorId));
            assertFalse(commentatorRepository.existsById(commentatorId));
            assertEquals(0, countMinistries(commentatorId));
            assertEquals(0, countOrphanMinistries(commentatorId));
            assertFalse(getCommentators().stream().anyMatch(commentator -> commentator.id().equals(createdCommentatorId)));
            assertEquals(initialCommentatorIds, legacyCommentatorIds());
            assertEquivalentLegacyAndParallelCommentatorSources();
        } finally {
            cleanupCommentator(commentatorId);
        }
    }

    @Test
    void shouldNotCreateDuplicateCommentatorMinistryAfterConsecutiveUpdates() throws Exception {
        Long commentatorId = null;
        try {
            commentatorId = payloadFromObject(postCommentator("Commentator Sequential Update", uniquePhoneNumber())
                    .andExpect(status().isCreated())
                    .andReturn()).id();

            putCommentator(commentatorId, "Commentator Sequential Update B", uniquePhoneNumber())
                    .andExpect(status().isOk());
            putCommentator(commentatorId, "Commentator Sequential Update C", uniquePhoneNumber())
                    .andExpect(status().isOk());

            assertEquals(1, countMinistries(commentatorId, MinistryType.COMMENTATOR));
            assertContainsOnce(getCommentators(), commentatorId);
        } finally {
            cleanupCommentator(commentatorId);
        }
    }

    private org.springframework.test.web.servlet.ResultActions postCommentator(String name, String phoneNumber) throws Exception {
        return mockMvc.perform(post("/comentaristas")
                .with(user("admin").roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(commentatorPayload(name, phoneNumber)));
    }

    private org.springframework.test.web.servlet.ResultActions putCommentator(
            Long commentatorId,
            String name,
            String phoneNumber
    ) throws Exception {
        return mockMvc.perform(put("/comentaristas/{id}", commentatorId)
                .with(user("admin").roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(commentatorPayload(name, phoneNumber)));
    }

    private org.springframework.test.web.servlet.ResultActions deleteCommentator(Long commentatorId) throws Exception {
        return mockMvc.perform(delete("/comentaristas/{id}", commentatorId)
                .with(user("admin").roles("ADMIN"))
                .with(csrf()));
    }

    private List<PersonPayload> getCommentators() throws Exception {
        MvcResult result = mockMvc.perform(get("/comentaristas").with(user("operator").roles("OPERATOR")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return StreamSupport.stream(root.spliterator(), false)
                .map(this::payloadFromNode)
                .toList();
    }

    private PersonPayload payloadFromObject(MvcResult result) throws Exception {
        return payloadFromNode(objectMapper.readTree(result.getResponse().getContentAsString()));
    }

    private PersonPayload payloadFromNode(JsonNode node) {
        return new PersonPayload(
                node.path("id").asLong(),
                node.path("name").asText(),
                node.path("phoneNumber").asText(),
                node.path("birthdayDate").asText()
        );
    }

    private void assertJsonContract(MvcResult result) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(Set.of("id", "name", "phoneNumber", "birthdayDate"), fieldNames(node));
        assertTrue(node.path("id").isNumber());
        assertTrue(node.path("name").isTextual());
        assertTrue(node.path("phoneNumber").isTextual());
        assertTrue(node.path("birthdayDate").isTextual());
        assertFalse(node.has("password"));
        assertFalse(node.has("ministryType"));
    }

    private Set<String> fieldNames(JsonNode node) {
        Set<String> result = new LinkedHashSet<>();
        node.fieldNames().forEachRemaining(result::add);
        return result;
    }

    private void assertContainsOnce(List<PersonPayload> commentators, Long commentatorId) {
        assertEquals(1, commentators.stream().filter(commentator -> commentator.id().equals(commentatorId)).count());
    }

    private void assertPersonRow(Long commentatorId, String expectedName, String expectedPhoneNumber, String expectedPersonType) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM tb_person
                WHERE id = ?
                  AND name = ?
                  AND phone_number = ?
                  AND person_type = ?
                """,
                Integer.class,
                commentatorId,
                expectedName,
                expectedPhoneNumber,
                expectedPersonType
        );
        assertEquals(1, count);
        assertEquals(expectedPersonType, personType(commentatorId));
    }

    private MinistrySnapshot assertSingleMinistry(Long personId, MinistryType ministryType, boolean active) {
        List<MinistrySnapshot> rows = jdbcTemplate.query(
                """
                SELECT id, active, created_at, updated_at
                FROM tb_person_ministry
                WHERE person_id = ?
                  AND ministry_type = ?
                """,
                (rs, rowNum) -> new MinistrySnapshot(
                        rs.getLong("id"),
                        rs.getBoolean("active"),
                        toLocalDateTime(rs.getTimestamp("created_at")),
                        toLocalDateTime(rs.getTimestamp("updated_at"))
                ),
                personId,
                ministryType.name()
        );
        assertEquals(1, rows.size());
        assertEquals(active, rows.get(0).active());
        return rows.get(0);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private void deactivateCommentatorMinistry(Long personId) {
        assertEquals(1, jdbcTemplate.update(
                """
                UPDATE tb_person_ministry
                SET active = FALSE,
                    updated_at = CURRENT_TIMESTAMP(6)
                WHERE person_id = ?
                  AND ministry_type = ?
                """,
                personId,
                MinistryType.COMMENTATOR.name()
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

    private Set<Long> legacyCommentatorIds() {
        return new LinkedHashSet<>(commentatorRepository.findAll().stream()
                .map(commentator -> commentator.getId())
                .toList());
    }

    private Set<Long> activeCommentatorIds() {
        return new LinkedHashSet<>(idsOrderedByActiveCommentatorMinistry());
    }

    private List<Long> idsOrderedByActiveCommentatorMinistry() {
        return jdbcTemplate.queryForList(
                """
                SELECT p.id
                FROM tb_person_ministry pm
                INNER JOIN tb_person p ON p.id = pm.person_id
                WHERE pm.ministry_type = ?
                  AND pm.active = TRUE
                ORDER BY p.name ASC, p.id ASC
                """,
                Long.class,
                MinistryType.COMMENTATOR.name()
        );
    }

    private void assertEquivalentLegacyAndParallelCommentatorSources() {
        assertEquals(legacyCommentatorIds(), activeCommentatorIds());
    }

    private Set<Long> idsFrom(List<PersonPayload> commentators) {
        return new LinkedHashSet<>(commentators.stream().map(PersonPayload::id).toList());
    }

    private String personType(Long personId) {
        return jdbcTemplate.queryForObject(
                "SELECT person_type FROM tb_person WHERE id = ?",
                String.class,
                personId
        );
    }

    private List<MinistryType> ministryTypes(Long personId) {
        return personMinistryRepository.findAllByPersonId(personId).stream()
                .map(ministry -> ministry.getMinistryType())
                .toList();
    }

    private int countAllMinistries() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tb_person_ministry", Integer.class);
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

    private int countOrphanMinistries(Long personId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM tb_person_ministry pm
                LEFT JOIN tb_person p ON p.id = pm.person_id
                WHERE pm.person_id = ?
                  AND p.id IS NULL
                """,
                Integer.class,
                personId
        );
        return count == null ? 0 : count;
    }

    private void cleanupCommentator(Long commentatorId) {
        if (commentatorId == null || !personRepository.existsById(commentatorId)) {
            return;
        }
        jdbcTemplate.update("DELETE FROM tb_event_assignment WHERE person_id = ?", commentatorId);
        jdbcTemplate.update("DELETE FROM tb_event_person WHERE person_id = ?", commentatorId);
        jdbcTemplate.update("DELETE FROM tb_person_ministry WHERE person_id = ?", commentatorId);
        jdbcTemplate.update("DELETE FROM tb_person_role WHERE person_id = ?", commentatorId);
        jdbcTemplate.update("DELETE FROM tb_person WHERE id = ?", commentatorId);
    }

    private String commentatorPayload(String name, String phoneNumber) {
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

    private record PersonPayload(Long id, String name, String phoneNumber, String birthdayDate) {
    }

    private record MinistrySnapshot(Long id, Boolean active, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}
