package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.ParticipationResponseRequestDTO;
import com.eventoscelebrativos.dto.response.ParticipationResponseResponseDTO;

import java.util.Collection;
import java.util.Map;

public interface EventParticipationResponseService {

    ParticipationResponseResponseDTO respond(Long personId, Long eventId, ParticipationResponseRequestDTO requestDTO);

    /**
     * Chaves pelo eventId; somente respostas da pessoa informada.
     */
    Map<Long, ParticipationResponseSnapshot> findByPersonIdAndEventIds(Long personId, Collection<Long> eventIds);

    /**
     * Chaves pelo personId; todas as respostas do evento informado.
     */
    Map<Long, ParticipationResponseSnapshot> findByEventId(Long eventId);

    void retainOnlyForPersonIds(Long eventId, Collection<Long> personIdsStillAssigned);
}
