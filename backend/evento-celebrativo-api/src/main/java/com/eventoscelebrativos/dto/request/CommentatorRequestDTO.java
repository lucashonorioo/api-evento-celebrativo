package com.eventoscelebrativos.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class CommentatorRequestDTO implements PersonAccessRequest {

    @NotBlank(message = "O campo nome nao pode ser vazio")
    private String name;

    @NotBlank(message = "O campo telefone nao pode ser vazio")
    @Size(min = 11, max = 11, message = "O telefone deve ter 11 digitos com o DD")
    private String phoneNumber;

    @NotNull(message = "O campo da data nao pode ser vazio")
    @Past(message = "A data de nascimento so pode ser no passado")
    private LocalDate birthdayDate;

    @Schema(description = "Senha opcional. Na criacao, quando presente sem createAccess, preserva o payload legado e cria conta.")
    private String password;

    @Schema(description = "Opcional. true cria pessoa e conta; false cria somente pessoa; ausente preserva compatibilidade legada.")
    private Boolean createAccess;

    @Schema(description = "Perfil da conta quando createAccess=true ou quando a senha legada cria conta. Valores: ROLE_ADMIN ou ROLE_OPERATOR.")
    private String accessRole;

    public CommentatorRequestDTO() {
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

    @Override
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public Boolean getCreateAccess() {
        return createAccess;
    }

    public void setCreateAccess(Boolean createAccess) {
        this.createAccess = createAccess;
    }

    @Override
    public String getAccessRole() {
        return accessRole;
    }

    public void setAccessRole(String accessRole) {
        this.accessRole = accessRole;
    }
}
