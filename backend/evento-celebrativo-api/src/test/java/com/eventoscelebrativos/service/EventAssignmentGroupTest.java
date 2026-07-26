package com.eventoscelebrativos.service;

import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.model.EventAssignmentType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventAssignmentGroupTest {

    @Test
    void shouldAllowSamePersonInTwoDifferentAssignmentTypes() {
        EventAssignmentGroup group = EventAssignmentGroup.from(1L, List.of(
                snapshot(100L, 10L, EventAssignmentType.READER),
                snapshot(101L, 10L, EventAssignmentType.COMMENTATOR)
        ));

        assertEquals(List.of(10L), group.readers().stream().map(EventAssignmentSnapshot::personId).toList());
        assertEquals(List.of(10L), group.commentators().stream().map(EventAssignmentSnapshot::personId).toList());
    }

    @Test
    void shouldRejectDuplicatedPersonAndTypePair() {
        assertThrows(BusinessException.class, () -> EventAssignmentGroup.from(1L, List.of(
                snapshot(100L, 10L, EventAssignmentType.READER),
                snapshot(101L, 10L, EventAssignmentType.READER)
        )));
    }

    @Test
    void shouldRejectMoreThanOnePriest() {
        assertThrows(BusinessException.class, () -> EventAssignmentGroup.from(1L, List.of(
                snapshot(100L, 10L, EventAssignmentType.PRIEST),
                snapshot(101L, 11L, EventAssignmentType.PRIEST)
        )));
    }

    private EventAssignmentSnapshot snapshot(Long assignmentId, Long personId, EventAssignmentType assignmentType) {
        return new EventAssignmentSnapshot(assignmentId, 1L, personId, assignmentType, "Pessoa " + personId, null);
    }
}
