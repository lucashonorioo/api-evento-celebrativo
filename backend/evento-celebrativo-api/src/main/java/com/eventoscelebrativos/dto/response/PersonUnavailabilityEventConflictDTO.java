package com.eventoscelebrativos.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public class PersonUnavailabilityEventConflictDTO {

    private Long personId;
    private String personName;
    private List<String> assignmentTypes;
    private LocalDateTime startAt;
    private LocalDateTime endAt;

    public PersonUnavailabilityEventConflictDTO() {
    }

    public PersonUnavailabilityEventConflictDTO(
            Long personId,
            String personName,
            List<String> assignmentTypes,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {
        this.personId = personId;
        this.personName = personName;
        this.assignmentTypes = assignmentTypes;
        this.startAt = startAt;
        this.endAt = endAt;
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

    public List<String> getAssignmentTypes() {
        return assignmentTypes;
    }

    public void setAssignmentTypes(List<String> assignmentTypes) {
        this.assignmentTypes = assignmentTypes;
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
}
