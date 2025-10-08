package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.PriestRequestDTO;
import com.eventoscelebrativos.dto.response.PriestResponseDTO;

import java.util.List;

public interface PriestService {

    PriestResponseDTO createPriest(PriestRequestDTO Padre);
    List<PriestResponseDTO> findAllPriests();
    PriestResponseDTO findPriestById(Long id);
    PriestResponseDTO updatePriest(Long id, PriestRequestDTO priestRequestDTO);
    void deletePriestById(Long id);

}
