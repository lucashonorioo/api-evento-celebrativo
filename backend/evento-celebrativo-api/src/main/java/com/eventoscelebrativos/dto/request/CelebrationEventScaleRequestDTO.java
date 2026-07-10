package com.eventoscelebrativos.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public class CelebrationEventScaleRequestDTO {

    @NotNull(message = "O campo locationId não pode ser vazio")
    @Positive(message = "O campo locationId deve ser positivo")
    private Long locationId;

    @Positive(message = "O campo priestId deve ser positivo")
    private Long priestId;

    private List<@Positive(message = "Os IDs dos leitores devem ser positivos") Long> readerIds;
    private List<@Positive(message = "Os IDs dos comentaristas devem ser positivos") Long> commentatorIds;
    private List<@Positive(message = "Os IDs dos ministros da palavra devem ser positivos") Long> ministerOfTheWordIds;
    private List<@Positive(message = "Os IDs dos ministros da Eucaristia devem ser positivos") Long> eucharisticMinisterIds;

    public CelebrationEventScaleRequestDTO() {
    }

    public CelebrationEventScaleRequestDTO(
            Long locationId,
            Long priestId,
            List<Long> readerIds,
            List<Long> commentatorIds,
            List<Long> ministerOfTheWordIds,
            List<Long> eucharisticMinisterIds
    ) {
        this.locationId = locationId;
        this.priestId = priestId;
        this.readerIds = readerIds;
        this.commentatorIds = commentatorIds;
        this.ministerOfTheWordIds = ministerOfTheWordIds;
        this.eucharisticMinisterIds = eucharisticMinisterIds;
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
