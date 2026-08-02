package com.eventoscelebrativos.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

public class AdminPasswordResetRequestDTO {

    @Schema(description = "Nova senha definida por administrador para outra conta. Invalida tokens antigos.")
    private String newPassword;

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
