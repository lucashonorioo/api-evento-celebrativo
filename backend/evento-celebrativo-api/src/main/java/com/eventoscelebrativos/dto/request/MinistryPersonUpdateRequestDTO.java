package com.eventoscelebrativos.dto.request;

import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Atualizacao escopada de pessoa por um coordenador ({@code PUT /ministerios/{ministryType}/pessoas/{personId}}).
 * Aceita somente name e birthdayDate: phoneNumber nunca pode ser alterado por este fluxo (usado por
 * UserAccount/username). Qualquer outro campo no corpo - inclusive phoneNumber, password, roles,
 * ministries, active, personActive, account, accountEnabled, enabled, username, coordinator,
 * parishResponsibilities, ou campo desconhecido - e capturado por {@link JsonAnySetter} (mesmo com
 * valor null, vazio ou false) e rejeitado em {@link #rejectForbiddenFields()}, sem ignora-lo
 * silenciosamente.
 */
public class MinistryPersonUpdateRequestDTO {

    @NotBlank(message = "O campo nome não pode ser vazio")
    private String name;

    @NotNull(message = "O campo da data não pode ser vazio")
    @Past(message = "A data de nascimento só pode ser no passado")
    private LocalDate birthdayDate;

    private final Set<String> forbiddenFieldsPresent = new LinkedHashSet<>();

    public MinistryPersonUpdateRequestDTO() {
    }

    public MinistryPersonUpdateRequestDTO(String name, LocalDate birthdayDate) {
        this.name = name;
        this.birthdayDate = birthdayDate;
    }

    @JsonAnySetter
    private void captureUnknown(String name, Object value) {
        forbiddenFieldsPresent.add(name);
    }

    public void rejectForbiddenFields() {
        if (!forbiddenFieldsPresent.isEmpty()) {
            throw new BadRequestException(
                    "Campos não permitidos na atualização escopada de pessoa: "
                            + String.join(", ", forbiddenFieldsPresent),
                    "MINISTRY_PERSON_UPDATE_FIELDS_INVALID"
            );
        }
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
