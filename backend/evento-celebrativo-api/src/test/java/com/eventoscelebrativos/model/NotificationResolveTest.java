package com.eventoscelebrativos.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Prova, isolada de banco (unidade pura), o contrato de {@link Notification#resolve} apos o
 * hardening: rejeita resolvedAt nulo ou com fracao de segundo antes de tocar activeSourceKey,
 * permanece idempotente numa segunda chamada e continua restrita a SCHEDULE_CONFLICT.
 */
class NotificationResolveTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 1, 1, 10, 0, 0);

    @Test
    void shouldRejectNullResolvedAtWithoutClearingActiveSourceKey() {
        Notification notification = activeConflict();

        assertThrows(IllegalArgumentException.class, () -> notification.resolve(null));

        assertNull(notification.getResolvedAt());
        assertNotNull(notification.getActiveSourceKey(), "activeSourceKey nao pode ser limpo quando o instante e invalido");
    }

    @Test
    void shouldRejectResolvedAtWithFractionalSecondPrecisionWithoutClearingActiveSourceKey() {
        Notification notification = activeConflict();
        LocalDateTime fractional = LocalDateTime.of(2026, 1, 2, 10, 0, 0, 500_000_000);

        assertThrows(IllegalArgumentException.class, () -> notification.resolve(fractional));

        assertNull(notification.getResolvedAt());
        assertNotNull(notification.getActiveSourceKey(), "activeSourceKey nao pode ser limpo quando o instante e invalido");
    }

    @Test
    void shouldResolveAndClearActiveSourceKeyWhenResolvedAtIsValid() {
        Notification notification = activeConflict();
        LocalDateTime resolvedAt = LocalDateTime.of(2026, 1, 2, 10, 0, 0);

        notification.resolve(resolvedAt);

        assertEquals(resolvedAt, notification.getResolvedAt());
        assertNull(notification.getActiveSourceKey());
    }

    @Test
    void shouldBeIdempotentOnSecondResolveEvenWithADifferentValidInstant() {
        Notification notification = activeConflict();
        LocalDateTime firstResolvedAt = LocalDateTime.of(2026, 1, 2, 10, 0, 0);
        notification.resolve(firstResolvedAt);

        notification.resolve(LocalDateTime.of(2026, 1, 3, 10, 0, 0));

        assertEquals(firstResolvedAt, notification.getResolvedAt(), "Segunda chamada nao pode alterar o resolvedAt ja definido");
    }

    @Test
    void shouldBeIdempotentOnSecondResolveEvenWhenPassedAnInvalidInstant() {
        Notification notification = activeConflict();
        LocalDateTime firstResolvedAt = LocalDateTime.of(2026, 1, 2, 10, 0, 0);
        notification.resolve(firstResolvedAt);

        notification.resolve(null);

        assertEquals(firstResolvedAt, notification.getResolvedAt(),
                "Notificacao ja resolvida deve permanecer inalterada mesmo recebendo um argumento invalido");
    }

    @Test
    void shouldRejectResolvingNotificationsOutsideScheduleConflictCategory() {
        Notification notification = Notification.administrative(
                null, "Sistema", NotificationAudience.ADMIN, "Titulo", "Mensagem", CREATED_AT);

        assertThrows(IllegalStateException.class, () -> notification.resolve(LocalDateTime.of(2026, 1, 2, 10, 0, 0)));
    }

    private Notification activeConflict() {
        return Notification.scheduleConflict(
                NotificationAudience.ADMIN,
                "Conflito de escala detectado",
                "Mensagem de teste",
                "CELEBRATION_EVENT",
                1L,
                "SCHEDULE_UNAVAILABILITY_CONFLICT",
                "1:2",
                "SCHEDULE_UNAVAILABILITY_CONFLICT:1:2",
                CREATED_AT
        );
    }
}
