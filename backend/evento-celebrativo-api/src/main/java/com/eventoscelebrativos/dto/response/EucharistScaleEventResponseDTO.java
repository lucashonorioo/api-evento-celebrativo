package com.eventoscelebrativos.dto.response;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class EucharistScaleEventResponseDTO {

    String nameMassOrEvent;
    LocalDateTime startAt;
    /** Termino previsto do compromisso para planejamento de disponibilidade e escala. */
    LocalDateTime endAt;
    String churchName;
    List<String> nameMinisters;


    public EucharistScaleEventResponseDTO(String nameMassOrEvent, LocalDateTime startAt, LocalDateTime endAt, String churchName) {
        this.nameMassOrEvent = nameMassOrEvent;
        this.startAt = startAt;
        this.endAt = endAt;
        this.churchName = churchName;
        this.nameMinisters = new ArrayList<>();
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

    public String getChurchName() {
        return churchName;
    }

    public List<String> getNameMinisters() {
        return nameMinisters;
    }
}
