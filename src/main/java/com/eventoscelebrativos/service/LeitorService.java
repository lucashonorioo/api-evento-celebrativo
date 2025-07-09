package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.LeitorRequestDTO;
import com.eventoscelebrativos.dto.response.LeitorResponseDTO;
import com.eventoscelebrativos.model.Leitor;

import java.util.List;
import java.util.Optional;

public interface LeitorService {

    LeitorResponseDTO criarLeitor(LeitorRequestDTO leitor);
    List<LeitorResponseDTO> listarTodosLeitor();
    LeitorResponseDTO buscarLeitorPorId(Long id);
    LeitorResponseDTO atualizarLeitor(Long id, LeitorRequestDTO leitorRequestDTO);
    void deletarLeitor(Long id);

}
