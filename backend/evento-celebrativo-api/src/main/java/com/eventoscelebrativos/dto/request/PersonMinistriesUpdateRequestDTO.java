package com.eventoscelebrativos.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public class PersonMinistriesUpdateRequestDTO {

    @NotNull(message = "O campo ministries não pode ser nulo")
    private List<String> ministries;

    public PersonMinistriesUpdateRequestDTO() {
    }

    public PersonMinistriesUpdateRequestDTO(List<String> ministries) {
        this.ministries = ministries;
    }

    public List<String> getMinistries() {
        return ministries;
    }

    public void setMinistries(List<String> ministries) {
        this.ministries = ministries;
    }
}
