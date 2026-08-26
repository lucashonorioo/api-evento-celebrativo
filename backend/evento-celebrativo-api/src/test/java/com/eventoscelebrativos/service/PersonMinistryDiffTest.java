package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static com.eventoscelebrativos.support.LegacyMinistryTestFactory.personMinistry;
import static com.eventoscelebrativos.support.LegacyMinistryTestFactory.unitMinistry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonMinistryDiffTest {

    @Test
    void shouldAddAllDesiredMinistriesWhenPersonHasNone() {
        PersonMinistryDiff diff = PersonMinistryDiff.compute(
                Set.of(ministryId(MinistryType.READER), ministryId(MinistryType.COMMENTATOR)),
                List.of()
        );

        assertEquals(Set.of(ministryId(MinistryType.READER), ministryId(MinistryType.COMMENTATOR)), diff.toAdd());
        assertTrue(diff.toReactivate().isEmpty());
        assertTrue(diff.toDeactivate().isEmpty());
        assertTrue(diff.unchanged().isEmpty());
    }

    @Test
    void shouldKeepActiveMinistryPresentInDesiredSetAsUnchanged() {
        PersonMinistry activeReader = ministry(MinistryType.READER, true);

        PersonMinistryDiff diff = PersonMinistryDiff.compute(
                Set.of(ministryId(MinistryType.READER)),
                List.of(activeReader)
        );

        assertTrue(diff.toAdd().isEmpty());
        assertTrue(diff.toReactivate().isEmpty());
        assertTrue(diff.toDeactivate().isEmpty());
        assertEquals(Set.of(ministryId(MinistryType.READER)), diff.unchanged());
    }

    @Test
    void shouldReactivateInactiveMinistryPresentInDesiredSet() {
        PersonMinistry inactiveReader = ministry(MinistryType.READER, false);

        PersonMinistryDiff diff = PersonMinistryDiff.compute(
                Set.of(ministryId(MinistryType.READER)),
                List.of(inactiveReader)
        );

        assertTrue(diff.toAdd().isEmpty());
        assertEquals(List.of(inactiveReader), diff.toReactivate());
        assertTrue(diff.toDeactivate().isEmpty());
        assertTrue(diff.unchanged().isEmpty());
    }

    @Test
    void shouldDeactivateActiveMinistryAbsentFromDesiredSet() {
        PersonMinistry activeCommentator = ministry(MinistryType.COMMENTATOR, true);

        PersonMinistryDiff diff = PersonMinistryDiff.compute(
                Set.of(),
                List.of(activeCommentator)
        );

        assertTrue(diff.toAdd().isEmpty());
        assertTrue(diff.toReactivate().isEmpty());
        assertEquals(List.of(activeCommentator), diff.toDeactivate());
        assertTrue(diff.unchanged().isEmpty());
    }

    @Test
    void shouldNotDeactivateAlreadyInactiveMinistryAbsentFromDesiredSet() {
        PersonMinistry inactivePriest = ministry(MinistryType.PRIEST, false);

        PersonMinistryDiff diff = PersonMinistryDiff.compute(
                Set.of(),
                List.of(inactivePriest)
        );

        assertTrue(diff.toAdd().isEmpty());
        assertTrue(diff.toReactivate().isEmpty());
        assertTrue(diff.toDeactivate().isEmpty());
        assertTrue(diff.unchanged().isEmpty());
    }

    @Test
    void shouldComputeAllFourCategoriesSimultaneously() {
        PersonMinistry unchangedReader = ministry(MinistryType.READER, true);
        PersonMinistry reactivatedCommentator = ministry(MinistryType.COMMENTATOR, false);
        PersonMinistry deactivatedPriest = ministry(MinistryType.PRIEST, true);

        PersonMinistryDiff diff = PersonMinistryDiff.compute(
                Set.of(
                        ministryId(MinistryType.READER),
                        ministryId(MinistryType.COMMENTATOR),
                        ministryId(MinistryType.EUCHARISTIC_MINISTER)
                ),
                List.of(unchangedReader, reactivatedCommentator, deactivatedPriest)
        );

        assertEquals(Set.of(ministryId(MinistryType.EUCHARISTIC_MINISTER)), diff.toAdd());
        assertEquals(List.of(reactivatedCommentator), diff.toReactivate());
        assertEquals(List.of(deactivatedPriest), diff.toDeactivate());
        assertEquals(Set.of(ministryId(MinistryType.READER)), diff.unchanged());
    }

    @Test
    void shouldComputeEmptyDiffWhenDesiredAndExistingAreBothEmpty() {
        PersonMinistryDiff diff = PersonMinistryDiff.compute(Set.of(), List.of());

        assertTrue(diff.toAdd().isEmpty());
        assertTrue(diff.toReactivate().isEmpty());
        assertTrue(diff.toDeactivate().isEmpty());
        assertTrue(diff.unchanged().isEmpty());
    }

    private PersonMinistry ministry(MinistryType type, boolean active) {
        PersonMinistry personMinistry = personMinistry(
                new Person("Pessoa Teste", "34999999999", LocalDate.of(1990, 1, 1)),
                type
        );
        personMinistry.setActive(active);
        return personMinistry;
    }

    private Long ministryId(MinistryType type) {
        return unitMinistry(type).getId();
    }
}
