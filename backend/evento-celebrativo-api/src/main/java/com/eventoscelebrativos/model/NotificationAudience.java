package com.eventoscelebrativos.model;

/**
 * Publico de uma {@link Notification}: GLOBAL (todas as contas elegiveis), ADMIN (contas cuja unica
 * role e ROLE_ADMIN), MINISTRY (contas cujas pessoas tem vinculo ativo com um ou mais ministerios
 * selecionados) e DIRECT (contas de pessoas explicitamente informadas por personId).
 */
public enum NotificationAudience {
    GLOBAL,
    ADMIN,
    MINISTRY,
    DIRECT
}
