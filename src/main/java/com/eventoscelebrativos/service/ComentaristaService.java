package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.ComentaristaRequestDTO;
import com.eventoscelebrativos.dto.response.ComentaristaResponseDTO;
import com.eventoscelebrativos.model.Comentarista;

import java.util.List;
import java.util.Optional;

public interface ComentaristaService {

    ComentaristaResponseDTO criarComentarista(ComentaristaRequestDTO comentaristaRequestDTO);
    List<ComentaristaResponseDTO> listarTodosComentaristas();
    ComentaristaResponseDTO buscarComentaristaPorId(Long id);
    ComentaristaResponseDTO atualizarComentarista(Long id, ComentaristaRequestDTO comentaristaRequestDTO);
    void deletarComentarista(Long id);

}
