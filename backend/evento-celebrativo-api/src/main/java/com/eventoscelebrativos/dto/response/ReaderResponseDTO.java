package com.eventoscelebrativos.dto.response;

import java.time.LocalDate;

public class ReaderResponseDTO {

    private Long id;
    private String name;
    private String phoneNumber;
    private LocalDate birthdayDate;

    public ReaderResponseDTO(Long id, String name, String phoneNumber, LocalDate birthdayDate) {
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
