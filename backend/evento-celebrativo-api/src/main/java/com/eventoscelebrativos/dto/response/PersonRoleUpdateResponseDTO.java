package com.eventoscelebrativos.dto.response;

import java.util.List;

public class PersonRoleUpdateResponseDTO {

    private Long id;
    private String name;
    private String phoneNumber;
    private String personType;
    private List<String> roles;

    public PersonRoleUpdateResponseDTO(Long id, String name, String phoneNumber, String personType, List<String> roles) {
        this.id = id;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.personType = personType;
        this.roles = roles;
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

    public String getPersonType() {
        return personType;
    }

    public List<String> getRoles() {
        return roles;
    }
}
