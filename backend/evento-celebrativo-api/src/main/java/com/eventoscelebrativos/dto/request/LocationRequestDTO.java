package com.eventoscelebrativos.dto.request;

import jakarta.validation.constraints.NotBlank;

public class LocationRequestDTO {

    @NotBlank(message = "O nome da igreja não pode ser vazio")
    private String churchName;

    @NotBlank(message = "O endereço não pode ser vazio")
    private String address;

    public LocationRequestDTO(){

    }

    public LocationRequestDTO(String churchName, String address) {
        this.churchName = churchName;
        this.address = address;
    }

    public String getChurchName() {
        return churchName;
    }

    public void setChurchName(String churchName) {
        this.churchName = churchName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}
