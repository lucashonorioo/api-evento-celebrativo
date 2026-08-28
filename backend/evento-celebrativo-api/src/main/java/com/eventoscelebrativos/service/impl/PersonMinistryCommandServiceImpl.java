package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.exception.exceptions.MinistryInactiveException;
import com.eventoscelebrativos.exception.exceptions.MinistryLegacyCompatibilityRequiredException;
import com.eventoscelebrativos.exception.exceptions.MinistryPersonInactiveException;
import com.eventoscelebrativos.exception.exceptions.PastorPriestMinistryRequiredException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.model.Ministry;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.ParishResponsibilityType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.repository.EventAssignmentRepository;
import com.eventoscelebrativos.repository.MinistryRepository;
import com.eventoscelebrativos.repository.ParishStaffAssignmentRepository;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.service.LegacyMinistryTypeResolver;
import com.eventoscelebrativos.service.PersonMinistryCatalogSyncResult;
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
    private final MinistryRepository ministryRepository;
    private final PersonMinistryRepository personMinistryRepository;
    private final EventAssignmentRepository eventAssignmentRepository;
    private final ParishStaffAssignmentRepository parishStaffAssignmentRepository;
    private final LegacyMinistryTypeResolver legacyMinistryTypeResolver;
    private final Clock clock;

    public PersonMinistryCommandServiceImpl(
            PersonRepository personRepository,
            MinistryRepository ministryRepository,
            PersonMinistryRepository personMinistryRepository,
            EventAssignmentRepository eventAssignmentRepository,
            ParishStaffAssignmentRepository parishStaffAssignmentRepository,
            LegacyMinistryTypeResolver legacyMinistryTypeResolver,
            Clock clock
    ) {
        this.personRepository = personRepository;
        this.ministryRepository = ministryRepository;
        this.personMinistryRepository = personMinistryRepository;
        this.eventAssignmentRepository = eventAssignmentRepository;
        this.parishStaffAssignmentRepository = parishStaffAssignmentRepository;
        this.legacyMinistryTypeResolver = legacyMinistryTypeResolver;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Person create(Person person, Ministry ministry) {
        if (person == null || ministry == null) {
            throw new BusinessException("Pessoa e funcao ministerial sao obrigatorias");
        }
        Ministry lockedMinistry = requireActiveMinistryForUpdate(ministry.getId());
        MinistryType legacyMinistryType = requireLegacyMinistryTypeForWrite(lockedMinistry);
        Person saved = personRepository.save(person);
        try {
            personMinistryRepository.save(new PersonMinistry(saved, lockedMinistry, legacyMinistryType));
        } catch (DataIntegrityViolationException e) {
            throw new DatabaseException("Nao e possivel associar esta pessoa a funcao ministerial informada.");
        }
        return saved;
    }

    @Override
    @Transactional
    public Person create(Person person, MinistryType ministryType) {
        if (ministryType == null) {
            throw new BusinessException("Pessoa e funcao ministerial sao obrigatorias");
        }
        return create(person, legacyMinistryTypeResolver.requireMinistry(ministryType));
    }

    @Override
    public Person requireActiveMinistryPerson(Long personId, MinistryType ministryType, String entityLabel) {
        Ministry ministry = legacyMinistryTypeResolver.requireMinistry(ministryType);
        return requireActiveMinistryPerson(personId, ministry, entityLabel);
    }

    @Override
    @Transactional
    public Person requireActiveMinistryPersonForUpdate(Long personId, MinistryType ministryType, String entityLabel) {
        Ministry ministry = legacyMinistryTypeResolver.requireMinistry(ministryType);
        return requireActiveMinistryPersonForUpdate(personId, ministry, entityLabel);
    }

    @Override
    public Person requireActiveMinistryPerson(Long personId, Ministry ministry, String entityLabel) {
        return requireActiveMinistryPerson(personId, ministry, entityLabel, false);
    }

    @Override
    @Transactional
    public Person requireActiveMinistryPersonForUpdate(Long personId, Ministry ministry, String entityLabel) {
        return requireActiveMinistryPerson(personId, ministry, entityLabel, true);
    }

    private Person requireActiveMinistryPerson(
            Long personId,
            Ministry ministry,
            String entityLabel,
            boolean forUpdate
    ) {
        if (personId == null || personId <= 0) {
            throw new BusinessException("O Id deve ser positivo e nao nulo");
        }
        requireMinistry(ministry);
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
    public void removeMinistry(Long personId, Ministry ministry, String entityLabel) {
        requireMinistry(ministry);
        requireActiveMinistryPerson(personId, ministry, entityLabel, true);
        if (isPriestMinistry(ministry)) {
            guardPastorRequiresActivePriest(personId);
        }
        if (eventAssignmentRepository.existsActiveOrFutureByPersonIdAndAssignmentType(
                personId,
                requireLegacyEventAssignmentType(ministry),
                LocalDateTime.now(clock).withNano(0))) {
            throw new DatabaseException("Nao e possivel excluir este registro, pois ele possui vinculos com outros cadastros.");
        }
        PersonMinistry personMinistry = personMinistryRepository.findByPersonIdAndMinistryId(personId, ministry.getId())
                .orElseThrow(() -> new ResourceNotFoundException(entityLabel, personId));
        personMinistry.deactivate();
        personMinistryRepository.save(personMinistry);
    }

    @Override
    @Transactional
    public void removeMinistry(Long personId, MinistryType ministryType, String entityLabel) {
        Ministry ministry = legacyMinistryTypeResolver.requireMinistry(ministryType);
        removeMinistry(personId, ministry, entityLabel);
    }

    @Override
    @Transactional
    public Person addOrReactivateMinistry(Long personId, Ministry ministry) {
        if (personId == null || personId <= 0 || ministry == null) {
            throw new BusinessException("Pessoa e funcao ministerial sao obrigatorias");
        }
        Person person = personRepository.findByIdForUpdate(personId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa", personId));
        if (!person.isActive()) {
            throw new MinistryPersonInactiveException();
        }

        Ministry lockedMinistry = requireActiveMinistryForUpdate(ministry.getId());
        MinistryType legacyMinistryType = requireLegacyMinistryTypeForWrite(lockedMinistry);

        Optional<PersonMinistry> existing = personMinistryRepository.findByPersonIdAndMinistryId(
                personId,
                lockedMinistry.getId()
        );
        if (existing.isEmpty()) {
            try {
                personMinistryRepository.save(new PersonMinistry(person, lockedMinistry, legacyMinistryType));
            } catch (DataIntegrityViolationException e) {
                throw new DatabaseException("Nao e possivel associar esta pessoa a funcao ministerial informada.");
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
    public Person addOrReactivateMinistry(Long personId, MinistryType ministryType) {
        if (ministryType == null) {
            throw new BusinessException("Pessoa e funcao ministerial sao obrigatorias");
        }
        return addOrReactivateMinistry(personId, legacyMinistryTypeResolver.requireMinistry(ministryType));
    }

    @Override
    @Transactional
    public PersonMinistrySyncResult syncMinistries(Long personId, Set<MinistryType> desiredMinistries) {
        if (desiredMinistries == null) {
            throw new BusinessException("O conjunto de ministerios e obrigatorio");
        }
        Map<MinistryType, Ministry> ministriesByType = legacyMinistryTypeResolver.requireMinistries(desiredMinistries);
        List<Long> desiredMinistryIds = desiredMinistries.stream()
                .map(type -> ministriesByType.get(type).getId())
                .toList();
        PersonMinistryCatalogSyncResult result = syncMinistriesById(personId, desiredMinistryIds);

        Set<Long> mappedIds = new LinkedHashSet<>();
        mappedIds.addAll(result.activeMinistryIds());
        mappedIds.addAll(result.added());
        mappedIds.addAll(result.reactivated());
        mappedIds.addAll(result.deactivated());
        mappedIds.addAll(result.unchanged());
        Map<Long, MinistryType> legacyTypesByMinistryId =
                legacyMinistryTypeResolver.requireTypesByPersistentMinistryId(mappedIds);

        return new PersonMinistrySyncResult(
                result.person(),
                mapMinistryIdsToLegacyTypes(result.activeMinistryIds(), legacyTypesByMinistryId),
                mapMinistryIdsToLegacyTypes(result.added(), legacyTypesByMinistryId),
                mapMinistryIdsToLegacyTypes(result.reactivated(), legacyTypesByMinistryId),
                mapMinistryIdsToLegacyTypes(result.deactivated(), legacyTypesByMinistryId),
                mapMinistryIdsToLegacyTypes(result.unchanged(), legacyTypesByMinistryId)
        );
    }

    @Override
    @Transactional
    public PersonMinistryCatalogSyncResult syncMinistriesById(Long personId, List<Long> desiredMinistryIds) {
        if (personId == null || personId <= 0) {
            throw new BusinessException("O Id deve ser positivo e nao nulo");
        }
        Set<Long> normalizedDesiredMinistryIds = normalizeDesiredMinistryIds(desiredMinistryIds);
        Person person = personRepository.findByIdForUpdate(personId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa", personId));

        Map<Long, Ministry> ministriesById = lockExistingMinistries(normalizedDesiredMinistryIds);
        List<PersonMinistry> existing = personMinistryRepository.findAllByPersonId(personId);
        PersonMinistryDiff diff = PersonMinistryDiff.compute(normalizedDesiredMinistryIds, existing);

        validateOperationalMinistries(diff, ministriesById);
        Map<Long, MinistryType> legacyTypesByMinistryId = requireLegacyTypesForWrite(diff);
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
            throw new DatabaseException("Nao e possivel associar esta pessoa as funcoes ministeriais informadas.");
        }
        for (PersonMinistry ministry : diff.toReactivate()) {
            ministry.activate();
            personMinistryRepository.save(ministry);
        }
        for (PersonMinistry ministry : diff.toDeactivate()) {
            ministry.deactivate();
            personMinistryRepository.save(ministry);
        }

        return new PersonMinistryCatalogSyncResult(
                person,
                Set.copyOf(normalizedDesiredMinistryIds),
                diff.toAdd(),
                mapPersonMinistriesToMinistryIds(diff.toReactivate()),
                mapPersonMinistriesToMinistryIds(diff.toDeactivate()),
                diff.unchanged()
        );
    }

    /**
     * Preserva o invariante PASTOR ativo -> PRIEST ativo em qualquer caminho de escrita que possa
     * desativar PRIEST. Person ja deve estar bloqueada antes desta checagem.
     */
    private void guardPastorRequiresActivePriest(Long personId) {
        if (parishStaffAssignmentRepository.existsByPersonIdAndResponsibilityAndActiveTrue(
                personId, ParishResponsibilityType.PASTOR)) {
            throw new PastorPriestMinistryRequiredException();
        }
    }

    private Set<Long> normalizeDesiredMinistryIds(List<Long> rawMinistryIds) {
        if (rawMinistryIds == null) {
            throw new BusinessException("O conjunto de ministerios e obrigatorio");
        }
        Set<Long> desired = new LinkedHashSet<>();
        for (Long ministryId : rawMinistryIds) {
            if (ministryId == null || ministryId <= 0) {
                throw new BadRequestException("Id de ministerio invalido");
            }
            if (!desired.add(ministryId)) {
                throw new BusinessException("Ministerio duplicado no request: " + ministryId);
            }
        }
        return desired;
    }

    private Map<Long, Ministry> lockExistingMinistries(Set<Long> ministryIds) {
        if (ministryIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Ministry> ministriesById = ministryRepository.findAllByIdInForUpdate(ministryIds)
                .stream()
                .collect(Collectors.toMap(
                        Ministry::getId,
                        ministry -> ministry,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        for (Long ministryId : ministryIds) {
            if (!ministriesById.containsKey(ministryId)) {
                throw new ResourceNotFoundException("Ministerio", ministryId);
            }
        }
        return ministriesById;
    }

    private Ministry requireActiveMinistryForUpdate(Long ministryId) {
        if (ministryId == null || ministryId <= 0) {
            throw new BusinessException("Funcao ministerial persistente e obrigatoria");
        }
        Ministry ministry = ministryRepository.findByIdForUpdate(ministryId)
                .orElseThrow(() -> new ResourceNotFoundException("Ministerio", ministryId));
        if (!ministry.isActive()) {
            throw new MinistryInactiveException();
        }
        return ministry;
    }

    private void validateOperationalMinistries(PersonMinistryDiff diff, Map<Long, Ministry> ministriesById) {
        Set<Long> idsThatRemainActive = new LinkedHashSet<>();
        idsThatRemainActive.addAll(diff.toAdd());
        diff.toReactivate().forEach(personMinistry -> idsThatRemainActive.add(personMinistry.getMinistry().getId()));
        idsThatRemainActive.addAll(diff.unchanged());

        for (Long ministryId : idsThatRemainActive) {
            Ministry ministry = ministriesById.get(ministryId);
            if (ministry == null || !ministry.isActive()) {
                throw new MinistryInactiveException();
            }
        }
    }

    private Map<Long, MinistryType> requireLegacyTypesForWrite(PersonMinistryDiff diff) {
        Set<Long> idsRequiringLegacyCompatibility = new LinkedHashSet<>(diff.toAdd());
        diff.toReactivate().forEach(personMinistry ->
                idsRequiringLegacyCompatibility.add(personMinistry.getMinistry().getId()));
        if (idsRequiringLegacyCompatibility.isEmpty()) {
            return Map.of();
        }
        try {
            return legacyMinistryTypeResolver.requireTypesByPersistentMinistryId(idsRequiringLegacyCompatibility);
        } catch (IllegalStateException e) {
            throw new MinistryLegacyCompatibilityRequiredException();
        }
    }

    private void validateNoAssignmentConflicts(Long personId, List<PersonMinistry> toDeactivate) {
        LocalDateTime currentSecond = LocalDateTime.now(clock).withNano(0);
        List<MinistryType> conflicting = toDeactivate.stream()
                .filter(personMinistry -> eventAssignmentRepository.existsActiveOrFutureByPersonIdAndAssignmentType(
                        personId,
                        requireLegacyEventAssignmentType(personMinistry.getMinistry()),
                        currentSecond))
                .map(personMinistry -> legacyMinistryTypeResolver.requireMinistryType(personMinistry.getMinistry()))
                .toList();
        if (!conflicting.isEmpty()) {
            String types = conflicting.stream().map(Enum::name).collect(Collectors.joining(", "));
            throw new DatabaseException(
                    "Nao e possivel remover os seguintes ministerios, pois possuem vinculos com escalas: " + types
            );
        }
    }

    private boolean isPriestMinistry(PersonMinistry personMinistry) {
        return isPriestMinistry(personMinistry.getMinistry());
    }

    private boolean isPriestMinistry(Ministry ministry) {
        return ministry.getId().equals(legacyMinistryTypeResolver.requireMinistry(MinistryType.PRIEST).getId());
    }

    private void requireMinistry(Ministry ministry) {
        if (ministry == null || ministry.getId() == null || ministry.getId() <= 0) {
            throw new BusinessException("Funcao ministerial persistente e obrigatoria");
        }
    }

    private MinistryType requireLegacyMinistryTypeForWrite(Ministry ministry) {
        requireMinistry(ministry);
        try {
            return legacyMinistryTypeResolver.requireMinistryType(ministry);
        } catch (IllegalStateException e) {
            throw new MinistryLegacyCompatibilityRequiredException();
        }
    }

    private com.eventoscelebrativos.model.EventAssignmentType requireLegacyEventAssignmentType(Ministry ministry) {
        try {
            return legacyMinistryTypeResolver.requireEventAssignmentType(ministry);
        } catch (IllegalStateException e) {
            throw new MinistryLegacyCompatibilityRequiredException();
        }
    }

    private Set<MinistryType> mapMinistryIdsToLegacyTypes(
            Set<Long> ministryIds,
            Map<Long, MinistryType> legacyTypesByMinistryId
    ) {
        return ministryIds.stream()
                .map(legacyTypesByMinistryId::get)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<Long> mapPersonMinistriesToMinistryIds(List<PersonMinistry> personMinistries) {
        return personMinistries.stream()
                .map(personMinistry -> personMinistry.getMinistry().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
