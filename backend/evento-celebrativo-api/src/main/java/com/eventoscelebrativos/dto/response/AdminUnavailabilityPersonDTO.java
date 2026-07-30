package com.eventoscelebrativos.dto.response;

import java.time.LocalDate;

public class AdminUnavailabilityPersonDTO {

    private Long personId;
    private String personName;
    private LocalDate startDate;
    private LocalDate endDate;

    public AdminUnavailabilityPersonDTO() {
    }

    public AdminUnavailabilityPersonDTO(Long personId, String personName, LocalDate startDate, LocalDate endDate) {
        this.personId = personId;
        this.personName = personName;
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
