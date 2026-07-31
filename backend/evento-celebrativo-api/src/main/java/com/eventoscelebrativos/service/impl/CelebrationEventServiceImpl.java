package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.request.CelebrationEventRequestDTO;
import com.eventoscelebrativos.dto.request.CelebrationEventScaleRequestDTO;
import com.eventoscelebrativos.dto.request.CelebrationEventWithScaleRequestDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventResponseDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventScaleDetailResponseDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventScaleParticipationDetailResponseDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventScaleResponseDTO;
import com.eventoscelebrativos.dto.response.EventScheduleAssignmentResponseDTO;
import com.eventoscelebrativos.dto.response.EventScheduleQueryResponseDTO;
import com.eventoscelebrativos.dto.response.EucharistScaleEventResponseDTO;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.exception.exceptions.MultipleAssignmentsForPersonInEventException;
import com.eventoscelebrativos.exception.exceptions.TemporalPrecisionNotSupportedException;
import com.eventoscelebrativos.mapper.CelebrationEventMapper;
import com.eventoscelebrativos.mapper.CelebrationEventScaleDetailMapper;
import com.eventoscelebrativos.mapper.CelebrationEventScaleMapper;
import com.eventoscelebrativos.mapper.CelebrationEventScaleParticipationMapper;
import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.EventScheduleType;
import com.eventoscelebrativos.model.Location;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.projection.EventScheduleAssignmentProjection;
import com.eventoscelebrativos.projection.EventScheduleEventProjection;
import com.eventoscelebrativos.projection.EucharistScaleEventProjection;
import com.eventoscelebrativos.model.EventAssignment;
import com.eventoscelebrativos.repository.CelebrationEventRepository;
import com.eventoscelebrativos.repository.EventAssignmentRepository;
import com.eventoscelebrativos.repository.LocationRepository;
import com.eventoscelebrativos.service.CelebrationEventService;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.service.EventAssignmentCommandService;
import com.eventoscelebrativos.service.EventAssignmentGroup;
import com.eventoscelebrativos.service.EventAssignmentReadService;
import com.eventoscelebrativos.service.EventAssignmentTarget;
import com.eventoscelebrativos.service.EventParticipationResponseService;
import com.eventoscelebrativos.service.EventScaleAssignmentPlan;
import com.eventoscelebrativos.service.ParticipationResponseSnapshot;
import com.eventoscelebrativos.service.PersonMinistryEligibilityResolver;
import com.eventoscelebrativos.service.PersonUnavailabilityConflictService;
import com.eventoscelebrativos.service.ScaleParticipantEligibility;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;


@Service
public class CelebrationEventServiceImpl implements CelebrationEventService {

    private static final int MAX_PAGE_SIZE = 100;

    private final CelebrationEventRepository celebrationEventRepository;
    private final LocationRepository locationRepository;
    private final CelebrationEventMapper celebrationEventMapper;
    private final CelebrationEventScaleMapper celebrationEventScaleMapper;
    private final CelebrationEventScaleDetailMapper celebrationEventScaleDetailMapper;
    private final CelebrationEventScaleParticipationMapper celebrationEventScaleParticipationMapper;
    private final EventAssignmentCommandService eventAssignmentCommandService;
    private final EventAssignmentReadService eventAssignmentReadService;
    private final EventAssignmentRepository eventAssignmentRepository;
    private final EventParticipationResponseService eventParticipationResponseService;
    private final PersonMinistryEligibilityResolver personMinistryEligibilityResolver;
    private final PersonUnavailabilityConflictService personUnavailabilityConflictService;
    private final Clock clock;

