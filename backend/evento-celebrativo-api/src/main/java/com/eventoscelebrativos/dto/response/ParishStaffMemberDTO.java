package com.eventoscelebrativos.dto.response;

public class ParishStaffMemberDTO {

    private Long personId;
    private String name;

    public ParishStaffMemberDTO() {
    }

    public ParishStaffMemberDTO(Long personId, String name) {
        this.personId = personId;
        this.name = name;
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
}
