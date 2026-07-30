package com.eventoscelebrativos.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;
import java.util.List;

public class CelebrationEventWithScaleRequestDTO {

    @NotBlank(message = "O campo nome não pode ser vazio")
    private String nameMassOrEvent;

    @NotNull(message = "O campo startAt não pode ser vazio")
    private LocalDateTime startAt;

    /**
     * Termino previsto do compromisso para planejamento de disponibilidade e escala.
     * Nao representa o termino real da celebracao.
     */
    @NotNull(message = "O campo endAt não pode ser vazio")
    private LocalDateTime endAt;

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

    @JsonAnySetter
    private void rejectUnknownProperty(String property, Object value) {
        throw new IllegalArgumentException("Campo desconhecido no contrato de evento com escala: " + property);
    }
}
