package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.model.Reader;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.ReaderRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.person-ministry.shadow-read.reader-enabled=true",
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
@AutoConfigureMockMvc
@Transactional
class ReaderShadowReadIntegrationTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReaderRepository readerRepository;

    @Autowired
    private PersonMinistryRepository personMinistryRepository;

    @Autowired
    private PersonMinistryReadService personMinistryReadService;

    @Autowired
    private PersonMinistryShadowReadComparator personMinistryShadowReadComparator;

    @Test
    void shouldReturnLegacyResponseAndMatchEquivalentReaderShadowRead() throws Exception {
        saveReaderWithMinistry("Zzz Shadow Match Alpha", MinistryType.READER);
        saveReaderWithMinistry("Zzz Shadow Match Beta", MinistryType.READER);

        List<Reader> legacyReaders = readerRepository.findAll();
        PersonMinistryShadowReadReport report = compareAgainstParallelRead(legacyReaders);

        MvcResult result = getReaders();

        assertTrue(report.matched(), () -> "Shadow read issues: " + report.issues());
        assertEquals(payloadFrom(legacyReaders), responsePayloadFrom(result));
    }

    @Test
    void shouldKeepAdditionalMinistryAsValidAndReturnLegacyResponse() throws Exception {
        Reader reader = saveReaderWithMinistry("Zzz Shadow Additional Reader", MinistryType.READER);
        saveMinistry(reader, MinistryType.COMMENTATOR, true);

        List<Reader> legacyReaders = readerRepository.findAll();
        PersonMinistryShadowReadReport report = compareAgainstParallelRead(legacyReaders);

        MvcResult result = getReaders();

        assertTrue(report.matched(), () -> "Shadow read issues: " + report.issues());
        assertEquals(payloadFrom(legacyReaders), responsePayloadFrom(result));
        assertEquals(1, countMinistry(reader.getId(), MinistryType.COMMENTATOR, true));
    }

    @Test
    void shouldReturnLegacyResponseAndDetectMissingExpectedReaderMinistry() throws Exception {
        Reader reader = saveReaderWithMinistry("Zzz Shadow Missing Reader", MinistryType.READER);
        personMinistryRepository.deleteAllByPersonId(reader.getId());
        personMinistryRepository.flush();

        List<Reader> legacyReaders = readerRepository.findAll();
        PersonMinistryShadowReadReport report = compareAgainstParallelRead(legacyReaders);

        MvcResult result = getReaders();

        assertEquals(payloadFrom(legacyReaders), responsePayloadFrom(result));
        assertFalse(report.matched());
        assertTrue(report.hasIssue(PersonMinistryShadowReadIssueType.CONTENT_MISMATCH));
        assertTrue(report.hasIssue(PersonMinistryShadowReadIssueType.TOTAL_ELEMENTS_MISMATCH));
        assertEquals(0, countMinistries(reader.getId()));
    }

    @Test
    void shouldReturnLegacyResponseAndDetectInactiveExpectedReaderMinistry() throws Exception {
        Reader reader = saveReaderWithMinistry("Zzz Shadow Inactive Reader", MinistryType.READER);
        saveMinistry(reader, MinistryType.COMMENTATOR, true);

        PersonMinistry readerMinistry = personMinistryRepository
                .findByPersonIdAndMinistryType(reader.getId(), MinistryType.READER)
                .orElseThrow();
        readerMinistry.setActive(false);
        personMinistryRepository.saveAndFlush(readerMinistry);

        List<Reader> legacyReaders = readerRepository.findAll();
        PersonMinistryShadowReadReport report = compareAgainstParallelRead(legacyReaders);

        MvcResult result = getReaders();

        assertEquals(payloadFrom(legacyReaders), responsePayloadFrom(result));
        assertFalse(report.matched());
        assertTrue(report.hasIssue(PersonMinistryShadowReadIssueType.CONTENT_MISMATCH));
        assertTrue(report.hasIssue(PersonMinistryShadowReadIssueType.TOTAL_ELEMENTS_MISMATCH));
        assertEquals(1, countMinistry(reader.getId(), MinistryType.READER, false));
        assertEquals(1, countMinistry(reader.getId(), MinistryType.COMMENTATOR, true));
    }

    private PersonMinistryShadowReadReport compareAgainstParallelRead(List<Reader> legacyReaders) {
        PageRequest pageable = PageRequest.of(0, Math.max(legacyReaders.size(), 1));
        Page<Reader> legacyPage = new PageImpl<>(legacyReaders, pageable, legacyReaders.size());
        Page<Person> parallelPage = personMinistryReadService.findActivePeopleByMinistry(MinistryType.READER, pageable);
        return personMinistryShadowReadComparator.compare(
                MinistryType.READER,
                legacyPage,
                parallelPage,
                PersonMinistryShadowReadComparisonOptions.unorderedList()
        );
    }

    private MvcResult getReaders() throws Exception {
        return mockMvc.perform(get("/leitores").with(user("operator").roles("OPERATOR")))
                .andExpect(status().isOk())
                .andReturn();
    }

    private Reader saveReaderWithMinistry(String name, MinistryType ministryType) {
        Reader reader = new Reader();
        reader.setName(name);
        reader.setPhoneNumber(uniquePhoneNumber());
        reader.setBirthdayDate(BIRTHDAY);
        reader.setPassword("encoded-password");

        Reader saved = readerRepository.saveAndFlush(reader);
        saveMinistry(saved, ministryType, true);
        return saved;
    }

    private void saveMinistry(Person person, MinistryType ministryType, boolean active) {
        PersonMinistry ministry = new PersonMinistry(person, ministryType);
        ministry.setActive(active);
        personMinistryRepository.saveAndFlush(ministry);
    }

    private String uniquePhoneNumber() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "34977" + suffix;
    }

    private List<ReaderPayload> payloadFrom(List<Reader> readers) {
        return readers.stream()
                .map(reader -> new ReaderPayload(
                        reader.getId(),
                        reader.getName(),
                        reader.getPhoneNumber(),
                        reader.getBirthdayDate().toString()
                ))
                .toList();
    }

    private List<ReaderPayload> responsePayloadFrom(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return StreamSupport.stream(root.spliterator(), false)
                .map(node -> new ReaderPayload(
                        node.path("id").asLong(),
                        node.path("name").asText(),
                        node.path("phoneNumber").asText(),
                        node.path("birthdayDate").asText()
                ))
                .toList();
    }

    private int countMinistries(Long personId) {
        return personMinistryRepository.findAllByPersonId(personId).size();
    }

    private long countMinistry(Long personId, MinistryType ministryType, boolean active) {
        return personMinistryRepository.findAllByPersonId(personId).stream()
                .filter(ministry -> ministryType.equals(ministry.getMinistryType()))
                .filter(ministry -> Boolean.valueOf(active).equals(ministry.getActive()))
                .count();
    }

    private record ReaderPayload(Long id, String name, String phoneNumber, String birthdayDate) {
    }
}
