package com.eventoscelebrativos.dto.response;

import com.eventoscelebrativos.model.CelebrationEventStatus;

import java.time.LocalDateTime;

public class CelebrationEventResponseDTO {

    private Long id;
    private String nameMassOrEvent;
    private LocalDateTime startAt;
    /** Termino previsto do compromisso para planejamento de disponibilidade e escala. */
    private LocalDateTime endAt;
    private Boolean massOrCelebration;
    private CelebrationEventStatus status;


    public CelebrationEventResponseDTO(Long id, String nameMassOrEvent, LocalDateTime startAt, LocalDateTime endAt, Boolean massOrCelebration, CelebrationEventStatus status) {
        this.id = id;
        this.nameMassOrEvent = nameMassOrEvent;
        this.startAt = startAt;
        this.endAt = endAt;
        this.massOrCelebration = massOrCelebration;
        this.status = status;
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

    public CelebrationEventStatus getStatus() {
        return status;
    }

}
