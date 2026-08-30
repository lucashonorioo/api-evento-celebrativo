package com.eventoscelebrativos.service;

import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.LegacyMinistryTypeMapping;
import com.eventoscelebrativos.model.Ministry;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.repository.LegacyMinistryTypeMappingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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

    private final LegacyMinistryTypeMappingRepository mappingRepository =
            mock(LegacyMinistryTypeMappingRepository.class);
    private final LegacyMinistryTypeResolver resolver = new LegacyMinistryTypeResolver(mappingRepository);

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
    void shouldResolveLegacyTypeByPersistentMapping() {
        Ministry reader = unitMinistry(MinistryType.READER);
        when(mappingRepository.findByMinistryType(MinistryType.READER))
                .thenReturn(Optional.of(mapping(reader, MinistryType.READER)));

        assertSame(reader, resolver.requireMinistry(MinistryType.READER));
        verify(mappingRepository).findByMinistryType(MinistryType.READER);
    }

    @Test
    void shouldResolveDistinctLegacyTypesInSingleBatch() {
        Ministry reader = unitMinistry(MinistryType.READER);
        Ministry commentator = unitMinistry(MinistryType.COMMENTATOR);
        when(mappingRepository.findByMinistryTypeIn(argThat(this::containsReaderAndCommentator)))
                .thenReturn(List.of(
                        mapping(reader, MinistryType.READER),
                        mapping(commentator, MinistryType.COMMENTATOR)
                ));

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
    void shouldRejectMissingCatalogMapping() {
        when(mappingRepository.findByMinistryType(MinistryType.READER)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> resolver.requireMinistry(MinistryType.READER));
    }

    @Test
    void shouldConvertPersistentMinistryBackToLegacyTypeById() {
        Ministry reader = unitMinistry(MinistryType.READER);
        when(mappingRepository.findByMinistryId(reader.getId()))
                .thenReturn(Optional.of(mapping(reader, MinistryType.READER)));

        assertEquals(MinistryType.READER, resolver.requireMinistryType(reader));
    }

    @Test
    void shouldConvertPersistentMinistryBackToLegacyTypeAfterRename() {
        Ministry reader = unitMinistry(MinistryType.READER);
        reader.rename("Leitores e Salmistas");
        when(mappingRepository.findByMinistryId(reader.getId()))
                .thenReturn(Optional.of(mapping(reader, MinistryType.READER)));

        assertEquals(MinistryType.READER, resolver.requireMinistryType(reader));
    }

    @Test
    void shouldRejectPersistentMinistryWithoutLegacyEquivalent() {
        Ministry acolitos = new Ministry("Acolitos");
        ReflectionTestUtils.setField(acolitos, "id", 99L);
        when(mappingRepository.findByMinistryId(acolitos.getId())).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> resolver.requireMinistryType(acolitos));
    }

    @Test
    void shouldConvertPersistentMinistryToLegacyEventAssignmentType() {
        Ministry eucharisticMinister = unitMinistry(MinistryType.EUCHARISTIC_MINISTER);
        when(mappingRepository.findByMinistryId(eucharisticMinister.getId()))
                .thenReturn(Optional.of(mapping(eucharisticMinister, MinistryType.EUCHARISTIC_MINISTER)));

        assertEquals(
                EventAssignmentType.EUCHARISTIC_MINISTER,
                resolver.requireEventAssignmentType(eucharisticMinister)
        );
    }

    private LegacyMinistryTypeMapping mapping(Ministry ministry, MinistryType ministryType) {
        return new LegacyMinistryTypeMapping(ministry, ministryType);
    }

    private boolean containsReaderAndCommentator(Collection<MinistryType> ministryTypes) {
        return ministryTypes != null
                && ministryTypes.size() == 2
                && ministryTypes.contains(MinistryType.READER)
                && ministryTypes.contains(MinistryType.COMMENTATOR);
    }
}
