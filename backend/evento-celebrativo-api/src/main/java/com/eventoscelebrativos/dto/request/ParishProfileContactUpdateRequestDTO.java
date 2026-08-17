package com.eventoscelebrativos.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;

/**
 * Subconjunto administrável pela secretaria paroquial (ROLE_OPERATOR com responsabilidade
 * PARISH_SECRETARY ativa) ou por ROLE_ADMIN via {@code PUT /paroquia/contato}. Deliberadamente não
 * possui {@code name} nem {@code diocese}: a identidade institucional da paróquia continua exclusiva
 * de {@code PUT /paroquia} (ParishProfileUpdateRequestDTO). A configuração efetiva de Jackson neste
 * projeto NÃO falha por padrão em propriedades desconhecidas (campos extras são silenciosamente
 * ignorados); por isso, como em {@link CelebrationEventRequestDTO}, {@link #rejectUnknownProperty}
 * rejeita explicitamente qualquer campo fora desta lista (ex.: name, diocese, id, configured) com
 * 400 (INVALID_REQUEST_BODY), fail-closed.
 */
public class ParishProfileContactUpdateRequestDTO {

    private String institutionalPhone;

    private String institutionalEmail;

    private String officeAddress;

    private String officeHours;

    public ParishProfileContactUpdateRequestDTO() {
    }

    public ParishProfileContactUpdateRequestDTO(
            String institutionalPhone,
            String institutionalEmail,
            String officeAddress,
            String officeHours
    ) {
        this.institutionalPhone = institutionalPhone;
        this.institutionalEmail = institutionalEmail;
        this.officeAddress = officeAddress;
        this.officeHours = officeHours;
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

    @JsonAnySetter
    private void rejectUnknownProperty(String property, Object value) {
        throw new IllegalArgumentException("Campo desconhecido no contrato de contato da paróquia: " + property);
    }
}
