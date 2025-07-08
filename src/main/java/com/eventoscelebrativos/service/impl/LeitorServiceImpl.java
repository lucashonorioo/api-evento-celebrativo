package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.model.Leitor;
import com.eventoscelebrativos.repository.LeitorRepository;
import com.eventoscelebrativos.service.LeitorService;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class LeitorServiceImpl implements LeitorService {

    private final LeitorRepository leitorRepository;

    public LeitorServiceImpl(LeitorRepository leitorRepository) {
        this.leitorRepository = leitorRepository;
    }


    @Override
    @Transactional
    public Leitor criarLeitor(Leitor leitor) {
        if(leitor.getNome() == null){
            throw new BusinessException("O nome não pode ser vazio");
        }
        if(leitor.getDataAniversario() == null){
            throw new BusinessException("A data de aniversario não pode ser vazia");
        }
        return leitorRepository.save(leitor);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Leitor> listarTodosLeitor() {
        return leitorRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Leitor> buscarLeitorPorId(Long id) {
        return leitorRepository.findById(id);
    }

    @Override
    @Transactional
    public Leitor atualizarLeitor(Long id, Leitor leitorAtualizado) {
        Optional<Leitor> leitorOptional = leitorRepository.findById(id);
        if(leitorOptional.isEmpty()){
            throw new ResourceNotFoundException("O leitor não foi encontrado com id: " + id);
        }
        if(leitorAtualizado.getNome() == null){
            throw new BusinessException("O nome não pode ser vazio");
        }
        if(leitorAtualizado.getDataAniversario() == null){
            throw new BusinessException("A data de aniversario não pode ser vazia");
        }

        Leitor leitorExistente = leitorOptional.get();
        leitorExistente.setNome(leitorAtualizado.getNome());
        leitorExistente.setDataAniversario(leitorAtualizado.getDataAniversario());
        return leitorExistente;
    }

    @Override
    @Transactional
    public void deletarLeitor(Long id) {
        Optional<Leitor> leitorOptional = leitorRepository.findById(id);
        if (leitorOptional.isEmpty()){
            throw new ResourceNotFoundException("O leitor não foi encontrado com id: " + id);
        }
        leitorRepository.deleteById(id);
    }
}
