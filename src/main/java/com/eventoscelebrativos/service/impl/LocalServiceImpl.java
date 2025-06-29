package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.model.Local;
import com.eventoscelebrativos.repository.LocalRepository;
import com.eventoscelebrativos.service.LocalService;
import com.eventoscelebrativos.service.exception.BusinessRuleViolationException;
import com.eventoscelebrativos.service.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class LocalServiceImpl implements LocalService {

    private final LocalRepository localRepository;

    public LocalServiceImpl(LocalRepository localRepository) {
        this.localRepository = localRepository;
    }

    @Override
    public Local criarLocal(Local local) {
        if(local.getNomeDaIgreja() == null){
            throw new BusinessRuleViolationException("O nome do local não pode ser vazio");
        }
        if(local.getEndereco() == null){
            throw new BusinessRuleViolationException("O endereço não pode ser vazio");
        }
        return localRepository.save(local);
    }

    @Override
    public List<Local> listarTodosLocais() {
        return localRepository.findAll();
    }

    @Override
    public Optional<Local> buscarLocalPorId(Long id) {
        return localRepository.findById(id);
    }

    @Override
    public Local atualizarLocal(Long id, Local localAtualizado) {
        Optional<Local> localOptional = localRepository.findById(id);
        if(localOptional.isEmpty()){
            throw new ResourceNotFoundException("O local não foi encontrado com id: " + id);
        }

        if(localAtualizado.getNomeDaIgreja() == null){
            throw new BusinessRuleViolationException("O nome do local não pode ser vazio");
        }
        if(localAtualizado.getEndereco() == null){
            throw new BusinessRuleViolationException("O endereço não pode ser vazio");
        }
        Local localExistente = localOptional.get();
        localExistente.setNomeDaIgreja(localAtualizado.getNomeDaIgreja());
        localExistente.setEndereco(localAtualizado.getEndereco());
        return localRepository.save(localExistente);
    }

    @Override
    public void deletarLocal(Long id) {
        Optional<Local> localOptional = localRepository.findById(id);
        if(localOptional.isEmpty()){
            throw new ResourceNotFoundException("O local não foi encontrado com id: " + id);
        }
        localRepository.deleteById(id);
    }
}
