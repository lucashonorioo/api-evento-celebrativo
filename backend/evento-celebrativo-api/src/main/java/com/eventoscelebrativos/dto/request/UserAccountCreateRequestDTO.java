package com.eventoscelebrativos.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

public class UserAccountCreateRequestDTO {

    @Schema(description = "Senha inicial da conta. Minimo de 6 caracteres; nao e retornada em nenhuma resposta.")
    private String initialPassword;

    @Schema(description = "Perfil unico da conta. Opcional; assume ROLE_OPERATOR. Valores: ROLE_ADMIN ou ROLE_OPERATOR.")
    private String role;

    public String getInitialPassword() {
        return initialPassword;
    }

    public void setInitialPassword(String initialPassword) {
        this.initialPassword = initialPassword;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
