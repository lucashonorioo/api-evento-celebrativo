package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.repository.PriestRepository;
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
        "app.person-ministry.read-source.priest=PARALLEL",
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
@AutoConfigureMockMvc
class PriestParallelCutoverConsistencyIntegrationTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PriestRepository priestRepository;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private PersonMinistryRepository personMinistryRepository;

    @Test
    void shouldKeepPriestLifecycleConsistentWhenParallelReadIsOfficialSource() throws Exception {
        Long priestId = null;
        try {
            List<PersonPayload> initialPriests = getPriests();
            Set<Long> initialPriestIds = idsFrom(initialPriests);
            String createPhoneNumber = uniquePhoneNumber();
            String updatePhoneNumber = uniquePhoneNumber();

            assertFalse(initialPriests.stream().anyMatch(priest -> priest.phoneNumber().equals(createPhoneNumber)));
            assertEquivalentLegacyAndParallelPriestSources();

            MvcResult createResult = postPriest("Priest Cutover Alpha", createPhoneNumber)
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("Location"))
                    .andReturn();

            PersonPayload createdPriest = payloadFromObject(createResult);
            priestId = createdPriest.id();
            Long createdPriestId = priestId;
            assertFalse(initialPriestIds.contains(priestId));
            assertEquals("Priest Cutover Alpha", createdPriest.name());
            assertEquals(createPhoneNumber, createdPriest.phoneNumber());
            assertEquals(BIRTHDAY.toString(), createdPriest.birthdayDate());
            assertJsonContract(createResult);

            assertPersonRow(priestId, "Priest Cutover Alpha", createPhoneNumber, "priest");
            MinistrySnapshot createdMinistry = assertSingleMinistry(priestId, MinistryType.PRIEST, true);
            assertNotNull(createdMinistry.createdAt());
            assertNotNull(createdMinistry.updatedAt());
            assertEquals(1, countMinistries(priestId));

            int ministryRowsBeforeRead = countAllMinistries();
            List<PersonPayload> priestsAfterCreate = getPriests();
            assertEquals(ministryRowsBeforeRead, countAllMinistries());
            assertContainsOnce(priestsAfterCreate, priestId);
            assertEquals(idsOrderedByActivePriestMinistry(), priestsAfterCreate.stream().map(PersonPayload::id).toList());
            assertEquivalentLegacyAndParallelPriestSources();

            MvcResult updateResult = putPriest(priestId, "Priest Cutover Beta", updatePhoneNumber)
                    .andExpect(status().isOk())
                    .andReturn();

            PersonPayload updatedPriest = payloadFromObject(updateResult);
            assertEquals(priestId, updatedPriest.id());
            assertEquals("Priest Cutover Beta", updatedPriest.name());
            assertEquals(updatePhoneNumber, updatedPriest.phoneNumber());
            assertJsonContract(updateResult);

            assertPersonRow(priestId, "Priest Cutover Beta", updatePhoneNumber, "priest");
            MinistrySnapshot updatedMinistry = assertSingleMinistry(priestId, MinistryType.PRIEST, true);
            assertEquals(createdMinistry.id(), updatedMinistry.id());
            assertEquals(createdMinistry.createdAt(), updatedMinistry.createdAt());
            assertEquals(1, countMinistries(priestId, MinistryType.PRIEST));

            List<PersonPayload> priestsAfterUpdate = getPriests();
            assertContainsOnce(priestsAfterUpdate, priestId);
            assertTrue(priestsAfterUpdate.stream().anyMatch(priest ->
                    priest.id().equals(createdPriestId)
                            && priest.name().equals("Priest Cutover Beta")
                            && priest.phoneNumber().equals(updatePhoneNumber)));
            assertFalse(priestsAfterUpdate.stream().anyMatch(priest -> priest.phoneNumber().equals(createPhoneNumber)));
            assertEquals(idsOrderedByActivePriestMinistry(), priestsAfterUpdate.stream().map(PersonPayload::id).toList());
            assertEquivalentLegacyAndParallelPriestSources();

            deactivatePriestMinistry(priestId);
            MinistrySnapshot inactiveMinistry = assertSingleMinistry(priestId, MinistryType.PRIEST, false);
            assertTrue(legacyPriestIds().contains(priestId));
            assertFalse(activePriestIds().contains(priestId));
            assertFalse(getPriests().stream().anyMatch(priest -> priest.id().equals(createdPriestId)));

            Thread.sleep(25);
            putPriest(priestId, "Priest Cutover Gamma", updatePhoneNumber)
                    .andExpect(status().isOk());

            MinistrySnapshot reactivatedMinistry = assertSingleMinistry(priestId, MinistryType.PRIEST, true);
            assertEquals(createdMinistry.id(), reactivatedMinistry.id());
            assertEquals(createdMinistry.createdAt(), reactivatedMinistry.createdAt());
            assertNotEquals(inactiveMinistry.updatedAt(), reactivatedMinistry.updatedAt());
            assertContainsOnce(getPriests(), priestId);
            assertEquivalentLegacyAndParallelPriestSources();

            addMinistry(priestId, MinistryType.READER);
            putPriest(priestId, "Priest Cutover Delta", updatePhoneNumber)
                    .andExpect(status().isOk());

            assertEquals(
                    Set.of(MinistryType.PRIEST, MinistryType.READER),
                    new LinkedHashSet<>(ministryTypes(priestId))
            );
            assertSingleMinistry(priestId, MinistryType.PRIEST, true);
            assertContainsOnce(getPriests(), priestId);
            assertEquivalentLegacyAndParallelPriestSources();

            deletePriest(priestId)
                    .andExpect(status().isNoContent());

            assertFalse(personRepository.existsById(priestId));
            assertFalse(priestRepository.existsById(priestId));
            assertEquals(0, countMinistries(priestId));
            assertEquals(0, countOrphanMinistries(priestId));
            assertFalse(getPriests().stream().anyMatch(priest -> priest.id().equals(createdPriestId)));
            assertEquals(initialPriestIds, legacyPriestIds());
            assertEquivalentLegacyAndParallelPriestSources();
        } finally {
            cleanupPriest(priestId);
        }
    }

    @Test
    void shouldNotCreateDuplicatePriestMinistryAfterConsecutiveUpdates() throws Exception {
        Long priestId = null;
        try {
            priestId = payloadFromObject(postPriest("Priest Sequential Update", uniquePhoneNumber())
                    .andExpect(status().isCreated())
                    .andReturn()).id();

            putPriest(priestId, "Priest Sequential Update B", uniquePhoneNumber())
                    .andExpect(status().isOk());
            putPriest(priestId, "Priest Sequential Update C", uniquePhoneNumber())
                    .andExpect(status().isOk());

            assertEquals(1, countMinistries(priestId, MinistryType.PRIEST));
            assertContainsOnce(getPriests(), priestId);
        } finally {
            cleanupPriest(priestId);
        }
    }

    private org.springframework.test.web.servlet.ResultActions postPriest(String name, String phoneNumber) throws Exception {
        return mockMvc.perform(post("/padres")
                .with(user("admin").roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(priestPayload(name, phoneNumber)));
    }

    private org.springframework.test.web.servlet.ResultActions putPriest(Long priestId, String name, String phoneNumber) throws Exception {
        return mockMvc.perform(put("/padres/{id}", priestId)
                .with(user("admin").roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(priestPayload(name, phoneNumber)));
    }

    private org.springframework.test.web.servlet.ResultActions deletePriest(Long priestId) throws Exception {
        return mockMvc.perform(delete("/padres/{id}", priestId)
                .with(user("admin").roles("ADMIN"))
                .with(csrf()));
    }

    private List<PersonPayload> getPriests() throws Exception {
        MvcResult result = mockMvc.perform(get("/padres").with(user("operator").roles("OPERATOR")))
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

    private void assertContainsOnce(List<PersonPayload> priests, Long priestId) {
        assertEquals(1, priests.stream().filter(priest -> priest.id().equals(priestId)).count());
    }

    private void assertPersonRow(Long priestId, String expectedName, String expectedPhoneNumber, String expectedPersonType) {
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
                priestId,
                expectedName,
                expectedPhoneNumber,
                expectedPersonType
        );
        assertEquals(1, count);
        assertEquals(expectedPersonType, personType(priestId));
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

    private void deactivatePriestMinistry(Long personId) {
        assertEquals(1, jdbcTemplate.update(
                """
                UPDATE tb_person_ministry
                SET active = FALSE,
                    updated_at = CURRENT_TIMESTAMP(6)
                WHERE person_id = ?
                  AND ministry_type = ?
                """,
                personId,
                MinistryType.PRIEST.name()
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

    private Set<Long> legacyPriestIds() {
        return new LinkedHashSet<>(priestRepository.findAll().stream()
                .map(priest -> priest.getId())
                .toList());
    }

    private Set<Long> activePriestIds() {
        return new LinkedHashSet<>(idsOrderedByActivePriestMinistry());
    }

    private List<Long> idsOrderedByActivePriestMinistry() {
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
                MinistryType.PRIEST.name()
        );
    }

    private void assertEquivalentLegacyAndParallelPriestSources() {
        assertEquals(legacyPriestIds(), activePriestIds());
    }

    private Set<Long> idsFrom(List<PersonPayload> priests) {
        return new LinkedHashSet<>(priests.stream().map(PersonPayload::id).toList());
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

    private void cleanupPriest(Long priestId) {
        if (priestId == null || !personRepository.existsById(priestId)) {
            return;
        }
        jdbcTemplate.update("DELETE FROM tb_event_assignment WHERE person_id = ?", priestId);
        jdbcTemplate.update("DELETE FROM tb_event_person WHERE person_id = ?", priestId);
        jdbcTemplate.update("DELETE FROM tb_person_ministry WHERE person_id = ?", priestId);
        jdbcTemplate.update("DELETE FROM tb_person_role WHERE person_id = ?", priestId);
        jdbcTemplate.update("DELETE FROM tb_person WHERE id = ?", priestId);
    }

    private String priestPayload(String name, String phoneNumber) {
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
