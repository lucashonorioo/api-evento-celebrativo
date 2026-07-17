package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.service.PersonMinistryCompatibilityService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonMinistryCompatibilityServiceImpl implements PersonMinistryCompatibilityService {

    private final PersonMinistryRepository personMinistryRepository;

    public PersonMinistryCompatibilityServiceImpl(PersonMinistryRepository personMinistryRepository) {
        this.personMinistryRepository = personMinistryRepository;
    }

    @Override
    @Transactional
    public PersonMinistry ensureMinistry(Person person, MinistryType ministryType) {
        if (person == null || person.getId() == null || ministryType == null) {
            throw new BusinessException("Pessoa e funÃ§Ã£o ministerial sÃ£o obrigatÃ³rias");
        }

        return personMinistryRepository.findByPersonIdAndMinistryType(person.getId(), ministryType)
                .map(existing -> {
                    if (Boolean.FALSE.equals(existing.getActive())) {
                        existing.activate();
                        return personMinistryRepository.save(existing);
                    }
                    return existing;
                })
                .orElseGet(() -> personMinistryRepository.save(new PersonMinistry(person, ministryType)));
    }

    @Override
    @Transactional
    public void deleteAllForPerson(Long personId) {
        if (personId == null || personId <= 0) {
            throw new BusinessException("O Id deve ser positivo e nÃ£o nulo");
        }
        personMinistryRepository.deleteAllByPersonId(personId);
    }
}
