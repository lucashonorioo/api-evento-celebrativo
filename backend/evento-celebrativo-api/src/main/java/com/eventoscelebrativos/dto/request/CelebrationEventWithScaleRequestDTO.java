package com.eventoscelebrativos.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public class CelebrationEventWithScaleRequestDTO {

    @NotBlank(message = "O campo nome não pode ser vazio")
    private String nameMassOrEvent;

    @NotNull(message = "O campo da data não pode ser vazio")
    @FutureOrPresent(message = "A data só pode ser no presente ou futuro")
    private LocalDate eventDate;

    @NotNull(message = "O campo da hora não pode ser vazio")
    private LocalTime eventTime;

    @NotNull(message = "É obrigatório informar se é uma missa ou celebração.")
    private Boolean massOrCelebration;

    @NotNull(message = "O campo locationId não pode ser vazio")
    @Positive(message = "O campo locationId deve ser positivo")
    private Long locationId;

    @Positive(message = "O campo priestId deve ser positivo")
    private Long priestId;

    private List<@Positive(message = "Os IDs dos leitores devem ser positivos") Long> readerIds;
    private List<@Positive(message = "Os IDs dos comentaristas devem ser positivos") Long> commentatorIds;
    private List<@Positive(message = "Os IDs dos ministros da palavra devem ser positivos") Long> ministerOfTheWordIds;
    private List<@Positive(message = "Os IDs dos ministros da Eucaristia devem ser positivos") Long> eucharisticMinisterIds;

    public CelebrationEventWithScaleRequestDTO() {
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

    public Long getLocationId() {
        return locationId;
    }

    public void setLocationId(Long locationId) {
        this.locationId = locationId;
    }

    public Long getPriestId() {
        return priestId;
    }

    public void setPriestId(Long priestId) {
        this.priestId = priestId;
    }

    public List<Long> getReaderIds() {
        return readerIds;
    }

    public void setReaderIds(List<Long> readerIds) {
        this.readerIds = readerIds;
    }

    public List<Long> getCommentatorIds() {
        return commentatorIds;
    }

    public void setCommentatorIds(List<Long> commentatorIds) {
        this.commentatorIds = commentatorIds;
    }

    public List<Long> getMinisterOfTheWordIds() {
        return ministerOfTheWordIds;
    }

    public void setMinisterOfTheWordIds(List<Long> ministerOfTheWordIds) {
        this.ministerOfTheWordIds = ministerOfTheWordIds;
    }

    public List<Long> getEucharisticMinisterIds() {
        return eucharisticMinisterIds;
    }

    public void setEucharisticMinisterIds(List<Long> eucharisticMinisterIds) {
        this.eucharisticMinisterIds = eucharisticMinisterIds;
    }
}
