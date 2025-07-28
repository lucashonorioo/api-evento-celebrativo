package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.ReaderRequestDTO;
import com.eventoscelebrativos.dto.response.ReaderResponseDTO;

import java.util.List;

public interface LeitorService {

    ReaderResponseDTO criarLeitor(ReaderRequestDTO leitor);
    List<ReaderResponseDTO> listarTodosLeitor();
    ReaderResponseDTO buscarLeitorPorId(Long id);
    ReaderResponseDTO atualizarLeitor(Long id, ReaderRequestDTO readerRequestDTO);
    void deletarLeitor(Long id);

}
