package com.eventoscelebrativos.service.impl;




import com.eventoscelebrativos.config.PersonMinistryReadSource;
import com.eventoscelebrativos.config.PersonMinistryReadSourceProperties;
import com.eventoscelebrativos.config.PersonMinistryShadowReadProperties;
import com.eventoscelebrativos.dto.request.MinisterOfTheWordRequestDTO;
import com.eventoscelebrativos.dto.response.MinisterOfTheWordResponseDTO;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.mapper.MinisterOfTheWordMapper;
import com.eventoscelebrativos.model.MinisterOfTheWord;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.repository.MinisterOfTheWordRepository;
import com.eventoscelebrativos.repository.RoleRepository;
import com.eventoscelebrativos.service.MinistryTypeResolver;
import com.eventoscelebrativos.service.PersonMinistryCompatibilityService;
import com.eventoscelebrativos.service.PersonMinistryReadService;
import com.eventoscelebrativos.service.PersonMinistryShadowReadComparisonOptions;
import com.eventoscelebrativos.service.PersonMinistryShadowReadExecutor;
import com.eventoscelebrativos.service.MinisterOfTheWordService;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MinisterOfTheWordServiceImpl implements MinisterOfTheWordService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MinisterOfTheWordServiceImpl.class);

    private final MinisterOfTheWordRepository ministerOfTheWordRepository;
    private final MinisterOfTheWordMapper ministerOfTheWordMapper;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PersonMinistryCompatibilityService personMinistryCompatibilityService;
    private final MinistryTypeResolver ministryTypeResolver;
    private final PersonMinistryReadService personMinistryReadService;
    private final PersonMinistryShadowReadExecutor personMinistryShadowReadExecutor;
    private final PersonMinistryReadSourceProperties readSourceProperties;
    private final PersonMinistryShadowReadProperties shadowReadProperties;

    public MinisterOfTheWordServiceImpl(
            MinisterOfTheWordRepository ministerOfTheWordRepository,
            MinisterOfTheWordMapper ministerOfTheWordMapper,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            PersonMinistryCompatibilityService personMinistryCompatibilityService,
            MinistryTypeResolver ministryTypeResolver,
            PersonMinistryReadService personMinistryReadService,
            PersonMinistryShadowReadExecutor personMinistryShadowReadExecutor,
            PersonMinistryReadSourceProperties readSourceProperties,
            PersonMinistryShadowReadProperties shadowReadProperties
    ) {
        this.ministerOfTheWordRepository = ministerOfTheWordRepository;
        this.ministerOfTheWordMapper = ministerOfTheWordMapper;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.personMinistryCompatibilityService = personMinistryCompatibilityService;
        this.ministryTypeResolver = ministryTypeResolver;
        this.personMinistryReadService = personMinistryReadService;
        this.personMinistryShadowReadExecutor = personMinistryShadowReadExecutor;
        this.readSourceProperties = readSourceProperties;
        this.shadowReadProperties = shadowReadProperties;
    }


    @Override
    @Transactional
    public MinisterOfTheWordResponseDTO createMinisterOfTheWord(MinisterOfTheWordRequestDTO ministerOfTheWordRequestDTO) {
        MinisterOfTheWord ministerOfTheWord = ministerOfTheWordMapper.toEntity(ministerOfTheWordRequestDTO);

        ministerOfTheWord.setPassword(passwordEncoder.encode(ministerOfTheWord.getPassword()));

        Role operatorRole = roleRepository.findByAuthority("ROLE_OPERATOR")
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de acesso", "ROLE_OPERATOR"));

        ministerOfTheWord.addRole(operatorRole);

        ministerOfTheWord = ministerOfTheWordRepository.save(ministerOfTheWord);
        ensureLegacyMinistry(ministerOfTheWord);

        return ministerOfTheWordMapper.toDto(ministerOfTheWord);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MinisterOfTheWordResponseDTO> findAllMinistersOfTheWord() {
        if (PersonMinistryReadSource.PARALLEL.equals(readSourceProperties.getMinisterOfTheWord())) {
            LOGGER.debug("minister-of-the-word read source={}", PersonMinistryReadSource.PARALLEL);
            List<Person> people = personMinistryReadService.findAllActivePeopleByMinistry(MinistryType.MINISTER_OF_THE_WORD);
            return ministerOfTheWordMapper.toDtoPersonList(people);
        }

        LOGGER.debug("minister-of-the-word read source={}", PersonMinistryReadSource.LEGACY);
        List<MinisterOfTheWord> ministrosDaPalavra = ministerOfTheWordRepository.findAll();
        if (shadowReadProperties.isMinisterOfTheWordEnabled()) {
            personMinistryShadowReadExecutor.execute(
                    true,
                    MinistryType.MINISTER_OF_THE_WORD,
                    ministrosDaPalavra,
                    PersonMinistryShadowReadComparisonOptions.unorderedList()
            );
        }
        return ministerOfTheWordMapper.toDtoList(ministrosDaPalavra);
    }

    @Override
    @Transactional(readOnly = true)
    public MinisterOfTheWordResponseDTO findMinisterOfTheWordById(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        MinisterOfTheWord ministerOfTheWord = ministerOfTheWordRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Ministro Da Palavra", id));
        return ministerOfTheWordMapper.toDto(ministerOfTheWord);
    }

    @Override
    @Transactional
    public MinisterOfTheWordResponseDTO updateMinisterOfTheWord(Long id, MinisterOfTheWordRequestDTO ministerOfTheWordRequestDTO) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        try {
            MinisterOfTheWord ministerOfTheWord = ministerOfTheWordRepository.getReferenceById(id);
            ministerOfTheWordMapper.updateMinisterOfTheWordFromDto(ministerOfTheWordRequestDTO, ministerOfTheWord);

            ministerOfTheWord.setPassword(passwordEncoder.encode(ministerOfTheWord.getPassword()));

            MinisterOfTheWord ministerOfTheWordSalvo = ministerOfTheWordRepository.save(ministerOfTheWord);
            ensureLegacyMinistry(ministerOfTheWordSalvo);
            return ministerOfTheWordMapper.toDto(ministerOfTheWordSalvo);
        }catch (EntityNotFoundException e){
            throw new ResourceNotFoundException("Ministro da Palavra", id);
        }
    }

    @Override
    @Transactional
    public void deleteMinisterOfTheWord(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        if(!ministerOfTheWordRepository.existsById(id)){
            throw new ResourceNotFoundException("Ministro da Palavra", id);
        }
        try {
            personMinistryCompatibilityService.deleteAllForPerson(id);
            ministerOfTheWordRepository.deleteById(id);
            ministerOfTheWordRepository.flush();
        }
        catch (DataIntegrityViolationException e){
            throw new DatabaseException("Não é possível excluir este registro, pois ele possui vínculos com outros cadastros.");
        }
    }

    private void ensureLegacyMinistry(MinisterOfTheWord ministerOfTheWord) {
        MinistryType ministryType = ministryTypeResolver.resolve(ministerOfTheWord);
        personMinistryCompatibilityService.ensureMinistry(ministerOfTheWord, ministryType);
    }
}
