package com.eventoscelebrativos.service;

import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.model.EventAssignmentType;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record EventAssignmentGroup(
        Long eventId,
        EventAssignmentSnapshot priest,
        List<EventAssignmentSnapshot> readers,
        List<EventAssignmentSnapshot> commentators,
        List<EventAssignmentSnapshot> ministersOfTheWord,
        List<EventAssignmentSnapshot> eucharisticMinisters
) {

    public EventAssignmentGroup {
        readers = immutableList(readers);
        commentators = immutableList(commentators);
        ministersOfTheWord = immutableList(ministersOfTheWord);
        eucharisticMinisters = immutableList(eucharisticMinisters);
    }

    public static EventAssignmentGroup from(Long eventId, Collection<EventAssignmentSnapshot> snapshots) {
        validateEventId(eventId);
        Set<Long> personIds = new HashSet<>();
        List<EventAssignmentSnapshot> safeSnapshots = snapshots == null
                ? List.of()
                : snapshots.stream()
                        .peek(snapshot -> validateSnapshot(eventId, snapshot, personIds))
                        .sorted(EventAssignmentSnapshot.deterministicOrder())
                        .toList();

        List<EventAssignmentSnapshot> priests = byType(safeSnapshots, EventAssignmentType.PRIEST);
        if (priests.size() > 1) {
            throw new BusinessException("Evento possui mais de um padre em atribuicoes paralelas");
        }

        return new EventAssignmentGroup(
                eventId,
                priests.isEmpty() ? null : priests.get(0),
                byType(safeSnapshots, EventAssignmentType.READER),
                byType(safeSnapshots, EventAssignmentType.COMMENTATOR),
                byType(safeSnapshots, EventAssignmentType.MINISTER_OF_THE_WORD),
                byType(safeSnapshots, EventAssignmentType.EUCHARISTIC_MINISTER)
        );
    }

    private static List<EventAssignmentSnapshot> byType(
            List<EventAssignmentSnapshot> snapshots,
            EventAssignmentType assignmentType
    ) {
        return snapshots.stream()
                .filter(snapshot -> snapshot.assignmentType() == assignmentType)
                .sorted(EventAssignmentSnapshot.deterministicOrder())
                .toList();
    }

    private static void validateEventId(Long eventId) {
        if (eventId == null || eventId <= 0) {
            throw new BusinessException("Id do evento e obrigatorio para agrupar atribuicoes");
        }
    }

    private static void validateSnapshot(Long eventId, EventAssignmentSnapshot snapshot, Set<Long> personIds) {
        if (snapshot == null || !eventId.equals(snapshot.eventId())) {
            throw new BusinessException("Atribuicao paralela pertence a outro evento");
        }
        if (snapshot.personId() == null || snapshot.personId() <= 0) {
            throw new BusinessException("Pessoa da atribuicao paralela e obrigatoria");
        }
        if (snapshot.assignmentType() == null) {
            throw new BusinessException("Tipo da atribuicao paralela e obrigatorio");
        }
        if (!personIds.add(snapshot.personId())) {
            throw new BusinessException("A mesma pessoa possui mais de uma atribuicao paralela no evento");
        }
    }

    private static List<EventAssignmentSnapshot> immutableList(List<EventAssignmentSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return List.of();
        }
        return List.copyOf(snapshots);
    }
}
