package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.model.EventoCelebrativo;
import com.eventoscelebrativos.repository.EventoCelebrativoRepository;
import com.eventoscelebrativos.service.EventoCelebrativoService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class EventoCelebrativoServiceImpl implements EventoCelebrativoService {

    private final EventoCelebrativoRepository eventoCelebrativoRepository;

    public EventoCelebrativoServiceImpl(EventoCelebrativoRepository eventoCelebrativoRepository) {
        this.eventoCelebrativoRepository = eventoCelebrativoRepository;
    }

    @Override
    public EventoCelebrativo criarEvento(EventoCelebrativo eventoCelebrativo) {
        eventoCelebrativo.setId(null);
        return eventoCelebrativoRepository.save(eventoCelebrativo);
    }

    @Override
    public List<EventoCelebrativo> listarTodosEventos() {
        return List.of();
    }

    @Override
    public Optional<EventoCelebrativo> buscarEventoPorId(Long id) {
        return Optional.empty();
    }

    @Override
    public EventoCelebrativo atualizarEvento(EventoCelebrativo eventoCelebrativo) {
        return null;
    }

    @Override
    public void deletarEvento(Long id) {

    }
}
