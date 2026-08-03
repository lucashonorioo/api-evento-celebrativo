package com.eventoscelebrativos.model;

/**
 * Categoria de conteudo de uma {@link Notification}. Persistida como VARCHAR sem CHECK restrito aos
 * valores atuais (ver V16__create_notification_management), para permitir novas categorias em
 * comandos SYSTEM futuros (ex.: conflitos de escala) sem nova migration de schema.
 */
public enum NotificationCategory {
    GENERAL
}
