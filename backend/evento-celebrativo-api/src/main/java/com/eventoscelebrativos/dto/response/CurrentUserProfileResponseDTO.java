package com.eventoscelebrativos.dto.response;

import com.eventoscelebrativos.model.MinistryType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class CurrentUserProfileResponseDTO {

    private Long id;
    private String name;
    private String phoneNumber;
    private LocalDate birthdayDate;
    private List<String> roles = new ArrayList<>();
    private List<MinistryType> ministries = new ArrayList<>();

    public CurrentUserProfileResponseDTO() {
    }

    public CurrentUserProfileResponseDTO(
            Long id,
            String name,
            String phoneNumber,
            LocalDate birthdayDate,
            List<String> roles,
            List<MinistryType> ministries
    ) {
        this.id = id;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.birthdayDate = birthdayDate;
        this.roles = roles;
        this.ministries = ministries;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public List<MinistryType> getMinistries() {
        return ministries;
    }

    public void setMinistries(List<MinistryType> ministries) {
        this.ministries = ministries;
    }
}
