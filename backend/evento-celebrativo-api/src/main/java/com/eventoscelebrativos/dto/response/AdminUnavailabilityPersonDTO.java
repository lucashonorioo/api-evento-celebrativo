package com.eventoscelebrativos.dto.response;

import java.util.List;

public class AdminUnavailabilityPersonDTO {

    private Long personId;
    private String personName;
    private List<AdminUnavailabilityRangeDTO> unavailabilities;

    public AdminUnavailabilityPersonDTO() {
    }

    public AdminUnavailabilityPersonDTO(Long personId, String personName, List<AdminUnavailabilityRangeDTO> unavailabilities) {
        this.personId = personId;
        this.personName = personName;
        this.unavailabilities = unavailabilities;
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

    public List<AdminUnavailabilityRangeDTO> getUnavailabilities() {
        return unavailabilities;
    }

    public void setUnavailabilities(List<AdminUnavailabilityRangeDTO> unavailabilities) {
        this.unavailabilities = unavailabilities;
    }
}
