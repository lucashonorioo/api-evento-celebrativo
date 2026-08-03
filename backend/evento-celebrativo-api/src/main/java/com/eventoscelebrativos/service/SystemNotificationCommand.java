package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.NotificationAudience;
import com.eventoscelebrativos.model.NotificationCategory;

import java.util.List;
import java.util.Objects;

/**
 * Comando interno para envio de notificacao SYSTEM. Usado exclusivamente por chamadores internos
 * (nenhum controller pode construir ou selecionar origin SYSTEM); nao contem sender, pois SYSTEM
 * nunca tem remetente, e nao contem resolvedAt, que comeca sempre nulo.
 */
public record SystemNotificationCommand(
        NotificationAudience audience,
        NotificationCategory category,
        String title,
        String message,
        List<MinistryType> ministryTypes,
        List<Long> personIds,
        String referenceType,
        Long referenceId,
        String sourceType,
        String sourceKey
) {

    public SystemNotificationCommand {
        Objects.requireNonNull(audience, "audience é obrigatório");
        Objects.requireNonNull(category, "category é obrigatório");
        ministryTypes = ministryTypes == null ? List.of() : List.copyOf(ministryTypes);
        personIds = personIds == null ? List.of() : List.copyOf(personIds);
    }
}
