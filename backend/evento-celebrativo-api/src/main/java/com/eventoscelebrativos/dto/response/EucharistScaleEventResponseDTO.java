package com.eventoscelebrativos.dto.response;

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