    public CelebrationEventServiceImpl(
            CelebrationEventRepository celebrationEventRepository,
            LocationRepository locationRepository,
            CelebrationEventMapper celebrationEventMapper,
            CelebrationEventScaleMapper celebrationEventScaleMapper,
            CelebrationEventScaleDetailMapper celebrationEventScaleDetailMapper,
            CelebrationEventScaleParticipationMapper celebrationEventScaleParticipationMapper,
            EventAssignmentCommandService eventAssignmentCommandService,
            EventAssignmentReadService eventAssignmentReadService,
            EventAssignmentRepository eventAssignmentRepository,
            EventParticipationResponseService eventParticipationResponseService,
            PersonMinistryEligibilityResolver personMinistryEligibilityResolver,
            PersonUnavailabilityConflictService personUnavailabilityConflictService,
            Clock clock
    ) {
        this.celebrationEventRepository = celebrationEventRepository;
        this.locationRepository = locationRepository;
        this.celebrationEventMapper = celebrationEventMapper;
        this.celebrationEventScaleMapper = celebrationEventScaleMapper;
        this.celebrationEventScaleDetailMapper = celebrationEventScaleDetailMapper;
        this.celebrationEventScaleParticipationMapper = celebrationEventScaleParticipationMapper;
        this.eventAssignmentCommandService = eventAssignmentCommandService;
        this.eventAssignmentReadService = eventAssignmentReadService;
        this.eventAssignmentRepository = eventAssignmentRepository;
        this.eventParticipationResponseService = eventParticipationResponseService;
        this.personMinistryEligibilityResolver = personMinistryEligibilityResolver;
        this.personUnavailabilityConflictService = personUnavailabilityConflictService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CelebrationEventResponseDTO createEvent(CelebrationEventRequestDTO celebrationEventRequestDTO) {
        validateCreationRange(celebrationEventRequestDTO.getStartAt(), celebrationEventRequestDTO.getEndAt());
        CelebrationEvent celebrationEvent = celebrationEventMapper.toEntity(celebrationEventRequestDTO);
        celebrationEvent = celebrationEventRepository.save(celebrationEvent);

        return celebrationEventMapper.toDto(celebrationEvent);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CelebrationEventResponseDTO> findAllEvents() {
        List<CelebrationEvent> celebrationEvents = celebrationEventRepository.findAll();
        return celebrationEventMapper.toDtoList(celebrationEvents);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EucharistScaleEventResponseDTO> findEucharistScale(Pageable pageable, LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new BusinessException("As datas estão inválidas");
        }

        Page<EucharistScaleEventProjection> eventPage =
                celebrationEventRepository.findEucharistScaleByAssignments(pageable, rangeStart(startDate), rangeEnd(endDate));
        List<Long> eventIds = eventPage.getContent().stream()
                .map(EucharistScaleEventProjection::getEventId)
                .distinct()
                .toList();
        Map<Long, List<String>> ministersByEvent = findEucharistMinistersByEvent(eventIds);

        List<EucharistScaleEventResponseDTO> content = eventPage.getContent().stream()
                .map(event -> toEucharistScaleResponse(
                        event,
                        ministersByEvent.getOrDefault(event.getEventId(), List.of())
                ))
                .toList();

        return new PageImpl<>(content, eventPage.getPageable(), eventPage.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EventScheduleQueryResponseDTO> findEventSchedules(
            LocalDate startDate,
            LocalDate endDate,
            EventScheduleType type,
            int page,
            int size,
            boolean includeUnassigned
    ) {
        validateEventScheduleQuery(startDate, endDate, type, page, size);

        PageRequest pageable = PageRequest.of(page, size);
        EventAssignmentType assignmentType = toAssignmentType(type);
        Page<EventScheduleEventProjection> eventPage = celebrationEventRepository.findEventScheduleEventsByAssignments(
                pageable,
                rangeStart(startDate),
                rangeEnd(endDate),
                assignmentType.name(),
                includeUnassigned
        );

        List<Long> eventIds = eventPage.getContent().stream()
                .map(EventScheduleEventProjection::getEventId)
                .toList();

        Map<Long, List<EventScheduleAssignmentResponseDTO>> assignmentsByEvent =
                findAssignmentsByEventAssignmentType(eventIds, assignmentType);

        List<EventScheduleQueryResponseDTO> content = eventPage.getContent().stream()
                .map(event -> toEventScheduleQueryResponse(event, type, assignmentsByEvent))
                .toList();

        return new PageImpl<>(content, pageable, eventPage.getTotalElements());
    }


    @Override
    @Transactional(readOnly = true)
    public CelebrationEventResponseDTO findEventById(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        CelebrationEvent celebrationEvent = celebrationEventRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Evento celebrativo", id));
        return celebrationEventMapper.toDto(celebrationEvent);
    }

    @Override
    @Transactional(readOnly = true)
    public CelebrationEventScaleDetailResponseDTO findScaleByEventId(Long id) {
        validateId(id);
        CelebrationEvent celebrationEvent = celebrationEventRepository.findByIdWithLocations(id)
                .orElseThrow(() -> new ResourceNotFoundException("Evento celebrativo", id));

        Location location = firstLocation(celebrationEvent);
        EventAssignmentGroup assignments = EventAssignmentGroup.from(
                celebrationEvent.getId(),
                eventAssignmentReadService.findAllByEventId(id)
        );

        return celebrationEventScaleDetailMapper.toDto(
                celebrationEvent,
                location,
                assignments
        );
    }

    @Override
    @Transactional(readOnly = true)
    public CelebrationEventScaleParticipationDetailResponseDTO findScaleParticipationByEventId(Long id) {
        validateId(id);
        CelebrationEvent celebrationEvent = celebrationEventRepository.findByIdWithLocations(id)
                .orElseThrow(() -> new ResourceNotFoundException("Evento celebrativo", id));

        Location location = firstLocation(celebrationEvent);
        EventAssignmentGroup assignments = EventAssignmentGroup.from(
                celebrationEvent.getId(),
                eventAssignmentReadService.findAllByEventId(id)
        );
        Map<Long, ParticipationResponseSnapshot> participationByPersonId =
                eventParticipationResponseService.findByEventId(id);

        return celebrationEventScaleParticipationMapper.toDto(
                celebrationEvent,
                location,
                assignments,
                participationByPersonId
        );
    }

    @Override
    @Transactional
    public CelebrationEventResponseDTO updateEvent(Long id, CelebrationEventRequestDTO celebrationEventRequestDTO) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        validateRange(celebrationEventRequestDTO.getStartAt(), celebrationEventRequestDTO.getEndAt());

        // Protocolo de concorrencia: nenhuma leitura simples (nao bloqueante) pode ocorrer antes de
        // todos os locks necessarios. Sob MySQL/InnoDB com REPEATABLE READ, a primeira leitura nao
        // bloqueante da transacao fixa o snapshot para TODAS as leituras simples seguintes, mesmo as
        // que rodam depois de esperar um lock. Por isso currentAssignments usa uma consulta com
        // PESSIMISTIC_WRITE (nao fixa snapshot) em vez de uma leitura simples.
        CelebrationEvent celebrationEvent = celebrationEventRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Evento celebrativo", id));

        // Todos os participantes deste fluxo sao pessoas atuais (nenhuma pessoa nova e adicionada
        // por aqui). Uma alteracao de startAt/endAt pode tornar assignments existentes
        // incompatíveis com indisponibilidades ja cadastradas; isso e permitido e o conflito passa
        // a ser derivado nas consultas administrativas, em vez de bloquear esta operacao. O lock das
        // pessoas e mantido apenas como ponto de serializacao comum com o fluxo pessoal de
        // indisponibilidade (secao 25/30), nao para validacao bloqueante.
        List<EventAssignment> currentAssignments = eventAssignmentRepository.findAllByEventIdForUpdate(id);
        List<Long> assignedPersonIds = currentAssignments.stream()
                .map(assignment -> assignment.getPerson().getId())
                .distinct()
                .sorted()
                .toList();

        if (!assignedPersonIds.isEmpty()) {
            personUnavailabilityConflictService.lockPersonsInOrder(assignedPersonIds);
        }

        celebrationEventMapper.updateCelebrationEventMapperFromDto(celebrationEventRequestDTO, celebrationEvent);
        celebrationEvent = celebrationEventRepository.save(celebrationEvent);

        return celebrationEventMapper.toDto(celebrationEvent);
    }

    @Override
    @Transactional
    public CelebrationEventScaleResponseDTO updateEventScale(Long id, CelebrationEventScaleRequestDTO celebrationEventScaleRequestDTO) {
        validateId(id);
        // Mesmo cuidado de updateEvent: nenhuma leitura simples (nao bloqueante) pode ocorrer antes de
        // todos os locks necessarios, preservando a validade do snapshot da transacao sob
        // MySQL/InnoDB REPEATABLE READ. Ordem: evento -> assignments atuais -> pessoas.
        CelebrationEvent celebrationEvent = celebrationEventRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Evento celebrativo", id));

        List<EventAssignment> currentAssignments = eventAssignmentRepository.findAllByEventIdForUpdate(id);
        Set<Long> currentPersonIds = currentAssignments.stream()
                .map(assignment -> assignment.getPerson().getId())
                .collect(Collectors.toSet());

        Map<Long, EventAssignmentType> desiredAssignmentsByPersonId =
                buildDesiredAssignments(id, celebrationEventScaleRequestDTO);

        Set<Long> unionPersonIds = new TreeSet<>(desiredAssignmentsByPersonId.keySet());
        unionPersonIds.addAll(currentPersonIds);
        personUnavailabilityConflictService.lockPersonsInOrder(unionPersonIds);

        ScalePlanResult planResult = buildScalePlanResult(celebrationEvent, celebrationEventScaleRequestDTO);
        EventScaleAssignmentPlan plan = planResult.plan();
        List<EventAssignmentTarget> targets = plan.toTargets();

        // Pessoa retida (personId ja atribuido ao evento antes desta chamada, mesmo que a funcao
        // tenha mudado) pode permanecer mesmo em conflito com indisponibilidade existente; somente
        // pessoa nova (personId ainda nao atribuido) e validada contra a indisponibilidade
        // bloqueante, evitando barrar quem ja estava na escala por um conflito derivado.
        List<EventAssignmentTarget> newPersonTargets = targets.stream()
                .filter(target -> !currentPersonIds.contains(target.person().getId()))
                .toList();
        validateAvailabilityForTargets(newPersonTargets, celebrationEvent.getStartAt(), celebrationEvent.getEndAt());

        applyLocation(celebrationEvent, planResult.location());
        eventAssignmentCommandService.synchronizeAssignments(celebrationEvent, currentAssignments, targets);

        return celebrationEventScaleMapper.toDto(celebrationEvent, plan);
    }

    @Override
    @Transactional
    public CelebrationEventScaleResponseDTO createEventWithScale(CelebrationEventWithScaleRequestDTO celebrationEventWithScaleRequestDTO) {
        CelebrationEvent celebrationEvent = new CelebrationEvent();
        celebrationEvent.setNameMassOrEvent(celebrationEventWithScaleRequestDTO.getNameMassOrEvent());
        celebrationEvent.setStartAt(celebrationEventWithScaleRequestDTO.getStartAt());
        celebrationEvent.setEndAt(celebrationEventWithScaleRequestDTO.getEndAt());
        celebrationEvent.setMassOrCelebration(celebrationEventWithScaleRequestDTO.getMassOrCelebration());
        validateCreationRange(celebrationEvent.getStartAt(), celebrationEvent.getEndAt());

        CelebrationEventScaleRequestDTO scaleRequest = toScaleRequest(celebrationEventWithScaleRequestDTO);

        // Estado desejado construido e validado (sem leitura ao banco) antes de qualquer lock, para
        // rejeitar duplicidades de pessoa o mais cedo possivel.
        Map<Long, EventAssignmentType> desiredAssignmentsByPersonId = buildDesiredAssignments(null, scaleRequest);

        // IDs extraidos do mapa ja validado (sem leitura ao banco) para bloquear as Persons antes de
        // qualquer leitura simples, pelo mesmo motivo de updateEventScale.
        personUnavailabilityConflictService.lockPersonsInOrder(new TreeSet<>(desiredAssignmentsByPersonId.keySet()));

        ScalePlanResult planResult = buildScalePlanResult(celebrationEvent, scaleRequest);
        EventScaleAssignmentPlan plan = planResult.plan();
        List<EventAssignmentTarget> targets = plan.toTargets();

        validateAvailabilityForTargets(targets, celebrationEvent.getStartAt(), celebrationEvent.getEndAt());

        applyLocation(celebrationEvent, planResult.location());
        CelebrationEvent savedEvent = celebrationEventRepository.save(celebrationEvent);

        eventAssignmentCommandService.synchronizeAssignments(savedEvent, List.of(), targets);

        return celebrationEventScaleMapper.toDto(savedEvent, plan);
    }

    /**
     * Constroi o estado final desejado da escala (uma unica funcao por pessoa) a partir de todas as
     * listas do DTO em conjunto. eventId e null durante a criacao com escala, quando o evento ainda
     * nao foi persistido.
     */
    private Map<Long, EventAssignmentType> buildDesiredAssignments(Long eventId, CelebrationEventScaleRequestDTO dto) {
        Map<Long, EventAssignmentType> desiredAssignmentsByPersonId = new LinkedHashMap<>();
        addDesiredAssignment(eventId, desiredAssignmentsByPersonId, dto.getPriestId(), EventAssignmentType.PRIEST);
        addDesiredAssignments(eventId, desiredAssignmentsByPersonId, dto.getReaderIds(), EventAssignmentType.READER);
        addDesiredAssignments(eventId, desiredAssignmentsByPersonId, dto.getCommentatorIds(), EventAssignmentType.COMMENTATOR);
        addDesiredAssignments(eventId, desiredAssignmentsByPersonId, dto.getMinisterOfTheWordIds(), EventAssignmentType.MINISTER_OF_THE_WORD);
        addDesiredAssignments(eventId, desiredAssignmentsByPersonId, dto.getEucharisticMinisterIds(), EventAssignmentType.EUCHARISTIC_MINISTER);
        return desiredAssignmentsByPersonId;
    }

    private void addDesiredAssignments(
            Long eventId,
            Map<Long, EventAssignmentType> desiredAssignmentsByPersonId,
            List<Long> personIds,
            EventAssignmentType assignmentType
    ) {
        if (personIds == null) {
            return;
        }
        for (Long personId : personIds) {
            addDesiredAssignment(eventId, desiredAssignmentsByPersonId, personId, assignmentType);
        }
    }

    private void addDesiredAssignment(
            Long eventId,
            Map<Long, EventAssignmentType> desiredAssignmentsByPersonId,
            Long personId,
            EventAssignmentType assignmentType
    ) {
        if (personId == null) {
            return;
        }
        EventAssignmentType existingType = desiredAssignmentsByPersonId.putIfAbsent(personId, assignmentType);
        if (existingType != null) {
            throw new MultipleAssignmentsForPersonInEventException(eventId, personId, EnumSet.of(existingType, assignmentType));
        }
    }

    private void validateAvailabilityForTargets(Collection<EventAssignmentTarget> targets, LocalDateTime startAt, LocalDateTime endAt) {
        if (targets.isEmpty()) {
            return;
        }
        Map<Long, Set<EventAssignmentType>> typesByPerson = new TreeMap<>();
        for (EventAssignmentTarget target : targets) {
            typesByPerson
                    .computeIfAbsent(target.person().getId(), personId -> EnumSet.noneOf(EventAssignmentType.class))
                    .add(target.assignmentType());
        }
        personUnavailabilityConflictService.validateAvailabilityForEvent(typesByPerson, startAt, endAt);
    }

    private void validateRange(LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt == null || endAt == null) {
            throw new BusinessException("startAt e endAt são obrigatórios");
        }
        validateSecondPrecision(startAt, endAt);
        if (!startAt.isBefore(endAt)) {
            throw new BusinessException("startAt deve ser anterior a endAt");
        }
    }

    private void validateCreationRange(LocalDateTime startAt, LocalDateTime endAt) {
        validateRange(startAt, endAt);
        LocalDateTime currentSecond = LocalDateTime.now(clock).withNano(0);
        if (startAt.isBefore(currentSecond)) {
            throw new BusinessException("startAt só pode ser no presente ou futuro");
        }
    }

    private void validateSecondPrecision(LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt.getNano() != 0 || endAt.getNano() != 0) {
            throw new TemporalPrecisionNotSupportedException();
        }
    }

    private LocalDateTime rangeStart(LocalDate startDate) {
        return startDate.atStartOfDay();
    }

    private LocalDateTime rangeEnd(LocalDate endDate) {
        return endDate.plusDays(1).atStartOfDay();
    }

    @Override
    @Transactional
    public void deleteEventById(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        if(!celebrationEventRepository.existsById(id)){
            throw new ResourceNotFoundException("Evento celebrativo", id);
        }
        try{
            eventAssignmentCommandService.deleteAllForEvent(id);
            celebrationEventRepository.deleteById(id);
            celebrationEventRepository.flush();
        }
        catch (DataIntegrityViolationException e){
            throw new DatabaseException("Não é possível excluir este registro, pois ele possui vínculos com outros cadastros.");
        }
    }

    private record ScalePlanResult(EventScaleAssignmentPlan plan, Location location) {
    }

    private void applyLocation(CelebrationEvent celebrationEvent, Location location) {
        celebrationEvent.getLocations().clear();
        celebrationEvent.getLocations().add(location);
    }

    private ScalePlanResult buildScalePlanResult(CelebrationEvent celebrationEvent, CelebrationEventScaleRequestDTO dto) {
        validateId(dto.getLocationId());

        Location location = locationRepository.findById(dto.getLocationId())
                .orElseThrow(() -> new ResourceNotFoundException("Local", dto.getLocationId()));

        List<Long> readerIds = safeList(dto.getReaderIds());
        List<Long> commentatorIds = safeList(dto.getCommentatorIds());
        List<Long> ministerOfTheWordIds = safeList(dto.getMinisterOfTheWordIds());
        List<Long> eucharisticMinisterIds = safeList(dto.getEucharisticMinisterIds());

        Map<MinistryType, List<Long>> idsByMinistry = new EnumMap<>(MinistryType.class);
        idsByMinistry.put(MinistryType.PRIEST, optionalId(dto.getPriestId()));
        idsByMinistry.put(MinistryType.READER, readerIds);
        idsByMinistry.put(MinistryType.COMMENTATOR, commentatorIds);
        idsByMinistry.put(MinistryType.MINISTER_OF_THE_WORD, ministerOfTheWordIds);
        idsByMinistry.put(MinistryType.EUCHARISTIC_MINISTER, eucharisticMinisterIds);

        Map<MinistryType, Map<Long, ScaleParticipantEligibility>> eligibilityByMinistry =
                groupEligibilityByMinistry(personMinistryEligibilityResolver.resolve(idsByMinistry));

        EventScaleAssignmentPlan.Builder planBuilder = EventScaleAssignmentPlan.builder();

        addOptionalPerson(planBuilder, dto.getPriestId(), MinistryType.PRIEST, "padre", eligibilityByMinistry);
        addPeople(planBuilder, readerIds, MinistryType.READER, "leitor", eligibilityByMinistry);
        addPeople(planBuilder, commentatorIds, MinistryType.COMMENTATOR, "comentarista", eligibilityByMinistry);
        addPeople(planBuilder, ministerOfTheWordIds, MinistryType.MINISTER_OF_THE_WORD, "ministro da Palavra", eligibilityByMinistry);
        addPeople(planBuilder, eucharisticMinisterIds, MinistryType.EUCHARISTIC_MINISTER, "ministro da Eucaristia", eligibilityByMinistry);

        return new ScalePlanResult(planBuilder.build(), location);
    }

    private List<Long> optionalId(Long id) {
        return id == null ? List.of() : List.of(id);
    }

    private Map<MinistryType, Map<Long, ScaleParticipantEligibility>> groupEligibilityByMinistry(
            List<ScaleParticipantEligibility> eligibilities
    ) {
        return eligibilities.stream()
                .collect(Collectors.groupingBy(
                        ScaleParticipantEligibility::ministryType,
                        Collectors.toMap(ScaleParticipantEligibility::personId, Function.identity())
                ));
    }

    private CelebrationEventScaleRequestDTO toScaleRequest(CelebrationEventWithScaleRequestDTO dto) {
        return new CelebrationEventScaleRequestDTO(
                dto.getLocationId(),
                dto.getPriestId(),
                dto.getReaderIds(),
                dto.getCommentatorIds(),
                dto.getMinisterOfTheWordIds(),
                dto.getEucharisticMinisterIds()
        );
    }

    private void addPeople(
            EventScaleAssignmentPlan.Builder planBuilder,
            List<Long> ids,
            MinistryType ministryType,
            String roleName,
            Map<MinistryType, Map<Long, ScaleParticipantEligibility>> eligibilityByMinistry
    ) {
        for (Long id : ids) {
            addOptionalPerson(planBuilder, id, ministryType, roleName, eligibilityByMinistry);
        }
    }

    private void addOptionalPerson(
            EventScaleAssignmentPlan.Builder planBuilder,
            Long id,
            MinistryType ministryType,
            String roleName,
            Map<MinistryType, Map<Long, ScaleParticipantEligibility>> eligibilityByMinistry
    ) {
        if (id == null) {
            return;
        }
        validateId(id);

        ScaleParticipantEligibility eligibility = eligibilityByMinistry
                .getOrDefault(ministryType, Map.of())
                .get(id);

        if (eligibility == null || !eligibility.personFound()) {
            throw new ResourceNotFoundException("Pessoa", id);
        }
        if (!eligibility.ministryAssigned()) {
            throw new BusinessException(
                    "A pessoa informada para " + roleName + " não possui a função ministerial ativa correspondente"
            );
        }

        Person person = eligibility.person();

        planBuilder.add(person, toAssignmentType(ministryType));
    }

    private List<Long> safeList(List<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids;
    }

    private void validateId(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
    }

    private Location firstLocation(CelebrationEvent celebrationEvent) {
        if (celebrationEvent.getLocations().isEmpty()) {
            return null;
        }
        return celebrationEvent.getLocations().stream()
                .min(Comparator.comparing(Location::getId, Comparator.nullsLast(Long::compareTo)))
                .orElse(null);
    }

    private void validateEventScheduleQuery(
            LocalDate startDate,
            LocalDate endDate,
            EventScheduleType type,
            int page,
            int size
    ) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new BusinessException("As datas estão inválidas");
        }
        if (type == null) {
            throw new BusinessException("O tipo da escala deve ser informado");
        }
        if (page < 0) {
            throw new BusinessException("A página deve ser maior ou igual a zero");
        }
        if (size <= 0 || size > MAX_PAGE_SIZE) {
            throw new BusinessException("O tamanho da página deve ser maior que zero e menor ou igual a 100");
        }
    }

    private Map<Long, List<EventScheduleAssignmentResponseDTO>> findAssignmentsByEventAssignmentType(
            List<Long> eventIds,
            EventAssignmentType assignmentType
    ) {
        if (eventIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return celebrationEventRepository.findEventScheduleAssignmentsByAssignmentType(eventIds, assignmentType.name()).stream()
                .collect(Collectors.groupingBy(
                        EventScheduleAssignmentProjection::getEventId,
                        Collectors.mapping(
                                assignment -> new EventScheduleAssignmentResponseDTO(
                                        assignment.getPersonId(),
                                        assignment.getPersonName()
                                ),
                                Collectors.toList()
                        )
                ));
    }


    private Map<Long, List<String>> findEucharistMinistersByEvent(List<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return celebrationEventRepository.findEucharistScaleAssignmentsByEventIds(eventIds).stream()
                .collect(Collectors.groupingBy(
                        EventScheduleAssignmentProjection::getEventId,
                        Collectors.mapping(
                                EventScheduleAssignmentProjection::getPersonName,
                                Collectors.toList()
                        )
                ));
    }

    private EucharistScaleEventResponseDTO toEucharistScaleResponse(
            EucharistScaleEventProjection projection,
            List<String> ministerNames
    ) {
        EucharistScaleEventResponseDTO dto = new EucharistScaleEventResponseDTO(
                projection.getNameMassOrEvent(),
                projection.getStartAt(),
                projection.getEndAt(),
                projection.getChurchName()
        );
        dto.getNameMinisters().addAll(ministerNames);
        return dto;
    }

    private EventAssignmentType toAssignmentType(EventScheduleType type) {
        return switch (type) {
            case PRIEST -> EventAssignmentType.PRIEST;
            case READER -> EventAssignmentType.READER;
            case COMMENTATOR -> EventAssignmentType.COMMENTATOR;
            case MINISTER_OF_THE_WORD -> EventAssignmentType.MINISTER_OF_THE_WORD;
            case EUCHARISTIC_MINISTER -> EventAssignmentType.EUCHARISTIC_MINISTER;
        };
    }

    private EventAssignmentType toAssignmentType(MinistryType ministryType) {
        return switch (ministryType) {
            case PRIEST -> EventAssignmentType.PRIEST;
            case READER -> EventAssignmentType.READER;
            case COMMENTATOR -> EventAssignmentType.COMMENTATOR;
            case MINISTER_OF_THE_WORD -> EventAssignmentType.MINISTER_OF_THE_WORD;
            case EUCHARISTIC_MINISTER -> EventAssignmentType.EUCHARISTIC_MINISTER;
        };
    }

    private EventScheduleQueryResponseDTO toEventScheduleQueryResponse(
            EventScheduleEventProjection event,
            EventScheduleType type,
            Map<Long, List<EventScheduleAssignmentResponseDTO>> assignmentsByEvent
    ) {
        EventScheduleQueryResponseDTO dto = new EventScheduleQueryResponseDTO();
        dto.setEventId(event.getEventId());
        dto.setEventName(event.getEventName());
        dto.setStartAt(event.getStartAt());
        dto.setEndAt(event.getEndAt());
        dto.setMassOrCelebration(event.getMassOrCelebration());
        dto.setLocationId(event.getLocationId());
        dto.setChurchName(event.getChurchName());
        dto.setAssignmentType(type);
        dto.setAssignments(assignmentsByEvent.getOrDefault(event.getEventId(), List.of()));
        return dto;
    }
}

