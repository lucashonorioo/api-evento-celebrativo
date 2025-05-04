package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.EventoCelebrativo;

import java.util.List;
import java.util.Optional;

public interface EventoCelebrativoService {

    EventoCelebrativo criarEvento(EventoCelebrativo eventoCelebrativo);
    List<EventoCelebrativo> listarTodosEventos();
    Optional<EventoCelebrativo> buscarEventoPorId(Long id);
    EventoCelebrativo atualizarEvento(EventoCelebrativo eventoCelebrativo);
    void deletarEvento(Long id);

}
