package com.eventoscelebrativos.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class ReaderUpdateRequestDTO extends PersonCadastralUpdateRequestDTO {

    @NotBlank(message = "O campo nome nao pode ser vazio")
    private String name;

    @NotBlank(message = "O campo telefone nao pode ser vazio")
    @Size(min = 11, max = 11, message = "O telefone deve ter 11 digitos com o DD")
    private String phoneNumber;

    @NotNull(message = "O campo da data nao pode ser vazio")
    @Past(message = "A data de nascimento so pode ser no passado")
    private LocalDate birthdayDate;

    public ReaderUpdateRequestDTO() {
    }

    public ReaderUpdateRequestDTO(String name, String phoneNumber, LocalDate birthdayDate) {
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.birthdayDate = birthdayDate;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public LocalDate getBirthdayDate() {
        return birthdayDate;
    }

    public void setBirthdayDate(LocalDate birthdayDate) {
        this.birthdayDate = birthdayDate;
    }
}
