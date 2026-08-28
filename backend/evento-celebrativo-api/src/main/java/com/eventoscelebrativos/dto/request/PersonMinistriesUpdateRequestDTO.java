package com.eventoscelebrativos.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public class PersonMinistriesUpdateRequestDTO {

    @NotNull(message = "O campo ministryIds nao pode ser nulo")
    private List<Long> ministryIds;

    public PersonMinistriesUpdateRequestDTO() {
    }

    public PersonMinistriesUpdateRequestDTO(List<Long> ministryIds) {
        this.ministryIds = ministryIds;
    }

    public List<Long> getMinistryIds() {
        return ministryIds;
    }

    public void setMinistryIds(List<Long> ministryIds) {
        this.ministryIds = ministryIds;
    }
}
