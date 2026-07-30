package com.eventoscelebrativos.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public class CelebrationEventRequestDTO {

    @NotBlank(message = "O campo nome não pode ser vazio")
    private String nameMassOrEvent;

    @NotNull(message = "O campo startAt não pode ser vazio")
    @FutureOrPresent(message = "O startAt só pode ser no presente ou futuro")
    private LocalDateTime startAt;

    @NotNull(message = "O campo endAt não pode ser vazio")
    private LocalDateTime endAt;

    @NotNull(message = "É obrigatório informar se é uma missa ou celebração.")
    private Boolean massOrCelebration;

    public CelebrationEventRequestDTO(){

    }

    public CelebrationEventRequestDTO(String nameMassOrEvent, LocalDateTime startAt, LocalDateTime endAt, Boolean massOrCelebration) {
        this.nameMassOrEvent = nameMassOrEvent;
        this.startAt = startAt;
        this.endAt = endAt;
        this.massOrCelebration = massOrCelebration;
    }

    public String getNameMassOrEvent() {
        return nameMassOrEvent;
    }

    public void setNameMassOrEvent(String nameMassOrEvent) {
        this.nameMassOrEvent = nameMassOrEvent;
    }

    public LocalDateTime getStartAt() {
        return startAt;
    }

    public void setStartAt(LocalDateTime startAt) {
        this.startAt = startAt;
    }

    public LocalDateTime getEndAt() {
        return endAt;
    }

    public void setEndAt(LocalDateTime endAt) {
        this.endAt = endAt;
    }

    public Boolean getMassOrCelebration() {
        return massOrCelebration;
    }

    public void setMassOrCelebration(Boolean massOrCelebration) {
        this.massOrCelebration = massOrCelebration;
    }
}
