package com.eventoscelebrativos.dto.request;

import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Update cadastral administrativo (PUT /pessoas/{id}). Aceita somente name, phoneNumber e
 * birthdayDate. Qualquer outro campo presente no JSON - inclusive password, createAccess,
 * accessRole, role, roles, ministries, active, personActive, accountEnabled, enabled, username,
 * accountId, tokenVersion, passwordHash, account, ou qualquer campo desconhecido - e capturado por
 * {@link JsonAnySetter} (mesmo com valor null, vazio ou false) e rejeitado em
 * {@link #rejectForbiddenFields()}, sem ignora-lo silenciosamente.
 */
public class PersonAdminUpdateRequestDTO {

    @NotBlank(message = "O campo nome não pode ser vazio")
    private String name;

    @NotBlank(message = "O campo telefone não pode ser vazio")
    @Size(min = 11, max = 11, message = "O telefone deve ter 11 dígitos com o DD")
    private String phoneNumber;

    @NotNull(message = "O campo da data não pode ser vazio")
    @Past(message = "A data de nascimento só pode ser no passado")
    private LocalDate birthdayDate;

    private final Set<String> forbiddenFieldsPresent = new LinkedHashSet<>();

    public PersonAdminUpdateRequestDTO() {
    }

    public PersonAdminUpdateRequestDTO(String name, String phoneNumber, LocalDate birthdayDate) {
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.birthdayDate = birthdayDate;
    }

    @JsonAnySetter
    private void captureUnknown(String name, Object value) {
        forbiddenFieldsPresent.add(name);
    }

    public void rejectForbiddenFields() {
        if (!forbiddenFieldsPresent.isEmpty()) {
            throw new BadRequestException(
                    "Campos não permitidos na atualização cadastral administrativa: "
                            + String.join(", ", forbiddenFieldsPresent),
                    "PERSON_ADMIN_UPDATE_FIELDS_INVALID"
            );
        }
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
