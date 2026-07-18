package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.repository.ReaderRepository;
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
        "app.person-ministry.read-source.reader=PARALLEL",
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
@AutoConfigureMockMvc
class ReaderParallelCutoverConsistencyIntegrationTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ReaderRepository readerRepository;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private PersonMinistryRepository personMinistryRepository;

    @Test
    void shouldKeepReaderLifecycleConsistentWhenParallelReadIsOfficialSource() throws Exception {
        Long readerId = null;
        try {
        List<PersonPayload> initialReaders = getReaders();
        Set<Long> initialReaderIds = idsFrom(initialReaders);
        String createPhoneNumber = uniquePhoneNumber();
        String updatePhoneNumber = uniquePhoneNumber();

        assertFalse(initialReaders.stream().anyMatch(reader -> reader.phoneNumber().equals(createPhoneNumber)));
        assertEquivalentLegacyAndParallelReaderSources();

        MvcResult createResult = postReader("Reader Cutover Alpha", createPhoneNumber)
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn();

        PersonPayload createdReader = payloadFromObject(createResult);
        readerId = createdReader.id();
        Long createdReaderId = readerId;
        assertFalse(initialReaderIds.contains(readerId));
        assertEquals("Reader Cutover Alpha", createdReader.name());
        assertEquals(createPhoneNumber, createdReader.phoneNumber());
        assertEquals(BIRTHDAY.toString(), createdReader.birthdayDate());
        assertJsonContract(createResult);

        assertPersonRow(readerId, "Reader Cutover Alpha", createPhoneNumber, "reader");
        MinistrySnapshot createdMinistry = assertSingleMinistry(readerId, MinistryType.READER, true);
        assertNotNull(createdMinistry.createdAt());
        assertNotNull(createdMinistry.updatedAt());
        assertEquals(1, countMinistries(readerId));

        int ministryRowsBeforeRead = countAllMinistries();
        List<PersonPayload> readersAfterCreate = getReaders();
        assertEquals(ministryRowsBeforeRead, countAllMinistries());
        assertContainsOnce(readersAfterCreate, readerId);
        assertEquals(idsOrderedByActiveReaderMinistry(), readersAfterCreate.stream().map(PersonPayload::id).toList());
        assertEquivalentLegacyAndParallelReaderSources();

        MvcResult updateResult = putReader(readerId, "Reader Cutover Beta", updatePhoneNumber)
                .andExpect(status().isOk())
                .andReturn();

        PersonPayload updatedReader = payloadFromObject(updateResult);
        assertEquals(readerId, updatedReader.id());
        assertEquals("Reader Cutover Beta", updatedReader.name());
        assertEquals(updatePhoneNumber, updatedReader.phoneNumber());
        assertJsonContract(updateResult);

        assertPersonRow(readerId, "Reader Cutover Beta", updatePhoneNumber, "reader");
        MinistrySnapshot updatedMinistry = assertSingleMinistry(readerId, MinistryType.READER, true);
        assertEquals(createdMinistry.id(), updatedMinistry.id());
        assertEquals(createdMinistry.createdAt(), updatedMinistry.createdAt());
        assertEquals(1, countMinistries(readerId, MinistryType.READER));

        List<PersonPayload> readersAfterUpdate = getReaders();
        assertContainsOnce(readersAfterUpdate, readerId);
        assertTrue(readersAfterUpdate.stream().anyMatch(reader ->
                reader.id().equals(createdReaderId)
                        && reader.name().equals("Reader Cutover Beta")
                        && reader.phoneNumber().equals(updatePhoneNumber)));
        assertFalse(readersAfterUpdate.stream().anyMatch(reader -> reader.phoneNumber().equals(createPhoneNumber)));
        assertEquals(idsOrderedByActiveReaderMinistry(), readersAfterUpdate.stream().map(PersonPayload::id).toList());
        assertEquivalentLegacyAndParallelReaderSources();

        deactivateReaderMinistry(readerId);
        MinistrySnapshot inactiveMinistry = assertSingleMinistry(readerId, MinistryType.READER, false);
        assertTrue(legacyReaderIds().contains(readerId));
        assertFalse(activeReaderIds().contains(readerId));
        assertFalse(getReaders().stream().anyMatch(reader -> reader.id().equals(createdReaderId)));

        Thread.sleep(25);
        putReader(readerId, "Reader Cutover Gamma", updatePhoneNumber)
                .andExpect(status().isOk());

        MinistrySnapshot reactivatedMinistry = assertSingleMinistry(readerId, MinistryType.READER, true);
        assertEquals(createdMinistry.id(), reactivatedMinistry.id());
        assertEquals(createdMinistry.createdAt(), reactivatedMinistry.createdAt());
        assertNotEquals(inactiveMinistry.updatedAt(), reactivatedMinistry.updatedAt());
        assertContainsOnce(getReaders(), readerId);
        assertEquivalentLegacyAndParallelReaderSources();

        addMinistry(readerId, MinistryType.COMMENTATOR);
        putReader(readerId, "Reader Cutover Delta", updatePhoneNumber)
                .andExpect(status().isOk());

        assertEquals(
                Set.of(MinistryType.READER, MinistryType.COMMENTATOR),
                new LinkedHashSet<>(ministryTypes(readerId))
        );
        assertSingleMinistry(readerId, MinistryType.READER, true);
        assertContainsOnce(getReaders(), readerId);
        assertEquivalentLegacyAndParallelReaderSources();

        deleteReader(readerId)
                .andExpect(status().isNoContent());

        assertFalse(personRepository.existsById(readerId));
        assertFalse(readerRepository.existsById(readerId));
        assertEquals(0, countMinistries(readerId));
        assertEquals(0, countOrphanMinistries(readerId));
        assertFalse(getReaders().stream().anyMatch(reader -> reader.id().equals(createdReaderId)));
        assertEquals(initialReaderIds, legacyReaderIds());
        assertEquivalentLegacyAndParallelReaderSources();
        } finally {
            cleanupReader(readerId);
        }
    }

    @Test
    void shouldNotCreateDuplicateReaderMinistryAfterConsecutiveUpdates() throws Exception {
        Long readerId = null;
        try {
            readerId = payloadFromObject(postReader("Reader Sequential Update", uniquePhoneNumber())
                    .andExpect(status().isCreated())
                    .andReturn()).id();

            putReader(readerId, "Reader Sequential Update B", uniquePhoneNumber())
                    .andExpect(status().isOk());
            putReader(readerId, "Reader Sequential Update C", uniquePhoneNumber())
                    .andExpect(status().isOk());

            assertEquals(1, countMinistries(readerId, MinistryType.READER));
            assertContainsOnce(getReaders(), readerId);
        } finally {
            cleanupReader(readerId);
        }
    }

    private org.springframework.test.web.servlet.ResultActions postReader(String name, String phoneNumber) throws Exception {
        return mockMvc.perform(post("/leitores")
                .with(user("admin").roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(readerPayload(name, phoneNumber)));
    }

    private org.springframework.test.web.servlet.ResultActions putReader(Long readerId, String name, String phoneNumber) throws Exception {
        return mockMvc.perform(put("/leitores/{id}", readerId)
                .with(user("admin").roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(readerPayload(name, phoneNumber)));
    }

    private org.springframework.test.web.servlet.ResultActions deleteReader(Long readerId) throws Exception {
        return mockMvc.perform(delete("/leitores/{id}", readerId)
                .with(user("admin").roles("ADMIN"))
                .with(csrf()));
    }

    private List<PersonPayload> getReaders() throws Exception {
        MvcResult result = mockMvc.perform(get("/leitores").with(user("operator").roles("OPERATOR")))
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

    private void assertContainsOnce(List<PersonPayload> readers, Long readerId) {
        assertEquals(1, readers.stream().filter(reader -> reader.id().equals(readerId)).count());
    }

    private void assertPersonRow(Long readerId, String expectedName, String expectedPhoneNumber, String expectedPersonType) {
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
                readerId,
                expectedName,
                expectedPhoneNumber,
                expectedPersonType
        );
        assertEquals(1, count);
        assertEquals(expectedPersonType, personType(readerId));
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

    private void deactivateReaderMinistry(Long personId) {
        assertEquals(1, jdbcTemplate.update(
                """
                UPDATE tb_person_ministry
                SET active = FALSE,
                    updated_at = CURRENT_TIMESTAMP(6)
                WHERE person_id = ?
                  AND ministry_type = ?
                """,
                personId,
                MinistryType.READER.name()
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

    private Set<Long> legacyReaderIds() {
        return new LinkedHashSet<>(readerRepository.findAll().stream()
                .map(reader -> reader.getId())
                .toList());
    }

    private Set<Long> activeReaderIds() {
        return new LinkedHashSet<>(idsOrderedByActiveReaderMinistry());
    }

    private List<Long> idsOrderedByActiveReaderMinistry() {
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
                MinistryType.READER.name()
        );
    }

    private void assertEquivalentLegacyAndParallelReaderSources() {
        assertEquals(legacyReaderIds(), activeReaderIds());
    }

    private Set<Long> idsFrom(List<PersonPayload> readers) {
        return new LinkedHashSet<>(readers.stream().map(PersonPayload::id).toList());
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

    private void cleanupReader(Long readerId) {
        if (readerId == null || !personRepository.existsById(readerId)) {
            return;
        }
        jdbcTemplate.update("DELETE FROM tb_event_assignment WHERE person_id = ?", readerId);
        jdbcTemplate.update("DELETE FROM tb_event_person WHERE person_id = ?", readerId);
        jdbcTemplate.update("DELETE FROM tb_person_ministry WHERE person_id = ?", readerId);
        jdbcTemplate.update("DELETE FROM tb_person_role WHERE person_id = ?", readerId);
        jdbcTemplate.update("DELETE FROM tb_person WHERE id = ?", readerId);
    }

    private String readerPayload(String name, String phoneNumber) {
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
