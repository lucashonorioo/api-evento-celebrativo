package com.eventoscelebrativos.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class CelebrationEventResponseDTO {

    private Long id;
    private String nameMassOrEvent;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Boolean massOrCelebration;


    public CelebrationEventResponseDTO(Long id, String nameMassOrEvent, LocalDateTime startAt, LocalDateTime endAt, Boolean massOrCelebration) {
        this.id = id;
        this.nameMassOrEvent = nameMassOrEvent;
        this.startAt = startAt;
        this.endAt = endAt;
        this.massOrCelebration = massOrCelebration;
    }

    public Long getId() {
        return id;
    }

    public String getNameMassOrEvent() {
        return nameMassOrEvent;
    }

    public LocalDateTime getStartAt() {
        return startAt;
    }

    public LocalDateTime getEndAt() {
        return endAt;
    }

    public Boolean getMassOrCelebration() {
        return massOrCelebration;
    }

    /**
     * @deprecated derivado exclusivamente de {@link #getStartAt()}; use startAt/endAt.
     * Mantido apenas como compatibilidade de leitura temporária para consumidores ainda nao
     * migrados; sera removido quando esses consumidores adotarem o contrato canonico.
     */
    @Deprecated
    public LocalDate getEventDate() {
        return startAt == null ? null : startAt.toLocalDate();
    }

    /**
     * @deprecated derivado exclusivamente de {@link #getStartAt()}; use startAt/endAt.
     * Mantido apenas como compatibilidade de leitura temporária para consumidores ainda nao
     * migrados; sera removido quando esses consumidores adotarem o contrato canonico.
     */
    @Deprecated
    public LocalTime getEventTime() {
        return startAt == null ? null : startAt.toLocalTime();
    }
}
