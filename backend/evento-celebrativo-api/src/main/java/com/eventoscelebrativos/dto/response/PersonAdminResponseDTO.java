package com.eventoscelebrativos.dto.response;

import java.util.ArrayList;
import java.util.List;

public class PersonAdminResponseDTO {

    private Long id;
    private String name;
    private String phoneNumber;
    private String personType;
    private List<String> roles = new ArrayList<>();

    public PersonAdminResponseDTO() {
    }

    public PersonAdminResponseDTO(Long id, String name, String phoneNumber, String personType, List<String> roles) {
        this.id = id;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.personType = personType;
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

    public String getPersonType() {
        return personType;
    }

    public void setPersonType(String personType) {
        this.personType = personType;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }
}
