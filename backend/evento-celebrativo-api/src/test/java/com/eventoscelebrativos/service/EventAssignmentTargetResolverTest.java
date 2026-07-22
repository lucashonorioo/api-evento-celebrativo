package com.eventoscelebrativos.service;

import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.model.Commentator;
import com.eventoscelebrativos.model.EucharisticMinister;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.MinisterOfTheWord;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Priest;
import com.eventoscelebrativos.model.Reader;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventAssignmentTargetResolverTest {

    private final EventAssignmentTargetResolver resolver = new EventAssignmentTargetResolver();

    @Test
    void shouldResolveAllLegacyParticipantTypes() {
        Priest priest = person(new Priest(), 13L, "Priest");
        Reader reader = person(new Reader(), 4L, "Reader");
        Commentator commentator = person(new Commentator(), 5L, "Commentator");
        MinisterOfTheWord ministerOfTheWord = person(new MinisterOfTheWord(), 6L, "Word Minister");
        EucharisticMinister eucharisticMinister = person(new EucharisticMinister(), 7L, "Eucharistic Minister");

        List<EventAssignmentTarget> result = resolver.resolve(List.of(
                eucharisticMinister,
                ministerOfTheWord,
                commentator,
                reader,
                priest
        ));

        assertEquals(List.of(
                EventAssignmentType.PRIEST,
                EventAssignmentType.READER,
                EventAssignmentType.COMMENTATOR,
                EventAssignmentType.MINISTER_OF_THE_WORD,
                EventAssignmentType.EUCHARISTIC_MINISTER
        ), result.stream().map(EventAssignmentTarget::assignmentType).toList());
        assertEquals(List.of(13L, 4L, 5L, 6L, 7L), result.stream().map(target -> target.person().getId()).toList());
    }

    @Test
    void shouldReturnEmptyTargetsForNullOrEmptyParticipants() {
        assertEquals(List.of(), resolver.resolve(null));
        assertEquals(List.of(), resolver.resolve(List.of()));
    }

    @Test
    void shouldSortTargetsDeterministicallyWithinEachType() {
        Reader second = person(new Reader(), 5L, "Ana");
        Reader third = person(new Reader(), 6L, "Bruno");
        Reader first = person(new Reader(), 4L, "Ana");

        List<EventAssignmentTarget> result = resolver.resolve(List.of(third, second, first));

        assertEquals(List.of(4L, 5L, 6L), result.stream().map(target -> target.person().getId()).toList());
    }

    @Test
    void shouldRejectSamePersonInTwoFunctions() {
        Priest priest = person(new Priest(), 10L, "Same Person Priest");
        Reader reader = person(new Reader(), 10L, "Same Person Reader");

        assertThrows(BusinessException.class, () -> resolver.resolve(List.of(priest, reader)));
    }

    @Test
    void shouldNotModifyParticipants() {
        Reader reader = person(new Reader(), 4L, "Reader");
        List<Person> participants = new ArrayList<>(List.of(reader));

        List<EventAssignmentTarget> result = resolver.resolve(participants);

        assertEquals(1, participants.size());
        assertSame(reader, participants.get(0));
        assertSame(reader, result.get(0).person());
    }

    private <T extends Person> T person(T person, Long id, String name) {
        person.setId(id);
        person.setName(name);
        person.setPhoneNumber("34974" + String.format("%06d", id));
        return person;
    }
}
