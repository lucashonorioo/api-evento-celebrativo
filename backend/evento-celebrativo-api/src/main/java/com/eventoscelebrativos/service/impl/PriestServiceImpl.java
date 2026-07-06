package com.eventoscelebrativos.service.impl;





import com.eventoscelebrativos.dto.request.PriestRequestDTO;
import com.eventoscelebrativos.dto.response.PriestResponseDTO;
import com.eventoscelebrativos.mapper.PriestMapper;
import com.eventoscelebrativos.model.Priest;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.repository.PriestRepository;
import com.eventoscelebrativos.repository.RoleRepository;
import com.eventoscelebrativos.service.PriestService;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PriestServiceImpl implements PriestService {

    private final PriestRepository priestRepository;
    private final PriestMapper priestMapper;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public PriestServiceImpl(PriestRepository priestRepository, PriestMapper priestMapper, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.priestRepository = priestRepository;
        this.priestMapper = priestMapper;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }


    @Override
    @Transactional
    public PriestResponseDTO createPriest(PriestRequestDTO priestRequestDTO) {
        Priest priest = priestMapper.toEntity(priestRequestDTO);

        priest.setPassword(passwordEncoder.encode(priestRequestDTO.getPassword()));

        Role operatorRole = roleRepository.findByAuthority("ROLE_OPERATOR")
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de acesso", "ROLE_OPERATOR"));

        priest.addRole(operatorRole);

        priest = priestRepository.save(priest);
        return priestMapper.toDto(priest);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PriestResponseDTO> findAllPriests() {
        List<Priest> priests = priestRepository.findAll();
        return priestMapper.toDtoList(priests);
    }

    @Override
    @Transactional(readOnly = true)
    public PriestResponseDTO findPriestById(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positio e não nulo");
        }
        Priest priest = priestRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Padre", id));
        return priestMapper.toDto(priest);
    }

    @Override
    @Transactional
    public PriestResponseDTO updatePriest(Long id, PriestRequestDTO priestRequestDTO) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positio e não nulo");
        }
        try {
            Priest priest = priestRepository.getReferenceById(id);
            priestMapper.updatePriestFromDto(priestRequestDTO, priest);

            priest.setPassword(passwordEncoder.encode(priestRequestDTO.getPassword()));

            Priest priestSalvo = priestRepository.save(priest);
            return priestMapper.toDto(priestSalvo);

        }catch (EntityNotFoundException e){
            throw new ResourceNotFoundException("Padre", id);
        }
    }

    @Override
    @Transactional
    public void deletePriestById(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positio e não nulo");
        }
        if(!priestRepository.existsById(id)){
            throw new ResourceNotFoundException("Padre", id);
        }
        priestRepository.deleteById(id);

    }
}
