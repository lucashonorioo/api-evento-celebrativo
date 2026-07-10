package com.eventoscelebrativos.dto.response;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class CelebrationEventScaleResponseDTO {

    private Long eventId;
    private String nameMassOrEvent;
    private LocalDate eventDate;
    private LocalTime eventTime;
    private Boolean massOrCelebration;
    private CelebrationEventScaleLocationResponseDTO location;
    private CelebrationEventScalePersonResponseDTO priest;
    private List<CelebrationEventScalePersonResponseDTO> readers = new ArrayList<>();
    private List<CelebrationEventScalePersonResponseDTO> commentators = new ArrayList<>();
    private List<CelebrationEventScalePersonResponseDTO> ministersOfTheWord = new ArrayList<>();
    private List<CelebrationEventScalePersonResponseDTO> eucharisticMinisters = new ArrayList<>();

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
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

    public CelebrationEventScaleLocationResponseDTO getLocation() {
        return location;
    }

    public void setLocation(CelebrationEventScaleLocationResponseDTO location) {
        this.location = location;
    }

    public CelebrationEventScalePersonResponseDTO getPriest() {
        return priest;
    }

    public void setPriest(CelebrationEventScalePersonResponseDTO priest) {
        this.priest = priest;
    }

    public List<CelebrationEventScalePersonResponseDTO> getReaders() {
        return readers;
    }

    public void setReaders(List<CelebrationEventScalePersonResponseDTO> readers) {
        this.readers = readers;
    }

    public List<CelebrationEventScalePersonResponseDTO> getCommentators() {
        return commentators;
    }

    public void setCommentators(List<CelebrationEventScalePersonResponseDTO> commentators) {
        this.commentators = commentators;
    }

    public List<CelebrationEventScalePersonResponseDTO> getMinistersOfTheWord() {
        return ministersOfTheWord;
    }

    public void setMinistersOfTheWord(List<CelebrationEventScalePersonResponseDTO> ministersOfTheWord) {
        this.ministersOfTheWord = ministersOfTheWord;
    }

    public List<CelebrationEventScalePersonResponseDTO> getEucharisticMinisters() {
        return eucharisticMinisters;
    }

    public void setEucharisticMinisters(List<CelebrationEventScalePersonResponseDTO> eucharisticMinisters) {
        this.eucharisticMinisters = eucharisticMinisters;
    }
}
