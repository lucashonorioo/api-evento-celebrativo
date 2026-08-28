package com.eventoscelebrativos.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class MinistryRequestDTO {

    @NotBlank(message = "O nome do ministerio e obrigatorio")
    @Size(max = 150, message = "O nome do ministerio deve ter no maximo 150 caracteres")
    private String name;

    public MinistryRequestDTO() {
    }

    public MinistryRequestDTO(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
