package com.eventoscelebrativos.service;

import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.Person;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Estado canonico de uma escala: pares pessoa/EventAssignmentType ja validados por
 * PersonMinistry e pela compatibilidade legada, na ordem em que foram aceitos. E o unico
 * insumo para validar e persistir a escala oficial em EventAssignment. Permite a mesma
 * pessoa em tipos de atribuicao diferentes e nao depende de person_type, de subclasses
 * de Person nem de tb_event_person.
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
        private final Set<PersonAssignmentTypeKey> usedPairs = new HashSet<>();

        private Builder() {
        }

        public Builder add(Person person, EventAssignmentType assignmentType) {
            if (person == null || person.getId() == null || assignmentType == null) {
                throw new BusinessException("Pessoa e tipo de atribuição do evento são obrigatórios");
            }
            if (!usedPairs.add(new PersonAssignmentTypeKey(person.getId(), assignmentType))) {
                throw new BusinessException("A mesma pessoa não pode ocupar a mesma função duas vezes na mesma escala");
            }
            entries.add(new Entry(person, assignmentType));
            return this;
        }

        public EventScaleAssignmentPlan build() {
            return new EventScaleAssignmentPlan(entries);
        }
    }

    private record PersonAssignmentTypeKey(Long personId, EventAssignmentType assignmentType) {
    }
}
