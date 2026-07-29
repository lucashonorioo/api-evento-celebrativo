package com.eventoscelebrativos.dto.response;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class CelebrationEventScaleParticipationDetailResponseDTO {

    private Long eventId;
    private String eventName;
    private LocalDate eventDate;
    private LocalTime eventTime;
    private Boolean massOrCelebration;
    private CelebrationEventScaleLocationResponseDTO location;
    private CelebrationEventScaleParticipationPersonResponseDTO priest;
    private List<CelebrationEventScaleParticipationPersonResponseDTO> readers = new ArrayList<>();
    private List<CelebrationEventScaleParticipationPersonResponseDTO> commentators = new ArrayList<>();
    private List<CelebrationEventScaleParticipationPersonResponseDTO> ministersOfTheWord = new ArrayList<>();
    private List<CelebrationEventScaleParticipationPersonResponseDTO> eucharisticMinisters = new ArrayList<>();

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
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

    public CelebrationEventScaleParticipationPersonResponseDTO getPriest() {
        return priest;
    }

    public void setPriest(CelebrationEventScaleParticipationPersonResponseDTO priest) {
        this.priest = priest;
    }

    public List<CelebrationEventScaleParticipationPersonResponseDTO> getReaders() {
        return readers;
    }

    public void setReaders(List<CelebrationEventScaleParticipationPersonResponseDTO> readers) {
        this.readers = readers;
    }

    public List<CelebrationEventScaleParticipationPersonResponseDTO> getCommentators() {
        return commentators;
    }

    public void setCommentators(List<CelebrationEventScaleParticipationPersonResponseDTO> commentators) {
        this.commentators = commentators;
    }

    public List<CelebrationEventScaleParticipationPersonResponseDTO> getMinistersOfTheWord() {
        return ministersOfTheWord;
    }

    public void setMinistersOfTheWord(List<CelebrationEventScaleParticipationPersonResponseDTO> ministersOfTheWord) {
        this.ministersOfTheWord = ministersOfTheWord;
    }

    public List<CelebrationEventScaleParticipationPersonResponseDTO> getEucharisticMinisters() {
        return eucharisticMinisters;
    }

    public void setEucharisticMinisters(List<CelebrationEventScaleParticipationPersonResponseDTO> eucharisticMinisters) {
        this.eucharisticMinisters = eucharisticMinisters;
    }
}
