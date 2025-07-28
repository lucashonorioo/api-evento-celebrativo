package com.eventoscelebrativos.dto.response;

import java.time.LocalDate;
import java.time.LocalTime;

public class CelebrationEventResponseDTO {

    private Long id;
    private String nameMassOrEvent;
    private LocalDate eventDate;
    private LocalTime eventTime;
    private Boolean massOrCelebration;


    public CelebrationEventResponseDTO(Long id, String nameMassOrEvent, LocalDate eventDate, LocalTime eventTime, Boolean massOrCelebration) {
        this.id = id;
        this.nameMassOrEvent = nameMassOrEvent;
        this.eventDate = eventDate;
        this.eventTime = eventTime;
        this.massOrCelebration = massOrCelebration;
    }

    public Long getId() {
        return id;
    }

    public String getNameMassOrEvent() {
        return nameMassOrEvent;
    }

    public LocalDate getEventDate() {
        return eventDate;
    }

    public LocalTime getEventTime() {
        return eventTime;
    }

    public Boolean getMassOrCelebration() {
        return massOrCelebration;
    }
}
