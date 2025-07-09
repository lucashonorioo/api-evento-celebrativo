package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.request.LocalRequestDTO;
import com.eventoscelebrativos.dto.response.LocalResponseDTO;
import com.eventoscelebrativos.mapper.LocalMapper;
import com.eventoscelebrativos.model.Local;
import com.eventoscelebrativos.repository.LocalRepository;
import com.eventoscelebrativos.service.LocalService;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class LocalServiceImpl implements LocalService {

    private final LocalRepository localRepository;
    private final LocalMapper localMapper;

    public LocalServiceImpl(LocalRepository localRepository, LocalMapper localMapper) {
        this.localRepository = localRepository;
        this.localMapper = localMapper;
    }

    @Override
    @Transactional
    public LocalResponseDTO criarLocal(LocalRequestDTO localRequestDTO) {
        Local local = localMapper.toEntity(localRequestDTO);
        local = localRepository.save(local);
        return localMapper.toDto(local);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LocalResponseDTO> listarTodosLocais() {
        List<Local> locais = localRepository.findAll();
        return localMapper.toDtoList(locais);
    }

    @Override
    @Transactional(readOnly = true)
    public LocalResponseDTO buscarLocalPorId(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        Local local = localRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Local", id));
        return localMapper.toDto(local);
    }

    @Override
    @Transactional
    public LocalResponseDTO atualizarLocal(Long id, LocalRequestDTO localRequestDTO) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        Local local = localRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Local", id));
        localMapper.atualizarLocalFromDto(localRequestDTO, local);
        Local localSalvo = localRepository.save(local);

        return localMapper.toDto(localSalvo);
    }

    @Override
    @Transactional
    public void deletarLocal(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        Local local = localRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Local", id));
        localRepository.deleteById(id);
    }
}
