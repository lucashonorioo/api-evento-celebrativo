package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.request.ReaderRequestDTO;
import com.eventoscelebrativos.dto.response.ReaderResponseDTO;
import com.eventoscelebrativos.mapper.ReaderMapper;
import com.eventoscelebrativos.model.Reader;
import com.eventoscelebrativos.repository.ReaderRepository;
import com.eventoscelebrativos.service.LeitorService;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LeitorServiceImpl implements LeitorService {

    private final ReaderRepository readerRepository;
    private final ReaderMapper readerMapper;

    public LeitorServiceImpl(ReaderRepository readerRepository, ReaderMapper readerMapper) {
        this.readerRepository = readerRepository;
        this.readerMapper = readerMapper;
    }


    @Override
    @Transactional
    public ReaderResponseDTO criarLeitor(ReaderRequestDTO readerRequestDTO) {
        Reader reader = readerMapper.toEntity(readerRequestDTO);
        reader = readerRepository.save(reader);
        return readerMapper.toDto(reader);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReaderResponseDTO> listarTodosLeitor() {
        List<Reader> reader = readerRepository.findAll();
        return readerMapper.toDtoList(reader);
    }

    @Override
    @Transactional(readOnly = true)
    public ReaderResponseDTO buscarLeitorPorId(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positvio e não nulo");
        }
        Reader reader = readerRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Leitor", id));
        return readerMapper.toDto(reader);
    }

    @Override
    @Transactional
    public ReaderResponseDTO atualizarLeitor(Long id, ReaderRequestDTO readerRequestDTO) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
       Reader reader = readerRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Leitor", id));
       readerMapper.atualizarLeitorFromDto(readerRequestDTO, reader);
       Reader readerSalvo = readerRepository.save(reader);

        return readerMapper.toDto(readerSalvo);
    }

    @Override
    @Transactional
    public void deletarLeitor(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        Reader reader = readerRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Leitor", id));
        readerRepository.deleteById(id);
    }
}
