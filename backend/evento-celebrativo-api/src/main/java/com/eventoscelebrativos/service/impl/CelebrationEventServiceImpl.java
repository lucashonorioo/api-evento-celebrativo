package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.request.CelebrationEventRequestDTO;
import com.eventoscelebrativos.dto.request.CelebrationEventScaleRequestDTO;
import com.eventoscelebrativos.dto.request.CelebrationEventWithScaleRequestDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventResponseDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventScaleDetailResponseDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventScaleResponseDTO;
import com.eventoscelebrativos.dto.response.EventScheduleAssignmentResponseDTO;
import com.eventoscelebrativos.dto.response.EventScheduleQueryResponseDTO;
import com.eventoscelebrativos.dto.response.EucharistScaleEventResponseDTO;
import com.eventoscelebrativos.config.EventAssignmentReadSource;
import com.eventoscelebrativos.config.EventAssignmentReadSourceProperties;
import com.eventoscelebrativos.config.EventAssignmentShadowReadProperties;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.mapper.CelebrationEventMapper;
import com.eventoscelebrativos.mapper.CelebrationEventScaleDetailMapper;
import com.eventoscelebrativos.mapper.CelebrationEventScaleMapper;
import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.Commentator;
import com.eventoscelebrativos.model.EucharisticMinister;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.EventScheduleType;
import com.eventoscelebrativos.model.Location;
import com.eventoscelebrativos.model.MinisterOfTheWord;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Priest;
import com.eventoscelebrativos.model.Reader;
import com.eventoscelebrativos.projection.EventScheduleAssignmentProjection;
import com.eventoscelebrativos.projection.EventScheduleEventProjection;
import com.eventoscelebrativos.projection.EucharistScaleEventProjection;
import com.eventoscelebrativos.repository.CelebrationEventRepository;
import com.eventoscelebrativos.repository.LocationRepository;
import com.eventoscelebrativos.service.CelebrationEventService;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.service.EventAssignmentCompatibilityService;
import com.eventoscelebrativos.service.EventAssignmentGroup;
import com.eventoscelebrativos.service.EventAssignmentReadService;
import com.eventoscelebrativos.service.EventAssignmentShadowReadExecutor;
import com.eventoscelebrativos.service.EventAssignmentSnapshot;
import com.eventoscelebrativos.service.EventScaleAssignmentPlan;
import com.eventoscelebrativos.service.LegacyScaleMirrorService;
import com.eventoscelebrativos.service.PersonMinistryEligibilityResolver;
import com.eventoscelebrativos.service.ScaleLegacyCompatibilityValidator;
import com.eventoscelebrativos.service.ScaleParticipantEligibility;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;


