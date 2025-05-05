package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.Comentarista;

import java.util.List;
import java.util.Optional;

public interface ComentaristaService {

    Comentarista criarComentarista(Comentarista comentarista);
    List<Comentarista> listarTodosComentaristas();
    Optional<Comentarista> buscarComentaristaPorId(Long id);
    Comentarista atualizarComentarista(Long id, Comentarista comentaristaAtualizado);
    void deletarComentarista(Long id);

}
