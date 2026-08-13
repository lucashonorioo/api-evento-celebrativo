package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.response.ParishStaffTeamResponseDTO;
import com.eventoscelebrativos.dto.response.PersonParishResponsibilitiesResponseDTO;

public interface ParishStaffAssignmentService {

    PersonParishResponsibilitiesResponseDTO findResponsibilitiesByPerson(Long personId);

    ParishStaffTeamResponseDTO findCurrentTeam();

    void grantPastor(Long personId);

    void revokePastor(Long personId);

    void grantSecretary(Long personId);

    void revokeSecretary(Long personId);
}
