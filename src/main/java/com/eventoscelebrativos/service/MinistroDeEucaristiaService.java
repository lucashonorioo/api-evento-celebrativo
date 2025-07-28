package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.EucharisticMinisterRequestDTO;
import com.eventoscelebrativos.dto.response.EucharisticMinisterResponseDTO;

import java.util.List;

public interface MinistroDeEucaristiaService {

    EucharisticMinisterResponseDTO criarMinistroDeEucaristia(EucharisticMinisterRequestDTO eucharisticMinisterRequestDTO);
    List<EucharisticMinisterResponseDTO> listarTodosMinistroDeEucaristia();
    EucharisticMinisterResponseDTO buscarMinistroDeEucaristiaPorId(Long id);
    EucharisticMinisterResponseDTO atualizarMinistroDeEucaristia(Long id, EucharisticMinisterRequestDTO eucharisticMinisterRequestDTO);
    void deletarMinistroDeEucaristia(Long id);

}
