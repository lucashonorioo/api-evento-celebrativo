package com.eventoscelebrativos.dto.response;

import java.time.LocalDate;
import java.util.List;

public class PersonUnavailabilityEventConflictDTO {

    private Long personId;
    private String personName;
    private List<String> assignmentTypes;
    private LocalDate startDate;
    private LocalDate endDate;

    public PersonUnavailabilityEventConflictDTO() {
    }

    public PersonUnavailabilityEventConflictDTO(
            Long personId,
            String personName,
            List<String> assignmentTypes,
            LocalDate startDate,
            LocalDate endDate
    ) {
        this.personId = personId;
        this.personName = personName;
        this.assignmentTypes = assignmentTypes;
        this.startDate = startDate;
        this.endDate = endDate;
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

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }
}
