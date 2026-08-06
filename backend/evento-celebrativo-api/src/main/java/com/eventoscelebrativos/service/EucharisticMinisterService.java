package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.EucharisticMinisterRequestDTO;
import com.eventoscelebrativos.dto.request.EucharisticMinisterUpdateRequestDTO;
import com.eventoscelebrativos.dto.response.EucharisticMinisterResponseDTO;

import java.util.List;

public interface EucharisticMinisterService {

    EucharisticMinisterResponseDTO createEucharisticMinister(EucharisticMinisterRequestDTO eucharisticMinisterRequestDTO);
    List<EucharisticMinisterResponseDTO> findAllEucharisticMinisters();
    EucharisticMinisterResponseDTO findEucharisticMinistersById(Long id);
    EucharisticMinisterResponseDTO updateEucharisticMinisters(Long id, EucharisticMinisterUpdateRequestDTO eucharisticMinisterUpdateRequestDTO);
    void deleteEucharisticMinisterById(Long id);

}
