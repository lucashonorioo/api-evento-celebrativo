package com.eventoscelebrativos.dto.response;

public class PersonMinistryMembershipResponseDTO {

    private Long id;
    private String name;
    private boolean coordinator;

    public PersonMinistryMembershipResponseDTO() {
    }

    public PersonMinistryMembershipResponseDTO(Long id, String name, boolean coordinator) {
        this.id = id;
        this.name = name;
        this.coordinator = coordinator;
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

    public boolean isCoordinator() {
        return coordinator;
    }

    public void setCoordinator(boolean coordinator) {
        this.coordinator = coordinator;
    }
}
