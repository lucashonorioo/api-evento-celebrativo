package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.exception.exceptions.MinistryInactiveException;
import com.eventoscelebrativos.exception.exceptions.MinistryPersonInactiveException;
import com.eventoscelebrativos.exception.exceptions.PastorPriestMinistryRequiredException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.model.EventAssignmentType;
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
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final EntityManager entityManager;
    private final Clock clock;

    public PersonMinistryCommandServiceImpl(
            PersonRepository personRepository,
            MinistryRepository ministryRepository,
            PersonMinistryRepository personMinistryRepository,
            EventAssignmentRepository eventAssignmentRepository,
            ParishStaffAssignmentRepository parishStaffAssignmentRepository,
            LegacyMinistryTypeResolver legacyMinistryTypeResolver,
            EntityManager entityManager,
            Clock clock
    ) {
        this.personRepository = personRepository;
        this.ministryRepository = ministryRepository;
        this.personMinistryRepository = personMinistryRepository;
        this.eventAssignmentRepository = eventAssignmentRepository;
        this.parishStaffAssignmentRepository = parishStaffAssignmentRepository;
        this.legacyMinistryTypeResolver = legacyMinistryTypeResolver;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Person create(Person person, Ministry ministry) {
        if (person == null || ministry == null) {
            throw new BusinessException("Pessoa e funcao ministerial sao obrigatorias");
        }
        Ministry lockedMinistry = requireActiveMinistryForUpdate(ministry.getId());
        Person saved = personRepository.save(person);
        try {
            personMinistryRepository.save(new PersonMinistry(saved, lockedMinistry));
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
        findLegacyEventAssignmentType(ministry).ifPresent(assignmentType -> {
            if (eventAssignmentRepository.existsActiveOrFutureByPersonIdAndAssignmentType(
                    personId,
                    assignmentType,
                    LocalDateTime.now(clock).withNano(0))) {
                throw new DatabaseException("Nao e possivel excluir este registro, pois ele possui vinculos com outros cadastros.");
            }
        });
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
        // Existing-person membership writes use Person -> Ministry -> PersonMinistry. The Ministry
        // row is refreshed after the pessimistic lock so a Ministry loaded earlier in this
        // persistence context cannot authorize a stale active=true write after catalog deactivation.
        Person person = personRepository.findByIdForUpdate(personId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa", personId));
        if (!person.isActive()) {
            throw new MinistryPersonInactiveException();
        }

        Ministry lockedMinistry = requireActiveMinistryForUpdate(ministry.getId());

        Optional<PersonMinistry> existing = personMinistryRepository.findByPersonIdAndMinistryId(
                personId,
                lockedMinistry.getId()
        );
        if (existing.isEmpty()) {
            try {
                personMinistryRepository.save(new PersonMinistry(person, lockedMinistry));
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
                legacyMinistryTypeResolver.findTypesByPersistentMinistryId(mappedIds);

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
        validateNoAssignmentConflicts(personId, diff.toDeactivate());
        if (diff.toDeactivate().stream().anyMatch(this::isPriestMinistry)) {
            guardPastorRequiresActivePriest(personId);
        }

        try {
            for (Long ministryId : diff.toAdd()) {
                Ministry ministry = ministriesById.get(ministryId);
                personMinistryRepository.save(new PersonMinistry(person, ministry));
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
        List<Long> sortedMinistryIds = ministryIds.stream().sorted().toList();
        Set<Long> existingIds = ministryRepository.findAllById(sortedMinistryIds)
                .stream()
                .map(Ministry::getId)
                .collect(Collectors.toSet());
        for (Long ministryId : sortedMinistryIds) {
            if (!existingIds.contains(ministryId)) {
                throw new ResourceNotFoundException("Ministerio", ministryId);
            }
        }

        Map<Long, Ministry> ministriesById = ministryRepository.findAllByIdInForUpdate(sortedMinistryIds)
                .stream()
                .peek(this::refreshMinistryForUpdate)
                .collect(Collectors.toMap(
                        Ministry::getId,
                        ministry -> ministry,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        for (Long ministryId : sortedMinistryIds) {
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
        if (!ministryRepository.existsById(ministryId)) {
            throw new ResourceNotFoundException("Ministerio", ministryId);
        }
        Ministry ministry = ministryRepository.findByIdForUpdate(ministryId)
                .orElseThrow(() -> new ResourceNotFoundException("Ministerio", ministryId));
        refreshMinistryForUpdate(ministry);
        if (!ministry.isActive()) {
            throw new MinistryInactiveException();
        }
        return ministry;
    }

    private void refreshMinistryForUpdate(Ministry ministry) {
        entityManager.refresh(ministry, LockModeType.PESSIMISTIC_WRITE);
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

    private void validateNoAssignmentConflicts(Long personId, List<PersonMinistry> toDeactivate) {
        LocalDateTime currentSecond = LocalDateTime.now(clock).withNano(0);
        List<MinistryType> conflicting = toDeactivate.stream()
                .map(personMinistry -> legacyMinistryTypeResolver.findMinistryType(personMinistry.getMinistry()))
                .flatMap(Optional::stream)
                .filter(ministryType -> eventAssignmentRepository.existsActiveOrFutureByPersonIdAndAssignmentType(
                        personId,
                        EventAssignmentType.valueOf(ministryType.name()),
                        currentSecond))
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
        return legacyMinistryTypeResolver.findMinistryType(ministry)
                .filter(MinistryType.PRIEST::equals)
                .isPresent();
    }

    private void requireMinistry(Ministry ministry) {
        if (ministry == null || ministry.getId() == null || ministry.getId() <= 0) {
            throw new BusinessException("Funcao ministerial persistente e obrigatoria");
        }
    }

    private Optional<EventAssignmentType> findLegacyEventAssignmentType(Ministry ministry) {
        return legacyMinistryTypeResolver.findEventAssignmentType(ministry);
    }

    private Set<MinistryType> mapMinistryIdsToLegacyTypes(
            Set<Long> ministryIds,
            Map<Long, MinistryType> legacyTypesByMinistryId
    ) {
        return ministryIds.stream()
                .map(legacyTypesByMinistryId::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<Long> mapPersonMinistriesToMinistryIds(List<PersonMinistry> personMinistries) {
        return personMinistries.stream()
                .map(personMinistry -> personMinistry.getMinistry().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
