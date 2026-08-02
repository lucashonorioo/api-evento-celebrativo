package com.eventoscelebrativos.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public class PersonActiveRequestDTO {

    @NotNull(message = "O campo active e obrigatorio")
    @Schema(description = "true ativa a pessoa; false desativa sem alterar UserAccount.enabled.")
    private Boolean active;

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
