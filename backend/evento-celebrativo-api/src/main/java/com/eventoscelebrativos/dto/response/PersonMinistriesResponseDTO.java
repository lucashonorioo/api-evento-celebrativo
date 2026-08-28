package com.eventoscelebrativos.dto.response;

import java.util.List;

public class PersonMinistriesResponseDTO {

    private Long id;
    private List<PersonMinistryMembershipResponseDTO> ministries;

    public PersonMinistriesResponseDTO() {
    }

    public PersonMinistriesResponseDTO(Long id, List<PersonMinistryMembershipResponseDTO> ministries) {
        this.id = id;
        this.ministries = ministries;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public List<PersonMinistryMembershipResponseDTO> getMinistries() {
        return ministries;
    }

    public void setMinistries(List<PersonMinistryMembershipResponseDTO> ministries) {
        this.ministries = ministries;
    }
}
