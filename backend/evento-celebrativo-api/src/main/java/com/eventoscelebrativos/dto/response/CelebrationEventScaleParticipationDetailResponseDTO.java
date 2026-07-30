package com.eventoscelebrativos.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class CelebrationEventScaleParticipationDetailResponseDTO {

    private Long eventId;
    private String eventName;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
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

    /**
     * @deprecated derivado exclusivamente de {@link #getStartAt()}; use startAt/endAt.
     */
    @Deprecated
    public LocalDate getEventDate() {
        return startAt == null ? null : startAt.toLocalDate();
    }

    /**
     * @deprecated derivado exclusivamente de {@link #getStartAt()}; use startAt/endAt.
     */
    @Deprecated
    public LocalTime getEventTime() {
        return startAt == null ? null : startAt.toLocalTime();
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
