package com.eventoscelebrativos.dto.response;

import com.eventoscelebrativos.model.MinistryType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class PersonAdminResponseDTO {

    private Long id;
    private String name;
    private String phoneNumber;
    private LocalDate birthdayDate;
    private boolean personActive;
    private List<MinistryType> ministries = new ArrayList<>();
    private boolean accountExists;
    private Boolean accountEnabled;
    private String username;
    private List<String> roles = new ArrayList<>();

    public PersonAdminResponseDTO() {
    }

    public PersonAdminResponseDTO(
            Long id,
            String name,
            String phoneNumber,
            LocalDate birthdayDate,
            boolean personActive,
            List<MinistryType> ministries,
            boolean accountExists,
            Boolean accountEnabled,
            String username,
            List<String> roles
    ) {
        this.id = id;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.birthdayDate = birthdayDate;
        this.personActive = personActive;
        this.ministries = ministries;
        this.accountExists = accountExists;
        this.accountEnabled = accountEnabled;
        this.username = username;
        this.roles = roles;
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

    public boolean isPersonActive() {
        return personActive;
    }

    public void setPersonActive(boolean personActive) {
        this.personActive = personActive;
    }

    public List<MinistryType> getMinistries() {
        return ministries;
    }

    public void setMinistries(List<MinistryType> ministries) {
        this.ministries = ministries;
    }

    public boolean isAccountExists() {
        return accountExists;
    }

    public void setAccountExists(boolean accountExists) {
        this.accountExists = accountExists;
    }

    public Boolean getAccountEnabled() {
        return accountEnabled;
    }

    public void setAccountEnabled(Boolean accountEnabled) {
        this.accountEnabled = accountEnabled;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }
}
