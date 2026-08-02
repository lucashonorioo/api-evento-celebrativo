package com.eventoscelebrativos.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

public class SelfPasswordChangeRequestDTO {

    @Schema(description = "Senha atual validada somente contra UserAccount.passwordHash.")
    private String currentPassword;

    @Schema(description = "Nova senha da propria conta. Invalida tokens antigos apos a requisicao atual.")
    private String newPassword;

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
