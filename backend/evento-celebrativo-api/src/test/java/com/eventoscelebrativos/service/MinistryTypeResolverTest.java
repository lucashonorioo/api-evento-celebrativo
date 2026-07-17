package com.eventoscelebrativos.service;

import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.model.Commentator;
import com.eventoscelebrativos.model.EucharisticMinister;
import com.eventoscelebrativos.model.MinisterOfTheWord;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Priest;
import com.eventoscelebrativos.model.Reader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class MinistryTypeResolverTest {

    private final MinistryTypeResolver resolver = new MinistryTypeResolver();

    @Test
    void shouldResolveAllLegacySubtypes() {
        assertEquals(MinistryType.READER, resolver.resolve(new Reader()));
        assertEquals(MinistryType.COMMENTATOR, resolver.resolve(new Commentator()));
        assertEquals(MinistryType.PRIEST, resolver.resolve(new Priest()));
        assertEquals(MinistryType.MINISTER_OF_THE_WORD, resolver.resolve(new MinisterOfTheWord()));
        assertEquals(MinistryType.EUCHARISTIC_MINISTER, resolver.resolve(new EucharisticMinister()));
    }

    @Test
    void shouldThrowWhenSubtypeIsUnknown() {
        assertThrows(BusinessException.class, () -> resolver.resolve(mock(Person.class)));
    }
}
