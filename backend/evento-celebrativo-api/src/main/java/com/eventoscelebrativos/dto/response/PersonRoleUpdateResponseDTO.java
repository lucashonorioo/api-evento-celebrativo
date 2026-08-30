package com.eventoscelebrativos.dto.response;

import java.util.List;

public class PersonRoleUpdateResponseDTO {

    private Long id;
    private String name;
    private String phoneNumber;
    private List<MinistrySummaryDTO> ministries;
    private List<String> roles;

    public PersonRoleUpdateResponseDTO(
            Long id,
            String name,
            String phoneNumber,
            List<MinistrySummaryDTO> ministries,
            List<String> roles
    ) {
        this.id = id;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.ministries = ministries;
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

    public List<MinistrySummaryDTO> getMinistries() {
        return ministries;
    }

    public List<String> getRoles() {
        return roles;
    }
}
