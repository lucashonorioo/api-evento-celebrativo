package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.Commentator;
import com.eventoscelebrativos.model.EucharisticMinister;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.MinisterOfTheWord;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Priest;
import com.eventoscelebrativos.model.Reader;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class LegacyEventAssignmentSnapshotResolverTest {

    private final LegacyEventAssignmentSnapshotResolver resolver = new LegacyEventAssignmentSnapshotResolver();

    @Test
    void shouldResolveFiveLegacySubtypes() {
        CelebrationEvent event = event(1L);
        Priest priest = person(new Priest(), 10L, "Priest", "priest");
        Reader reader = person(new Reader(), 11L, "Reader", "reader");
        Commentator commentator = person(new Commentator(), 12L, "Commentator", "commentator");
        MinisterOfTheWord ministerOfTheWord = person(new MinisterOfTheWord(), 13L, "Word", "minister_of_the_word");
        EucharisticMinister eucharisticMinister = person(new EucharisticMinister(), 14L, "Eucharist", "eucharistic_minister");
        event.getPeople().addAll(List.of(eucharisticMinister, ministerOfTheWord, commentator, reader, priest));

        List<EventAssignmentSnapshot> result = resolver.resolve(event);

        assertEquals(List.of(
                EventAssignmentType.PRIEST,
                EventAssignmentType.READER,
                EventAssignmentType.COMMENTATOR,
                EventAssignmentType.MINISTER_OF_THE_WORD,
                EventAssignmentType.EUCHARISTIC_MINISTER
        ), result.stream().map(EventAssignmentSnapshot::assignmentType).toList());
        assertEquals(List.of(10L, 11L, 12L, 13L, 14L), result.stream().map(EventAssignmentSnapshot::personId).toList());
        result.forEach(snapshot -> assertNull(snapshot.assignmentId()));
    }

    @Test
    void shouldReturnEmptyListForEventWithoutParticipants() {
        assertEquals(List.of(), resolver.resolve(event(1L)));
    }

    @Test
    void shouldSortDeterministicallyAndIgnoreInputOrderForComparison() {
        CelebrationEvent event = event(1L);
        Reader second = person(new Reader(), 12L, "Ana", "reader");
        Reader first = person(new Reader(), 11L, "Ana", "reader");
        Reader third = person(new Reader(), 13L, "Bruno", "reader");
        event.getPeople().addAll(List.of(third, second, first));

        assertEquals(List.of(11L, 12L, 13L),
                resolver.resolve(event).stream().map(EventAssignmentSnapshot::personId).toList());
    }

    @Test
    void shouldRepresentUnknownSubtypeWithoutMutatingEvent() {
        CelebrationEvent event = event(1L);
        UnknownPerson unknown = person(new UnknownPerson(), 99L, "Unknown", "unknown_type");
        List<Person> originalPeople = new ArrayList<>(List.of(unknown));
        event.getPeople().addAll(originalPeople);

        List<EventAssignmentSnapshot> result = resolver.resolve(event);

        assertEquals(1, result.size());
        assertNull(result.get(0).assignmentType());
        assertEquals("unknown_type", result.get(0).personType());
        assertEquals(1, event.getPeople().size());
        assertSame(unknown, event.getPeople().get(0));
    }

    private CelebrationEvent event(Long eventId) {
        CelebrationEvent event = new CelebrationEvent();
        event.setId(eventId);
        return event;
    }

    private <T extends Person> T person(T person, Long personId, String name, String personType) {
        person.setId(personId);
        person.setName(name);
        person.setPhoneNumber("34977" + String.format("%06d", personId));
        person.setPersonType(personType);
        return person;
    }

    private static class UnknownPerson extends Person {
        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() {
            return List.of();
        }

        @Override
        public String getUsername() {
            return getPhoneNumber();
        }

        @Override
        public boolean isAccountNonExpired() {
            return true;
        }

        @Override
        public boolean isAccountNonLocked() {
            return true;
        }

        @Override
        public boolean isCredentialsNonExpired() {
            return true;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }
    }
}
