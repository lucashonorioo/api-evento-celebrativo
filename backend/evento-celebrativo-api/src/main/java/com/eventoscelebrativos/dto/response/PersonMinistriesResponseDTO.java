package com.eventoscelebrativos.dto.response;

import com.eventoscelebrativos.model.MinistryType;

import java.util.List;

public class PersonMinistriesResponseDTO {

    private Long id;
    private List<MinistryType> ministries;

    public PersonMinistriesResponseDTO() {
    }

    public PersonMinistriesResponseDTO(Long id, List<MinistryType> ministries) {
        this.id = id;
        this.ministries = ministries;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public List<MinistryType> getMinistries() {
        return ministries;
    }

    public void setMinistries(List<MinistryType> ministries) {
        this.ministries = ministries;
    }
}
