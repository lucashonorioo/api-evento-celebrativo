package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.repository.EventAssignmentRepository;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.service.PersonMinistryCommandService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonMinistryCommandServiceImpl implements PersonMinistryCommandService {

    private final PersonRepository personRepository;
    private final PersonMinistryRepository personMinistryRepository;
    private final EventAssignmentRepository eventAssignmentRepository;

    public PersonMinistryCommandServiceImpl(
            PersonRepository personRepository,
            PersonMinistryRepository personMinistryRepository,
            EventAssignmentRepository eventAssignmentRepository
    ) {
        this.personRepository = personRepository;
        this.personMinistryRepository = personMinistryRepository;
        this.eventAssignmentRepository = eventAssignmentRepository;
    }

    @Override
    @Transactional
    public Person create(Person person, MinistryType ministryType) {
        if (person == null || ministryType == null) {
            throw new BusinessException("Pessoa e função ministerial são obrigatórias");
        }
        Person saved = personRepository.save(person);
        try {
            personMinistryRepository.save(new PersonMinistry(saved, ministryType));
        } catch (DataIntegrityViolationException e) {
            throw new DatabaseException("Não é possível associar esta pessoa à função ministerial informada.");
        }
        return saved;
    }

    @Override
    public Person requireActiveMinistryPerson(Long personId, MinistryType ministryType, String entityLabel) {
        if (personId == null || personId <= 0) {
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new ResourceNotFoundException(entityLabel, personId));
        personMinistryRepository.findByPersonIdAndMinistryType(personId, ministryType)
                .filter(pm -> Boolean.TRUE.equals(pm.getActive()))
                .orElseThrow(() -> new ResourceNotFoundException(entityLabel, personId));
        return person;
    }

    @Override
    @Transactional
    public Person save(Person person) {
        return personRepository.save(person);
    }

    @Override
    @Transactional
    public void removeMinistry(Long personId, MinistryType ministryType, String entityLabel) {
        requireActiveMinistryPerson(personId, ministryType, entityLabel);
        EventAssignmentType assignmentType = EventAssignmentType.valueOf(ministryType.name());
        if (eventAssignmentRepository.existsByPersonIdAndAssignmentType(personId, assignmentType)) {
            throw new DatabaseException("Não é possível excluir este registro, pois ele possui vínculos com outros cadastros.");
        }
        PersonMinistry personMinistry = personMinistryRepository.findByPersonIdAndMinistryType(personId, ministryType)
                .orElseThrow(() -> new ResourceNotFoundException(entityLabel, personId));
        personMinistry.deactivate();
        personMinistryRepository.save(personMinistry);
    }
}
