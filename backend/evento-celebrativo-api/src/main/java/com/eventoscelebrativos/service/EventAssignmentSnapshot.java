package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.EventAssignmentType;

import java.util.Comparator;

public record EventAssignmentSnapshot(
        Long assignmentId,
        Long eventId,
        Long personId,
        EventAssignmentType assignmentType,
        String personName,
        String personType
) {

    public static Comparator<EventAssignmentSnapshot> deterministicOrder() {
        return Comparator
                .comparing(EventAssignmentSnapshot::eventId, Comparator.nullsLast(Long::compareTo))
                .thenComparingInt(snapshot -> assignmentTypeOrder(snapshot.assignmentType()))
                .thenComparing(snapshot -> sortableName(snapshot.personName()))
                .thenComparing(EventAssignmentSnapshot::personId, Comparator.nullsLast(Long::compareTo))
                .thenComparing(EventAssignmentSnapshot::assignmentId, Comparator.nullsLast(Long::compareTo));
    }

    public static int assignmentTypeOrder(EventAssignmentType assignmentType) {
        if (assignmentType == null) {
            return 99;
        }
        return switch (assignmentType) {
            case PRIEST -> 0;
            case READER -> 1;
            case COMMENTATOR -> 2;
            case MINISTER_OF_THE_WORD -> 3;
            case EUCHARISTIC_MINISTER -> 4;
        };
    }

    private static String sortableName(String name) {
        if (name == null) {
            return "";
        }
        return name.toLowerCase();
    }
}
