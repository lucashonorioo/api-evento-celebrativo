package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.request.MinistryRequestDTO;
import com.eventoscelebrativos.dto.request.MinistryStatusUpdateRequestDTO;
import com.eventoscelebrativos.dto.response.MinistryResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.exception.exceptions.LifecycleConflictException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.model.Ministry;
import com.eventoscelebrativos.repository.MinistryRepository;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.service.MinistryAdministrationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MinistryAdministrationServiceImpl implements MinistryAdministrationService {

    private static final String MINISTRY_ENTITY = "Ministerio";
    private static final String MINISTRY_NAME_CONFLICT = "MINISTRY_NAME_ALREADY_EXISTS";
    private static final String MINISTRY_HAS_ACTIVE_MEMBERSHIPS = "MINISTRY_HAS_ACTIVE_MEMBERSHIPS";

    private final MinistryRepository ministryRepository;
    private final PersonMinistryRepository personMinistryRepository;
    private final EntityManager entityManager;

    public MinistryAdministrationServiceImpl(
            MinistryRepository ministryRepository,
            PersonMinistryRepository personMinistryRepository,
            EntityManager entityManager
    ) {
        this.ministryRepository = ministryRepository;
        this.personMinistryRepository = personMinistryRepository;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MinistryResponseDTO> findAll() {
        return ministryRepository.findAllByOrderByNameAscIdAsc()
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MinistryResponseDTO findById(Long ministryId) {
        validateId(ministryId);
        Ministry ministry = ministryRepository.findById(ministryId)
                .orElseThrow(() -> new ResourceNotFoundException(MINISTRY_ENTITY, ministryId));
        return toResponseDTO(ministry);
    }

    @Override
    @Transactional
    public MinistryResponseDTO create(MinistryRequestDTO requestDTO) {
        validateRequest(requestDTO);
        Ministry ministry = createMinistry(requestDTO.getName());
        try {
            return toResponseDTO(ministryRepository.saveAndFlush(ministry));
        } catch (DataIntegrityViolationException e) {
            throw duplicateNameConflict();
        }
    }

    @Override
    @Transactional
    public MinistryResponseDTO rename(Long ministryId, MinistryRequestDTO requestDTO) {
        validateId(ministryId);
        validateRequest(requestDTO);
        Ministry ministry = requireMinistryForUpdate(ministryId);
        try {
            ministry.rename(requestDTO.getName());
            return toResponseDTO(ministryRepository.saveAndFlush(ministry));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        } catch (DataIntegrityViolationException e) {
            throw duplicateNameConflict();
        }
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MinistryResponseDTO updateStatus(Long ministryId, MinistryStatusUpdateRequestDTO requestDTO) {
        validateId(ministryId);
        if (requestDTO == null || requestDTO.getActive() == null) {
            throw new BadRequestException("O campo active e obrigatorio");
        }

        // Catalog status changes lock only the Ministry row. Membership writers that can create,
        // reactivate or keep a PersonMinistry active must acquire and refresh this same row before
        // writing. READ_COMMITTED makes the post-lock membership guard observe rows committed by a
        // writer that won the Ministry lock first, without adding gap locks for absent memberships.
        Ministry ministry = requireMinistryForUpdate(ministryId);
        if (Boolean.TRUE.equals(requestDTO.getActive())) {
            if (!ministry.isActive()) {
                ministry.activate();
            }
            return toResponseDTO(ministryRepository.saveAndFlush(ministry));
        }

        if (!ministry.isActive()) {
            return toResponseDTO(ministry);
        }
        if (personMinistryRepository.existsByMinistryIdAndActiveTrue(ministryId)) {
            throw new LifecycleConflictException(
                    "Nao e possivel desativar ministerio com vinculos ativos.",
                    MINISTRY_HAS_ACTIVE_MEMBERSHIPS
            );
        }
        ministry.deactivate();
        return toResponseDTO(ministryRepository.saveAndFlush(ministry));
    }

    private void validateRequest(MinistryRequestDTO requestDTO) {
        if (requestDTO == null) {
            throw new BadRequestException("Os dados do ministerio sao obrigatorios");
        }
    }

    private void validateId(Long ministryId) {
        if (ministryId == null || ministryId <= 0) {
            throw new BadRequestException("O Id do ministerio deve ser positivo e nao nulo");
        }
    }

    private Ministry requireMinistryForUpdate(Long ministryId) {
        if (!ministryRepository.existsById(ministryId)) {
            throw new ResourceNotFoundException(MINISTRY_ENTITY, ministryId);
        }
        Ministry ministry = ministryRepository.findByIdForUpdate(ministryId)
                .orElseThrow(() -> new ResourceNotFoundException(MINISTRY_ENTITY, ministryId));
        entityManager.refresh(ministry, LockModeType.PESSIMISTIC_WRITE);
        return ministry;
    }

    private Ministry createMinistry(String name) {
        try {
            return new Ministry(name);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    private LifecycleConflictException duplicateNameConflict() {
        return new LifecycleConflictException(
                "Ja existe ministerio com este nome.",
                MINISTRY_NAME_CONFLICT
        );
    }

    private MinistryResponseDTO toResponseDTO(Ministry ministry) {
        return new MinistryResponseDTO(ministry.getId(), ministry.getName(), ministry.isActive());
    }
}
