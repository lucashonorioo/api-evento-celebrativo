package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.request.ReaderRequestDTO;
import com.eventoscelebrativos.dto.response.ReaderResponseDTO;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.mapper.ReaderMapper;
import com.eventoscelebrativos.model.Reader;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.repository.ReaderRepository;
import com.eventoscelebrativos.repository.RoleRepository;
import com.eventoscelebrativos.service.ReaderService;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReaderServiceImpl implements ReaderService {

    private final ReaderRepository readerRepository;
    private final ReaderMapper readerMapper;

    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public ReaderServiceImpl(ReaderRepository readerRepository, ReaderMapper readerMapper, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.readerRepository = readerRepository;
        this.readerMapper = readerMapper;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }


    @Override
    @Transactional
    public ReaderResponseDTO createReader(ReaderRequestDTO readerRequestDTO) {
        Reader reader = readerMapper.toEntity(readerRequestDTO);

        reader.setPassword(passwordEncoder.encode(readerRequestDTO.getPassword()));

        Role operatorRole = roleRepository.findByAuthority("ROLE_OPERATOR")
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de acesso", "ROLE_OPERATOR"));

        reader.addRole(operatorRole);

        reader = readerRepository.save(reader);
        return readerMapper.toDto(reader);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReaderResponseDTO> findAllReaders() {
        List<Reader> reader = readerRepository.findAll();
        return readerMapper.toDtoList(reader);
    }

    @Override
    @Transactional(readOnly = true)
    public ReaderResponseDTO findReaderById(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positvio e não nulo");
        }
        Reader reader = readerRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Leitor", id));
        return readerMapper.toDto(reader);
    }

    @Override
    @Transactional
    public ReaderResponseDTO updateReader(Long id, ReaderRequestDTO readerRequestDTO) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        try {
            Reader reader = readerRepository.getReferenceById(id);
            readerMapper.updateReaderFromDto(readerRequestDTO, reader);

            reader.setPassword(passwordEncoder.encode(readerRequestDTO.getPassword()));

            Reader readerSalvo = readerRepository.save(reader);

            return readerMapper.toDto(readerSalvo);
        }catch (EntityNotFoundException e){
            throw new ResourceNotFoundException("Leitor", id);
        }
    }

    @Override
    @Transactional
    public void deleteReaderById(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        if(!readerRepository.existsById(id)){
            throw new ResourceNotFoundException("Leitor", id);
        }
        try {
            readerRepository.deleteById(id);
            readerRepository.flush();
        }
        catch (DataIntegrityViolationException e){
            throw new DatabaseException("Não é possível excluir este registro, pois ele possui vínculos com outros cadastros.");
        }

    }
}
