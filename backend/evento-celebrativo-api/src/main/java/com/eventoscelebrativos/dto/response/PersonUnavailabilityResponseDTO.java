package com.eventoscelebrativos.dto.response;

import java.time.LocalDate;

public class PersonUnavailabilityResponseDTO {

    private Long id;
    private LocalDate startDate;
    private LocalDate endDate;
    private String reason;

    public PersonUnavailabilityResponseDTO() {
    }

    public PersonUnavailabilityResponseDTO(Long id, LocalDate startDate, LocalDate endDate, String reason) {
        this.id = id;
        this.startDate = startDate;
        this.endDate = endDate;
        this.reason = reason;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
