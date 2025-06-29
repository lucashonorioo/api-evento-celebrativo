package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.model.EventoCelebrativo;
import com.eventoscelebrativos.repository.EventoCelebrativoRepository;
import com.eventoscelebrativos.service.EventoCelebrativoService;
import com.eventoscelebrativos.service.exception.BusinessRuleViolationException;
import com.eventoscelebrativos.service.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
        if (eventoCelebrativo.getNomeMissaOuEvento() == null) {
            throw new BusinessRuleViolationException("O nome da missa ou evento não pode ser vazio.");
        }
        if (eventoCelebrativo.getMissaOuCelebracao() == null) {
            throw new BusinessRuleViolationException("O campo 'missa ou celebração' não pode ser nulo.");
        }
        if (eventoCelebrativo.getDataHoraEvento() == null || eventoCelebrativo.getDataHoraEvento().isBefore(LocalDateTime.now())) {
            throw new BusinessRuleViolationException("A data e hora do evento não podem ser no passado.");
        }
        eventoCelebrativo.setId(null);
        return eventoCelebrativoRepository.save(eventoCelebrativo);
    }

    @Override
    public List<EventoCelebrativo> listarTodosEventos() {
        return eventoCelebrativoRepository.findAll();
    }

    @Override
    public Optional<EventoCelebrativo> buscarEventoPorId(Long id) {
        return eventoCelebrativoRepository.findById(id);
    }

    @Override
    public EventoCelebrativo atualizarEvento(Long id, EventoCelebrativo eventoAtualizado) {
        Optional<EventoCelebrativo> eventoCelebrativoOptional = eventoCelebrativoRepository.findById(id);
        if(eventoCelebrativoOptional.isEmpty()){
            throw new ResourceNotFoundException("Evento não encontrato com o id:" + id);
        }
        EventoCelebrativo eventoExistente = eventoCelebrativoOptional.get();

        if(eventoAtualizado.getNomeMissaOuEvento() == null){
            throw new BusinessRuleViolationException("O nome da missa ou evento não pode ser vazio.");
        }
        if(eventoAtualizado.getMissaOuCelebracao() == null){
            throw new BusinessRuleViolationException("O campo Missa ou Celebração não pode ser vazio");
        }
        if(eventoAtualizado.getDataHoraEvento() == null || eventoAtualizado.getDataHoraEvento().isBefore(LocalDateTime.now())){
            throw new BusinessRuleViolationException("A data não pode ser vazia e não pode ser no passado");
        }

        eventoExistente.setNomeMissaOuEvento(eventoAtualizado.getNomeMissaOuEvento());
        eventoExistente.setMissaOuCelebracao(eventoAtualizado.getMissaOuCelebracao());
        eventoExistente.setDataHoraEvento(eventoAtualizado.getDataHoraEvento());
        return eventoCelebrativoRepository.save(eventoExistente);
    }

    @Override
    public void deletarEvento(Long id) {
        Optional<EventoCelebrativo> eventoExistenteOptional = eventoCelebrativoRepository.findById(id);
        if(eventoExistenteOptional.isEmpty()){
            throw new ResourceNotFoundException("Evento não encontrado com o id: " + id);
        }
        eventoCelebrativoRepository.deleteById(id);
    }
}
