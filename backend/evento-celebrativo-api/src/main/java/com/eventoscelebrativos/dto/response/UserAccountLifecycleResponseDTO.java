package com.eventoscelebrativos.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public class UserAccountLifecycleResponseDTO {

    @Schema(description = "Identificador da pessoa. Nao expoe accountId.")
    private Long personId;

    @Schema(description = "Status atual da pessoa.")
    private boolean personActive;

    @Schema(description = "Indica se existe conta de acesso para a pessoa.")
    private boolean accountExists;

    @Schema(description = "Username da conta quando existente; continua derivado do telefone.")
    private String username;

    @Schema(description = "Status enabled da conta quando existente.")
    private Boolean accountEnabled;

    @Schema(description = "Roles atuais da conta, ordenadas deterministicamente.")
    private List<String> roles;

    public UserAccountLifecycleResponseDTO(
            Long personId,
            boolean personActive,
            boolean accountExists,
            String username,
            Boolean accountEnabled,
            List<String> roles
    ) {
        this.personId = personId;
        this.personActive = personActive;
        this.accountExists = accountExists;
        this.username = username;
        this.accountEnabled = accountEnabled;
        this.roles = roles;
    }

    public Long getPersonId() {
        return personId;
    }

    public boolean isPersonActive() {
        return personActive;
    }

    public boolean isAccountExists() {
        return accountExists;
    }

    public String getUsername() {
        return username;
    }

    public Boolean getAccountEnabled() {
        return accountEnabled;
    }

    public List<String> getRoles() {
        return roles;
    }
}
