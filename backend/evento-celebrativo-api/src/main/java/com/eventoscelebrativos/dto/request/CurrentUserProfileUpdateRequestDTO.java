package com.eventoscelebrativos.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;

import java.time.LocalDate;

public class CurrentUserProfileUpdateRequestDTO {

    @NotBlank(message = "O campo nome não pode ser vazio")
    private String name;

    @NotNull(message = "O campo da data não pode ser vazio")
    @Past(message = "A data de nascimento só pode ser no passado")
    private LocalDate birthdayDate;

    public CurrentUserProfileUpdateRequestDTO() {
    }

    public CurrentUserProfileUpdateRequestDTO(String name, LocalDate birthdayDate) {
        this.name = name;
        this.birthdayDate = birthdayDate;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDate getBirthdayDate() {
        return birthdayDate;
    }

    public void setBirthdayDate(LocalDate birthdayDate) {
        this.birthdayDate = birthdayDate;
    }
}
