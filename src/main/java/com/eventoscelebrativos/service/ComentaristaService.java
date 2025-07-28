package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.CommentatorRequestDTO;
import com.eventoscelebrativos.dto.response.CommentatorResponseDTO;

import java.util.List;

public interface ComentaristaService {

    CommentatorResponseDTO criarComentarista(CommentatorRequestDTO commentatorRequestDTO);
    List<CommentatorResponseDTO> listarTodosComentaristas();
    CommentatorResponseDTO buscarComentaristaPorId(Long id);
    CommentatorResponseDTO atualizarComentarista(Long id, CommentatorRequestDTO commentatorRequestDTO);
    void deletarComentarista(Long id);

}
