package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.Commentator;
import com.eventoscelebrativos.model.EucharisticMinister;
import com.eventoscelebrativos.model.MinisterOfTheWord;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.model.Priest;
import com.eventoscelebrativos.model.Reader;
import com.eventoscelebrativos.repository.CommentatorRepository;
import com.eventoscelebrativos.repository.EucharisticMinisterRepository;
import com.eventoscelebrativos.repository.MinisterOfTheWordRepository;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.repository.PriestRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.person-ministry.shadow-read.commentator-enabled=true",
        "app.person-ministry.shadow-read.priest-enabled=true",
        "app.person-ministry.shadow-read.minister-of-the-word-enabled=true",
        "app.person-ministry.shadow-read.eucharistic-minister-enabled=true",
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
@AutoConfigureMockMvc
@Transactional
class PersonMinistryShadowReadExpansionIntegrationTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private CommentatorRepository commentatorRepository;

    @Autowired
    private PriestRepository priestRepository;

    @Autowired
    private MinisterOfTheWordRepository ministerOfTheWordRepository;

    @Autowired
    private EucharisticMinisterRepository eucharisticMinisterRepository;

    @Autowired
    private PersonMinistryRepository personMinistryRepository;

    @Autowired
    private PersonMinistryReadService personMinistryReadService;

    @Autowired
    private PersonMinistryShadowReadComparator personMinistryShadowReadComparator;

    @ParameterizedTest
    @MethodSource("expandedMinistryTypes")
    void shouldReturnLegacyResponseAndMatchEquivalentShadowRead(MinistryType ministryType) throws Exception {
        saveLegacyPersonWithMinistry(ministryType, "Zzz Shadow Match " + ministryType + " Alpha");
        saveLegacyPersonWithMinistry(ministryType, "Zzz Shadow Match " + ministryType + " Beta");

        List<? extends Person> legacyPeople = legacyPeople(ministryType);
        PersonMinistryShadowReadReport report = compareAgainstParallelRead(ministryType, legacyPeople);

        MvcResult result = getList(ministryType);

        assertTrue(report.matched(), () -> "Shadow read issues: " + report.issues());
        assertEquals(payloadFrom(legacyPeople), responsePayloadFrom(result));
    }

    @ParameterizedTest
    @MethodSource("expandedMinistryTypes")
    void shouldReturnLegacyResponseAndDetectMissingExpectedMinistry(MinistryType ministryType) throws Exception {
        Person person = saveLegacyPersonWithMinistry(ministryType, "Zzz Shadow Missing " + ministryType);
        personMinistryRepository.deleteAllByPersonId(person.getId());
        personMinistryRepository.flush();

        List<? extends Person> legacyPeople = legacyPeople(ministryType);
        PersonMinistryShadowReadReport report = compareAgainstParallelRead(ministryType, legacyPeople);

        MvcResult result = getList(ministryType);

        assertEquals(payloadFrom(legacyPeople), responsePayloadFrom(result));
        assertFalse(report.matched());
        assertTrue(report.hasIssue(PersonMinistryShadowReadIssueType.CONTENT_MISMATCH));
        assertTrue(report.hasIssue(PersonMinistryShadowReadIssueType.TOTAL_ELEMENTS_MISMATCH));
        assertTrue(report.missingInParallelIds().contains(person.getId()));
        assertEquals(0, countMinistries(person.getId()));
    }

    @ParameterizedTest
    @MethodSource("expandedMinistryTypes")
    void shouldReturnLegacyResponseAndDetectInactiveExpectedMinistry(MinistryType ministryType) throws Exception {
        Person person = saveLegacyPersonWithMinistry(ministryType, "Zzz Shadow Inactive " + ministryType);

        PersonMinistry ministry = personMinistryRepository
                .findByPersonIdAndMinistryType(person.getId(), ministryType)
                .orElseThrow();
        ministry.setActive(false);
        personMinistryRepository.saveAndFlush(ministry);

        List<? extends Person> legacyPeople = legacyPeople(ministryType);
        PersonMinistryShadowReadReport report = compareAgainstParallelRead(ministryType, legacyPeople);

        MvcResult result = getList(ministryType);

        assertEquals(payloadFrom(legacyPeople), responsePayloadFrom(result));
        assertFalse(report.matched());
        assertTrue(report.hasIssue(PersonMinistryShadowReadIssueType.CONTENT_MISMATCH));
        assertTrue(report.hasIssue(PersonMinistryShadowReadIssueType.TOTAL_ELEMENTS_MISMATCH));
        assertTrue(report.missingInParallelIds().contains(person.getId()));
        assertEquals(1, countMinistry(person.getId(), ministryType, false));
    }

    @Test
    void shouldReportAdditionalParallelCommentatorWithoutChangingLegacyResponse() throws Exception {
        Reader reader = new Reader();
        populatePerson(reader, "Zzz Shadow Additional Commentator Reader");
        Reader savedReader = personRepository.saveAndFlush(reader);
        saveMinistry(savedReader, MinistryType.READER, true);
        saveMinistry(savedReader, MinistryType.COMMENTATOR, true);

        List<? extends Person> legacyCommentators = legacyPeople(MinistryType.COMMENTATOR);
        PersonMinistryShadowReadReport report = compareAgainstParallelRead(MinistryType.COMMENTATOR, legacyCommentators);

        MvcResult result = getList(MinistryType.COMMENTATOR);

        assertFalse(legacyCommentators.stream().map(Person::getId).toList().contains(savedReader.getId()));
        assertEquals(payloadFrom(legacyCommentators), responsePayloadFrom(result));
        assertFalse(report.matched());
        assertTrue(report.hasIssue(PersonMinistryShadowReadIssueType.CONTENT_MISMATCH));
        assertTrue(report.additionalInParallelIds().contains(savedReader.getId()));
        assertEquals(1, countMinistry(savedReader.getId(), MinistryType.READER, true));
        assertEquals(1, countMinistry(savedReader.getId(), MinistryType.COMMENTATOR, true));
    }

    static Stream<MinistryType> expandedMinistryTypes() {
        return Stream.of(
                MinistryType.COMMENTATOR,
                MinistryType.PRIEST,
                MinistryType.MINISTER_OF_THE_WORD,
                MinistryType.EUCHARISTIC_MINISTER
        );
    }

    private PersonMinistryShadowReadReport compareAgainstParallelRead(
            MinistryType ministryType,
            List<? extends Person> legacyPeople
    ) {
        PageRequest pageable = PageRequest.of(0, Math.max(legacyPeople.size(), 1));
        Page<? extends Person> legacyPage = new PageImpl<>(List.copyOf(legacyPeople), pageable, legacyPeople.size());
        Page<Person> parallelPage = personMinistryReadService.findActivePeopleByMinistry(ministryType, pageable);
        if (parallelPage.getTotalElements() > parallelPage.getNumberOfElements()) {
            pageable = PageRequest.of(0, Math.toIntExact(parallelPage.getTotalElements()));
            legacyPage = new PageImpl<>(List.copyOf(legacyPeople), pageable, legacyPeople.size());
            parallelPage = personMinistryReadService.findActivePeopleByMinistry(ministryType, pageable);
        }
        return personMinistryShadowReadComparator.compare(
                ministryType,
                legacyPage,
                parallelPage,
                PersonMinistryShadowReadComparisonOptions.unorderedList()
        );
    }

    private MvcResult getList(MinistryType ministryType) throws Exception {
        return mockMvc.perform(get(endpoint(ministryType)).with(user("operator").roles("OPERATOR")))
                .andExpect(status().isOk())
                .andReturn();
    }

    private Person saveLegacyPersonWithMinistry(MinistryType ministryType, String name) {
        Person person = legacyPerson(ministryType);
        populatePerson(person, name);
        Person saved = personRepository.saveAndFlush(person);
        saveMinistry(saved, ministryType, true);
        return saved;
    }

    private Person legacyPerson(MinistryType ministryType) {
        return switch (ministryType) {
            case COMMENTATOR -> new Commentator();
            case PRIEST -> new Priest();
            case MINISTER_OF_THE_WORD -> new MinisterOfTheWord();
            case EUCHARISTIC_MINISTER -> new EucharisticMinister();
            case READER -> new Reader();
        };
    }

    private void populatePerson(Person person, String name) {
        person.setName(name);
        person.setPhoneNumber(uniquePhoneNumber());
        person.setBirthdayDate(BIRTHDAY);
        person.setPassword("encoded-password");
    }

    private void saveMinistry(Person person, MinistryType ministryType, boolean active) {
        PersonMinistry ministry = new PersonMinistry(person, ministryType);
        ministry.setActive(active);
        personMinistryRepository.saveAndFlush(ministry);
    }

    private List<? extends Person> legacyPeople(MinistryType ministryType) {
        return switch (ministryType) {
            case COMMENTATOR -> commentatorRepository.findAll();
            case PRIEST -> priestRepository.findAll();
            case MINISTER_OF_THE_WORD -> ministerOfTheWordRepository.findAll();
            case EUCHARISTIC_MINISTER -> eucharisticMinisterRepository.findAll();
            case READER -> throw new IllegalArgumentException("Reader is covered by ReaderShadowReadIntegrationTest");
        };
    }

    private String endpoint(MinistryType ministryType) {
        return switch (ministryType) {
            case COMMENTATOR -> "/comentaristas";
            case PRIEST -> "/padres";
            case MINISTER_OF_THE_WORD -> "/ministrosDaPalavra";
            case EUCHARISTIC_MINISTER -> "/ministrosDeEucaristia";
            case READER -> "/leitores";
        };
    }

    private String uniquePhoneNumber() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "34977" + suffix;
    }

    private List<PersonPayload> payloadFrom(List<? extends Person> people) {
        return people.stream()
                .map(person -> new PersonPayload(
                        person.getId(),
                        person.getName(),
                        person.getPhoneNumber(),
                        person.getBirthdayDate().toString()
                ))
                .toList();
    }

    private List<PersonPayload> responsePayloadFrom(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return StreamSupport.stream(root.spliterator(), false)
                .map(node -> new PersonPayload(
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

    private record PersonPayload(Long id, String name, String phoneNumber, String birthdayDate) {
    }
}
