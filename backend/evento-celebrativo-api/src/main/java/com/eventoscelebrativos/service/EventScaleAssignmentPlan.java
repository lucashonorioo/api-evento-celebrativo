package com.eventoscelebrativos.service;

import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.MultipleAssignmentsForPersonInEventException;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.Person;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Estado canonico de uma escala: pares pessoa/EventAssignmentType ja validados por
 * PersonMinistry e pela compatibilidade legada, na ordem em que foram aceitos. E o unico
 * insumo para validar e persistir a escala oficial em EventAssignment. Cada pessoa ocupa no
 * maximo uma unica funcao na escala; nao depende de person_type, de subclasses de Person
 * nem de tb_event_person.
 */
public final class EventScaleAssignmentPlan {

    private final List<Entry> entries;

    private EventScaleAssignmentPlan(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<Entry> entries() {
        return entries;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public List<Person> people() {
        return entries.stream().map(Entry::person).toList();
    }

    public List<EventAssignmentTarget> toTargets() {
        return entries.stream()
                .map(entry -> new EventAssignmentTarget(entry.person(), entry.assignmentType()))
                .toList();
    }

    public record Entry(Person person, EventAssignmentType assignmentType) {
    }

    public static final class Builder {
        private final List<Entry> entries = new ArrayList<>();
        private final Map<Long, EventAssignmentType> usedTypeByPersonId = new HashMap<>();

        private Builder() {
        }

        public Builder add(Person person, EventAssignmentType assignmentType) {
            if (person == null || person.getId() == null || assignmentType == null) {
                throw new BusinessException("Pessoa e tipo de atribuição do evento são obrigatórios");
            }
            EventAssignmentType existingType = usedTypeByPersonId.putIfAbsent(person.getId(), assignmentType);
            if (existingType != null) {
                throw new MultipleAssignmentsForPersonInEventException(
                        null, person.getId(), EnumSet.of(existingType, assignmentType));
            }
            entries.add(new Entry(person, assignmentType));
            return this;
        }

        public EventScaleAssignmentPlan build() {
            return new EventScaleAssignmentPlan(entries);
        }
    }
}
