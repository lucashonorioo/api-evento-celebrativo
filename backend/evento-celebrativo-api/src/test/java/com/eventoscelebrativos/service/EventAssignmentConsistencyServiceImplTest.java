package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Priest;
import com.eventoscelebrativos.model.Reader;
import com.eventoscelebrativos.service.impl.EventAssignmentConsistencyServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventAssignmentConsistencyServiceImplTest {

    private final EventAssignmentConsistencyService service =
            new EventAssignmentConsistencyServiceImpl(new LegacyEventAssignmentSnapshotResolver());

    @Test
    void shouldReportConsistentEventWhenLegacyAndParallelAreEquivalent() {
        CelebrationEvent event = event(1L, reader(10L, "Reader"), priest(11L, "Priest"));
        List<EventAssignmentSnapshot> parallel = List.of(
                snapshot(101L, 1L, 10L, EventAssignmentType.READER),
                snapshot(102L, 1L, 11L, EventAssignmentType.PRIEST)
        );

        EventAssignmentConsistencyReport report = service.compareEvent(event, parallel);

        assertTrue(report.consistent());
        assertEquals(2, report.legacyAssignmentCount());
        assertEquals(2, report.parallelAssignmentCount());
        assertEquals(List.of(), report.issues());
    }

    @Test
    void shouldIgnoreOrderingWhenComparing() {
        CelebrationEvent event = event(1L, reader(10L, "Reader"), priest(11L, "Priest"));
        List<EventAssignmentSnapshot> parallel = List.of(
                snapshot(102L, 1L, 11L, EventAssignmentType.PRIEST),
                snapshot(101L, 1L, 10L, EventAssignmentType.READER)
        );

        assertTrue(service.compareEvent(event, parallel).consistent());
    }

    @Test
    void shouldReportMissingParallelAssignment() {
        CelebrationEvent event = event(1L, reader(10L, "Reader"));

        EventAssignmentConsistencyReport report = service.compareEvent(event, List.of());

        assertIssue(report, EventAssignmentConsistencyIssueType.MISSING_PARALLEL_ASSIGNMENT);
        EventAssignmentConsistencyIssue issue = report.issues().get(0);
        assertEquals(10L, issue.personId());
        assertEquals(EventAssignmentType.READER, issue.legacyType());
    }

    @Test
    void shouldReportExtraParallelAssignment() {
        CelebrationEvent event = event(1L);

        EventAssignmentConsistencyReport report = service.compareEvent(
                event,
                List.of(snapshot(100L, 1L, 99L, EventAssignmentType.READER))
        );

        assertIssue(report, EventAssignmentConsistencyIssueType.EXTRA_PARALLEL_ASSIGNMENT);
        EventAssignmentConsistencyIssue issue = report.issues().get(0);
        assertEquals(99L, issue.personId());
        assertEquals(EventAssignmentType.READER, issue.parallelType());
    }

    @Test
    void shouldReportAssignmentTypeMismatchForSamePerson() {
        CelebrationEvent event = event(1L, reader(10L, "Reader"));

        EventAssignmentConsistencyReport report = service.compareEvent(
                event,
                List.of(snapshot(100L, 1L, 10L, EventAssignmentType.COMMENTATOR))
        );

        assertIssue(report, EventAssignmentConsistencyIssueType.ASSIGNMENT_TYPE_MISMATCH);
        EventAssignmentConsistencyIssue issue = report.issues().get(0);
        assertEquals(EventAssignmentType.READER, issue.legacyType());
        assertEquals(EventAssignmentType.COMMENTATOR, issue.parallelType());
    }

    @Test
    void shouldReportDuplicateParallelAssignmentForSamePerson() {
        CelebrationEvent event = event(1L, reader(10L, "Reader"));

        EventAssignmentConsistencyReport report = service.compareEvent(
                event,
                List.of(
                        snapshot(100L, 1L, 10L, EventAssignmentType.READER),
                        snapshot(101L, 1L, 10L, EventAssignmentType.READER)
                )
        );

        assertIssue(report, EventAssignmentConsistencyIssueType.DUPLICATE_PARALLEL_ASSIGNMENT);
    }

    @Test
    void shouldReportMultiplePriests() {
        CelebrationEvent event = event(1L, priest(10L, "First Priest"), priest(11L, "Second Priest"));

        EventAssignmentConsistencyReport report = service.compareEvent(
                event,
                List.of(
                        snapshot(100L, 1L, 10L, EventAssignmentType.PRIEST),
                        snapshot(101L, 1L, 11L, EventAssignmentType.PRIEST)
                )
        );

        assertIssue(report, EventAssignmentConsistencyIssueType.MULTIPLE_PRIESTS);
    }

    @Test
    void shouldReportUnknownLegacyPersonType() {
        CelebrationEvent event = event(1L, person(new UnknownPerson(), 10L, "Unknown", "unknown"));

        EventAssignmentConsistencyReport report = service.compareEvent(event, List.of());

        assertIssue(report, EventAssignmentConsistencyIssueType.UNKNOWN_LEGACY_PERSON_TYPE);
        assertEquals("unknown", report.issues().get(0).legacyPersonType());
    }

    @Test
    void shouldCompareSeveralEventsIndependently() {
        CelebrationEvent consistent = event(1L, reader(10L, "Reader"));
        CelebrationEvent missing = event(2L, reader(20L, "Reader 20"));

        Map<Long, EventAssignmentConsistencyReport> reports = service.compareEvents(
                List.of(missing, consistent),
                Map.of(1L, List.of(snapshot(100L, 1L, 10L, EventAssignmentType.READER)))
        );

        assertEquals(List.of(1L, 2L), reports.keySet().stream().toList());
        assertTrue(reports.get(1L).consistent());
        assertFalse(reports.get(2L).consistent());
        assertIssue(reports.get(2L), EventAssignmentConsistencyIssueType.MISSING_PARALLEL_ASSIGNMENT);
    }

    private void assertIssue(EventAssignmentConsistencyReport report, EventAssignmentConsistencyIssueType issueType) {
        assertFalse(report.consistent());
        assertTrue(report.issues().stream().anyMatch(issue -> issue.issueType() == issueType));
    }

    private EventAssignmentSnapshot snapshot(
            Long assignmentId,
            Long eventId,
            Long personId,
            EventAssignmentType assignmentType
    ) {
        return new EventAssignmentSnapshot(
                assignmentId,
                eventId,
                personId,
                assignmentType,
                "Person " + personId,
                null
        );
    }

    private CelebrationEvent event(Long eventId, Person... people) {
        CelebrationEvent event = new CelebrationEvent();
        event.setId(eventId);
        event.getPeople().addAll(List.of(people));
        return event;
    }

    private Reader reader(Long id, String name) {
        return person(new Reader(), id, name, "reader");
    }

    private Priest priest(Long id, String name) {
        return person(new Priest(), id, name, "priest");
    }

    private <T extends Person> T person(T person, Long id, String name, String personType) {
        person.setId(id);
        person.setName(name);
        person.setPhoneNumber("34978" + String.format("%06d", id));
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
