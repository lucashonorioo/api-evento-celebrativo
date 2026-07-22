package com.eventoscelebrativos.service;

import com.eventoscelebrativos.config.EventAssignmentShadowReadProperties;
import com.eventoscelebrativos.config.EventAssignmentReadSource;
import com.eventoscelebrativos.config.EventAssignmentReadSourceProperties;
import com.eventoscelebrativos.dto.request.CelebrationEventRequestDTO;
import com.eventoscelebrativos.dto.request.CelebrationEventScaleRequestDTO;
import com.eventoscelebrativos.dto.request.CelebrationEventWithScaleRequestDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventResponseDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventScaleDetailResponseDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventScaleResponseDTO;
import com.eventoscelebrativos.dto.response.EventScheduleQueryResponseDTO;
import com.eventoscelebrativos.dto.response.EucharistScaleEventResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
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
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Priest;
import com.eventoscelebrativos.model.Reader;
import com.eventoscelebrativos.projection.EventScheduleAssignmentProjection;
import com.eventoscelebrativos.projection.EventScheduleEventProjection;
import com.eventoscelebrativos.projection.EucharistScaleEventProjection;
import com.eventoscelebrativos.repository.CelebrationEventRepository;
import com.eventoscelebrativos.repository.LocationRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.service.impl.CelebrationEventServiceImpl;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CelebrationEventServiceImplTest {

    private static final LocalDate EVENT_DATE = LocalDate.of(2026, 8, 15);
    private static final LocalTime EVENT_TIME = LocalTime.of(19, 30);

    @Mock
    private CelebrationEventRepository repository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private PersonRepository personRepository;

    @Mock
    private CelebrationEventMapper mapper;

    @Mock
    private CelebrationEventScaleMapper scaleMapper;

    @Mock
    private CelebrationEventScaleDetailMapper scaleDetailMapper;

    @Mock
    private EventAssignmentTargetResolver eventAssignmentTargetResolver;

    @Mock
    private EventAssignmentCompatibilityService eventAssignmentCompatibilityService;

    @Mock
    private EventAssignmentReadSourceProperties eventAssignmentReadSourceProperties;

    @Mock
    private EventAssignmentReadService eventAssignmentReadService;

    @Mock
    private EventAssignmentShadowReadProperties eventAssignmentShadowReadProperties;

    @Mock
    private EventAssignmentShadowReadExecutor eventAssignmentShadowReadExecutor;

    @InjectMocks
    private CelebrationEventServiceImpl service;

    @BeforeEach
    void configureDefaultReadSource() {
        lenient().when(eventAssignmentReadSourceProperties.getEventScaleDetail())
                .thenReturn(EventAssignmentReadSource.LEGACY);
    }

    @Test
    void shouldCreateEvent() {
        CelebrationEventRequestDTO request = request();
        CelebrationEvent entity = event(null);
        CelebrationEvent saved = event(1L);
        CelebrationEventResponseDTO response = response(1L);

        when(mapper.toEntity(request)).thenReturn(entity);
        when(repository.save(entity)).thenReturn(saved);
        when(mapper.toDto(saved)).thenReturn(response);

        assertSame(response, service.createEvent(request));
        verifyNoInteractions(eventAssignmentTargetResolver, eventAssignmentCompatibilityService);
    }

    @Test
    void shouldFindEventByIdWhenExists() {
        CelebrationEvent entity = event(1L);
        CelebrationEventResponseDTO response = response(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(mapper.toDto(entity)).thenReturn(response);

        assertSame(response, service.findEventById(1L));
    }

    @Test
    void shouldInvokeEventDetailShadowReadAfterMappingWhenEnabled() {
        CelebrationEvent entity = event(1L);
        CelebrationEventResponseDTO response = response(1L);
        when(eventAssignmentShadowReadProperties.isEventDetailEnabled()).thenReturn(true);
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(mapper.toDto(entity)).thenReturn(response);

        assertSame(response, service.findEventById(1L));

        verify(eventAssignmentShadowReadExecutor).compareEventIfEnabled(
                eq(true),
                eq("event-detail"),
                org.mockito.ArgumentMatchers.<Supplier<Optional<CelebrationEvent>>>any()
        );
    }

    @Test
    void shouldFindEventByIdWithoutMandatoryAssignmentRead() {
        CelebrationEvent entity = event(1L);
        CelebrationEventResponseDTO response = response(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(mapper.toDto(entity)).thenReturn(response);

        assertSame(response, service.findEventById(1L));

        verify(repository).findById(1L);
        verify(mapper).toDto(entity);
        verify(eventAssignmentShadowReadExecutor).compareEventIfEnabled(
                eq(false),
                eq("event-detail"),
                org.mockito.ArgumentMatchers.<Supplier<Optional<CelebrationEvent>>>any()
        );
        verifyNoInteractions(eventAssignmentReadService);
        verify(repository, never()).findByIdWithLocations(anyLong());
    }

    @Test
    void shouldPropagateLegacyFailureWithoutParallelFallbackWhenFindingEventById() {
        when(repository.findById(1L)).thenThrow(new IllegalStateException("controlled legacy failure"));

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> service.findEventById(1L));

        assertEquals("controlled legacy failure", exception.getMessage());
        verifyNoInteractions(eventAssignmentReadService, eventAssignmentShadowReadExecutor);
        verify(repository, never()).findByIdWithLocations(anyLong());
    }

    @Test
    void shouldThrowBusinessExceptionWhenEventIdIsInvalid() {
        assertAll(
                () -> assertThrows(BusinessException.class, () -> service.findEventById(null)),
                () -> assertThrows(BusinessException.class, () -> service.findEventById(0L)),
                () -> assertThrows(BusinessException.class, () -> service.findEventById(-1L))
        );
    }

    @Test
    void shouldThrowResourceNotFoundWhenEventDoesNotExist() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.findEventById(99L));
        verifyNoInteractions(eventAssignmentShadowReadExecutor);
    }

    @Test
    void shouldFindCompleteEventScale() {
        CelebrationEvent event = eventWithCompleteScale();
        CelebrationEventScaleDetailResponseDTO response = detailResponse();
        when(repository.findByIdWithLocations(1L)).thenReturn(Optional.of(event));
        when(repository.findByIdWithPeople(1L)).thenReturn(Optional.of(event));
        when(scaleDetailMapper.toDto(
                eq(event),
                any(Location.class),
                any(Priest.class),
                anyList(),
                anyList(),
                anyList(),
                anyList()
        )).thenReturn(response);

        assertSame(response, service.findScaleByEventId(1L));
    }

    @Test
    void shouldInvokeEventScaleDetailShadowReadAfterLegacyScaleIsLoadedWhenEnabled() {
        CelebrationEvent event = eventWithCompleteScale();
        CelebrationEventScaleDetailResponseDTO response = detailResponse();
        when(eventAssignmentShadowReadProperties.isEventScaleDetailEnabled()).thenReturn(true);
        when(repository.findByIdWithLocations(1L)).thenReturn(Optional.of(event));
        when(repository.findByIdWithPeople(1L)).thenReturn(Optional.of(event));
        when(scaleDetailMapper.toDto(
                eq(event),
                any(Location.class),
                any(Priest.class),
                anyList(),
                anyList(),
                anyList(),
                anyList()
        )).thenReturn(response);

        assertSame(response, service.findScaleByEventId(1L));

        verify(eventAssignmentShadowReadExecutor).compareEventIfEnabled(
                true,
                "event-scale-detail",
                event
        );
    }

    @Test
    void shouldFindEventScaleWithoutPriest() {
        CelebrationEvent event = event(1L);
        event.getLocations().add(location(1L));
        event.getPeople().add(person(new Reader(), 4L, "Alice"));
        when(repository.findByIdWithLocations(1L)).thenReturn(Optional.of(event));
        when(repository.findByIdWithPeople(1L)).thenReturn(Optional.of(event));
        when(scaleDetailMapper.toDto(eq(event), any(Location.class), isNull(), anyList(), anyList(), anyList(), anyList()))
                .thenReturn(detailResponse());

        service.findScaleByEventId(1L);

        verify(scaleDetailMapper).toDto(eq(event), any(Location.class), isNull(), anyList(), anyList(), anyList(), anyList());
    }

    @Test
    void shouldFindEventScaleWithEmptyRoleLists() {
        CelebrationEvent event = event(1L);
        event.getLocations().add(location(1L));
        event.getPeople().add(person(new Priest(), 13L, "Padre"));
        when(repository.findByIdWithLocations(1L)).thenReturn(Optional.of(event));
        when(repository.findByIdWithPeople(1L)).thenReturn(Optional.of(event));
        when(scaleDetailMapper.toDto(eq(event), any(Location.class), any(Priest.class), eq(List.of()), eq(List.of()), eq(List.of()), eq(List.of())))
                .thenReturn(detailResponse());

        service.findScaleByEventId(1L);

        verify(scaleDetailMapper).toDto(eq(event), any(Location.class), any(Priest.class), eq(List.of()), eq(List.of()), eq(List.of()), eq(List.of()));
    }

    @Test
    void shouldFindEventScaleWithoutLocation() {
        CelebrationEvent event = event(1L);
        event.getPeople().add(person(new Priest(), 13L, "Padre"));
        when(repository.findByIdWithLocations(1L)).thenReturn(Optional.of(event));
        when(repository.findByIdWithPeople(1L)).thenReturn(Optional.of(event));
        when(scaleDetailMapper.toDto(eq(event), isNull(), any(Priest.class), anyList(), anyList(), anyList(), anyList()))
                .thenReturn(detailResponse());

        service.findScaleByEventId(1L);

        verify(scaleDetailMapper).toDto(eq(event), isNull(), any(Priest.class), anyList(), anyList(), anyList(), anyList());
    }

    @Test
    void shouldSeparateAllPersonSubtypesWhenFindingEventScale() {
        CelebrationEvent event = eventWithCompleteScale();
        when(repository.findByIdWithLocations(1L)).thenReturn(Optional.of(event));
        when(repository.findByIdWithPeople(1L)).thenReturn(Optional.of(event));
        when(scaleDetailMapper.toDto(eq(event), any(), any(), anyList(), anyList(), anyList(), anyList()))
                .thenReturn(detailResponse());

        service.findScaleByEventId(1L);

        verify(scaleDetailMapper).toDto(
                eq(event),
                any(Location.class),
                any(Priest.class),
                argThat(list -> list.size() == 2 && list.stream().allMatch(Reader.class::isInstance)),
                argThat(list -> list.size() == 1 && list.stream().allMatch(Commentator.class::isInstance)),
                argThat(list -> list.size() == 1 && list.stream().allMatch(MinisterOfTheWord.class::isInstance)),
                argThat(list -> list.size() == 2 && list.stream().allMatch(EucharisticMinister.class::isInstance))
        );
    }

    @Test
    void shouldSortPeopleDeterministicallyWhenFindingEventScale() {
        CelebrationEvent event = event(1L);
        event.getLocations().add(location(1L));
        event.getPeople().add(person(new Reader(), 5L, "Bruno"));
        event.getPeople().add(person(new Reader(), 4L, "Ana"));
        event.getPeople().add(person(new Reader(), 3L, "Ana"));
        when(repository.findByIdWithLocations(1L)).thenReturn(Optional.of(event));
        when(repository.findByIdWithPeople(1L)).thenReturn(Optional.of(event));
        when(scaleDetailMapper.toDto(eq(event), any(), isNull(), anyList(), anyList(), anyList(), anyList()))
                .thenReturn(detailResponse());

        service.findScaleByEventId(1L);

        verify(scaleDetailMapper).toDto(
                eq(event),
                any(Location.class),
                isNull(),
                argThat(list -> List.of(3L, 4L, 5L).equals(list.stream().map(Person::getId).toList())),
                anyList(),
                anyList(),
                anyList()
        );
    }

    @Test
    void shouldNotExposePersonalDataInEventScaleDetailResponse() {
        assertAll(
                () -> assertThrows(NoSuchMethodException.class,
                        () -> CelebrationEventScaleDetailResponseDTO.class.getMethod("getPhoneNumber")),
                () -> assertThrows(NoSuchMethodException.class,
                        () -> CelebrationEventScaleDetailResponseDTO.class.getMethod("getBirthdayDate")),
                () -> assertThrows(NoSuchMethodException.class,
                        () -> CelebrationEventScaleDetailResponseDTO.class.getMethod("getPassword")),
                () -> assertThrows(NoSuchMethodException.class,
                        () -> CelebrationEventScaleDetailResponseDTO.class.getMethod("getRoles"))
        );
    }

    @Test
    void shouldThrowResourceNotFoundWhenFindingScaleForMissingEvent() {
        when(repository.findByIdWithLocations(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.findScaleByEventId(99L));
    }

    @Test
    void shouldThrowBusinessExceptionWhenFindingScaleWithInvalidId() {
        assertAll(
                () -> assertThrows(BusinessException.class, () -> service.findScaleByEventId(null)),
                () -> assertThrows(BusinessException.class, () -> service.findScaleByEventId(0L)),
                () -> assertThrows(BusinessException.class, () -> service.findScaleByEventId(-1L))
        );
    }

    @Test
    void shouldNotModifyEventWhenFindingScale() {
        CelebrationEvent event = eventWithCompleteScale();
        int locationCount = event.getLocations().size();
        int peopleCount = event.getPeople().size();
        when(repository.findByIdWithLocations(1L)).thenReturn(Optional.of(event));
        when(repository.findByIdWithPeople(1L)).thenReturn(Optional.of(event));
        when(scaleDetailMapper.toDto(eq(event), any(), any(), anyList(), anyList(), anyList(), anyList()))
                .thenReturn(detailResponse());

        service.findScaleByEventId(1L);

        assertEquals(locationCount, event.getLocations().size());
        assertEquals(peopleCount, event.getPeople().size());
        verify(repository, never()).save(any());
    }

    @Test
    void shouldThrowBusinessExceptionWhenEventScaleHasMoreThanOnePriest() {
        CelebrationEvent event = event(1L);
        event.getLocations().add(location(1L));
        event.getPeople().add(person(new Priest(), 13L, "Padre A"));
        event.getPeople().add(person(new Priest(), 14L, "Padre B"));
        when(repository.findByIdWithLocations(1L)).thenReturn(Optional.of(event));
        when(repository.findByIdWithPeople(1L)).thenReturn(Optional.of(event));

        assertThrows(BusinessException.class, () -> service.findScaleByEventId(1L));
        verifyNoInteractions(scaleDetailMapper);
    }

    @Test
    void shouldFindEventScaleFromParallelAssignmentsWithoutUsingLegacyPeopleOrShadow() {
        CelebrationEvent event = event(1L);
        event.getLocations().add(location(1L));
        event.getPeople().add(person(new Reader(), 99L, "Legacy Reader Not Used"));
        CelebrationEventScaleDetailResponseDTO response = detailResponse();
        when(eventAssignmentReadSourceProperties.getEventScaleDetail()).thenReturn(EventAssignmentReadSource.PARALLEL);
        when(repository.findByIdWithLocations(1L)).thenReturn(Optional.of(event));
        when(eventAssignmentReadService.findAllByEventId(1L)).thenReturn(List.of(
                snapshot(100L, 1L, 13L, EventAssignmentType.PRIEST, "Padre", "priest"),
                snapshot(101L, 1L, 5L, EventAssignmentType.READER, "Bruno", "reader"),
                snapshot(102L, 1L, 4L, EventAssignmentType.READER, "Ana", "reader"),
                snapshot(103L, 1L, 1L, EventAssignmentType.COMMENTATOR, "Luana", "commentator"),
                snapshot(104L, 1L, 7L, EventAssignmentType.MINISTER_OF_THE_WORD, "Davi", "minister_of_the_word"),
                snapshot(105L, 1L, 11L, EventAssignmentType.EUCHARISTIC_MINISTER, "Carlos", "eucharistic_minister"),
                snapshot(106L, 1L, 10L, EventAssignmentType.EUCHARISTIC_MINISTER, "Mariana", "eucharistic_minister")
        ));
        when(scaleDetailMapper.toDto(eq(event), any(Location.class), any(EventAssignmentGroup.class)))
                .thenReturn(response);

        assertSame(response, service.findScaleByEventId(1L));

        ArgumentCaptor<EventAssignmentGroup> groupCaptor = ArgumentCaptor.forClass(EventAssignmentGroup.class);
        verify(scaleDetailMapper).toDto(eq(event), any(Location.class), groupCaptor.capture());
        EventAssignmentGroup group = groupCaptor.getValue();
        assertEquals(13L, group.priest().personId());
        assertEquals(List.of(4L, 5L), group.readers().stream().map(EventAssignmentSnapshot::personId).toList());
        assertEquals(List.of(1L), group.commentators().stream().map(EventAssignmentSnapshot::personId).toList());
        assertEquals(List.of(7L), group.ministersOfTheWord().stream().map(EventAssignmentSnapshot::personId).toList());
        assertEquals(List.of(11L, 10L), group.eucharisticMinisters().stream().map(EventAssignmentSnapshot::personId).toList());
        verify(repository, never()).findByIdWithPeople(anyLong());
        verify(eventAssignmentReadService).findAllByEventId(1L);
        verifyNoInteractions(eventAssignmentShadowReadExecutor);
    }

    @Test
    void shouldGroupParallelEventScaleByAssignmentTypeInsteadOfPersonType() {
        CelebrationEvent event = event(1L);
        event.getLocations().add(location(1L));
        when(eventAssignmentReadSourceProperties.getEventScaleDetail()).thenReturn(EventAssignmentReadSource.PARALLEL);
        when(repository.findByIdWithLocations(1L)).thenReturn(Optional.of(event));
        when(eventAssignmentReadService.findAllByEventId(1L)).thenReturn(List.of(
                snapshot(100L, 1L, 20L, EventAssignmentType.EUCHARISTIC_MINISTER, "Reader Serving Eucharist", "reader")
        ));
        when(scaleDetailMapper.toDto(eq(event), any(Location.class), any(EventAssignmentGroup.class)))
                .thenReturn(detailResponse());

        service.findScaleByEventId(1L);

        ArgumentCaptor<EventAssignmentGroup> groupCaptor = ArgumentCaptor.forClass(EventAssignmentGroup.class);
        verify(scaleDetailMapper).toDto(eq(event), any(Location.class), groupCaptor.capture());
        EventAssignmentGroup group = groupCaptor.getValue();
        assertTrue(group.readers().isEmpty());
        assertEquals(List.of(20L), group.eucharisticMinisters().stream().map(EventAssignmentSnapshot::personId).toList());
        verify(repository, never()).findByIdWithPeople(anyLong());
    }

    @Test
    void shouldPropagateParallelFailureWithoutLegacyFallbackWhenFindingEventScale() {
        CelebrationEvent event = event(1L);
        when(eventAssignmentReadSourceProperties.getEventScaleDetail()).thenReturn(EventAssignmentReadSource.PARALLEL);
        when(repository.findByIdWithLocations(1L)).thenReturn(Optional.of(event));
        when(eventAssignmentReadService.findAllByEventId(1L))
                .thenThrow(new IllegalStateException("controlled parallel failure"));

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> service.findScaleByEventId(1L));

        assertEquals("controlled parallel failure", exception.getMessage());
        verify(repository, never()).findByIdWithPeople(anyLong());
        verifyNoInteractions(scaleDetailMapper, eventAssignmentShadowReadExecutor);
    }

    @Test
    void shouldPreserveNotFoundBehaviorWhenParallelEventScaleEventDoesNotExist() {
        when(eventAssignmentReadSourceProperties.getEventScaleDetail()).thenReturn(EventAssignmentReadSource.PARALLEL);
        when(repository.findByIdWithLocations(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.findScaleByEventId(99L));
        verify(repository, never()).findByIdWithPeople(anyLong());
        verifyNoInteractions(eventAssignmentReadService, scaleDetailMapper, eventAssignmentShadowReadExecutor);
    }

    @Test
    void shouldRejectParallelEventScaleWithDuplicatedPersonAssignment() {
        CelebrationEvent event = event(1L);
        when(eventAssignmentReadSourceProperties.getEventScaleDetail()).thenReturn(EventAssignmentReadSource.PARALLEL);
        when(repository.findByIdWithLocations(1L)).thenReturn(Optional.of(event));
        when(eventAssignmentReadService.findAllByEventId(1L)).thenReturn(List.of(
                snapshot(100L, 1L, 10L, EventAssignmentType.READER, "Pessoa", "reader"),
                snapshot(101L, 1L, 10L, EventAssignmentType.COMMENTATOR, "Pessoa", "reader")
        ));

        assertThrows(BusinessException.class, () -> service.findScaleByEventId(1L));
        verify(repository, never()).findByIdWithPeople(anyLong());
        verifyNoInteractions(scaleDetailMapper, eventAssignmentShadowReadExecutor);
    }

    @Test
    void shouldRejectParallelEventScaleWithMoreThanOnePriest() {
        CelebrationEvent event = event(1L);
        when(eventAssignmentReadSourceProperties.getEventScaleDetail()).thenReturn(EventAssignmentReadSource.PARALLEL);
        when(repository.findByIdWithLocations(1L)).thenReturn(Optional.of(event));
        when(eventAssignmentReadService.findAllByEventId(1L)).thenReturn(List.of(
                snapshot(100L, 1L, 10L, EventAssignmentType.PRIEST, "Padre A", "priest"),
                snapshot(101L, 1L, 11L, EventAssignmentType.PRIEST, "Padre B", "priest")
        ));

        assertThrows(BusinessException.class, () -> service.findScaleByEventId(1L));
        verify(repository, never()).findByIdWithPeople(anyLong());
        verifyNoInteractions(scaleDetailMapper, eventAssignmentShadowReadExecutor);
    }

    @Test
    void shouldRejectParallelEventScaleWithMissingAssignmentType() {
        CelebrationEvent event = event(1L);
        when(eventAssignmentReadSourceProperties.getEventScaleDetail()).thenReturn(EventAssignmentReadSource.PARALLEL);
        when(repository.findByIdWithLocations(1L)).thenReturn(Optional.of(event));
        when(eventAssignmentReadService.findAllByEventId(1L)).thenReturn(List.of(
                snapshot(100L, 1L, 10L, null, "Pessoa", "reader")
        ));

        assertThrows(BusinessException.class, () -> service.findScaleByEventId(1L));
        verify(repository, never()).findByIdWithPeople(anyLong());
        verifyNoInteractions(scaleDetailMapper, eventAssignmentShadowReadExecutor);
    }

    @Test
    void shouldRejectParallelEventScaleWithAssignmentFromAnotherEvent() {
        CelebrationEvent event = event(1L);
        when(eventAssignmentReadSourceProperties.getEventScaleDetail()).thenReturn(EventAssignmentReadSource.PARALLEL);
        when(repository.findByIdWithLocations(1L)).thenReturn(Optional.of(event));
        when(eventAssignmentReadService.findAllByEventId(1L)).thenReturn(List.of(
                snapshot(100L, 2L, 10L, EventAssignmentType.READER, "Pessoa", "reader")
        ));

        assertThrows(BusinessException.class, () -> service.findScaleByEventId(1L));
        verify(repository, never()).findByIdWithPeople(anyLong());
        verifyNoInteractions(scaleDetailMapper, eventAssignmentShadowReadExecutor);
    }

    @Test
    void shouldListEvents() {
        List<CelebrationEvent> entities = List.of(event(1L));
        List<CelebrationEventResponseDTO> responses = List.of(response(1L));
        when(repository.findAll()).thenReturn(entities);
        when(mapper.toDtoList(entities)).thenReturn(responses);

        assertSame(responses, service.findAllEvents());
    }

    @Test
    void shouldMapEucharistScaleProjectionToResponse() {
        PageRequest pageable = PageRequest.of(0, 10);
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 8, 31);
        EucharistScaleEventProjection projection = projection("Missa", EVENT_DATE, EVENT_TIME, "Igreja Matriz", "Ana, Bruno");
        when(repository.findEucharistScale(pageable, startDate, endDate))
                .thenReturn(new PageImpl<>(List.of(projection), pageable, 1));

        Page<EucharistScaleEventResponseDTO> result = service.findEucharistScale(pageable, startDate, endDate);

        assertEquals(1, result.getTotalElements());
        EucharistScaleEventResponseDTO dto = result.getContent().get(0);
        assertEquals("Missa", dto.getNameMassOrEvent());
        assertEquals(EVENT_DATE, dto.getEventDate());
        assertEquals(EVENT_TIME, dto.getEventTime());
        assertEquals("Igreja Matriz", dto.getChurchName());
        assertEquals(List.of("Ana", "Bruno"), dto.getNameMinisters());
    }

    @Test
    void shouldInvokeEucharistScalePartialShadowReadWhenEnabled() {
        PageRequest pageable = PageRequest.of(0, 10);
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 8, 31);
        EucharistScaleEventProjection projection = projection("Missa", EVENT_DATE, EVENT_TIME, "Igreja Matriz", "Ana");
        when(eventAssignmentShadowReadProperties.isEucharistScaleEnabled()).thenReturn(true);
        when(repository.findEucharistScale(pageable, startDate, endDate))
                .thenReturn(new PageImpl<>(List.of(projection), pageable, 1));

        service.findEucharistScale(pageable, startDate, endDate);

        verify(eventAssignmentShadowReadExecutor).comparePartialAssignmentsIfEnabled(
                eq(true),
                eq("eucharist-scale"),
                eq(List.of(1L)),
                eq(com.eventoscelebrativos.model.EventAssignmentType.EUCHARISTIC_MINISTER),
                org.mockito.ArgumentMatchers.<Supplier<List<EventAssignmentSnapshot>>>any()
        );
    }

    @Test
    void shouldThrowBusinessExceptionWhenEucharistScalePeriodIsInvalid() {
        PageRequest pageable = PageRequest.of(0, 10);

        assertAll(
                () -> assertThrows(BusinessException.class,
                        () -> service.findEucharistScale(pageable, null, EVENT_DATE)),
                () -> assertThrows(BusinessException.class,
                        () -> service.findEucharistScale(pageable, EVENT_DATE, null)),
                () -> assertThrows(BusinessException.class,
                        () -> service.findEucharistScale(pageable, EVENT_DATE.plusDays(1), EVENT_DATE))
        );
    }

    @Test
    void shouldFindEventSchedulesForEachType() {
        for (EventScheduleType type : EventScheduleType.values()) {
            when(repository.findEventScheduleEvents(any(), eq(EVENT_DATE), eq(EVENT_DATE), eq(type.getPersonType()), eq(false)))
                    .thenReturn(new PageImpl<>(List.of(scheduleEvent(1L)), PageRequest.of(0, 10), 1));
            when(repository.findEventScheduleAssignments(List.of(1L), type.getPersonType()))
                    .thenReturn(List.of(scheduleAssignment(1L, 10L, "Pessoa")));

            Page<EventScheduleQueryResponseDTO> result = service.findEventSchedules(EVENT_DATE, EVENT_DATE, type, 0, 10, false);

            assertEquals(1, result.getTotalElements());
            assertEquals(type, result.getContent().get(0).getAssignmentType());
            assertEquals(1, result.getContent().get(0).getAssignments().size());
        }
    }

    @Test
    void shouldThrowBusinessExceptionWhenEventSchedulePeriodIsInvalid() {
        assertAll(
                () -> assertThrows(BusinessException.class,
                        () -> service.findEventSchedules(null, EVENT_DATE, EventScheduleType.READER, 0, 10, false)),
                () -> assertThrows(BusinessException.class,
                        () -> service.findEventSchedules(EVENT_DATE, null, EventScheduleType.READER, 0, 10, false)),
                () -> assertThrows(BusinessException.class,
                        () -> service.findEventSchedules(EVENT_DATE.plusDays(1), EVENT_DATE, EventScheduleType.READER, 0, 10, false))
        );
    }

    @Test
    void shouldThrowBusinessExceptionWhenEventSchedulePageSizeIsInvalid() {
        assertAll(
                () -> assertThrows(BusinessException.class,
                        () -> service.findEventSchedules(EVENT_DATE, EVENT_DATE, EventScheduleType.READER, -1, 10, false)),
                () -> assertThrows(BusinessException.class,
                        () -> service.findEventSchedules(EVENT_DATE, EVENT_DATE, EventScheduleType.READER, 0, 0, false)),
                () -> assertThrows(BusinessException.class,
                        () -> service.findEventSchedules(EVENT_DATE, EVENT_DATE, EventScheduleType.READER, 0, 101, false))
        );
    }

    @Test
    void shouldMapEventScheduleToResponse() {
        when(repository.findEventScheduleEvents(any(), eq(EVENT_DATE), eq(EVENT_DATE), eq(EventScheduleType.READER.getPersonType()), eq(false)))
                .thenReturn(new PageImpl<>(List.of(scheduleEvent(1L)), PageRequest.of(0, 10), 1));
        when(repository.findEventScheduleAssignments(List.of(1L), EventScheduleType.READER.getPersonType()))
                .thenReturn(List.of(scheduleAssignment(1L, 10L, "Maria")));

        Page<EventScheduleQueryResponseDTO> result =
                service.findEventSchedules(EVENT_DATE, EVENT_DATE, EventScheduleType.READER, 0, 10, false);

        EventScheduleQueryResponseDTO dto = result.getContent().get(0);
        assertEquals(1L, dto.getEventId());
        assertEquals("Missa", dto.getEventName());
        assertEquals(EVENT_DATE, dto.getEventDate());
        assertEquals(EVENT_TIME, dto.getEventTime());
        assertEquals(1L, dto.getLocationId());
        assertEquals("Igreja Matriz", dto.getChurchName());
        assertEquals(EventScheduleType.READER, dto.getAssignmentType());
        assertEquals(10L, dto.getAssignments().get(0).getPersonId());
        assertEquals("Maria", dto.getAssignments().get(0).getPersonName());
    }

    @Test
    void shouldInvokeMonthlySchedulePartialShadowReadWhenEnabled() {
        when(eventAssignmentShadowReadProperties.isMonthlyScheduleEnabled()).thenReturn(true);
        when(repository.findEventScheduleEvents(any(), eq(EVENT_DATE), eq(EVENT_DATE), eq(EventScheduleType.READER.getPersonType()), eq(false)))
                .thenReturn(new PageImpl<>(List.of(scheduleEvent(1L)), PageRequest.of(0, 10), 1));
        when(repository.findEventScheduleAssignments(List.of(1L), EventScheduleType.READER.getPersonType()))
                .thenReturn(List.of(scheduleAssignment(1L, 10L, "Maria")));

        service.findEventSchedules(EVENT_DATE, EVENT_DATE, EventScheduleType.READER, 0, 10, false);

        verify(eventAssignmentShadowReadExecutor).comparePartialAssignmentsIfEnabled(
                eq(true),
                eq("monthly-schedule"),
                eq(List.of(1L)),
                eq(com.eventoscelebrativos.model.EventAssignmentType.READER),
                org.mockito.ArgumentMatchers.<Supplier<List<EventAssignmentSnapshot>>>any()
        );
    }

    @Test
    void shouldMapEventScheduleWithSeveralAssignments() {
        when(repository.findEventScheduleEvents(any(), eq(EVENT_DATE), eq(EVENT_DATE), eq(EventScheduleType.EUCHARISTIC_MINISTER.getPersonType()), eq(false)))
                .thenReturn(new PageImpl<>(List.of(scheduleEvent(1L)), PageRequest.of(0, 10), 1));
        when(repository.findEventScheduleAssignments(List.of(1L), EventScheduleType.EUCHARISTIC_MINISTER.getPersonType()))
                .thenReturn(List.of(
                        scheduleAssignment(1L, 10L, "Ana"),
                        scheduleAssignment(1L, 11L, "Bruno")
                ));

        Page<EventScheduleQueryResponseDTO> result =
                service.findEventSchedules(EVENT_DATE, EVENT_DATE, EventScheduleType.EUCHARISTIC_MINISTER, 0, 10, false);

        assertEquals(2, result.getContent().get(0).getAssignments().size());
    }

    @Test
    void shouldReturnEmptyAssignmentsWhenIncludeUnassignedIsTrueAndEventHasNoPerson() {
        when(repository.findEventScheduleEvents(any(), eq(EVENT_DATE), eq(EVENT_DATE), eq(EventScheduleType.PRIEST.getPersonType()), eq(true)))
                .thenReturn(new PageImpl<>(List.of(scheduleEvent(1L)), PageRequest.of(0, 10), 1));
        when(repository.findEventScheduleAssignments(List.of(1L), EventScheduleType.PRIEST.getPersonType()))
                .thenReturn(List.of());

        Page<EventScheduleQueryResponseDTO> result =
                service.findEventSchedules(EVENT_DATE, EVENT_DATE, EventScheduleType.PRIEST, 0, 10, true);

        assertTrue(result.getContent().get(0).getAssignments().isEmpty());
    }

    @Test
    void shouldReturnEmptyEventSchedulePage() {
        when(repository.findEventScheduleEvents(any(), eq(EVENT_DATE), eq(EVENT_DATE), eq(EventScheduleType.READER.getPersonType()), eq(false)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        Page<EventScheduleQueryResponseDTO> result =
                service.findEventSchedules(EVENT_DATE, EVENT_DATE, EventScheduleType.READER, 0, 10, false);

        assertTrue(result.isEmpty());
        verify(repository, never()).findEventScheduleAssignments(anyList(), anyString());
    }

    @Test
    void shouldNotExposePersonalDataInEventScheduleResponse() {
        assertAll(
                () -> assertThrows(NoSuchMethodException.class,
                        () -> EventScheduleQueryResponseDTO.class.getMethod("getPhoneNumber")),
                () -> assertThrows(NoSuchMethodException.class,
                        () -> EventScheduleQueryResponseDTO.class.getMethod("getBirthdayDate")),
                () -> assertThrows(NoSuchMethodException.class,
                        () -> EventScheduleQueryResponseDTO.class.getMethod("getPassword")),
                () -> assertThrows(NoSuchMethodException.class,
                        () -> EventScheduleQueryResponseDTO.class.getMethod("getRoles"))
        );
    }

    @Test
    void shouldUpdateEventWhenExists() {
        CelebrationEventRequestDTO request = request();
        CelebrationEvent entity = event(1L);
        CelebrationEvent saved = event(1L);
        CelebrationEventResponseDTO response = response(1L);

        when(repository.getReferenceById(1L)).thenReturn(entity);
        when(repository.save(entity)).thenReturn(saved);
        when(mapper.toDto(saved)).thenReturn(response);

        assertSame(response, service.updateEvent(1L, request));
        verify(mapper).updateCelebrationEventMapperFromDto(request, entity);
        verifyNoInteractions(eventAssignmentTargetResolver, eventAssignmentCompatibilityService);
    }

    @Test
    void shouldThrowResourceNotFoundWhenUpdatingMissingEvent() {
        when(repository.getReferenceById(99L)).thenThrow(new EntityNotFoundException());

        assertThrows(ResourceNotFoundException.class, () -> service.updateEvent(99L, request()));
    }

    @Test
    void shouldDeleteEventWhenExists() {
        when(repository.existsById(1L)).thenReturn(true);

        service.deleteEventById(1L);

        verify(eventAssignmentCompatibilityService).deleteAllForEvent(1L);
        verify(repository).deleteById(1L);
    }

    @Test
    void shouldThrowResourceNotFoundWhenDeletingMissingEvent() {
        when(repository.existsById(99L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.deleteEventById(99L));
        verify(repository, never()).deleteById(anyLong());
        verifyNoInteractions(eventAssignmentCompatibilityService);
    }

    @Test
    void shouldThrowDatabaseExceptionWhenDeletingReferencedEvent() {
        when(repository.existsById(1L)).thenReturn(true);
        doThrow(new DataIntegrityViolationException("constraint")).when(repository).flush();

        assertThrows(DatabaseException.class, () -> service.deleteEventById(1L));
        verify(eventAssignmentCompatibilityService).deleteAllForEvent(1L);
    }

    @Test
    void shouldUpdateEventScaleWhenEventExists() {
        CelebrationEvent event = event(1L);
        Location location = location(1L);
        Priest priest = person(new Priest(), 8L, "Padre");
        Reader reader = person(new Reader(), 2L, "Leitor");
        Commentator commentator = person(new Commentator(), 4L, "Comentarista");
        MinisterOfTheWord ministerOfTheWord = person(new MinisterOfTheWord(), 5L, "Ministro da Palavra");
        EucharisticMinister eucharisticMinister = person(new EucharisticMinister(), 6L, "Ministro da Eucaristia");
        CelebrationEventScaleRequestDTO request = scaleRequest();
        CelebrationEventScaleResponseDTO response = new CelebrationEventScaleResponseDTO();
        List<EventAssignmentTarget> targets = List.of(new EventAssignmentTarget(priest, com.eventoscelebrativos.model.EventAssignmentType.PRIEST));

        when(repository.findById(1L)).thenReturn(Optional.of(event));
        when(locationRepository.findById(1L)).thenReturn(Optional.of(location));
        when(personRepository.findById(8L)).thenReturn(Optional.of(priest));
        when(personRepository.findById(2L)).thenReturn(Optional.of(reader));
        when(personRepository.findById(4L)).thenReturn(Optional.of(commentator));
        when(personRepository.findById(5L)).thenReturn(Optional.of(ministerOfTheWord));
        when(personRepository.findById(6L)).thenReturn(Optional.of(eucharisticMinister));
        when(repository.save(event)).thenReturn(event);
        when(eventAssignmentTargetResolver.resolve(event.getPeople())).thenReturn(targets);
        when(scaleMapper.toDto(event)).thenReturn(response);

        assertSame(response, service.updateEventScale(1L, request));
        assertEquals(List.of(location), event.getLocations());
        assertEquals(List.of(priest, reader, commentator, ministerOfTheWord, eucharisticMinister), event.getPeople());
        verify(eventAssignmentCompatibilityService).synchronizeAssignments(event, targets);
    }

    @Test
    void shouldCreateEventWithScale() {
        Location location = location(1L);
        Priest priest = person(new Priest(), 8L, "Padre");
        CelebrationEventScaleResponseDTO response = new CelebrationEventScaleResponseDTO();
        List<EventAssignmentTarget> targets = List.of(new EventAssignmentTarget(priest, com.eventoscelebrativos.model.EventAssignmentType.PRIEST));

        when(locationRepository.findById(1L)).thenReturn(Optional.of(location));
        when(personRepository.findById(8L)).thenReturn(Optional.of(priest));
        when(repository.save(any(CelebrationEvent.class))).thenAnswer(invocation -> {
            CelebrationEvent event = invocation.getArgument(0);
            event.setId(1L);
            return event;
        });
        when(eventAssignmentTargetResolver.resolve(anyList())).thenReturn(targets);
        when(scaleMapper.toDto(any(CelebrationEvent.class))).thenReturn(response);

        assertSame(response, service.createEventWithScale(eventWithScaleRequest()));
        verify(repository).save(argThat(event ->
                "Missa".equals(event.getNameMassOrEvent())
                        && EVENT_DATE.equals(event.getEventDate())
                        && EVENT_TIME.equals(event.getEventTime())
                        && event.getLocations().contains(location)
                        && event.getPeople().contains(priest)
        ));
        verify(eventAssignmentCompatibilityService).synchronizeAssignments(any(CelebrationEvent.class), eq(targets));
    }

    @Test
    void shouldReplacePreviousScaleWhenUpdatingEventScale() {
        CelebrationEvent event = event(1L);
        Location oldLocation = location(9L);
        Reader oldReader = person(new Reader(), 9L, "Leitor antigo");
        event.getLocations().add(oldLocation);
        event.getPeople().add(oldReader);
        Location newLocation = location(1L);

        when(repository.findById(1L)).thenReturn(Optional.of(event));
        when(locationRepository.findById(1L)).thenReturn(Optional.of(newLocation));
        when(repository.save(event)).thenReturn(event);
        when(eventAssignmentTargetResolver.resolve(event.getPeople())).thenReturn(List.of());
        when(scaleMapper.toDto(event)).thenReturn(new CelebrationEventScaleResponseDTO());

        service.updateEventScale(1L, new CelebrationEventScaleRequestDTO(1L, null, null, null, null, null));

        assertEquals(List.of(newLocation), event.getLocations());
        assertTrue(event.getPeople().isEmpty());
        verify(eventAssignmentCompatibilityService).synchronizeAssignments(event, List.of());
    }

    @Test
    void shouldThrowResourceNotFoundWhenUpdatingScaleOfMissingEvent() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.updateEventScale(99L, scaleRequest()));
    }

    @Test
    void shouldThrowResourceNotFoundWhenScaleLocationDoesNotExist() {
        when(repository.findById(1L)).thenReturn(Optional.of(event(1L)));
        when(locationRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.updateEventScale(1L, scaleRequest()));
    }

    @Test
    void shouldThrowResourceNotFoundWhenScalePersonDoesNotExist() {
        when(repository.findById(1L)).thenReturn(Optional.of(event(1L)));
        when(locationRepository.findById(1L)).thenReturn(Optional.of(location(1L)));
        when(personRepository.findById(8L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.updateEventScale(1L, scaleRequest()));
    }

    @Test
    void shouldThrowBusinessExceptionWhenScalePersonHasWrongType() {
        when(repository.findById(1L)).thenReturn(Optional.of(event(1L)));
        when(locationRepository.findById(1L)).thenReturn(Optional.of(location(1L)));
        when(personRepository.findById(8L)).thenReturn(Optional.of(person(new Reader(), 8L, "Leitor")));

        assertThrows(BusinessException.class, () -> service.updateEventScale(1L, scaleRequest()));
    }

    @Test
    void shouldThrowBusinessExceptionWhenScaleLocationIdIsInvalid() {
        when(repository.findById(1L)).thenReturn(Optional.of(event(1L)));

        assertAll(
                () -> assertThrows(BusinessException.class,
                        () -> service.updateEventScale(1L, new CelebrationEventScaleRequestDTO(null, null, null, null, null, null))),
                () -> assertThrows(BusinessException.class,
                        () -> service.updateEventScale(1L, new CelebrationEventScaleRequestDTO(0L, null, null, null, null, null))),
                () -> assertThrows(BusinessException.class,
                        () -> service.updateEventScale(1L, new CelebrationEventScaleRequestDTO(-1L, null, null, null, null, null)))
        );
    }

    @Test
    void shouldTreatNullListsAsEmptyWhenUpdatingScale() {
        CelebrationEvent event = event(1L);
        Location location = location(1L);

        when(repository.findById(1L)).thenReturn(Optional.of(event));
        when(locationRepository.findById(1L)).thenReturn(Optional.of(location));
        when(repository.save(event)).thenReturn(event);
        when(scaleMapper.toDto(event)).thenReturn(new CelebrationEventScaleResponseDTO());

        service.updateEventScale(1L, new CelebrationEventScaleRequestDTO(1L, null, null, null, null, null));

        assertTrue(event.getPeople().isEmpty());
        assertEquals(List.of(location), event.getLocations());
    }

    @Test
    void shouldThrowBusinessExceptionWhenScaleListHasDuplicatedIds() {
        when(repository.findById(1L)).thenReturn(Optional.of(event(1L)));
        when(locationRepository.findById(1L)).thenReturn(Optional.of(location(1L)));

        CelebrationEventScaleRequestDTO request =
                new CelebrationEventScaleRequestDTO(1L, null, List.of(2L, 2L), null, null, null);

        assertThrows(BusinessException.class, () -> service.updateEventScale(1L, request));
    }

    @Test
    void shouldNotChangePersonPasswordRolesOrRegistrationDataWhenUpdatingScale() {
        CelebrationEvent event = event(1L);
        Location location = location(1L);
        Priest priest = person(new Priest(), 8L, "Padre");
        priest.setPassword("encoded");
        priest.setPhoneNumber("34999999999");

        when(repository.findById(1L)).thenReturn(Optional.of(event));
        when(locationRepository.findById(1L)).thenReturn(Optional.of(location));
        when(personRepository.findById(8L)).thenReturn(Optional.of(priest));
        when(repository.save(event)).thenReturn(event);
        when(scaleMapper.toDto(event)).thenReturn(new CelebrationEventScaleResponseDTO());

        service.updateEventScale(1L, new CelebrationEventScaleRequestDTO(1L, 8L, null, null, null, null));

        assertEquals("encoded", priest.getPassword());
        assertEquals("34999999999", priest.getPhoneNumber());
    }

    @Test
    void shouldNotCreateEventWithScaleWhenScaleIsInvalid() {
        when(locationRepository.findById(1L)).thenReturn(Optional.of(location(1L)));
        when(personRepository.findById(8L)).thenReturn(Optional.of(person(new Reader(), 8L, "Leitor")));

        assertThrows(BusinessException.class, () -> service.createEventWithScale(eventWithScaleRequest()));
        verify(repository, never()).save(any());
        verifyNoInteractions(eventAssignmentCompatibilityService);
    }

    private CelebrationEventRequestDTO request() {
        return new CelebrationEventRequestDTO("Missa", EVENT_DATE, EVENT_TIME, true);
    }

    private CelebrationEventScaleRequestDTO scaleRequest() {
        return new CelebrationEventScaleRequestDTO(
                1L,
                8L,
                List.of(2L),
                List.of(4L),
                List.of(5L),
                List.of(6L)
        );
    }

    private CelebrationEventWithScaleRequestDTO eventWithScaleRequest() {
        CelebrationEventWithScaleRequestDTO request = new CelebrationEventWithScaleRequestDTO();
        request.setNameMassOrEvent("Missa");
        request.setEventDate(EVENT_DATE);
        request.setEventTime(EVENT_TIME);
        request.setMassOrCelebration(true);
        request.setLocationId(1L);
        request.setPriestId(8L);
        return request;
    }

    private CelebrationEvent event(Long id) {
        CelebrationEvent event = new CelebrationEvent();
        event.setId(id);
        event.setNameMassOrEvent("Missa");
        event.setEventDate(EVENT_DATE);
        event.setEventTime(EVENT_TIME);
        event.setMassOrCelebration(true);
        return event;
    }

    private CelebrationEventResponseDTO response(Long id) {
        return new CelebrationEventResponseDTO(id, "Missa", EVENT_DATE, EVENT_TIME, true);
    }

    private CelebrationEventScaleDetailResponseDTO detailResponse() {
        CelebrationEventScaleDetailResponseDTO response = new CelebrationEventScaleDetailResponseDTO();
        response.setEventId(1L);
        response.setEventName("Missa");
        response.setEventDate(EVENT_DATE);
        response.setEventTime(EVENT_TIME);
        response.setMassOrCelebration(true);
        return response;
    }

    private CelebrationEvent eventWithCompleteScale() {
        CelebrationEvent event = event(1L);
        event.getLocations().add(location(1L));
        event.getPeople().add(person(new Priest(), 13L, "Padre"));
        event.getPeople().add(person(new Reader(), 5L, "Bruno"));
        event.getPeople().add(person(new Reader(), 4L, "Ana"));
        event.getPeople().add(person(new Commentator(), 1L, "Luana"));
        event.getPeople().add(person(new MinisterOfTheWord(), 7L, "Davi"));
        event.getPeople().add(person(new EucharisticMinister(), 11L, "Carlos"));
        event.getPeople().add(person(new EucharisticMinister(), 10L, "Mariana"));
        return event;
    }

    private Location location(Long id) {
        return new Location(id, "Igreja Matriz", "Praça Central");
    }

    private <T extends Person> T person(T person, Long id, String name) {
        person.setId(id);
        person.setName(name);
        person.setPhoneNumber("34" + id);
        return person;
    }

    private EventAssignmentSnapshot snapshot(
            Long assignmentId,
            Long eventId,
            Long personId,
            EventAssignmentType assignmentType,
            String personName,
            String personType
    ) {
        return new EventAssignmentSnapshot(
                assignmentId,
                eventId,
                personId,
                assignmentType,
                personName,
                personType
        );
    }

    private EventScheduleEventProjection scheduleEvent(Long eventId) {
        return new EventScheduleEventProjection() {
            @Override
            public Long getEventId() {
                return eventId;
            }

            @Override
            public String getEventName() {
                return "Missa";
            }

            @Override
            public LocalDate getEventDate() {
                return EVENT_DATE;
            }

            @Override
            public LocalTime getEventTime() {
                return EVENT_TIME;
            }

            @Override
            public Boolean getMassOrCelebration() {
                return true;
            }

            @Override
            public Long getLocationId() {
                return 1L;
            }

            @Override
            public String getChurchName() {
                return "Igreja Matriz";
            }
        };
    }

    private EventScheduleAssignmentProjection scheduleAssignment(Long eventId, Long personId, String personName) {
        return new EventScheduleAssignmentProjection() {
            @Override
            public Long getEventId() {
                return eventId;
            }

            @Override
            public Long getPersonId() {
                return personId;
            }

            @Override
            public String getPersonName() {
                return personName;
            }
        };
    }

    private EucharistScaleEventProjection projection(
            String nameMassOrEvent,
            LocalDate eventDate,
            LocalTime eventTime,
            String churchName,
            String ministerNames
    ) {
        return new EucharistScaleEventProjection() {
            @Override
            public Long getEventId() {
                return 1L;
            }

            @Override
            public String getNameMassOrEvent() {
                return nameMassOrEvent;
            }

            @Override
            public LocalDate getEventDate() {
                return eventDate;
            }

            @Override
            public LocalTime getEventTime() {
                return eventTime;
            }

            @Override
            public String getChurchName() {
                return churchName;
            }

            @Override
            public String getMinisterNames() {
                return ministerNames;
            }
        };
    }
}
