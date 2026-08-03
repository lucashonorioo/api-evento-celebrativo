package com.eventoscelebrativos.dto.response;

public class InvalidRecipientDTO {

    private Long personId;
    private InvalidRecipientReason reason;

    public InvalidRecipientDTO() {
    }

    public InvalidRecipientDTO(Long personId, InvalidRecipientReason reason) {
        this.personId = personId;
        this.reason = reason;
    }

    public Long getPersonId() {
        return personId;
    }

    public void setPersonId(Long personId) {
        this.personId = personId;
    }

    public InvalidRecipientReason getReason() {
        return reason;
    }

    public void setReason(InvalidRecipientReason reason) {
        this.reason = reason;
    }
}
