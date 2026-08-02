package com.eventoscelebrativos.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public class UserAccountEnabledRequestDTO {

    @NotNull(message = "O campo enabled e obrigatorio")
    @Schema(description = "true habilita a conta; false desabilita sem excluir e sem alterar Person.active.")
    private Boolean enabled;

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
