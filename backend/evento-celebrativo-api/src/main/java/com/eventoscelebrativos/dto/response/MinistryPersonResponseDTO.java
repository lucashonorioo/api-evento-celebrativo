package com.eventoscelebrativos.dto.response;

import java.time.LocalDate;

/**
 * Contrato de resposta escopado de {@code /ministerios/{ministryType}/pessoas}. Expoe somente os
 * dados necessarios ao gerenciamento ministerial (id, name, phoneNumber, birthdayDate); nunca expoe
 * accountId, username, accountEnabled, roles, passwordHash, tokenVersion, parishResponsibilities ou
 * coordinatedMinistries.
 */
public class MinistryPersonResponseDTO {

    private final Long id;
    private final String name;
    private final String phoneNumber;
    private final LocalDate birthdayDate;

    public MinistryPersonResponseDTO(Long id, String name, String phoneNumber, LocalDate birthdayDate) {
        this.id = id;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.birthdayDate = birthdayDate;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public LocalDate getBirthdayDate() {
        return birthdayDate;
    }
}
