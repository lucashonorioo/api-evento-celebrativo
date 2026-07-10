package com.eventoscelebrativos.dto.request;

import jakarta.validation.constraints.NotBlank;

public class PersonRoleUpdateRequestDTO {

    @NotBlank(message = "O campo role não pode ser vazio")
    private String role;

    public PersonRoleUpdateRequestDTO() {
    }

    public PersonRoleUpdateRequestDTO(String role) {
        this.role = role;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
