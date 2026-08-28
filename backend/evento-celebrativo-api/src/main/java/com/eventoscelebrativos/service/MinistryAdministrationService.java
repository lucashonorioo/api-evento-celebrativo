package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.MinistryRequestDTO;
import com.eventoscelebrativos.dto.request.MinistryStatusUpdateRequestDTO;
import com.eventoscelebrativos.dto.response.MinistryResponseDTO;

import java.util.List;

public interface MinistryAdministrationService {

    List<MinistryResponseDTO> findAll();

    MinistryResponseDTO findById(Long ministryId);

    MinistryResponseDTO create(MinistryRequestDTO requestDTO);

    MinistryResponseDTO rename(Long ministryId, MinistryRequestDTO requestDTO);

    MinistryResponseDTO updateStatus(Long ministryId, MinistryStatusUpdateRequestDTO requestDTO);
}
