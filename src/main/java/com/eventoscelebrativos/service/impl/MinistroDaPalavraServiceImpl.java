package com.eventoscelebrativos.service.impl;




import com.eventoscelebrativos.dto.request.MinistroDaPalavraRequestDTO;
import com.eventoscelebrativos.dto.response.MinistroDaPalavraResponseDTO;
import com.eventoscelebrativos.mapper.MinistroDaPalavraMapper;
import com.eventoscelebrativos.model.MinistroDaPalavra;
import com.eventoscelebrativos.repository.MinistroDaPalavraRepository;
import com.eventoscelebrativos.service.MinistroDaPalavraService;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class MinistroDaPalavraServiceImpl implements MinistroDaPalavraService {

    private final MinistroDaPalavraRepository ministroDaPalavraRepository;
    private final MinistroDaPalavraMapper ministroDaPalavraMapper;

    public MinistroDaPalavraServiceImpl(MinistroDaPalavraRepository ministroDaPalavraRepository, MinistroDaPalavraMapper ministroDaPalavraMapper) {
        this.ministroDaPalavraRepository = ministroDaPalavraRepository;
        this.ministroDaPalavraMapper = ministroDaPalavraMapper;
    }


    @Override
    @Transactional
    public MinistroDaPalavraResponseDTO criarMinistroDaPalavra(MinistroDaPalavraRequestDTO ministroDaPalavraRequestDTO) {
        MinistroDaPalavra ministroDaPalavra = ministroDaPalavraMapper.toEntity(ministroDaPalavraRequestDTO);
        ministroDaPalavra = ministroDaPalavraRepository.save(ministroDaPalavra);

        return ministroDaPalavraMapper.toDto(ministroDaPalavra);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MinistroDaPalavraResponseDTO> listarTodosMinistroDaPalavra() {
        List<MinistroDaPalavra> ministrosDaPalavra = ministroDaPalavraRepository.findAll();
        return ministroDaPalavraMapper.toDtoList(ministrosDaPalavra);
    }

    @Override
    @Transactional(readOnly = true)
    public MinistroDaPalavraResponseDTO buscarMinistroDaPalavraPorId(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        MinistroDaPalavra ministroDaPalavra = ministroDaPalavraRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Ministro Da Palavra", id));
        return ministroDaPalavraMapper.toDto(ministroDaPalavra);
    }

    @Override
    @Transactional
    public MinistroDaPalavraResponseDTO atualizarMinistroDaPalavra(Long id, MinistroDaPalavraRequestDTO ministroDaPalavraRequestDTO) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        MinistroDaPalavra ministroDaPalavra = ministroDaPalavraRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Ministro Da Palavra", id));
        ministroDaPalavraMapper.atualizarMinistroDaPalavraFromDto(ministroDaPalavraRequestDTO, ministroDaPalavra);
        MinistroDaPalavra ministroDaPalavraSalvo = ministroDaPalavraRepository.save(ministroDaPalavra);
        return ministroDaPalavraMapper.toDto(ministroDaPalavraSalvo);
    }

    @Override
    @Transactional
    public void deletarMinistroDaPalavra(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        MinistroDaPalavra ministroDaPalavraOptional = ministroDaPalavraRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Ministro Da Palavra", id));
        ministroDaPalavraRepository.deleteById(id);
    }
}
