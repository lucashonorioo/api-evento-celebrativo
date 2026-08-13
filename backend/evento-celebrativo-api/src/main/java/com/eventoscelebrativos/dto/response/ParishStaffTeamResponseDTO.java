package com.eventoscelebrativos.dto.response;

import java.util.List;

public class ParishStaffTeamResponseDTO {

    private ParishStaffMemberDTO pastor;
    private List<ParishStaffMemberDTO> secretaries;

    public ParishStaffTeamResponseDTO() {
    }

    public ParishStaffTeamResponseDTO(ParishStaffMemberDTO pastor, List<ParishStaffMemberDTO> secretaries) {
        this.pastor = pastor;
        this.secretaries = secretaries;
    }

    public ParishStaffMemberDTO getPastor() {
        return pastor;
    }

    public void setPastor(ParishStaffMemberDTO pastor) {
        this.pastor = pastor;
    }

    public List<ParishStaffMemberDTO> getSecretaries() {
        return secretaries;
    }

    public void setSecretaries(List<ParishStaffMemberDTO> secretaries) {
        this.secretaries = secretaries;
    }
}
