package com.eventoscelebrativos.dto.response;

import com.eventoscelebrativos.model.MinistryType;

import java.util.List;

public class PersonMinistriesResponseDTO {

    private Long id;
    private List<MinistryType> ministries;
    private List<MinistryType> coordinatedMinistries;

    public PersonMinistriesResponseDTO() {
    }

    public PersonMinistriesResponseDTO(Long id, List<MinistryType> ministries, List<MinistryType> coordinatedMinistries) {
        this.id = id;
        this.ministries = ministries;
        this.coordinatedMinistries = coordinatedMinistries;
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

    public List<MinistryType> getCoordinatedMinistries() {
        return coordinatedMinistries;
    }

    public void setCoordinatedMinistries(List<MinistryType> coordinatedMinistries) {
        this.coordinatedMinistries = coordinatedMinistries;
    }
}
