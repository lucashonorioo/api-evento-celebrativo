package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.Person;

import java.util.Set;

/**
 * Resultado da sincronizacao atomica do conjunto administrativo de ministerios por Ministry.id.
 */
public record PersonMinistryCatalogSyncResult(
        Person person,
        Set<Long> activeMinistryIds,
        Set<Long> added,
        Set<Long> reactivated,
        Set<Long> deactivated,
        Set<Long> unchanged
) {
}
