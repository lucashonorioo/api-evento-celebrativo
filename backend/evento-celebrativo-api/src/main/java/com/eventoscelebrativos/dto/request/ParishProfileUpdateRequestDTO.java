package com.eventoscelebrativos.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ParishProfileUpdateRequestDTO {

    @NotBlank(message = "O campo nome não pode ser vazio")
    @Size(max = 150, message = "O nome deve ter no máximo 150 caracteres")
    private String name;

    @NotBlank(message = "O campo diocese não pode ser vazio")
    @Size(max = 150, message = "A diocese deve ter no máximo 150 caracteres")
    private String diocese;

    private String institutionalPhone;

    private String institutionalEmail;

    private String officeAddress;

    private String officeHours;

    public ParishProfileUpdateRequestDTO() {
    }

    public ParishProfileUpdateRequestDTO(
            String name,
            String diocese,
            String institutionalPhone,
            String institutionalEmail,
            String officeAddress,
            String officeHours
    ) {
        this.name = name;
        this.diocese = diocese;
        this.institutionalPhone = institutionalPhone;
        this.institutionalEmail = institutionalEmail;
        this.officeAddress = officeAddress;
        this.officeHours = officeHours;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDiocese() {
        return diocese;
    }

    public void setDiocese(String diocese) {
        this.diocese = diocese;
    }

    public String getInstitutionalPhone() {
        return institutionalPhone;
    }

    public void setInstitutionalPhone(String institutionalPhone) {
        this.institutionalPhone = institutionalPhone;
    }

    public String getInstitutionalEmail() {
        return institutionalEmail;
    }

    public void setInstitutionalEmail(String institutionalEmail) {
        this.institutionalEmail = institutionalEmail;
    }

    public String getOfficeAddress() {
        return officeAddress;
    }

    public void setOfficeAddress(String officeAddress) {
        this.officeAddress = officeAddress;
    }

    public String getOfficeHours() {
        return officeHours;
    }

    public void setOfficeHours(String officeHours) {
        this.officeHours = officeHours;
    }
}
