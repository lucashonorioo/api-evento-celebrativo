package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.exception.exceptions.MinistryCoordinationRequiresActiveMinistryException;
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
        Ministry ministry = requireMinistry(ministryId);

        // Lock order: Person -> consulta comum de PersonMinistry -> decisao/mutacao. Sem lock
        // proprio sobre PersonMinistry: a serializacao vem inteiramente do lock da Person.
        personRepository.findByIdForUpdate(personId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa", personId));
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
        Ministry ministry = requireMinistry(ministryId);

        personRepository.findByIdForUpdate(personId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa", personId));
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
                .orElseThrow(() -> new ResourceNotFoundException("Ministério", ministryId));
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new BadRequestException("O Id deve ser positivo e não nulo");
        }
    }
}
