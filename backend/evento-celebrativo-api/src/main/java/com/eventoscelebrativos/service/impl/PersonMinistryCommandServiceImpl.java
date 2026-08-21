package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.exception.exceptions.MinistryPersonInactiveException;
import com.eventoscelebrativos.exception.exceptions.PastorPriestMinistryRequiredException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.ParishResponsibilityType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.repository.EventAssignmentRepository;
import com.eventoscelebrativos.repository.ParishStaffAssignmentRepository;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.service.PersonMinistryCommandService;
import com.eventoscelebrativos.service.PersonMinistryDiff;
import com.eventoscelebrativos.service.PersonMinistrySyncResult;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PersonMinistryCommandServiceImpl implements PersonMinistryCommandService {

    private final PersonRepository personRepository;
    private final PersonMinistryRepository personMinistryRepository;
    private final EventAssignmentRepository eventAssignmentRepository;
    private final ParishStaffAssignmentRepository parishStaffAssignmentRepository;
    private final Clock clock;

    public PersonMinistryCommandServiceImpl(
            PersonRepository personRepository,
            PersonMinistryRepository personMinistryRepository,
            EventAssignmentRepository eventAssignmentRepository,
            ParishStaffAssignmentRepository parishStaffAssignmentRepository,
            Clock clock
    ) {
        this.personRepository = personRepository;
        this.personMinistryRepository = personMinistryRepository;
        this.eventAssignmentRepository = eventAssignmentRepository;
        this.parishStaffAssignmentRepository = parishStaffAssignmentRepository;
        this.clock = clock;
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
        return requireActiveMinistryPerson(personId, ministryType, entityLabel, false);
    }

    @Override
    @Transactional
    public Person requireActiveMinistryPersonForUpdate(Long personId, MinistryType ministryType, String entityLabel) {
        return requireActiveMinistryPerson(personId, ministryType, entityLabel, true);
    }

    private Person requireActiveMinistryPerson(Long personId, MinistryType ministryType, String entityLabel, boolean forUpdate) {
        if (personId == null || personId <= 0) {
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        Person person = (forUpdate ? personRepository.findByIdForUpdate(personId) : personRepository.findById(personId))
                .orElseThrow(() -> new ResourceNotFoundException(entityLabel, personId));
        if (!person.isActive()) {
            throw new ResourceNotFoundException(entityLabel, personId);
        }
        personMinistryRepository.findByPersonIdAndMinistryType(personId, ministryType)
                .filter(pm -> Boolean.TRUE.equals(pm.getActive()))
                .orElseThrow(() -> new ResourceNotFoundException(entityLabel, personId));
        return person;
    }

    @Override
    @Transactional
    public void removeMinistry(Long personId, MinistryType ministryType, String entityLabel) {
        requireActiveMinistryPersonForUpdate(personId, ministryType, entityLabel);
        if (ministryType == MinistryType.PRIEST) {
            guardPastorRequiresActivePriest(personId);
        }
        EventAssignmentType assignmentType = EventAssignmentType.valueOf(ministryType.name());
        if (eventAssignmentRepository.existsActiveOrFutureByPersonIdAndAssignmentType(
                personId, assignmentType, LocalDateTime.now(clock).withNano(0))) {
            throw new DatabaseException("Não é possível excluir este registro, pois ele possui vínculos com outros cadastros.");
        }
        PersonMinistry personMinistry = personMinistryRepository.findByPersonIdAndMinistryType(personId, ministryType)
                .orElseThrow(() -> new ResourceNotFoundException(entityLabel, personId));
        personMinistry.deactivate();
        personMinistryRepository.save(personMinistry);
    }

    @Override
    @Transactional
    public Person addOrReactivateMinistry(Long personId, MinistryType ministryType) {
        if (personId == null || personId <= 0 || ministryType == null) {
            throw new BusinessException("Pessoa e função ministerial são obrigatórias");
        }
        Person person = personRepository.findByIdForUpdate(personId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa", personId));
        if (!person.isActive()) {
            throw new MinistryPersonInactiveException();
        }

        Optional<PersonMinistry> existing = personMinistryRepository.findByPersonIdAndMinistryType(personId, ministryType);
        if (existing.isEmpty()) {
            try {
                personMinistryRepository.save(new PersonMinistry(person, ministryType));
            } catch (DataIntegrityViolationException e) {
                throw new DatabaseException("Não é possível associar esta pessoa à função ministerial informada.");
            }
        } else if (!Boolean.TRUE.equals(existing.get().getActive())) {
            PersonMinistry personMinistry = existing.get();
            personMinistry.activate();
            personMinistryRepository.save(personMinistry);
        }
        return person;
    }

    @Override
    @Transactional
    public PersonMinistrySyncResult syncMinistries(Long personId, Set<MinistryType> desiredMinistries) {
        if (personId == null || personId <= 0) {
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        if (desiredMinistries == null) {
            throw new BusinessException("O conjunto de ministérios é obrigatório");
        }
        Person person = personRepository.findByIdForUpdate(personId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa", personId));

        List<PersonMinistry> existing = personMinistryRepository.findAllByPersonId(personId);
        PersonMinistryDiff diff = PersonMinistryDiff.compute(desiredMinistries, existing);

        validateNoAssignmentConflicts(personId, diff.toDeactivate());
        if (diff.toDeactivate().stream().anyMatch(pm -> pm.getMinistryType() == MinistryType.PRIEST)) {
            guardPastorRequiresActivePriest(personId);
        }

        try {
            for (MinistryType type : diff.toAdd()) {
                personMinistryRepository.save(new PersonMinistry(person, type));
            }
        } catch (DataIntegrityViolationException e) {
            throw new DatabaseException("Não é possível associar esta pessoa às funções ministeriais informadas.");
        }
        for (PersonMinistry ministry : diff.toReactivate()) {
            ministry.activate();
            personMinistryRepository.save(ministry);
        }
        for (PersonMinistry ministry : diff.toDeactivate()) {
            ministry.deactivate();
            personMinistryRepository.save(ministry);
        }

        return new PersonMinistrySyncResult(
                person,
                desiredMinistries,
                diff.toAdd(),
                diff.toReactivate().stream().map(PersonMinistry::getMinistryType).collect(Collectors.toUnmodifiableSet()),
                diff.toDeactivate().stream().map(PersonMinistry::getMinistryType).collect(Collectors.toUnmodifiableSet()),
                diff.unchanged()
        );
    }

    /**
     * Preserva o invariante PASTOR ativo -> PRIEST ativo em qualquer caminho de escrita que possa
     * desativar PRIEST (remocao individual e sincronizacao administrativa). Person ja deve estar
     * bloqueada (forUpdate) antes desta checagem.
     */
    private void guardPastorRequiresActivePriest(Long personId) {
        if (parishStaffAssignmentRepository.existsByPersonIdAndResponsibilityAndActiveTrue(
                personId, ParishResponsibilityType.PASTOR)) {
            throw new PastorPriestMinistryRequiredException();
        }
    }

    private void validateNoAssignmentConflicts(Long personId, List<PersonMinistry> toDeactivate) {
        LocalDateTime currentSecond = LocalDateTime.now(clock).withNano(0);
        List<MinistryType> conflicting = toDeactivate.stream()
                .map(PersonMinistry::getMinistryType)
                .filter(type -> eventAssignmentRepository.existsActiveOrFutureByPersonIdAndAssignmentType(
                        personId, EventAssignmentType.valueOf(type.name()), currentSecond))
                .toList();
        if (!conflicting.isEmpty()) {
            String types = conflicting.stream().map(Enum::name).collect(Collectors.joining(", "));
            throw new DatabaseException(
                    "Não é possível remover os seguintes ministérios, pois possuem vínculos com escalas: " + types
            );
        }
    }
}
