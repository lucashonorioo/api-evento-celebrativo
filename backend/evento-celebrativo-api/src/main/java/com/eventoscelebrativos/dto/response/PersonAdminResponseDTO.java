package com.eventoscelebrativos.dto.response;

import com.eventoscelebrativos.model.MinistryType;

import java.util.ArrayList;
import java.util.List;

public class PersonAdminResponseDTO {

    private Long id;
    private String name;
    private String phoneNumber;
    private List<MinistryType> ministries = new ArrayList<>();
    private List<String> roles = new ArrayList<>();

    public PersonAdminResponseDTO() {
    }

    public PersonAdminResponseDTO(Long id, String name, String phoneNumber, List<MinistryType> ministries, List<String> roles) {
        this.id = id;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.ministries = ministries;
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

    public List<MinistryType> getMinistries() {
        return ministries;
    }

    public void setMinistries(List<MinistryType> ministries) {
        this.ministries = ministries;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }
}
