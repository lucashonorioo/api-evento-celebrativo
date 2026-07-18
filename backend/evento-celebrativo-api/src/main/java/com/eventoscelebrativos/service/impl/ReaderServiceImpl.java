package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.config.PersonMinistryShadowReadProperties;
import com.eventoscelebrativos.dto.request.ReaderRequestDTO;
import com.eventoscelebrativos.dto.response.ReaderResponseDTO;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.mapper.ReaderMapper;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Reader;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.repository.ReaderRepository;
import com.eventoscelebrativos.repository.RoleRepository;
import com.eventoscelebrativos.service.MinistryTypeResolver;
import com.eventoscelebrativos.service.PersonMinistryCompatibilityService;
import com.eventoscelebrativos.service.PersonMinistryReadService;
import com.eventoscelebrativos.service.PersonMinistryShadowReadComparisonOptions;
import com.eventoscelebrativos.service.PersonMinistryShadowReadComparator;
import com.eventoscelebrativos.service.PersonMinistryShadowReadReport;
import com.eventoscelebrativos.service.ReaderService;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Service
public class ReaderServiceImpl implements ReaderService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReaderServiceImpl.class);

    private final ReaderRepository readerRepository;
    private final ReaderMapper readerMapper;

    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PersonMinistryCompatibilityService personMinistryCompatibilityService;
    private final MinistryTypeResolver ministryTypeResolver;
    private final PersonMinistryReadService personMinistryReadService;
    private final PersonMinistryShadowReadComparator personMinistryShadowReadComparator;
    private final PersonMinistryShadowReadProperties shadowReadProperties;

    public ReaderServiceImpl(
            ReaderRepository readerRepository,
            ReaderMapper readerMapper,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            PersonMinistryCompatibilityService personMinistryCompatibilityService,
            MinistryTypeResolver ministryTypeResolver,
            PersonMinistryReadService personMinistryReadService,
            PersonMinistryShadowReadComparator personMinistryShadowReadComparator,
            PersonMinistryShadowReadProperties shadowReadProperties
    ) {
        this.readerRepository = readerRepository;
        this.readerMapper = readerMapper;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.personMinistryCompatibilityService = personMinistryCompatibilityService;
        this.ministryTypeResolver = ministryTypeResolver;
        this.personMinistryReadService = personMinistryReadService;
        this.personMinistryShadowReadComparator = personMinistryShadowReadComparator;
        this.shadowReadProperties = shadowReadProperties;
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
        ensureLegacyMinistry(reader);
        return readerMapper.toDto(reader);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReaderResponseDTO> findAllReaders() {
        List<Reader> reader = readerRepository.findAll();
        runReaderShadowRead(reader);
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
            ensureLegacyMinistry(readerSalvo);

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
            personMinistryCompatibilityService.deleteAllForPerson(id);
            readerRepository.deleteById(id);
            readerRepository.flush();
        }
        catch (DataIntegrityViolationException e){
            throw new DatabaseException("Não é possível excluir este registro, pois ele possui vínculos com outros cadastros.");
        }

    }

    private void ensureLegacyMinistry(Reader reader) {
        MinistryType ministryType = ministryTypeResolver.resolve(reader);
        personMinistryCompatibilityService.ensureMinistry(reader, ministryType);
    }

    private void runReaderShadowRead(List<Reader> legacyReaders) {
        if (!shadowReadProperties.isReaderEnabled()) {
            return;
        }

        PageRequest pageable = PageRequest.of(0, Math.max(legacyReaders.size(), 1));
        Page<Reader> legacyPage = new PageImpl<>(legacyReaders, pageable, legacyReaders.size());

        try {
            Page<Person> parallelPage = personMinistryReadService.findActivePeopleByMinistry(
                    MinistryType.READER,
                    pageable
            );
            PersonMinistryShadowReadReport report = personMinistryShadowReadComparator.compare(
                    MinistryType.READER,
                    legacyPage,
                    parallelPage,
                    PersonMinistryShadowReadComparisonOptions.unorderedList()
            );
            logShadowReadReport(report);
        } catch (RuntimeException exception) {
            PersonMinistryShadowReadReport report = personMinistryShadowReadComparator.parallelReadFailure(
                    MinistryType.READER,
                    legacyPage
            );
            LOGGER.warn(
                    "PersonMinistry shadow read failed: ministryType={}, page={}, size={}, legacyIds={}, legacyTotalElements={}, issues={}",
                    report.ministryType(),
                    report.pageNumber(),
                    report.pageSize(),
                    report.legacyIds(),
                    report.legacyTotalElements(),
                    report.issues(),
                    exception
            );
        }
    }

    private void logShadowReadReport(PersonMinistryShadowReadReport report) {
        if (report.matched()) {
            LOGGER.debug(
                    "PersonMinistry shadow read matched: ministryType={}, page={}, size={}, legacyCount={}, parallelCount={}, legacyTotalElements={}, parallelTotalElements={}, orderCompared={}, orderDiffers={}, pageMetadataCompared={}",
                    report.ministryType(),
                    report.pageNumber(),
                    report.pageSize(),
                    report.legacyIds().size(),
                    report.parallelIds().size(),
                    report.legacyTotalElements(),
                    report.parallelTotalElements(),
                    report.orderCompared(),
                    report.orderDiffers(),
                    report.pageMetadataCompared()
            );
            return;
        }

        LOGGER.warn(
                "PersonMinistry shadow read divergence: ministryType={}, page={}, size={}, legacyIds={}, parallelIds={}, missingInParallelIds={}, additionalInParallelIds={}, legacyTotalElements={}, parallelTotalElements={}, legacyTotalPages={}, parallelTotalPages={}, orderCompared={}, orderDiffers={}, pageMetadataCompared={}, issues={}",
                report.ministryType(),
                report.pageNumber(),
                report.pageSize(),
                report.legacyIds(),
                report.parallelIds(),
                report.missingInParallelIds(),
                report.additionalInParallelIds(),
                report.legacyTotalElements(),
                report.parallelTotalElements(),
                report.legacyTotalPages(),
                report.parallelTotalPages(),
                report.orderCompared(),
                report.orderDiffers(),
                report.pageMetadataCompared(),
                report.issues()
        );
    }
}
