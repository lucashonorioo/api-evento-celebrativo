package com.eventoscelebrativos.dto.response;

import com.eventoscelebrativos.model.CelebrationEventStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class CelebrationEventScaleParticipationDetailResponseDTO {

    private Long eventId;
    private String eventName;
    private LocalDateTime startAt;
    /** Termino previsto do compromisso para planejamento de disponibilidade e escala. */
    private LocalDateTime endAt;
    private Boolean massOrCelebration;
    private CelebrationEventStatus status;
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

    public Boolean getMassOrCelebration() {
        return massOrCelebration;
    }

    public void setMassOrCelebration(Boolean massOrCelebration) {
        this.massOrCelebration = massOrCelebration;
    }

    public CelebrationEventStatus getStatus() {
        return status;
    }

    public void setStatus(CelebrationEventStatus status) {
        this.status = status;
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
