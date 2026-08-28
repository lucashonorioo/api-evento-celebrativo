package com.eventoscelebrativos.dto.request;

import jakarta.validation.constraints.NotNull;

public class MinistryStatusUpdateRequestDTO {

    @NotNull(message = "O campo active e obrigatorio")
    private Boolean active;

    public MinistryStatusUpdateRequestDTO() {
    }

    public MinistryStatusUpdateRequestDTO(Boolean active) {
        this.active = active;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
