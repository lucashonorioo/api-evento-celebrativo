package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.CommentatorRequestDTO;
import com.eventoscelebrativos.dto.request.EucharisticMinisterRequestDTO;
import com.eventoscelebrativos.dto.request.MinisterOfTheWordRequestDTO;
import com.eventoscelebrativos.dto.request.PriestRequestDTO;
import com.eventoscelebrativos.dto.request.ReaderRequestDTO;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
@ActiveProfiles("test")
class PersonMinistryLegacyCompatibilityIntegrationTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);
    private static final String PASSWORD = "raw-password";

    @Autowired
    private ReaderService readerService;

    @Autowired
    private CommentatorService commentatorService;

    @Autowired
    private PriestService priestService;

    @Autowired
    private MinisterOfTheWordService ministerOfTheWordService;

    @Autowired
    private EucharisticMinisterService eucharisticMinisterService;

    @Autowired
    private PersonMinistryCompatibilityService personMinistryCompatibilityService;

    @Autowired
    private PersonMinistryRepository personMinistryRepository;

    @Autowired
    private PersonRepository personRepository;

    @Test
    void shouldKeepPersonMinistriesInSyncWithLegacyCrudFlows() {
        shouldSyncReaderFlowAndPreserveAdditionalMinistries();
        shouldSyncCommentatorFlow();
        shouldSyncPriestFlow();
        shouldSyncMinisterOfTheWordFlow();
        shouldSyncEucharisticMinisterFlow();
    }

    private void shouldSyncReaderFlowAndPreserveAdditionalMinistries() {
        Long id = readerService.createReader(new ReaderRequestDTO("Compat Reader", "34972000001", BIRTHDAY, PASSWORD)).getId();

        assertMinistries(id, MinistryType.READER);

        Person person = personRepository.findById(id).orElseThrow();
        personMinistryCompatibilityService.ensureMinistry(person, MinistryType.COMMENTATOR);
        assertMinistries(id, MinistryType.READER, MinistryType.COMMENTATOR);

        readerService.updateReader(id, new ReaderRequestDTO("Compat Reader Updated", "34972000001", BIRTHDAY, PASSWORD));
        assertMinistries(id, MinistryType.READER, MinistryType.COMMENTATOR);

        readerService.deleteReaderById(id);
        assertPersonAndMinistriesDeleted(id);
    }

    private void shouldSyncCommentatorFlow() {
        Long id = commentatorService.createCommentator(new CommentatorRequestDTO("Compat Commentator", "34972000002", BIRTHDAY, PASSWORD)).getId();

        assertMinistries(id, MinistryType.COMMENTATOR);

        commentatorService.updateCommentator(id, new CommentatorRequestDTO("Compat Commentator Updated", "34972000002", BIRTHDAY, PASSWORD));
        assertMinistries(id, MinistryType.COMMENTATOR);

        commentatorService.deleteCommentatorById(id);
        assertPersonAndMinistriesDeleted(id);
    }

    private void shouldSyncPriestFlow() {
        Long id = priestService.createPriest(new PriestRequestDTO("Compat Priest", "34972000003", BIRTHDAY, PASSWORD)).getId();

        assertMinistries(id, MinistryType.PRIEST);

        priestService.updatePriest(id, new PriestRequestDTO("Compat Priest Updated", "34972000003", BIRTHDAY, PASSWORD));
        assertMinistries(id, MinistryType.PRIEST);

        priestService.deletePriestById(id);
        assertPersonAndMinistriesDeleted(id);
    }

    private void shouldSyncMinisterOfTheWordFlow() {
        Long id = ministerOfTheWordService.createMinisterOfTheWord(new MinisterOfTheWordRequestDTO("Compat Word Minister", "34972000004", BIRTHDAY, PASSWORD)).getId();

        assertMinistries(id, MinistryType.MINISTER_OF_THE_WORD);

        ministerOfTheWordService.updateMinisterOfTheWord(id, new MinisterOfTheWordRequestDTO("Compat Word Minister Updated", "34972000004", BIRTHDAY, PASSWORD));
        assertMinistries(id, MinistryType.MINISTER_OF_THE_WORD);

        ministerOfTheWordService.deleteMinisterOfTheWord(id);
        assertPersonAndMinistriesDeleted(id);
    }

    private void shouldSyncEucharisticMinisterFlow() {
        Long id = eucharisticMinisterService.createEucharisticMinister(new EucharisticMinisterRequestDTO("Compat Eucharistic Minister", "34972000005", BIRTHDAY, PASSWORD)).getId();

        assertMinistries(id, MinistryType.EUCHARISTIC_MINISTER);

        eucharisticMinisterService.updateEucharisticMinisters(id, new EucharisticMinisterRequestDTO("Compat Eucharistic Minister Updated", "34972000005", BIRTHDAY, PASSWORD));
        assertMinistries(id, MinistryType.EUCHARISTIC_MINISTER);

        eucharisticMinisterService.deleteEucharisticMinisterById(id);
        assertPersonAndMinistriesDeleted(id);
    }

    private void assertMinistries(Long personId, MinistryType... expectedTypes) {
        List<MinistryType> actualTypes = personMinistryRepository.findAllByPersonId(personId).stream()
                .map(PersonMinistry::getMinistryType)
                .toList();

        assertEquals(expectedTypes.length, actualTypes.size());
        for (MinistryType expectedType : expectedTypes) {
            assertTrue(actualTypes.contains(expectedType));
        }
    }

    private void assertPersonAndMinistriesDeleted(Long personId) {
        assertFalse(personRepository.existsById(personId));
        assertTrue(personMinistryRepository.findAllByPersonId(personId).isEmpty());
    }
}
