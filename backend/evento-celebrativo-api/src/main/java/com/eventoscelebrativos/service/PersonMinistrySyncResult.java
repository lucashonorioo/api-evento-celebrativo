package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;

import java.util.Set;

/**
 * Resultado da sincronizacao atomica do conjunto de ministerios de uma pessoa,
 * expondo o estado final e as quatro categorias de mudanca aplicadas por {@link PersonMinistryDiff}.
 */
public record PersonMinistrySyncResult(
        Person person,
        Set<MinistryType> activeMinistries,
        Set<MinistryType> added,
        Set<MinistryType> reactivated,
        Set<MinistryType> deactivated,
        Set<MinistryType> unchanged
) {
}
