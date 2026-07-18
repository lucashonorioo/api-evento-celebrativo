package com.eventoscelebrativos.service.impl;




import com.eventoscelebrativos.config.PersonMinistryShadowReadProperties;
import com.eventoscelebrativos.dto.request.EucharisticMinisterRequestDTO;
import com.eventoscelebrativos.dto.response.EucharisticMinisterResponseDTO;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.mapper.EucharisticMinisterMapper;
import com.eventoscelebrativos.model.EucharisticMinister;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.repository.EucharisticMinisterRepository;
import com.eventoscelebrativos.repository.RoleRepository;
import com.eventoscelebrativos.service.EucharisticMinisterService;
import com.eventoscelebrativos.service.MinistryTypeResolver;
import com.eventoscelebrativos.service.PersonMinistryCompatibilityService;
import com.eventoscelebrativos.service.PersonMinistryShadowReadComparisonOptions;
import com.eventoscelebrativos.service.PersonMinistryShadowReadExecutor;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EucharisticMinisterServiceImpl implements EucharisticMinisterService {

    private final EucharisticMinisterRepository eucharisticMinisterRepository;
    private final EucharisticMinisterMapper eucharisticMinisterMapper;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PersonMinistryCompatibilityService personMinistryCompatibilityService;
    private final MinistryTypeResolver ministryTypeResolver;
    private final PersonMinistryShadowReadExecutor personMinistryShadowReadExecutor;
    private final PersonMinistryShadowReadProperties shadowReadProperties;

    public EucharisticMinisterServiceImpl(
            EucharisticMinisterRepository eucharisticMinisterRepository,
            EucharisticMinisterMapper eucharisticMinisterMapper,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            PersonMinistryCompatibilityService personMinistryCompatibilityService,
            MinistryTypeResolver ministryTypeResolver,
            PersonMinistryShadowReadExecutor personMinistryShadowReadExecutor,
            PersonMinistryShadowReadProperties shadowReadProperties
    ) {
        this.eucharisticMinisterRepository = eucharisticMinisterRepository;
        this.eucharisticMinisterMapper = eucharisticMinisterMapper;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.personMinistryCompatibilityService = personMinistryCompatibilityService;
        this.ministryTypeResolver = ministryTypeResolver;
        this.personMinistryShadowReadExecutor = personMinistryShadowReadExecutor;
        this.shadowReadProperties = shadowReadProperties;
    }


    @Override
    @Transactional
    public EucharisticMinisterResponseDTO createEucharisticMinister(EucharisticMinisterRequestDTO eucharisticMinisterRequestDTO) {
        EucharisticMinister eucharisticMinister = eucharisticMinisterMapper.toEntity(eucharisticMinisterRequestDTO);

        eucharisticMinister.setPassword(passwordEncoder.encode(eucharisticMinisterRequestDTO.getPassword()));

        Role operatorRole = roleRepository.findByAuthority("ROLE_OPERATOR")
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de acesso,", "ROLE_OPERATOR"));

        eucharisticMinister.addRole(operatorRole);

        eucharisticMinister = eucharisticMinisterRepository.save(eucharisticMinister);
        ensureLegacyMinistry(eucharisticMinister);
        return eucharisticMinisterMapper.toDto(eucharisticMinister);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EucharisticMinisterResponseDTO> findAllEucharisticMinisters() {
        List<EucharisticMinister> ministrosDeEucaristia = eucharisticMinisterRepository.findAll();
        personMinistryShadowReadExecutor.execute(
                shadowReadProperties.isEucharisticMinisterEnabled(),
                MinistryType.EUCHARISTIC_MINISTER,
                ministrosDeEucaristia,
                PersonMinistryShadowReadComparisonOptions.unorderedList()
        );
        return eucharisticMinisterMapper.toDtoList(ministrosDeEucaristia);
    }

    @Override
    @Transactional(readOnly = true)
    public EucharisticMinisterResponseDTO findEucharisticMinistersById(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        EucharisticMinister eucharisticMinister = eucharisticMinisterRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Ministro De Eucaristia", id));

        return eucharisticMinisterMapper.toDto(eucharisticMinister);
    }

    @Override
    @Transactional
    public EucharisticMinisterResponseDTO updateEucharisticMinisters(Long id, EucharisticMinisterRequestDTO eucharisticMinisterRequestDTO) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        try {
            EucharisticMinister eucharisticMinister = eucharisticMinisterRepository.getReferenceById(id);

            eucharisticMinisterMapper.updateEucharisticMinisterFromDto(eucharisticMinisterRequestDTO, eucharisticMinister);
            eucharisticMinister.setPassword(passwordEncoder.encode(eucharisticMinisterRequestDTO.getPassword()));

            EucharisticMinister eucharisticMinisterSalvo = eucharisticMinisterRepository.save(eucharisticMinister);
            ensureLegacyMinistry(eucharisticMinisterSalvo);

            return eucharisticMinisterMapper.toDto(eucharisticMinisterSalvo);
        }catch (EntityNotFoundException e){
            throw new ResourceNotFoundException("Ministro de Eucaristia", id);
        }
    }

    @Override
    @Transactional
    public void deleteEucharisticMinisterById(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        if(!eucharisticMinisterRepository.existsById(id)){
            throw new ResourceNotFoundException("Ministro de Eucaristia", id);
        }
        try {
            personMinistryCompatibilityService.deleteAllForPerson(id);
            eucharisticMinisterRepository.deleteById(id);
            eucharisticMinisterRepository.flush();
        }
        catch (DataIntegrityViolationException e){
            throw new DatabaseException("Não é possível excluir este registro, pois ele possui vínculos com outros cadastros.");
        }

    }

    private void ensureLegacyMinistry(EucharisticMinister eucharisticMinister) {
        MinistryType ministryType = ministryTypeResolver.resolve(eucharisticMinister);
        personMinistryCompatibilityService.ensureMinistry(eucharisticMinister, ministryType);
    }
}
