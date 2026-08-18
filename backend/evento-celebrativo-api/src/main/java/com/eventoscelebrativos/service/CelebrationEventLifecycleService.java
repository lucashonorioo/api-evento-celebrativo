package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.response.CelebrationEventResponseDTO;

/**
 * Transicoes de lifecycle de {@link com.eventoscelebrativos.model.CelebrationEvent}
 * (ACTIVE/CANCELLED). Responsabilidade separada de {@link CelebrationEventService}: este ultimo ja
 * concentra criacao, edicao, escala e exclusao fisica com protocolo de locks complexo; cancelamento e
 * reativacao tem regras proprias (janela temporal, idempotencia, revalidacao de elegibilidade na
 * reativacao) que justificam um service dedicado.
 */
public interface CelebrationEventLifecycleService {

    CelebrationEventResponseDTO cancel(Long eventId);

    CelebrationEventResponseDTO reactivate(Long eventId);
}
