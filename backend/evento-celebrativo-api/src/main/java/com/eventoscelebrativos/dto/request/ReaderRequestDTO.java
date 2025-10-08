package com.eventoscelebrativos.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class ReaderRequestDTO {

    @NotBlank(message = "O campo nome não pode ser vazio")
    private String name;

    @NotBlank(message = "O campo telefone não pode ser vazio")
    @Size(min = 11, max = 11, message = "O Telefone deve ter 11 digitos com o DD")
    private String phoneNumber;

    @NotNull(message = "O campo da data não pode ser vazio")
    @Past(message = "A data de nascimento só pode ser no passado")
    private LocalDate birthdayDate;

    public ReaderRequestDTO(){

    }

    public ReaderRequestDTO(String name, String phoneNumber, LocalDate birthdayDate) {
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
