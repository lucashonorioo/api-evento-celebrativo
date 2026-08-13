package com.eventoscelebrativos.dto.response;

import java.util.List;

public class PersonParishResponsibilitiesResponseDTO {

    private Long personId;
    private String name;
    private List<ParishStaffAssignmentDTO> responsibilities;

    public PersonParishResponsibilitiesResponseDTO() {
    }

    public PersonParishResponsibilitiesResponseDTO(Long personId, String name, List<ParishStaffAssignmentDTO> responsibilities) {
        this.personId = personId;
        this.name = name;
        this.responsibilities = responsibilities;
    }

    public Long getPersonId() {
        return personId;
    }

    public void setPersonId(Long personId) {
        this.personId = personId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<ParishStaffAssignmentDTO> getResponsibilities() {
        return responsibilities;
    }

    public void setResponsibilities(List<ParishStaffAssignmentDTO> responsibilities) {
        this.responsibilities = responsibilities;
    }
}
