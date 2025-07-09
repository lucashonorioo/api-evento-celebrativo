package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.request.LeitorRequestDTO;
import com.eventoscelebrativos.dto.response.LeitorResponseDTO;
import com.eventoscelebrativos.mapper.LeitorMapper;
import com.eventoscelebrativos.model.Leitor;
import com.eventoscelebrativos.repository.LeitorRepository;
import com.eventoscelebrativos.service.LeitorService;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LeitorServiceImpl implements LeitorService {

    private final LeitorRepository leitorRepository;
    private final LeitorMapper leitorMapper;

    public LeitorServiceImpl(LeitorRepository leitorRepository, LeitorMapper leitorMapper) {
        this.leitorRepository = leitorRepository;
        this.leitorMapper = leitorMapper;
    }


    @Override
    @Transactional
    public LeitorResponseDTO criarLeitor(LeitorRequestDTO leitorRequestDTO) {
        Leitor leitor = leitorMapper.toEntity(leitorRequestDTO);
        leitor = leitorRepository.save(leitor);
        return leitorMapper.toDto(leitor);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeitorResponseDTO> listarTodosLeitor() {
        List<Leitor> leitor = leitorRepository.findAll();
        return leitorMapper.toDtoList(leitor);
    }

    @Override
    @Transactional(readOnly = true)
    public LeitorResponseDTO buscarLeitorPorId(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positvio e não nulo");
        }
        Leitor leitor = leitorRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Leitor", id));
        return leitorMapper.toDto(leitor);
    }

    @Override
    @Transactional
    public LeitorResponseDTO atualizarLeitor(Long id, LeitorRequestDTO leitorRequestDTO) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
       Leitor leitor = leitorRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Leitor", id));
       leitorMapper.atualizarLeitorFromDto(leitorRequestDTO, leitor);
       Leitor leitorSalvo = leitorRepository.save(leitor);

        return leitorMapper.toDto(leitorSalvo);
    }

    @Override
    @Transactional
    public void deletarLeitor(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        Leitor leitor = leitorRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Leitor", id));
        leitorRepository.deleteById(id);
    }
}
