package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.EventoCelebrativoRequestDTO;
import com.eventoscelebrativos.dto.response.EventoCelebrativoResponseDTO;
import com.eventoscelebrativos.dto.response.EventoEscalaMinistrosResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

public interface EventoCelebrativoService {

    EventoCelebrativoResponseDTO criarEvento(EventoCelebrativoRequestDTO eventoCelebrativoRequestDTO);
    List<EventoCelebrativoResponseDTO> listarTodosEventos();
    Page<EventoEscalaMinistrosResponseDTO> listarEscalaMinsEucaristia(Pageable pageable, LocalDate dataInicial, LocalDate dataFinal);
    EventoCelebrativoResponseDTO buscarEventoPorId(Long id);
    EventoCelebrativoResponseDTO atualizarEvento(Long id, EventoCelebrativoRequestDTO eventoCelebrativoRequestDTO);
    void deletarEvento(Long id);

}
