package com.eventoscelebrativos.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public class CelebrationEventRequestDTO {

    @NotBlank(message = "O campo nome não pode ser vazio")
    private String nameMassOrEvent;

    @NotNull(message = "O campo da data não pode ser vazio")
    @FutureOrPresent(message = "A data só pode ser no presente ou futuro")
    private LocalDate eventDate;

    @NotNull(message = "O campo da hora não pode ser vazio")
    private LocalTime eventTime;

    @NotNull(message = "É obrigatório informar se é uma missa ou celebração.")
    private Boolean massOrCelebration;

    public CelebrationEventRequestDTO(){

    }

    public CelebrationEventRequestDTO(String nameMassOrEvent, LocalDate eventDate, LocalTime eventTime, Boolean massOrCelebration) {
        this.nameMassOrEvent = nameMassOrEvent;
        this.eventDate = eventDate;
        this.eventTime = eventTime;
        this.massOrCelebration = massOrCelebration;
    }

    public String getNameMassOrEvent() {
        return nameMassOrEvent;
    }

    public void setNameMassOrEvent(String nameMassOrEvent) {
        this.nameMassOrEvent = nameMassOrEvent;
    }

    public LocalDate getEventDate() {
        return eventDate;
    }

    public void setEventDate(LocalDate eventDate) {
        this.eventDate = eventDate;
    }

    public LocalTime getEventTime() {
        return eventTime;
    }

    public void setEventTime(LocalTime eventTime) {
        this.eventTime = eventTime;
    }

    public Boolean getMassOrCelebration() {
        return massOrCelebration;
    }

    public void setMassOrCelebration(Boolean massOrCelebration) {
        this.massOrCelebration = massOrCelebration;
    }
}
