package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.PersonMinistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Diferenca pura entre o conjunto de ministerios desejado para uma pessoa e o estado atual dela
 * em {@code tb_person_ministry} (ativo ou inativo). Nao acessa banco nem persiste nada; apenas
 * classifica cada {@link MinistryType} do conjunto desejado e cada {@link PersonMinistry} existente
 * em uma das quatro categorias de mudanca.
 */
public final class PersonMinistryDiff {

    private final Set<MinistryType> toAdd;
    private final List<PersonMinistry> toReactivate;
    private final List<PersonMinistry> toDeactivate;
    private final Set<MinistryType> unchanged;

    private PersonMinistryDiff(
            Set<MinistryType> toAdd,
            List<PersonMinistry> toReactivate,
            List<PersonMinistry> toDeactivate,
            Set<MinistryType> unchanged
    ) {
        this.toAdd = Collections.unmodifiableSet(toAdd);
        this.toReactivate = Collections.unmodifiableList(toReactivate);
        this.toDeactivate = Collections.unmodifiableList(toDeactivate);
        this.unchanged = Collections.unmodifiableSet(unchanged);
    }

    public static PersonMinistryDiff compute(Set<MinistryType> desired, List<PersonMinistry> existing) {
        Map<MinistryType, PersonMinistry> existingByType = existing.stream()
                .collect(Collectors.toMap(PersonMinistry::getMinistryType, Function.identity()));

        Set<MinistryType> toAdd = new LinkedHashSet<>();
        List<PersonMinistry> toReactivate = new ArrayList<>();
        Set<MinistryType> unchanged = new LinkedHashSet<>();
        for (MinistryType type : desired) {
            PersonMinistry existingMinistry = existingByType.get(type);
            if (existingMinistry == null) {
                toAdd.add(type);
            } else if (Boolean.TRUE.equals(existingMinistry.getActive())) {
                unchanged.add(type);
            } else {
                toReactivate.add(existingMinistry);
            }
        }

        List<PersonMinistry> toDeactivate = existing.stream()
                .filter(personMinistry -> Boolean.TRUE.equals(personMinistry.getActive()))
                .filter(personMinistry -> !desired.contains(personMinistry.getMinistryType()))
                .toList();

        return new PersonMinistryDiff(toAdd, toReactivate, toDeactivate, unchanged);
    }

    public Set<MinistryType> toAdd() {
        return toAdd;
    }

    public List<PersonMinistry> toReactivate() {
        return toReactivate;
    }

    public List<PersonMinistry> toDeactivate() {
        return toDeactivate;
    }

    public Set<MinistryType> unchanged() {
        return unchanged;
    }
}
