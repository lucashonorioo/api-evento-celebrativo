package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.exception.exceptions.MinistryCoordinationRequiresActiveMinistryException;
import com.eventoscelebrativos.exception.exceptions.MinistryInactiveException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.model.Ministry;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.repository.MinistryRepository;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.service.MinistryCoordinationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class MinistryCoordinationServiceImpl implements MinistryCoordinationService {

    private final PersonRepository personRepository;
    private final MinistryRepository ministryRepository;
    private final PersonMinistryRepository personMinistryRepository;

    public MinistryCoordinationServiceImpl(
            PersonRepository personRepository,
            MinistryRepository ministryRepository,
            PersonMinistryRepository personMinistryRepository
    ) {
        this.personRepository = personRepository;
        this.ministryRepository = ministryRepository;
        this.personMinistryRepository = personMinistryRepository;
    }

    @Override
    @Transactional
    public void grantCoordinator(Long personId, Long ministryId) {
        validateId(personId);
        validateId(ministryId);

        // Lock order: Person -> Ministry -> PersonMinistry. This keeps Person as the first lock
        // for mutations of existing people and serializes with concurrent catalog deactivation.
        personRepository.findByIdForUpdate(personId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa", personId));
        Ministry ministry = requireActiveMinistryForUpdate(ministryId);
        PersonMinistry personMinistry = personMinistryRepository.findByPersonIdAndMinistryId(personId, ministry.getId())
                .filter(pm -> Boolean.TRUE.equals(pm.getActive()))
                .orElseThrow(MinistryCoordinationRequiresActiveMinistryException::new);

        if (Boolean.TRUE.equals(personMinistry.getCoordinator())) {
            return;
        }
        personMinistry.grantCoordination();
        personMinistryRepository.save(personMinistry);
    }

    @Override
    @Transactional
    public void revokeCoordinator(Long personId, Long ministryId) {
        validateId(personId);
        validateId(ministryId);

        personRepository.findByIdForUpdate(personId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa", personId));
        Ministry ministry = requireMinistry(ministryId);
        Optional<PersonMinistry> existing = personMinistryRepository.findByPersonIdAndMinistryId(personId, ministry.getId());

        if (existing.isEmpty() || !Boolean.TRUE.equals(existing.get().getCoordinator())) {
            return;
        }
        existing.get().revokeCoordination();
        personMinistryRepository.save(existing.get());
    }

    private Ministry requireMinistry(Long ministryId) {
        validateId(ministryId);
        return ministryRepository.findById(ministryId)
                .orElseThrow(() -> new ResourceNotFoundException("Ministerio", ministryId));
    }

    private Ministry requireActiveMinistryForUpdate(Long ministryId) {
        validateId(ministryId);
        Ministry ministry = ministryRepository.findByIdForUpdate(ministryId)
                .orElseThrow(() -> new ResourceNotFoundException("Ministerio", ministryId));
        if (!ministry.isActive()) {
            throw new MinistryInactiveException();
        }
        return ministry;
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new BadRequestException("O Id deve ser positivo e nao nulo");
        }
    }
}
