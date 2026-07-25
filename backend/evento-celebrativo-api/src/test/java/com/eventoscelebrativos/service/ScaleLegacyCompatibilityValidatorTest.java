package com.eventoscelebrativos.service;

import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.model.Commentator;
import com.eventoscelebrativos.model.EucharisticMinister;
import com.eventoscelebrativos.model.MinisterOfTheWord;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Priest;
import com.eventoscelebrativos.model.Reader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScaleLegacyCompatibilityValidatorTest {

    private final ScaleLegacyCompatibilityValidator validator = new ScaleLegacyCompatibilityValidator();

    @Test
    void shouldAcceptPersonWhoseLegacySubtypeMatchesTheRequestedRole() {
        Priest priest = new Priest();
        priest.setId(1L);

        assertDoesNotThrow(() -> validator.validate(priest, Priest.class, "padre"));
    }

    @Test
    void shouldAcceptEveryLegacySubtypeAgainstItsOwnRole() {
        assertDoesNotThrow(() -> validator.validate(newPerson(new Reader()), Reader.class, "leitor"));
        assertDoesNotThrow(() -> validator.validate(newPerson(new Commentator()), Commentator.class, "comentarista"));
        assertDoesNotThrow(() -> validator.validate(newPerson(new Priest()), Priest.class, "padre"));
        assertDoesNotThrow(() -> validator.validate(newPerson(new MinisterOfTheWord()), MinisterOfTheWord.class, "ministro da Palavra"));
        assertDoesNotThrow(() -> validator.validate(newPerson(new EucharisticMinister()), EucharisticMinister.class, "ministro da Eucaristia"));
    }

    @Test
    void shouldRejectPersonWithDivergentLegacySubtypeEvenWhenMinistryIsActive() {
        Reader reader = new Reader();
        reader.setId(2L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> validator.validate(reader, Priest.class, "padre")
        );
        assertTrue(exception.getMessage().contains("padre"));
        assertTrue(exception.getMessage().contains("compatível"));
    }

    @Test
    void shouldNotExposeLegacyClassNamesInTheRejectionMessage() {
        Commentator commentator = new Commentator();
        commentator.setId(3L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> validator.validate(commentator, EucharisticMinister.class, "ministro da Eucaristia")
        );
        assertTrue(exception.getMessage().contains("ministro da Eucaristia"));
        assertTrue(exception.getMessage().toLowerCase().contains("legado"));
    }

    private <T extends Person> T newPerson(T person) {
        person.setId(1L);
        person.setName("Fixture");
        return person;
    }
}
