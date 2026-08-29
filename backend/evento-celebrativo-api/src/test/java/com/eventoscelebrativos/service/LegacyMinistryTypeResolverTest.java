package com.eventoscelebrativos.service;

import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.Ministry;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.repository.MinistryRepository;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.eventoscelebrativos.support.LegacyMinistryTestFactory.unitMinistry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacyMinistryTypeResolverTest {

    private final MinistryRepository ministryRepository = mock(MinistryRepository.class);
    private final LegacyMinistryTypeResolver resolver = new LegacyMinistryTypeResolver(ministryRepository);

    @Test
    void shouldParseLegacyMinistryTypeCaseInsensitively() {
        assertEquals(MinistryType.READER, resolver.parseMinistryType(" reader "));
    }

    @Test
    void shouldRejectInvalidLegacyMinistryType() {
        assertThrows(BadRequestException.class, () -> resolver.parseMinistryType(null));
        assertThrows(BadRequestException.class, () -> resolver.parseMinistryType("  "));
        assertThrows(BadRequestException.class, () -> resolver.parseMinistryType("ACOLYTE"));
    }

    @Test
    void shouldResolveLegacyTypeByCatalogNormalizedName() {
        Ministry reader = unitMinistry(MinistryType.READER);
        when(ministryRepository.findByNormalizedName("LEITORES")).thenReturn(Optional.of(reader));

        assertSame(reader, resolver.requireMinistry(MinistryType.READER));
        verify(ministryRepository).findByNormalizedName("LEITORES");
    }

    @Test
    void shouldResolveDistinctLegacyTypesInSingleBatch() {
        Ministry reader = unitMinistry(MinistryType.READER);
        Ministry commentator = unitMinistry(MinistryType.COMMENTATOR);
        when(ministryRepository.findByNormalizedNameIn(argThat(this::containsReaderAndCommentator)))
                .thenReturn(List.of(reader, commentator));

        Map<MinistryType, Ministry> result = resolver.requireMinistries(List.of(
                MinistryType.READER,
                MinistryType.READER,
                MinistryType.COMMENTATOR
        ));

        assertEquals(2, result.size());
        assertSame(reader, result.get(MinistryType.READER));
        assertSame(commentator, result.get(MinistryType.COMMENTATOR));
    }

    @Test
    void shouldRejectMissingCatalogRow() {
        when(ministryRepository.findByNormalizedName("LEITORES")).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> resolver.requireMinistry(MinistryType.READER));
    }

    @Test
    void shouldConvertPersistentMinistryBackToLegacyType() {
        assertEquals(MinistryType.READER, resolver.requireMinistryType(unitMinistry(MinistryType.READER)));
        assertEquals(MinistryType.COMMENTATOR, resolver.requireMinistryType("COMENTARISTAS"));
    }

    @Test
    void shouldRejectPersistentMinistryWithoutLegacyEquivalent() {
        Ministry acolitos = new Ministry("Acolitos");

        assertThrows(IllegalStateException.class, () -> resolver.requireMinistryType(acolitos));
    }

    @Test
    void shouldConvertPersistentMinistryToLegacyEventAssignmentType() {
        assertEquals(
                EventAssignmentType.EUCHARISTIC_MINISTER,
                resolver.requireEventAssignmentType(unitMinistry(MinistryType.EUCHARISTIC_MINISTER))
        );
    }

    private boolean containsReaderAndCommentator(Collection<String> normalizedNames) {
        return normalizedNames != null
                && normalizedNames.size() == 2
                && normalizedNames.contains("LEITORES")
                && normalizedNames.contains("COMENTARISTAS");
    }
}
