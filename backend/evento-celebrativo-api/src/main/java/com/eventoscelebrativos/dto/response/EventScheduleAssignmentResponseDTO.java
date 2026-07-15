package com.eventoscelebrativos.dto.response;

public class EventScheduleAssignmentResponseDTO {

    private Long personId;
    private String personName;

    public EventScheduleAssignmentResponseDTO() {
    }

    public EventScheduleAssignmentResponseDTO(Long personId, String personName) {
        this.personId = personId;
        this.personName = personName;
    }

    public Long getPersonId() {
        return personId;
    }

    public void setPersonId(Long personId) {
        this.personId = personId;
    }

    public String getPersonName() {
        return personName;
    }

    public void setPersonName(String personName) {
        this.personName = personName;
    }
}
