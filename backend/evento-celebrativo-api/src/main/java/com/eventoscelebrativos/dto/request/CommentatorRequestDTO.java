package com.eventoscelebrativos.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class CommentatorRequestDTO {

    @NotBlank(message = "O campo nome não pode ser vazio")
    private String name;

    @NotBlank(message = "O campo telefone não pode ser vazio")
    @Size(min = 11, max = 11, message = "O Telefone deve ter 11 digitos com o DD")
    private String phoneNumber;

    @NotNull(message = "O campo da data não pode ser vazio")
    @Past(message = "A data de nascimento só pode ser no passado")
    private LocalDate birthdayDate;

    @NotBlank(message = "O campo senha não pode ser vazio")
    @Size(min = 6, message = "A senha deve ter no minimo 6 caracteres")
    private String password;

    public CommentatorRequestDTO(){

    }

    public CommentatorRequestDTO(String name, String phoneNumber, LocalDate birthdayDate, String password) {
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.birthdayDate = birthdayDate;
        this.password = password;
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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
