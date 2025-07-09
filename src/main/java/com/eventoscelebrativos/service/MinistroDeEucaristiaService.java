package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.MinistroDeEucaristiaRequestDTO;
import com.eventoscelebrativos.dto.response.MinistroDeEucaristiaResponseDTO;
import com.eventoscelebrativos.model.MinistroDeEucaristia;

import java.util.List;
import java.util.Optional;

public interface MinistroDeEucaristiaService {

    MinistroDeEucaristiaResponseDTO criarMinistroDeEucaristia(MinistroDeEucaristiaRequestDTO ministroDeEucaristiaRequestDTO);
    List<MinistroDeEucaristiaResponseDTO> listarTodosMinistroDeEucaristia();
    MinistroDeEucaristiaResponseDTO buscarMinistroDeEucaristiaPorId(Long id);
    MinistroDeEucaristiaResponseDTO atualizarMinistroDeEucaristia(Long id, MinistroDeEucaristiaRequestDTO ministroDeEucaristiaRequestDTO);
    void deletarMinistroDeEucaristia(Long id);

}
