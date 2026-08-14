package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.exception.exceptions.MinistryCoordinationRequiresActiveMinistryException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.service.MinistryCoordinationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class MinistryCoordinationServiceImpl implements MinistryCoordinationService {

    private final PersonRepository personRepository;
    private final PersonMinistryRepository personMinistryRepository;

    public MinistryCoordinationServiceImpl(
            PersonRepository personRepository,
            PersonMinistryRepository personMinistryRepository
    ) {
        this.personRepository = personRepository;
        this.personMinistryRepository = personMinistryRepository;
    }

    @Override
    @Transactional
    public void grantCoordinator(Long personId, String rawMinistryType) {
        validateId(personId);
        MinistryType ministryType = parseMinistryType(rawMinistryType);

        // Lock order: Person -> consulta comum de PersonMinistry -> decisao/mutacao. Sem lock
        // proprio sobre PersonMinistry: a serializacao vem inteiramente do lock da Person.
        personRepository.findByIdForUpdate(personId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa", personId));
        PersonMinistry personMinistry = personMinistryRepository.findByPersonIdAndMinistryType(personId, ministryType)
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
    public void revokeCoordinator(Long personId, String rawMinistryType) {
        validateId(personId);
        MinistryType ministryType = parseMinistryType(rawMinistryType);

        personRepository.findByIdForUpdate(personId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa", personId));
        Optional<PersonMinistry> existing = personMinistryRepository.findByPersonIdAndMinistryType(personId, ministryType);

        if (existing.isEmpty() || !Boolean.TRUE.equals(existing.get().getCoordinator())) {
            return;
        }
        existing.get().revokeCoordination();
        personMinistryRepository.save(existing.get());
    }

    private MinistryType parseMinistryType(String rawMinistryType) {
        if (rawMinistryType == null || rawMinistryType.isBlank()) {
            throw new BadRequestException("Tipo de ministerio invalido");
        }
        try {
            return MinistryType.valueOf(rawMinistryType.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Tipo de ministerio invalido: " + rawMinistryType);
        }
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new BadRequestException("O Id deve ser positivo e não nulo");
        }
    }
}
