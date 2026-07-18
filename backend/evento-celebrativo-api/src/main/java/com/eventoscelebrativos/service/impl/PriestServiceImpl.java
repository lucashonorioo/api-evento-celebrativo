package com.eventoscelebrativos.service.impl;





import com.eventoscelebrativos.config.PersonMinistryShadowReadProperties;
import com.eventoscelebrativos.dto.request.PriestRequestDTO;
import com.eventoscelebrativos.dto.response.PriestResponseDTO;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.mapper.PriestMapper;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Priest;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.repository.PriestRepository;
import com.eventoscelebrativos.repository.RoleRepository;
import com.eventoscelebrativos.service.MinistryTypeResolver;
import com.eventoscelebrativos.service.PersonMinistryCompatibilityService;
import com.eventoscelebrativos.service.PersonMinistryShadowReadComparisonOptions;
import com.eventoscelebrativos.service.PersonMinistryShadowReadExecutor;
import com.eventoscelebrativos.service.PriestService;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final PersonMinistryCompatibilityService personMinistryCompatibilityService;
    private final MinistryTypeResolver ministryTypeResolver;
    private final PersonMinistryShadowReadExecutor personMinistryShadowReadExecutor;
    private final PersonMinistryShadowReadProperties shadowReadProperties;

    public PriestServiceImpl(
            PriestRepository priestRepository,
            PriestMapper priestMapper,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            PersonMinistryCompatibilityService personMinistryCompatibilityService,
            MinistryTypeResolver ministryTypeResolver,
            PersonMinistryShadowReadExecutor personMinistryShadowReadExecutor,
            PersonMinistryShadowReadProperties shadowReadProperties
    ) {
        this.priestRepository = priestRepository;
        this.priestMapper = priestMapper;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.personMinistryCompatibilityService = personMinistryCompatibilityService;
        this.ministryTypeResolver = ministryTypeResolver;
        this.personMinistryShadowReadExecutor = personMinistryShadowReadExecutor;
        this.shadowReadProperties = shadowReadProperties;
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
        ensureLegacyMinistry(priest);
        return priestMapper.toDto(priest);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PriestResponseDTO> findAllPriests() {
        List<Priest> priests = priestRepository.findAll();
        personMinistryShadowReadExecutor.execute(
                shadowReadProperties.isPriestEnabled(),
                MinistryType.PRIEST,
                priests,
                PersonMinistryShadowReadComparisonOptions.unorderedList()
        );
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
            ensureLegacyMinistry(priestSalvo);
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
        try {
            personMinistryCompatibilityService.deleteAllForPerson(id);
            priestRepository.deleteById(id);
            priestRepository.flush();
        }
        catch (DataIntegrityViolationException e){
            throw new DatabaseException("Não é possível excluir este registro, pois ele possui vínculos com outros cadastros.");
        }

    }

    private void ensureLegacyMinistry(Priest priest) {
        MinistryType ministryType = ministryTypeResolver.resolve(priest);
        personMinistryCompatibilityService.ensureMinistry(priest, ministryType);
    }
}
