package com.eventoscelebrativos.model;

/**
 * Filtro de listagem por resolucao de conflito de escala (secao 14). ALL nao filtra por categoria
 * nem por resolvedAt; ACTIVE e RESOLVED restringem a category=SCHEDULE_CONFLICT, diferindo apenas
 * por resolvedAt ser nulo ou nao.
 */
public enum NotificationResolutionFilter {
    ALL,
    ACTIVE,
    RESOLVED
}
