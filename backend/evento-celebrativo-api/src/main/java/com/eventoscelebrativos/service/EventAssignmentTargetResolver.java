package com.eventoscelebrativos.service;

import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.model.Commentator;
import com.eventoscelebrativos.model.EucharisticMinister;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.MinisterOfTheWord;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Priest;
import com.eventoscelebrativos.model.Reader;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class EventAssignmentTargetResolver {

    public List<EventAssignmentTarget> resolve(Collection<? extends Person> participants) {
        if (participants == null || participants.isEmpty()) {
            return List.of();
        }

        List<EventAssignmentTarget> targets = new ArrayList<>();
        addTargets(targets, participants, Priest.class, EventAssignmentType.PRIEST);
        addTargets(targets, participants, Reader.class, EventAssignmentType.READER);
        addTargets(targets, participants, Commentator.class, EventAssignmentType.COMMENTATOR);
        addTargets(targets, participants, MinisterOfTheWord.class, EventAssignmentType.MINISTER_OF_THE_WORD);
        addTargets(targets, participants, EucharisticMinister.class, EventAssignmentType.EUCHARISTIC_MINISTER);
        validateUniquePeople(targets);
        return List.copyOf(targets);
    }

    private <T extends Person> void addTargets(
            List<EventAssignmentTarget> targets,
            Collection<? extends Person> participants,
            Class<T> expectedType,
            EventAssignmentType assignmentType
    ) {
        participants.stream()
                .filter(expectedType::isInstance)
                .map(expectedType::cast)
                .sorted(Comparator
                        .comparing(Person::getName, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(Person::getId, Comparator.nullsLast(Long::compareTo)))
                .map(person -> new EventAssignmentTarget(person, assignmentType))
                .forEach(targets::add);
    }

    private void validateUniquePeople(List<EventAssignmentTarget> targets) {
        Set<Long> personIds = new HashSet<>();
        for (EventAssignmentTarget target : targets) {
            if (target.person() == null || target.person().getId() == null || target.assignmentType() == null) {
                throw new BusinessException("Pessoa e tipo de atribuicao do evento sao obrigatorios");
            }
            if (!personIds.add(target.person().getId())) {
                throw new BusinessException("A mesma pessoa nao pode ocupar mais de uma funcao na mesma escala");
            }
        }
    }
}
