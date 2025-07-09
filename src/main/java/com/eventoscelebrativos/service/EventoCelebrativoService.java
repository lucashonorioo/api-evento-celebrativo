package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.EventoCelebrativoRequestDTO;
import com.eventoscelebrativos.dto.response.EventoCelebrativoResponseDTO;
import com.eventoscelebrativos.model.EventoCelebrativo;

import java.util.List;
import java.util.Optional;

public interface EventoCelebrativoService {

    EventoCelebrativoResponseDTO criarEvento(EventoCelebrativoRequestDTO eventoCelebrativoRequestDTO);
    List<EventoCelebrativoResponseDTO> listarTodosEventos();
    EventoCelebrativoResponseDTO buscarEventoPorId(Long id);
    EventoCelebrativoResponseDTO atualizarEvento(Long id, EventoCelebrativoRequestDTO eventoCelebrativoRequestDTO);
    void deletarEvento(Long id);

}
