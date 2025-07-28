package com.eventoscelebrativos.dto.response;

import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.Person;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class EucharistScaleEventResponseDTO {

    String nameMassOrEvent;
    LocalDate eventDate;
    LocalTime eventTime;
    String churchName;
    List<String> nameMinisters;


    public EucharistScaleEventResponseDTO(String nameMassOrEvent, LocalDate eventDate, LocalTime eventTime, String churchName) {
        this.nameMassOrEvent = nameMassOrEvent;
        this.eventDate = eventDate;
        this.eventTime = eventTime;
        this.churchName = churchName;
        this.nameMinisters = new ArrayList<>();
    }

    public EucharistScaleEventResponseDTO(CelebrationEvent celebrationEvent) {
        nameMassOrEvent = celebrationEvent.getNameMassOrEvent();
        eventDate = celebrationEvent.getEventDate();
        eventTime = celebrationEvent.getEventTime();
        churchName = celebrationEvent.getLocations().get(0).getChurchName();
        nameMinisters = celebrationEvent.getPeople().stream().filter(p -> "eucharistic_minister".equals(p.getType())).map(Person::getName).toList();
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

    public String getChurchName() {
        return churchName;
    }

    public List<String> getNameMinisters() {
        return nameMinisters;
    }
}
