package com.eventoscelebrativos.service;

import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.Commentator;
import com.eventoscelebrativos.model.EucharisticMinister;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.MinisterOfTheWord;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Priest;
import com.eventoscelebrativos.model.Reader;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class LegacyEventAssignmentSnapshotResolver {

    public List<EventAssignmentSnapshot> resolve(CelebrationEvent event) {
        validateEvent(event);
        return event.getPeople().stream()
                .map(person -> toSnapshot(event.getId(), person))
                .sorted(EventAssignmentSnapshot.deterministicOrder())
                .toList();
    }

    public Map<Long, List<EventAssignmentSnapshot>> resolveAll(Collection<CelebrationEvent> events) {
        if (events == null) {
            throw new BusinessException("Eventos sao obrigatorios para resolver atribuicoes legadas");
        }
        if (events.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, List<EventAssignmentSnapshot>> result = new LinkedHashMap<>();
        events.stream()
                .sorted((first, second) -> first.getId().compareTo(second.getId()))
                .forEach(event -> result.put(event.getId(), resolve(event)));
        return Collections.unmodifiableMap(result);
    }

    private EventAssignmentSnapshot toSnapshot(Long eventId, Person person) {
        if (person == null || person.getId() == null) {
            throw new BusinessException("Participante legado valido e obrigatorio para resolver atribuicoes");
        }
        return new EventAssignmentSnapshot(
                null,
                eventId,
                person.getId(),
                resolveType(person),
                person.getName(),
                person.getPersonType()
        );
    }

    private EventAssignmentType resolveType(Person person) {
        if (person instanceof Priest) {
            return EventAssignmentType.PRIEST;
        }
        if (person instanceof Reader) {
            return EventAssignmentType.READER;
        }
        if (person instanceof Commentator) {
            return EventAssignmentType.COMMENTATOR;
        }
        if (person instanceof MinisterOfTheWord) {
            return EventAssignmentType.MINISTER_OF_THE_WORD;
        }
        if (person instanceof EucharisticMinister) {
            return EventAssignmentType.EUCHARISTIC_MINISTER;
        }
        return null;
    }

    private void validateEvent(CelebrationEvent event) {
        if (event == null || event.getId() == null || event.getId() <= 0) {
            throw new BusinessException("Evento valido e obrigatorio para resolver atribuicoes legadas");
        }
    }
}
