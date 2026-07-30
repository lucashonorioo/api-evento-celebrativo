package com.eventoscelebrativos.dto.response;

import java.time.LocalDate;
import java.util.List;

public class AdminUnavailabilityResponseDTO {

    private LocalDate date;
    private List<AdminUnavailabilityPersonDTO> people;

    public AdminUnavailabilityResponseDTO() {
    }

    public AdminUnavailabilityResponseDTO(LocalDate date, List<AdminUnavailabilityPersonDTO> people) {
        this.date = date;
        this.people = people;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public List<AdminUnavailabilityPersonDTO> getPeople() {
        return people;
    }

    public void setPeople(List<AdminUnavailabilityPersonDTO> people) {
        this.people = people;
    }
}
