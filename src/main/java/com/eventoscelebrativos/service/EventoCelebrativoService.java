package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.CelebrationEventRequestDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventResponseDTO;
import com.eventoscelebrativos.dto.response.EucharistScaleEventResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

public interface EventoCelebrativoService {

    CelebrationEventResponseDTO criarEvento(CelebrationEventRequestDTO celebrationEventRequestDTO);
    List<CelebrationEventResponseDTO> listarTodosEventos();
    Page<EucharistScaleEventResponseDTO> listarEscalaMinsEucaristia(Pageable pageable, LocalDate dataInicial, LocalDate dataFinal);
    CelebrationEventResponseDTO buscarEventoPorId(Long id);
    CelebrationEventResponseDTO atualizarEvento(Long id, CelebrationEventRequestDTO celebrationEventRequestDTO);
    void deletarEvento(Long id);

}
