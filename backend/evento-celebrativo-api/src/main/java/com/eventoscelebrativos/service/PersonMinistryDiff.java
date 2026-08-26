package com.eventoscelebrativos.service;

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
 * Diferenca pura entre o conjunto de ministries persistentes desejado para uma pessoa e o estado
 * atual dela em {@code tb_person_ministry} (ativo ou inativo). Nao acessa banco nem persiste nada;
 * apenas classifica cada Ministry.id desejado e cada {@link PersonMinistry} existente em uma das
 * quatro categorias de mudanca.
 */
public final class PersonMinistryDiff {

    private final Set<Long> toAdd;
    private final List<PersonMinistry> toReactivate;
    private final List<PersonMinistry> toDeactivate;
    private final Set<Long> unchanged;

    private PersonMinistryDiff(
            Set<Long> toAdd,
            List<PersonMinistry> toReactivate,
            List<PersonMinistry> toDeactivate,
            Set<Long> unchanged
    ) {
        this.toAdd = Collections.unmodifiableSet(toAdd);
        this.toReactivate = Collections.unmodifiableList(toReactivate);
        this.toDeactivate = Collections.unmodifiableList(toDeactivate);
        this.unchanged = Collections.unmodifiableSet(unchanged);
    }

    public static PersonMinistryDiff compute(Set<Long> desiredMinistryIds, List<PersonMinistry> existing) {
        Map<Long, PersonMinistry> existingByMinistryId = existing.stream()
                .collect(Collectors.toMap(personMinistry -> personMinistry.getMinistry().getId(), Function.identity()));

        Set<Long> toAdd = new LinkedHashSet<>();
        List<PersonMinistry> toReactivate = new ArrayList<>();
        Set<Long> unchanged = new LinkedHashSet<>();
        for (Long ministryId : desiredMinistryIds) {
            PersonMinistry existingMinistry = existingByMinistryId.get(ministryId);
            if (existingMinistry == null) {
                toAdd.add(ministryId);
            } else if (Boolean.TRUE.equals(existingMinistry.getActive())) {
                unchanged.add(ministryId);
            } else {
                toReactivate.add(existingMinistry);
            }
        }

        List<PersonMinistry> toDeactivate = existing.stream()
                .filter(personMinistry -> Boolean.TRUE.equals(personMinistry.getActive()))
                .filter(personMinistry -> !desiredMinistryIds.contains(personMinistry.getMinistry().getId()))
                .toList();

        return new PersonMinistryDiff(toAdd, toReactivate, toDeactivate, unchanged);
    }

    public Set<Long> toAdd() {
        return toAdd;
    }

    public List<PersonMinistry> toReactivate() {
        return toReactivate;
    }

    public List<PersonMinistry> toDeactivate() {
        return toDeactivate;
    }

    public Set<Long> unchanged() {
        return unchanged;
    }
}
