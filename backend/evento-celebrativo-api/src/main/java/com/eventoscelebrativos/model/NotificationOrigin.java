package com.eventoscelebrativos.model;

/**
 * Origem de uma {@link Notification}. ADMIN e criada exclusivamente pelo endpoint administrativo de
 * envio; SYSTEM e criada exclusivamente por comandos internos tipados, nunca escolhida por um
 * controller.
 */
public enum NotificationOrigin {
    ADMIN,
    SYSTEM
}