@Service
public class CelebrationEventServiceImpl implements CelebrationEventService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CelebrationEventServiceImpl.class);
    private static final int MAX_PAGE_SIZE = 100;

    private final CelebrationEventRepository celebrationEventRepository;
    private final LocationRepository locationRepository;
    private final CelebrationEventMapper celebrationEventMapper;
    private final CelebrationEventScaleMapper celebrationEventScaleMapper;
    private final CelebrationEventScaleDetailMapper celebrationEventScaleDetailMapper;
    private final EventAssignmentCompatibilityService eventAssignmentCompatibilityService;
    private final EventAssignmentReadSourceProperties eventAssignmentReadSourceProperties;
    private final EventAssignmentReadService eventAssignmentReadService;
    private final EventAssignmentShadowReadProperties eventAssignmentShadowReadProperties;
    private final EventAssignmentShadowReadExecutor eventAssignmentShadowReadExecutor;
    private final PersonMinistryEligibilityResolver personMinistryEligibilityResolver;
    private final ScaleLegacyCompatibilityValidator scaleLegacyCompatibilityValidator;
    private final LegacyScaleMirrorService legacyScaleMirrorService;

    public CelebrationEventServiceImpl(
            CelebrationEventRepository celebrationEventRepository,
            LocationRepository locationRepository,
            CelebrationEventMapper celebrationEventMapper,
            CelebrationEventScaleMapper celebrationEventScaleMapper,
            CelebrationEventScaleDetailMapper celebrationEventScaleDetailMapper,
            EventAssignmentCompatibilityService eventAssignmentCompatibilityService,
            EventAssignmentReadSourceProperties eventAssignmentReadSourceProperties,
            EventAssignmentReadService eventAssignmentReadService,
            EventAssignmentShadowReadProperties eventAssignmentShadowReadProperties,
            EventAssignmentShadowReadExecutor eventAssignmentShadowReadExecutor,
            PersonMinistryEligibilityResolver personMinistryEligibilityResolver,
            ScaleLegacyCompatibilityValidator scaleLegacyCompatibilityValidator,
            LegacyScaleMirrorService legacyScaleMirrorService
    ) {
        this.celebrationEventRepository = celebrationEventRepository;
        this.locationRepository = locationRepository;
        this.celebrationEventMapper = celebrationEventMapper;
        this.celebrationEventScaleMapper = celebrationEventScaleMapper;
        this.celebrationEventScaleDetailMapper = celebrationEventScaleDetailMapper;
        this.eventAssignmentCompatibilityService = eventAssignmentCompatibilityService;
        this.eventAssignmentReadSourceProperties = eventAssignmentReadSourceProperties;
        this.eventAssignmentReadService = eventAssignmentReadService;
        this.eventAssignmentShadowReadProperties = eventAssignmentShadowReadProperties;
        this.eventAssignmentShadowReadExecutor = eventAssignmentShadowReadExecutor;
        this.personMinistryEligibilityResolver = personMinistryEligibilityResolver;
        this.scaleLegacyCompatibilityValidator = scaleLegacyCompatibilityValidator;
        this.legacyScaleMirrorService = legacyScaleMirrorService;
    }

    @Override
    @Transactional
    public CelebrationEventResponseDTO createEvent(CelebrationEventRequestDTO celebrationEventRequestDTO) {
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

        return switch (eventAssignmentReadSourceProperties.getEucharistScale()) {
            case LEGACY -> findEucharistScaleLegacy(pageable, startDate, endDate);
            case PARALLEL -> findEucharistScaleParallel(pageable, startDate, endDate);
        };
    }

    private Page<EucharistScaleEventResponseDTO> findEucharistScaleLegacy(
            Pageable pageable,
            LocalDate startDate,
            LocalDate endDate
    ) {
        Page<EucharistScaleEventProjection> projections =
                celebrationEventRepository.findEucharistScale(pageable, startDate, endDate);
        runEucharistScaleShadowRead(projections.getContent());

        return projections.map(this::toLegacyEucharistScaleResponse);
    }

    private Page<EucharistScaleEventResponseDTO> findEucharistScaleParallel(
            Pageable pageable,
            LocalDate startDate,
            LocalDate endDate
    ) {
        LOGGER.debug("eucharist-scale source = {}", EventAssignmentReadSource.PARALLEL);
        Page<EucharistScaleEventProjection> eventPage =
                celebrationEventRepository.findEucharistScaleByAssignments(pageable, startDate, endDate);
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
        return switch (eventAssignmentReadSourceProperties.getMonthlySchedule()) {
            case LEGACY -> findEventSchedulesLegacy(startDate, endDate, type, includeUnassigned, pageable);
            case PARALLEL -> findEventSchedulesParallel(startDate, endDate, type, includeUnassigned, pageable);
        };
    }

    private Page<EventScheduleQueryResponseDTO> findEventSchedulesLegacy(
            LocalDate startDate,
            LocalDate endDate,
            EventScheduleType type,
            boolean includeUnassigned,
            PageRequest pageable
    ) {
        Page<EventScheduleEventProjection> eventPage = celebrationEventRepository.findEventScheduleEvents(
                pageable,
                startDate,
                endDate,
                type.getPersonType(),
                includeUnassigned
        );

        List<Long> eventIds = eventPage.getContent().stream()
                .map(EventScheduleEventProjection::getEventId)
                .toList();

        Map<Long, List<EventScheduleAssignmentResponseDTO>> assignmentsByEvent = findAssignmentsByEvent(
                eventIds,
                type
        );

        List<EventScheduleQueryResponseDTO> content = eventPage.getContent().stream()
                .map(event -> toEventScheduleQueryResponse(event, type, assignmentsByEvent))
                .toList();
        runMonthlyScheduleShadowRead(eventIds, type, assignmentsByEvent);

        return new PageImpl<>(content, pageable, eventPage.getTotalElements());
    }

    private Page<EventScheduleQueryResponseDTO> findEventSchedulesParallel(
            LocalDate startDate,
            LocalDate endDate,
            EventScheduleType type,
            boolean includeUnassigned,
            PageRequest pageable
    ) {
        LOGGER.debug("monthly-schedule source = {}", EventAssignmentReadSource.PARALLEL);
        EventAssignmentType assignmentType = toAssignmentType(type);
        Page<EventScheduleEventProjection> eventPage = celebrationEventRepository.findEventScheduleEventsByAssignments(
                pageable,
                startDate,
                endDate,
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
        CelebrationEventResponseDTO response = celebrationEventMapper.toDto(celebrationEvent);
        eventAssignmentShadowReadExecutor.compareEventIfEnabled(
                eventAssignmentShadowReadProperties.isEventDetailEnabled(),
                "event-detail",
                () -> celebrationEventRepository.findByIdWithPeople(id)
        );
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public CelebrationEventScaleDetailResponseDTO findScaleByEventId(Long id) {
        validateId(id);
        return switch (eventAssignmentReadSourceProperties.getEventScaleDetail()) {
            case LEGACY -> findScaleByEventIdLegacy(id);
            case PARALLEL -> findScaleByEventIdParallel(id);
        };
    }

    private CelebrationEventScaleDetailResponseDTO findScaleByEventIdLegacy(Long id) {
        CelebrationEvent celebrationEvent = celebrationEventRepository.findByIdWithLocations(id)
                .orElseThrow(() -> new ResourceNotFoundException("Evento celebrativo", id));
        celebrationEventRepository.findByIdWithPeople(id)
                .orElseThrow(() -> new ResourceNotFoundException("Evento celebrativo", id));

        Location location = firstLocation(celebrationEvent);
        List<Priest> priests = peopleByType(celebrationEvent, Priest.class);
        if (priests.size() > 1) {
            throw new BusinessException("Evento possui mais de um padre vinculado à escala");
        }

        eventAssignmentShadowReadExecutor.compareEventIfEnabled(
                eventAssignmentShadowReadProperties.isEventScaleDetailEnabled(),
                "event-scale-detail",
                celebrationEvent
        );

        return celebrationEventScaleDetailMapper.toDto(
                celebrationEvent,
                location,
                priests.isEmpty() ? null : priests.get(0),
                peopleByType(celebrationEvent, Reader.class),
                peopleByType(celebrationEvent, Commentator.class),
                peopleByType(celebrationEvent, MinisterOfTheWord.class),
                peopleByType(celebrationEvent, EucharisticMinister.class)
        );
    }

    private CelebrationEventScaleDetailResponseDTO findScaleByEventIdParallel(Long id) {
        LOGGER.debug("event-scale-detail source = {}", EventAssignmentReadSource.PARALLEL);
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
    @Transactional
    public CelebrationEventResponseDTO updateEvent(Long id, CelebrationEventRequestDTO celebrationEventRequestDTO) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        try{
            CelebrationEvent celebrationEvent = celebrationEventRepository.getReferenceById(id);

            celebrationEventMapper.updateCelebrationEventMapperFromDto(celebrationEventRequestDTO, celebrationEvent);
            celebrationEvent = celebrationEventRepository.save(celebrationEvent);

            return celebrationEventMapper.toDto(celebrationEvent);
        }catch (EntityNotFoundException e){
            throw new ResourceNotFoundException("Evento celebrativo", id);
        }
    }

    @Override
    @Transactional
    public CelebrationEventScaleResponseDTO updateEventScale(Long id, CelebrationEventScaleRequestDTO celebrationEventScaleRequestDTO) {
        validateId(id);
        CelebrationEvent celebrationEvent = celebrationEventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Evento celebrativo", id));

        EventScaleAssignmentPlan plan = buildScalePlan(celebrationEvent, celebrationEventScaleRequestDTO);

        eventAssignmentCompatibilityService.synchronizeAssignments(celebrationEvent, plan.toTargets());
        legacyScaleMirrorService.synchronizeMirror(celebrationEvent, plan.people());

        return celebrationEventScaleMapper.toDto(celebrationEvent);
    }

    @Override
    @Transactional
    public CelebrationEventScaleResponseDTO createEventWithScale(CelebrationEventWithScaleRequestDTO celebrationEventWithScaleRequestDTO) {
        CelebrationEvent celebrationEvent = new CelebrationEvent();
        celebrationEvent.setNameMassOrEvent(celebrationEventWithScaleRequestDTO.getNameMassOrEvent());
        celebrationEvent.setEventDate(celebrationEventWithScaleRequestDTO.getEventDate());
        celebrationEvent.setEventTime(celebrationEventWithScaleRequestDTO.getEventTime());
        celebrationEvent.setMassOrCelebration(celebrationEventWithScaleRequestDTO.getMassOrCelebration());

        EventScaleAssignmentPlan plan = buildScalePlan(celebrationEvent, toScaleRequest(celebrationEventWithScaleRequestDTO));

        CelebrationEvent savedEvent = celebrationEventRepository.save(celebrationEvent);

        eventAssignmentCompatibilityService.synchronizeAssignments(savedEvent, plan.toTargets());
        legacyScaleMirrorService.synchronizeMirror(savedEvent, plan.people());

        return celebrationEventScaleMapper.toDto(savedEvent);
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
            eventAssignmentCompatibilityService.deleteAllForEvent(id);
            celebrationEventRepository.deleteById(id);
            celebrationEventRepository.flush();
        }
        catch (DataIntegrityViolationException e){
            throw new DatabaseException("Não é possível excluir este registro, pois ele possui vínculos com outros cadastros.");
        }
    }

    private EventScaleAssignmentPlan buildScalePlan(CelebrationEvent celebrationEvent, CelebrationEventScaleRequestDTO dto) {
        validateId(dto.getLocationId());

        Location location = locationRepository.findById(dto.getLocationId())
                .orElseThrow(() -> new ResourceNotFoundException("Local", dto.getLocationId()));

        List<Long> readerIds = safeList(dto.getReaderIds());
        List<Long> commentatorIds = safeList(dto.getCommentatorIds());
        List<Long> ministerOfTheWordIds = safeList(dto.getMinisterOfTheWordIds());
        List<Long> eucharisticMinisterIds = safeList(dto.getEucharisticMinisterIds());

        validateNoDuplicatedIds(readerIds, "leitor");
        validateNoDuplicatedIds(commentatorIds, "comentarista");
        validateNoDuplicatedIds(ministerOfTheWordIds, "ministro da Palavra");
        validateNoDuplicatedIds(eucharisticMinisterIds, "ministro da Eucaristia");

        Map<MinistryType, List<Long>> idsByMinistry = new EnumMap<>(MinistryType.class);
        idsByMinistry.put(MinistryType.PRIEST, optionalId(dto.getPriestId()));
        idsByMinistry.put(MinistryType.READER, readerIds);
        idsByMinistry.put(MinistryType.COMMENTATOR, commentatorIds);
        idsByMinistry.put(MinistryType.MINISTER_OF_THE_WORD, ministerOfTheWordIds);
        idsByMinistry.put(MinistryType.EUCHARISTIC_MINISTER, eucharisticMinisterIds);

        Map<MinistryType, Map<Long, ScaleParticipantEligibility>> eligibilityByMinistry =
                groupEligibilityByMinistry(personMinistryEligibilityResolver.resolve(idsByMinistry));

        EventScaleAssignmentPlan.Builder planBuilder = EventScaleAssignmentPlan.builder();

        addOptionalPerson(planBuilder, dto.getPriestId(), MinistryType.PRIEST, Priest.class, "padre", eligibilityByMinistry);
        addPeople(planBuilder, readerIds, MinistryType.READER, Reader.class, "leitor", eligibilityByMinistry);
        addPeople(planBuilder, commentatorIds, MinistryType.COMMENTATOR, Commentator.class, "comentarista", eligibilityByMinistry);
        addPeople(planBuilder, ministerOfTheWordIds, MinistryType.MINISTER_OF_THE_WORD, MinisterOfTheWord.class, "ministro da Palavra", eligibilityByMinistry);
        addPeople(planBuilder, eucharisticMinisterIds, MinistryType.EUCHARISTIC_MINISTER, EucharisticMinister.class, "ministro da Eucaristia", eligibilityByMinistry);

        celebrationEvent.getLocations().clear();
        celebrationEvent.getLocations().add(location);

        return planBuilder.build();
    }

    private List<Long> optionalId(Long id) {
        return id == null ? List.of() : List.of(id);
    }

    private void validateNoDuplicatedIds(List<Long> ids, String roleName) {
        Set<Long> seen = new HashSet<>();
        for (Long id : ids) {
            if (!seen.add(id)) {
                throw new BusinessException("Não é permitido informar IDs duplicados para " + roleName);
            }
        }
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
            Class<? extends Person> expectedLegacyType,
            String roleName,
            Map<MinistryType, Map<Long, ScaleParticipantEligibility>> eligibilityByMinistry
    ) {
        for (Long id : ids) {
            addOptionalPerson(planBuilder, id, ministryType, expectedLegacyType, roleName, eligibilityByMinistry);
        }
    }

    private void addOptionalPerson(
            EventScaleAssignmentPlan.Builder planBuilder,
            Long id,
            MinistryType ministryType,
            Class<? extends Person> expectedLegacyType,
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
        scaleLegacyCompatibilityValidator.validate(person, expectedLegacyType, roleName);

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

    private <T extends Person> List<T> peopleByType(CelebrationEvent celebrationEvent, Class<T> type) {
        return celebrationEvent.getPeople().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .sorted(Comparator
                        .comparing(Person::getName, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(Person::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();
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

    private Map<Long, List<EventScheduleAssignmentResponseDTO>> findAssignmentsByEvent(
            List<Long> eventIds,
            EventScheduleType type
    ) {
        if (eventIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return celebrationEventRepository.findEventScheduleAssignments(eventIds, type.getPersonType()).stream()
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

    private EucharistScaleEventResponseDTO toLegacyEucharistScaleResponse(
            EucharistScaleEventProjection projection
    ) {
        EucharistScaleEventResponseDTO dto = toEucharistScaleResponse(projection, List.of());

        if (projection.getMinisterNames() != null && !projection.getMinisterNames().isBlank()) {
            Arrays.stream(projection.getMinisterNames().split(","))
                    .map(String::trim)
                    .filter(name -> !name.isBlank())
                    .forEach(dto.getNameMinisters()::add);
        }

        return dto;
    }

    private EucharistScaleEventResponseDTO toEucharistScaleResponse(
            EucharistScaleEventProjection projection,
            List<String> ministerNames
    ) {
        EucharistScaleEventResponseDTO dto = new EucharistScaleEventResponseDTO(
                projection.getNameMassOrEvent(),
                projection.getEventDate(),
                projection.getEventTime(),
                projection.getChurchName()
        );
        dto.getNameMinisters().addAll(ministerNames);
        return dto;
    }

    private void runMonthlyScheduleShadowRead(
            List<Long> eventIds,
            EventScheduleType type,
            Map<Long, List<EventScheduleAssignmentResponseDTO>> assignmentsByEvent
    ) {
        eventAssignmentShadowReadExecutor.comparePartialAssignmentsIfEnabled(
                eventAssignmentShadowReadProperties.isMonthlyScheduleEnabled(),
                "monthly-schedule",
                eventIds,
                toAssignmentType(type),
                () -> toPartialSnapshots(eventIds, type, assignmentsByEvent)
        );
    }

    private void runEucharistScaleShadowRead(List<EucharistScaleEventProjection> projections) {
        List<Long> eventIds = projections.stream()
                .map(EucharistScaleEventProjection::getEventId)
                .toList();
        eventAssignmentShadowReadExecutor.comparePartialAssignmentsIfEnabled(
                eventAssignmentShadowReadProperties.isEucharistScaleEnabled(),
                "eucharist-scale",
                eventIds,
                EventAssignmentType.EUCHARISTIC_MINISTER,
                () -> findScheduleAssignmentSnapshots(eventIds, EventScheduleType.EUCHARISTIC_MINISTER)
        );
    }

    private List<EventAssignmentSnapshot> findScheduleAssignmentSnapshots(
            List<Long> eventIds,
            EventScheduleType type
    ) {
        if (eventIds.isEmpty()) {
            return List.of();
        }
        return celebrationEventRepository.findEventScheduleAssignments(eventIds, type.getPersonType()).stream()
                .map(assignment -> toPartialSnapshot(assignment, type))
                .toList();
    }

    private List<EventAssignmentSnapshot> toPartialSnapshots(
            List<Long> eventIds,
            EventScheduleType type,
            Map<Long, List<EventScheduleAssignmentResponseDTO>> assignmentsByEvent
    ) {
        return eventIds.stream()
                .flatMap(eventId -> assignmentsByEvent.getOrDefault(eventId, List.of()).stream()
                        .map(assignment -> toPartialSnapshot(eventId, assignment, type)))
                .toList();
    }

    private EventAssignmentSnapshot toPartialSnapshot(
            EventScheduleAssignmentProjection assignment,
            EventScheduleType type
    ) {
        return new EventAssignmentSnapshot(
                null,
                assignment.getEventId(),
                assignment.getPersonId(),
                toAssignmentType(type),
                assignment.getPersonName(),
                type.getPersonType()
        );
    }

    private EventAssignmentSnapshot toPartialSnapshot(
            Long eventId,
            EventScheduleAssignmentResponseDTO assignment,
            EventScheduleType type
    ) {
        return new EventAssignmentSnapshot(
                null,
                eventId,
                assignment.getPersonId(),
                toAssignmentType(type),
                assignment.getPersonName(),
                type.getPersonType()
        );
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
        dto.setEventDate(event.getEventDate());
        dto.setEventTime(event.getEventTime());
        dto.setMassOrCelebration(event.getMassOrCelebration());
        dto.setLocationId(event.getLocationId());
        dto.setChurchName(event.getChurchName());
        dto.setAssignmentType(type);
        dto.setAssignments(assignmentsByEvent.getOrDefault(event.getEventId(), List.of()));
        return dto;
    }
}

