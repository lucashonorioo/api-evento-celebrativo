package com.eventoscelebrativos.dto.response;

public class LocationResponseDTO {

    private Long id;
    private String churchName;
    private String address;

    public LocationResponseDTO(Long id, String churchName, String address) {
        this.id = id;
        this.churchName = churchName;
        this.address = address;
    }

    public Long getId() {
        return id;
    }

    public String getChurchName() {
        return churchName;
    }

    public String getAddress() {
        return address;
    }
}
