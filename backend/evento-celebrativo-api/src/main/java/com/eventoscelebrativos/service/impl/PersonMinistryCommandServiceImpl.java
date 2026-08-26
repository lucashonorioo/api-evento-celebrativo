package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.exception.exceptions.MinistryPersonInactiveException;
import com.eventoscelebrativos.exception.exceptions.PastorPriestMinistryRequiredException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.model.Ministry;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.ParishResponsibilityType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.repository.EventAssignmentRepository;
import com.eventoscelebrativos.repository.ParishStaffAssignmentRepository;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.service.LegacyMinistryTypeResolver;
import com.eventoscelebrativos.service.PersonMinistryCommandService;
import com.eventoscelebrativos.service.PersonMinistryDiff;
import com.eventoscelebrativos.service.PersonMinistrySyncResult;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PersonMinistryCommandServiceImpl implements PersonMinistryCommandService {

    private final PersonRepository personRepository;
    private final PersonMinistryRepository personMinistryRepository;
    private final EventAssignmentRepository eventAssignmentRepository;
    private final ParishStaffAssignmentRepository parishStaffAssignmentRepository;
    private final LegacyMinistryTypeResolver legacyMinistryTypeResolver;
    private final Clock clock;

    public PersonMinistryCommandServiceImpl(
            PersonRepository personRepository,
            PersonMinistryRepository personMinistryRepository,
            EventAssignmentRepository eventAssignmentRepository,
            ParishStaffAssignmentRepository parishStaffAssignmentRepository,
            LegacyMinistryTypeResolver legacyMinistryTypeResolver,
            Clock clock
    ) {
        this.personRepository = personRepository;
        this.personMinistryRepository = personMinistryRepository;
        this.eventAssignmentRepository = eventAssignmentRepository;
        this.parishStaffAssignmentRepository = parishStaffAssignmentRepository;
        this.legacyMinistryTypeResolver = legacyMinistryTypeResolver;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Person create(Person person, MinistryType ministryType) {
        if (person == null || ministryType == null) {
            throw new BusinessException("Pessoa e função ministerial são obrigatórias");
        }
        Ministry ministry = legacyMinistryTypeResolver.requireMinistry(ministryType);
        Person saved = personRepository.save(person);
        try {
            personMinistryRepository.save(new PersonMinistry(saved, ministry, ministryType));
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

    private Person requireActiveMinistryPerson(
            Long personId,
            MinistryType ministryType,
            String entityLabel,
            boolean forUpdate
    ) {
        Ministry ministry = legacyMinistryTypeResolver.requireMinistry(ministryType);
        return requireActiveMinistryPerson(personId, ministry, entityLabel, forUpdate);
    }

    private Person requireActiveMinistryPerson(
            Long personId,
            Ministry ministry,
            String entityLabel,
            boolean forUpdate
    ) {
        if (personId == null || personId <= 0) {
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        Person person = (forUpdate ? personRepository.findByIdForUpdate(personId) : personRepository.findById(personId))
                .orElseThrow(() -> new ResourceNotFoundException(entityLabel, personId));
        if (!person.isActive()) {
            throw new ResourceNotFoundException(entityLabel, personId);
        }
        personMinistryRepository.findByPersonIdAndMinistryId(personId, ministry.getId())
                .filter(pm -> Boolean.TRUE.equals(pm.getActive()))
                .orElseThrow(() -> new ResourceNotFoundException(entityLabel, personId));
        return person;
    }

    @Override
    @Transactional
    public void removeMinistry(Long personId, MinistryType ministryType, String entityLabel) {
        Ministry ministry = legacyMinistryTypeResolver.requireMinistry(ministryType);
        requireActiveMinistryPerson(personId, ministry, entityLabel, true);
        if (isPriestMinistry(ministry)) {
            guardPastorRequiresActivePriest(personId);
        }
        if (eventAssignmentRepository.existsActiveOrFutureByPersonIdAndAssignmentType(
                personId,
                legacyMinistryTypeResolver.requireEventAssignmentType(ministry),
                LocalDateTime.now(clock).withNano(0))) {
            throw new DatabaseException("Não é possível excluir este registro, pois ele possui vínculos com outros cadastros.");
        }
        PersonMinistry personMinistry = personMinistryRepository.findByPersonIdAndMinistryId(personId, ministry.getId())
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
        Ministry ministry = legacyMinistryTypeResolver.requireMinistry(ministryType);
        Person person = personRepository.findByIdForUpdate(personId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa", personId));
        if (!person.isActive()) {
            throw new MinistryPersonInactiveException();
        }

        Optional<PersonMinistry> existing = personMinistryRepository.findByPersonIdAndMinistryId(personId, ministry.getId());
        if (existing.isEmpty()) {
            try {
                personMinistryRepository.save(new PersonMinistry(person, ministry, ministryType));
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

        Map<MinistryType, Ministry> ministriesByType = legacyMinistryTypeResolver.requireMinistries(desiredMinistries);
        Map<Long, Ministry> ministriesById = ministriesByType.values().stream()
                .collect(Collectors.toMap(Ministry::getId, ministry -> ministry, (left, right) -> left, LinkedHashMap::new));
        Map<Long, MinistryType> legacyTypesByMinistryId = ministriesByType.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getValue().getId(),
                        Map.Entry::getKey,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Set<Long> desiredMinistryIds = desiredMinistries.stream()
                .map(type -> ministriesByType.get(type).getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<PersonMinistry> existing = personMinistryRepository.findAllByPersonId(personId);
        PersonMinistryDiff diff = PersonMinistryDiff.compute(desiredMinistryIds, existing);

        validateNoAssignmentConflicts(personId, diff.toDeactivate());
        if (diff.toDeactivate().stream().anyMatch(this::isPriestMinistry)) {
            guardPastorRequiresActivePriest(personId);
        }

        try {
            for (Long ministryId : diff.toAdd()) {
                Ministry ministry = ministriesById.get(ministryId);
                personMinistryRepository.save(new PersonMinistry(person, ministry, legacyTypesByMinistryId.get(ministryId)));
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
                mapMinistryIdsToLegacyTypes(diff.toAdd(), legacyTypesByMinistryId),
                mapPersonMinistriesToLegacyTypes(diff.toReactivate()),
                mapPersonMinistriesToLegacyTypes(diff.toDeactivate()),
                mapMinistryIdsToLegacyTypes(diff.unchanged(), legacyTypesByMinistryId)
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
                .filter(personMinistry -> eventAssignmentRepository.existsActiveOrFutureByPersonIdAndAssignmentType(
                        personId,
                        legacyMinistryTypeResolver.requireEventAssignmentType(personMinistry.getMinistry()),
                        currentSecond))
                .map(personMinistry -> legacyMinistryTypeResolver.requireMinistryType(personMinistry.getMinistry()))
                .toList();
        if (!conflicting.isEmpty()) {
            String types = conflicting.stream().map(Enum::name).collect(Collectors.joining(", "));
            throw new DatabaseException(
                    "Não é possível remover os seguintes ministérios, pois possuem vínculos com escalas: " + types
            );
        }
    }

    private boolean isPriestMinistry(PersonMinistry personMinistry) {
        return isPriestMinistry(personMinistry.getMinistry());
    }

    private boolean isPriestMinistry(Ministry ministry) {
        return ministry.getId().equals(legacyMinistryTypeResolver.requireMinistry(MinistryType.PRIEST).getId());
    }

    private Set<MinistryType> mapMinistryIdsToLegacyTypes(
            Set<Long> ministryIds,
            Map<Long, MinistryType> legacyTypesByMinistryId
    ) {
        return ministryIds.stream()
                .map(legacyTypesByMinistryId::get)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<MinistryType> mapPersonMinistriesToLegacyTypes(List<PersonMinistry> personMinistries) {
        return personMinistries.stream()
                .map(personMinistry -> legacyMinistryTypeResolver.requireMinistryType(personMinistry.getMinistry()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
