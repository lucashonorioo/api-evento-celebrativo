package com.eventoscelebrativos.dto.response;

import com.eventoscelebrativos.model.CelebrationEventStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class CelebrationEventScaleDetailResponseDTO {

    private Long eventId;
    private String eventName;
    private LocalDateTime startAt;
    /** Termino previsto do compromisso para planejamento de disponibilidade e escala. */
    private LocalDateTime endAt;
    private Boolean massOrCelebration;
    private CelebrationEventStatus status;
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
