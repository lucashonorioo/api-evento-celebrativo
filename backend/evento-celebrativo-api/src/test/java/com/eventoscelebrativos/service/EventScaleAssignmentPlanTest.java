package com.eventoscelebrativos.service;

import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.MultipleAssignmentsForPersonInEventException;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.Person;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventScaleAssignmentPlanTest {

    @Test
    void shouldBuildEmptyPlanWhenNothingIsAdded() {
        EventScaleAssignmentPlan plan = EventScaleAssignmentPlan.builder().build();

        assertTrue(plan.isEmpty());
        assertEquals(List.of(), plan.entries());
        assertEquals(List.of(), plan.people());
        assertEquals(List.of(), plan.toTargets());
    }

    @Test
    void shouldAcceptAllFiveAssignmentTypes() {
        Person priest = person(1L);
        Person reader = person(2L);
        Person commentator = person(3L);
        Person ministerOfTheWord = person(4L);
        Person eucharisticMinister = person(5L);

        EventScaleAssignmentPlan plan = EventScaleAssignmentPlan.builder()
                .add(priest, EventAssignmentType.PRIEST)
                .add(reader, EventAssignmentType.READER)
                .add(commentator, EventAssignmentType.COMMENTATOR)
                .add(ministerOfTheWord, EventAssignmentType.MINISTER_OF_THE_WORD)
                .add(eucharisticMinister, EventAssignmentType.EUCHARISTIC_MINISTER)
                .build();

        assertEquals(5, plan.entries().size());
        assertEquals(
                List.of(
                        EventAssignmentType.PRIEST,
                        EventAssignmentType.READER,
                        EventAssignmentType.COMMENTATOR,
                        EventAssignmentType.MINISTER_OF_THE_WORD,
                        EventAssignmentType.EUCHARISTIC_MINISTER
                ),
                plan.entries().stream().map(EventScaleAssignmentPlan.Entry::assignmentType).toList()
        );
    }

    @Test
    void shouldPreserveInsertionOrderInPeopleAndTargets() {
        Person second = person(2L);
        Person first = person(1L);

        EventScaleAssignmentPlan plan = EventScaleAssignmentPlan.builder()
                .add(second, EventAssignmentType.READER)
                .add(first, EventAssignmentType.COMMENTATOR)
                .build();

        assertEquals(List.of(2L, 1L), plan.people().stream().map(Person::getId).toList());
        assertEquals(
                List.of(EventAssignmentType.READER, EventAssignmentType.COMMENTATOR),
                plan.toTargets().stream().map(EventAssignmentTarget::assignmentType).toList()
        );
    }

    @Test
    void shouldRejectSamePersonAddedTwiceWithTheSameAssignmentType() {
        Person reader = person(1L);
        EventScaleAssignmentPlan.Builder builder = EventScaleAssignmentPlan.builder()
                .add(reader, EventAssignmentType.READER);

        MultipleAssignmentsForPersonInEventException exception = assertThrows(
                MultipleAssignmentsForPersonInEventException.class, () -> builder.add(reader, EventAssignmentType.READER));
        assertEquals(1L, exception.getPersonId());
        assertEquals(Set.of(EventAssignmentType.READER), exception.getConflictingAssignmentTypes());
    }

    @Test
    void shouldRejectSamePersonAddedTwiceWithDifferentAssignmentTypes() {
        Person person = person(1L);
        EventScaleAssignmentPlan.Builder builder = EventScaleAssignmentPlan.builder()
                .add(person, EventAssignmentType.PRIEST);

        MultipleAssignmentsForPersonInEventException exception = assertThrows(
                MultipleAssignmentsForPersonInEventException.class, () -> builder.add(person, EventAssignmentType.READER));
        assertEquals(1L, exception.getPersonId());
        assertEquals(Set.of(EventAssignmentType.PRIEST, EventAssignmentType.READER), exception.getConflictingAssignmentTypes());
    }

    @Test
    void shouldRejectNullPersonOrAssignmentType() {
        EventScaleAssignmentPlan.Builder builder = EventScaleAssignmentPlan.builder();

        assertThrows(BusinessException.class, () -> builder.add(null, EventAssignmentType.READER));
        assertThrows(BusinessException.class, () -> builder.add(person(1L), null));
    }

    @Test
    void shouldNotDependOnPersonTypeOrLegacySubtypeToBuildTargets() {
        Person genericPerson = new Person();
        genericPerson.setId(1L);
        genericPerson.setName("Generic");

        EventScaleAssignmentPlan plan = EventScaleAssignmentPlan.builder()
                .add(genericPerson, EventAssignmentType.EUCHARISTIC_MINISTER)
                .build();

        assertEquals(EventAssignmentType.EUCHARISTIC_MINISTER, plan.toTargets().get(0).assignmentType());
    }

    @Test
    void shouldReturnImmutableCollections() {
        EventScaleAssignmentPlan plan = EventScaleAssignmentPlan.builder()
                .add(person(1L), EventAssignmentType.READER)
                .build();

        assertThrows(UnsupportedOperationException.class, () -> plan.entries().add(null));
        assertThrows(UnsupportedOperationException.class, () -> plan.people().add(null));
        assertThrows(UnsupportedOperationException.class, () -> plan.toTargets().add(null));
    }

    private Person person(Long id) {
        Person person = new Person();
        person.setId(id);
        person.setName("Person " + id);
        return person;
    }
}
