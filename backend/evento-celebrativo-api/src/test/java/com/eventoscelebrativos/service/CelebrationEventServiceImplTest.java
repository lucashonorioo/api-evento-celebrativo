package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.CelebrationEventRequestDTO;
import com.eventoscelebrativos.dto.request.CelebrationEventScaleRequestDTO;
import com.eventoscelebrativos.dto.request.CelebrationEventWithScaleRequestDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventResponseDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventScaleDetailResponseDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventScaleResponseDTO;
import com.eventoscelebrativos.dto.response.EventScheduleAssignmentResponseDTO;
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
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Priest;
import com.eventoscelebrativos.model.Reader;
import com.eventoscelebrativos.projection.EventScheduleAssignmentProjection;
import com.eventoscelebrativos.projection.EventScheduleEventProjection;
import com.eventoscelebrativos.projection.EucharistScaleEventProjection;
import com.eventoscelebrativos.repository.CelebrationEventRepository;
import com.eventoscelebrativos.repository.LocationRepository;
import com.eventoscelebrativos.service.impl.CelebrationEventServiceImpl;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

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
    private PersonMinistryEligibilityResolver personMinistryEligibilityResolver;

    @Mock
    private CelebrationEventMapper mapper;

    @Mock
    private CelebrationEventScaleMapper scaleMapper;

    @Mock
    private CelebrationEventScaleDetailMapper scaleDetailMapper;

    @Mock
    private EventAssignmentCompatibilityService eventAssignmentCompatibilityService;

    @Mock
    private EventAssignmentReadService eventAssignmentReadService;

    @InjectMocks
    private CelebrationEventServiceImpl service;

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
        verifyNoInteractions(eventAssignmentCompatibilityService);
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
    void shouldFindEventByIdWithoutMandatoryAssignmentRead() {
        CelebrationEvent entity = event(1L);
        CelebrationEventResponseDTO response = response(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(mapper.toDto(entity)).thenReturn(response);

        assertSame(response, service.findEventById(1L));

        verify(repository).findById(1L);
        verify(mapper).toDto(entity);
        verifyNoInteractions(eventAssignmentReadService);
        verify(repository, never()).findByIdWithLocations(anyLong());
    }

    @Test
    void shouldPropagateLegacyFailureWithoutParallelFallbackWhenFindingEventById() {
        when(repository.findById(1L)).thenThrow(new IllegalStateException("controlled legacy failure"));

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> service.findEventById(1L));

        assertEquals("controlled legacy failure", exception.getMessage());
        verifyNoInteractions(eventAssignmentReadService);
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
    void shouldThrowBusinessExceptionWhenFindingScaleWithInvalidId() {
        assertAll(
                () -> assertThrows(BusinessException.class, () -> service.findScaleByEventId(null)),
                () -> assertThrows(BusinessException.class, () -> service.findScaleByEventId(0L)),
                () -> assertThrows(BusinessException.class, () -> service.findScaleByEventId(-1L))
        );
    }

    @Test
    void shouldNotModifyEventWhenFindingScale() {
        CelebrationEvent event = event(1L);
        event.getLocations().add(location(1L));
        int locationCount = event.getLocations().size();
        when(repository.findByIdWithLocations(1L)).thenReturn(Optional.of(event));
        when(eventAssignmentReadService.findAllByEventId(1L)).thenReturn(List.of());
        when(scaleDetailMapper.toDto(eq(event), any(Location.class), any(EventAssignmentGroup.class)))
                .thenReturn(detailResponse());

        service.findScaleByEventId(1L);

        assertEquals(locationCount, event.getLocations().size());
        verify(repository, never()).save(any());
    }

    @Test
    void shouldFindEventScaleFromParallelAssignmentsWithoutUsingLegacyPeople() {
        CelebrationEvent event = event(1L);
        event.getLocations().add(location(1L));
        event.getPeople().add(person(new Reader(), 99L, "Legacy Reader Not Used"));
        CelebrationEventScaleDetailResponseDTO response = detailResponse();
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
    }

    @Test
    void shouldGroupParallelEventScaleByAssignmentTypeInsteadOfPersonType() {
        CelebrationEvent event = event(1L);
        event.getLocations().add(location(1L));
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
        when(repository.findByIdWithLocations(1L)).thenReturn(Optional.of(event));
        when(eventAssignmentReadService.findAllByEventId(1L))
                .thenThrow(new IllegalStateException("controlled parallel failure"));

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> service.findScaleByEventId(1L));

        assertEquals("controlled parallel failure", exception.getMessage());
        verify(repository, never()).findByIdWithPeople(anyLong());
        verifyNoInteractions(scaleDetailMapper);
    }

    @Test
    void shouldPreserveNotFoundBehaviorWhenParallelEventScaleEventDoesNotExist() {
        when(repository.findByIdWithLocations(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.findScaleByEventId(99L));
        verify(repository, never()).findByIdWithPeople(anyLong());
        verifyNoInteractions(eventAssignmentReadService, scaleDetailMapper);
    }

    @Test
    void shouldAllowParallelEventScaleWithSamePersonInDifferentAssignmentTypes() {
        CelebrationEvent event = event(1L);
        event.getLocations().add(location(1L));
        when(repository.findByIdWithLocations(1L)).thenReturn(Optional.of(event));
        when(eventAssignmentReadService.findAllByEventId(1L)).thenReturn(List.of(
                snapshot(100L, 1L, 10L, EventAssignmentType.READER, "Pessoa", "reader"),
                snapshot(101L, 1L, 10L, EventAssignmentType.COMMENTATOR, "Pessoa", "reader")
        ));
        when(scaleDetailMapper.toDto(eq(event), any(Location.class), any(EventAssignmentGroup.class)))
                .thenReturn(detailResponse());

        service.findScaleByEventId(1L);

        ArgumentCaptor<EventAssignmentGroup> groupCaptor = ArgumentCaptor.forClass(EventAssignmentGroup.class);
        verify(scaleDetailMapper).toDto(eq(event), any(Location.class), groupCaptor.capture());
        EventAssignmentGroup group = groupCaptor.getValue();
        assertEquals(List.of(10L), group.readers().stream().map(EventAssignmentSnapshot::personId).toList());
        assertEquals(List.of(10L), group.commentators().stream().map(EventAssignmentSnapshot::personId).toList());
    }

    @Test
    void shouldRejectParallelEventScaleWithDuplicatedPersonAndTypePair() {
        CelebrationEvent event = event(1L);
        when(repository.findByIdWithLocations(1L)).thenReturn(Optional.of(event));
        when(eventAssignmentReadService.findAllByEventId(1L)).thenReturn(List.of(
                snapshot(100L, 1L, 10L, EventAssignmentType.READER, "Pessoa", "reader"),
                snapshot(101L, 1L, 10L, EventAssignmentType.READER, "Pessoa", "reader")
        ));

        assertThrows(BusinessException.class, () -> service.findScaleByEventId(1L));
        verify(repository, never()).findByIdWithPeople(anyLong());
        verifyNoInteractions(scaleDetailMapper);
    }

    @Test
    void shouldRejectParallelEventScaleWithMoreThanOnePriest() {
        CelebrationEvent event = event(1L);
        when(repository.findByIdWithLocations(1L)).thenReturn(Optional.of(event));
        when(eventAssignmentReadService.findAllByEventId(1L)).thenReturn(List.of(
                snapshot(100L, 1L, 10L, EventAssignmentType.PRIEST, "Padre A", "priest"),
                snapshot(101L, 1L, 11L, EventAssignmentType.PRIEST, "Padre B", "priest")
        ));

        assertThrows(BusinessException.class, () -> service.findScaleByEventId(1L));
        verify(repository, never()).findByIdWithPeople(anyLong());
        verifyNoInteractions(scaleDetailMapper);
    }

    @Test
    void shouldRejectParallelEventScaleWithMissingAssignmentType() {
        CelebrationEvent event = event(1L);
        when(repository.findByIdWithLocations(1L)).thenReturn(Optional.of(event));
        when(eventAssignmentReadService.findAllByEventId(1L)).thenReturn(List.of(
                snapshot(100L, 1L, 10L, null, "Pessoa", "reader")
        ));

        assertThrows(BusinessException.class, () -> service.findScaleByEventId(1L));
        verify(repository, never()).findByIdWithPeople(anyLong());
        verifyNoInteractions(scaleDetailMapper);
    }

    @Test
    void shouldRejectParallelEventScaleWithAssignmentFromAnotherEvent() {
        CelebrationEvent event = event(1L);
        when(repository.findByIdWithLocations(1L)).thenReturn(Optional.of(event));
        when(eventAssignmentReadService.findAllByEventId(1L)).thenReturn(List.of(
                snapshot(100L, 2L, 10L, EventAssignmentType.READER, "Pessoa", "reader")
        ));

        assertThrows(BusinessException.class, () -> service.findScaleByEventId(1L));
        verify(repository, never()).findByIdWithPeople(anyLong());
        verifyNoInteractions(scaleDetailMapper);
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
    void shouldFindEucharistScaleFromParallelAssignmentsWithoutLegacyRead() {
        PageRequest pageable = PageRequest.of(1, 2);
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 8, 31);
        EucharistScaleEventProjection first = projection(1L, "Missa A", EVENT_DATE, EVENT_TIME, "Igreja Matriz", null);
        EucharistScaleEventProjection second = projection(2L, "Missa B", EVENT_DATE.plusDays(1), EVENT_TIME, "Capela", null);
        when(repository.findEucharistScaleByAssignments(pageable, startDate, endDate))
                .thenReturn(new PageImpl<>(List.of(first, second), pageable, 5));
        when(repository.findEucharistScaleAssignmentsByEventIds(List.of(1L, 2L)))
                .thenReturn(List.of(
                        scheduleAssignment(1L, 10L, "Mariana"),
                        scheduleAssignment(1L, 11L, "Carlos"),
                        scheduleAssignment(2L, 12L, "Pessoa de outro subtipo")
                ));

        Page<EucharistScaleEventResponseDTO> result = service.findEucharistScale(pageable, startDate, endDate);

        assertEquals(5, result.getTotalElements());
        assertEquals(1, result.getNumber());
        assertEquals(List.of("Mariana", "Carlos"), result.getContent().get(0).getNameMinisters());
        assertEquals(List.of("Pessoa de outro subtipo"), result.getContent().get(1).getNameMinisters());
        verify(repository).findEucharistScaleByAssignments(pageable, startDate, endDate);
        verify(repository).findEucharistScaleAssignmentsByEventIds(List.of(1L, 2L));
        verifyNoInteractions(eventAssignmentReadService);
    }

    @Test
    void shouldReturnEmptyEucharistScalePageFromParallelSourceWithoutAssignmentBatch() {
        PageRequest pageable = PageRequest.of(0, 10);
        LocalDate startDate = LocalDate.of(2030, 1, 1);
        LocalDate endDate = LocalDate.of(2030, 1, 31);
        when(repository.findEucharistScaleByAssignments(pageable, startDate, endDate))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        Page<EucharistScaleEventResponseDTO> result = service.findEucharistScale(pageable, startDate, endDate);

        assertTrue(result.isEmpty());
        assertEquals(0, result.getTotalElements());
        verify(repository, never()).findEucharistScaleAssignmentsByEventIds(anyList());
    }

    @Test
    void shouldPropagateParallelFailureWithoutLegacyFallbackWhenFindingEucharistScale() {
        PageRequest pageable = PageRequest.of(0, 10);
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 8, 31);
        when(repository.findEucharistScaleByAssignments(pageable, startDate, endDate))
                .thenThrow(new IllegalStateException("controlled parallel failure"));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.findEucharistScale(pageable, startDate, endDate)
        );

        assertEquals("controlled parallel failure", exception.getMessage());
        verify(repository, never()).findEucharistScaleAssignmentsByEventIds(anyList());
        verifyNoInteractions(eventAssignmentReadService);
    }

    @Test
    void shouldPropagateParallelAssignmentBatchFailureWithoutLegacyFallbackWhenFindingEucharistScale() {
        PageRequest pageable = PageRequest.of(0, 10);
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 8, 31);
        EucharistScaleEventProjection projection = projection("Missa", EVENT_DATE, EVENT_TIME, "Igreja Matriz", null);
        when(repository.findEucharistScaleByAssignments(pageable, startDate, endDate))
                .thenReturn(new PageImpl<>(List.of(projection), pageable, 1));
        when(repository.findEucharistScaleAssignmentsByEventIds(List.of(1L)))
                .thenThrow(new IllegalStateException("controlled batch failure"));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.findEucharistScale(pageable, startDate, endDate)
        );

        assertEquals("controlled batch failure", exception.getMessage());
        verifyNoInteractions(eventAssignmentReadService);
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
        when(repository.findEventScheduleEventsByAssignments(any(), eq(EVENT_DATE), eq(EVENT_DATE), eq(EventAssignmentType.READER.name()), eq(false)))
                .thenReturn(new PageImpl<>(List.of(scheduleEvent(1L)), PageRequest.of(0, 10), 1));
        when(repository.findEventScheduleAssignmentsByAssignmentType(List.of(1L), EventAssignmentType.READER.name()))
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
    void shouldMapEventScheduleWithSeveralAssignments() {
        when(repository.findEventScheduleEventsByAssignments(any(), eq(EVENT_DATE), eq(EVENT_DATE), eq(EventAssignmentType.EUCHARISTIC_MINISTER.name()), eq(false)))
                .thenReturn(new PageImpl<>(List.of(scheduleEvent(1L)), PageRequest.of(0, 10), 1));
        when(repository.findEventScheduleAssignmentsByAssignmentType(List.of(1L), EventAssignmentType.EUCHARISTIC_MINISTER.name()))
                .thenReturn(List.of(
                        scheduleAssignment(1L, 10L, "Ana"),
                        scheduleAssignment(1L, 11L, "Bruno")
                ));

        Page<EventScheduleQueryResponseDTO> result =
                service.findEventSchedules(EVENT_DATE, EVENT_DATE, EventScheduleType.EUCHARISTIC_MINISTER, 0, 10, false);

        assertEquals(2, result.getContent().get(0).getAssignments().size());
    }

    @Test
    void shouldFindEventSchedulesFromParallelAssignmentsWithoutLegacyRead() {
        when(repository.findEventScheduleEventsByAssignments(
                any(),
                eq(EVENT_DATE),
                eq(EVENT_DATE),
                eq(EventAssignmentType.READER.name()),
                eq(false)
        )).thenReturn(new PageImpl<>(List.of(scheduleEvent(1L), scheduleEvent(2L)), PageRequest.of(1, 2), 5));
        when(repository.findEventScheduleAssignmentsByAssignmentType(List.of(1L, 2L), EventAssignmentType.READER.name()))
                .thenReturn(List.of(
                        scheduleAssignment(1L, 10L, "Ana"),
                        scheduleAssignment(2L, 11L, "Pessoa de outro subtipo")
                ));

        Page<EventScheduleQueryResponseDTO> result =
                service.findEventSchedules(EVENT_DATE, EVENT_DATE, EventScheduleType.READER, 1, 2, false);

        assertEquals(5, result.getTotalElements());
        assertEquals(1, result.getNumber());
        assertEquals(EventScheduleType.READER, result.getContent().get(0).getAssignmentType());
        assertEquals(List.of(10L), result.getContent().get(0).getAssignments().stream()
                .map(EventScheduleAssignmentResponseDTO::getPersonId)
                .toList());
        assertEquals(List.of(11L), result.getContent().get(1).getAssignments().stream()
                .map(EventScheduleAssignmentResponseDTO::getPersonId)
                .toList());
        verify(repository).findEventScheduleEventsByAssignments(
                any(),
                eq(EVENT_DATE),
                eq(EVENT_DATE),
                eq(EventAssignmentType.READER.name()),
                eq(false)
        );
        verify(repository).findEventScheduleAssignmentsByAssignmentType(List.of(1L, 2L), EventAssignmentType.READER.name());
        verifyNoInteractions(eventAssignmentReadService);
    }

    @Test
    void shouldPassIncludeUnassignedToParallelMonthlyScheduleQuery() {
        when(repository.findEventScheduleEventsByAssignments(
                any(),
                eq(EVENT_DATE),
                eq(EVENT_DATE),
                eq(EventAssignmentType.PRIEST.name()),
                eq(true)
        )).thenReturn(new PageImpl<>(List.of(scheduleEvent(1L)), PageRequest.of(0, 10), 1));
        when(repository.findEventScheduleAssignmentsByAssignmentType(List.of(1L), EventAssignmentType.PRIEST.name()))
                .thenReturn(List.of());

        Page<EventScheduleQueryResponseDTO> result =
                service.findEventSchedules(EVENT_DATE, EVENT_DATE, EventScheduleType.PRIEST, 0, 10, true);

        assertTrue(result.getContent().get(0).getAssignments().isEmpty());
        verify(repository).findEventScheduleEventsByAssignments(
                any(),
                eq(EVENT_DATE),
                eq(EVENT_DATE),
                eq(EventAssignmentType.PRIEST.name()),
                eq(true)
        );
    }

    @Test
    void shouldReturnEmptyEventSchedulePageFromParallelSourceWithoutAssignmentBatch() {
        when(repository.findEventScheduleEventsByAssignments(
                any(),
                eq(EVENT_DATE),
                eq(EVENT_DATE),
                eq(EventAssignmentType.READER.name()),
                eq(false)
        )).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        Page<EventScheduleQueryResponseDTO> result =
                service.findEventSchedules(EVENT_DATE, EVENT_DATE, EventScheduleType.READER, 0, 10, false);

        assertTrue(result.isEmpty());
        verify(repository, never()).findEventScheduleAssignmentsByAssignmentType(anyList(), anyString());
    }

    @Test
    void shouldPropagateParallelFailureWithoutLegacyFallbackWhenFindingEventSchedules() {
        when(repository.findEventScheduleEventsByAssignments(
                any(),
                eq(EVENT_DATE),
                eq(EVENT_DATE),
                eq(EventAssignmentType.READER.name()),
                eq(false)
        )).thenThrow(new IllegalStateException("controlled parallel failure"));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.findEventSchedules(EVENT_DATE, EVENT_DATE, EventScheduleType.READER, 0, 10, false)
        );

        assertEquals("controlled parallel failure", exception.getMessage());
        verify(repository, never()).findEventScheduleAssignmentsByAssignmentType(anyList(), anyString());
        verifyNoInteractions(eventAssignmentReadService);
    }

    @Test
    void shouldPropagateParallelAssignmentBatchFailureWithoutLegacyFallbackWhenFindingEventSchedules() {
        when(repository.findEventScheduleEventsByAssignments(
                any(),
                eq(EVENT_DATE),
                eq(EVENT_DATE),
                eq(EventAssignmentType.READER.name()),
                eq(false)
        )).thenReturn(new PageImpl<>(List.of(scheduleEvent(1L)), PageRequest.of(0, 10), 1));
        when(repository.findEventScheduleAssignmentsByAssignmentType(List.of(1L), EventAssignmentType.READER.name()))
                .thenThrow(new IllegalStateException("controlled batch failure"));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.findEventSchedules(EVENT_DATE, EVENT_DATE, EventScheduleType.READER, 0, 10, false)
        );

        assertEquals("controlled batch failure", exception.getMessage());
        verifyNoInteractions(eventAssignmentReadService);
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
        verifyNoInteractions(eventAssignmentCompatibilityService);
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

        when(repository.findById(1L)).thenReturn(Optional.of(event));
        when(locationRepository.findById(1L)).thenReturn(Optional.of(location));
        when(personMinistryEligibilityResolver.resolve(any())).thenReturn(List.of(
                eligible(priest, MinistryType.PRIEST),
                eligible(reader, MinistryType.READER),
                eligible(commentator, MinistryType.COMMENTATOR),
                eligible(ministerOfTheWord, MinistryType.MINISTER_OF_THE_WORD),
                eligible(eucharisticMinister, MinistryType.EUCHARISTIC_MINISTER)
        ));
        when(scaleMapper.toDto(eq(event), any(EventScaleAssignmentPlan.class))).thenReturn(response);

        assertSame(response, service.updateEventScale(1L, request));
        assertEquals(List.of(location), event.getLocations());

        List<EventAssignmentTarget> expectedTargets = List.of(
                new EventAssignmentTarget(priest, EventAssignmentType.PRIEST),
                new EventAssignmentTarget(reader, EventAssignmentType.READER),
                new EventAssignmentTarget(commentator, EventAssignmentType.COMMENTATOR),
                new EventAssignmentTarget(ministerOfTheWord, EventAssignmentType.MINISTER_OF_THE_WORD),
                new EventAssignmentTarget(eucharisticMinister, EventAssignmentType.EUCHARISTIC_MINISTER)
        );
        verify(eventAssignmentCompatibilityService).synchronizeAssignments(event, expectedTargets);
        assertTrue(event.getPeople().isEmpty());
    }

    @Test
    void shouldCreateEventWithScale() {
        Location location = location(1L);
        Priest priest = person(new Priest(), 8L, "Padre");
        CelebrationEventScaleResponseDTO response = new CelebrationEventScaleResponseDTO();

        when(locationRepository.findById(1L)).thenReturn(Optional.of(location));
        when(personMinistryEligibilityResolver.resolve(any())).thenReturn(List.of(eligible(priest, MinistryType.PRIEST)));
        when(repository.save(any(CelebrationEvent.class))).thenAnswer(invocation -> {
            CelebrationEvent event = invocation.getArgument(0);
            event.setId(1L);
            return event;
        });
        when(scaleMapper.toDto(any(CelebrationEvent.class), any(EventScaleAssignmentPlan.class))).thenReturn(response);

        assertSame(response, service.createEventWithScale(eventWithScaleRequest()));
        verify(repository).save(argThat(event ->
                "Missa".equals(event.getNameMassOrEvent())
                        && EVENT_DATE.equals(event.getEventDate())
                        && EVENT_TIME.equals(event.getEventTime())
                        && event.getLocations().contains(location)
        ));

        List<EventAssignmentTarget> expectedTargets = List.of(new EventAssignmentTarget(priest, EventAssignmentType.PRIEST));
        verify(eventAssignmentCompatibilityService).synchronizeAssignments(any(CelebrationEvent.class), eq(expectedTargets));

        InOrder inOrder = inOrder(repository, eventAssignmentCompatibilityService);
        inOrder.verify(repository).save(any(CelebrationEvent.class));
        inOrder.verify(eventAssignmentCompatibilityService).synchronizeAssignments(any(), any());
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
        when(personMinistryEligibilityResolver.resolve(any())).thenReturn(List.of());
        when(scaleMapper.toDto(eq(event), any(EventScaleAssignmentPlan.class))).thenReturn(new CelebrationEventScaleResponseDTO());

        service.updateEventScale(1L, new CelebrationEventScaleRequestDTO(1L, null, null, null, null, null));

        assertEquals(List.of(newLocation), event.getLocations());
        verify(eventAssignmentCompatibilityService).synchronizeAssignments(event, List.of());
        assertTrue(event.getPeople().isEmpty());
    }

    @Test
    void shouldNotClearLegacyMirrorWhenOfficialAssignmentWriteFailsOnUpdate() {
        CelebrationEvent event = event(1L);
        Location location = location(1L);
        Priest priest = person(new Priest(), 8L, "Padre");

        when(repository.findById(1L)).thenReturn(Optional.of(event));
        when(locationRepository.findById(1L)).thenReturn(Optional.of(location));
        when(personMinistryEligibilityResolver.resolve(any())).thenReturn(List.of(eligible(priest, MinistryType.PRIEST)));
        RuntimeException failure = new IllegalStateException("assignment write-through failed");
        doThrow(failure).when(eventAssignmentCompatibilityService).synchronizeAssignments(any(), any());

        RuntimeException result = assertThrows(RuntimeException.class, () ->
                service.updateEventScale(1L, new CelebrationEventScaleRequestDTO(1L, 8L, null, null, null, null)));

        assertSame(failure, result);
        assertEquals(List.of(), event.getPeople());
    }

    @Test
    void shouldNotSaveEventWithScaleWhenOfficialAssignmentWriteFailsOnCreate() {
        Location location = location(1L);
        Priest priest = person(new Priest(), 8L, "Padre");

        when(locationRepository.findById(1L)).thenReturn(Optional.of(location));
        when(personMinistryEligibilityResolver.resolve(any())).thenReturn(List.of(eligible(priest, MinistryType.PRIEST)));
        when(repository.save(any(CelebrationEvent.class))).thenAnswer(invocation -> {
            CelebrationEvent event = invocation.getArgument(0);
            event.setId(1L);
            return event;
        });
        RuntimeException failure = new IllegalStateException("assignment write-through failed");
        doThrow(failure).when(eventAssignmentCompatibilityService).synchronizeAssignments(any(), any());

        RuntimeException result = assertThrows(RuntimeException.class, () ->
                service.createEventWithScale(eventWithScaleRequest()));

        assertSame(failure, result);
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
        when(personMinistryEligibilityResolver.resolve(any()))
                .thenReturn(List.of(personNotFound(8L, MinistryType.PRIEST)));

        assertThrows(ResourceNotFoundException.class, () -> service.updateEventScale(1L, scaleRequest()));
    }

    @Test
    void shouldAcceptScalePersonWithActiveMinistryRegardlessOfLegacySubtype() {
        CelebrationEvent event = event(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(event));
        when(locationRepository.findById(1L)).thenReturn(Optional.of(location(1L)));
        Reader personWithPriestMinistry = person(new Reader(), 8L, "Leitor");
        when(personMinistryEligibilityResolver.resolve(any()))
                .thenReturn(List.of(eligible(personWithPriestMinistry, MinistryType.PRIEST)));
        when(scaleMapper.toDto(eq(event), any(EventScaleAssignmentPlan.class))).thenReturn(new CelebrationEventScaleResponseDTO());

        service.updateEventScale(1L, new CelebrationEventScaleRequestDTO(1L, 8L, null, null, null, null));

        verify(eventAssignmentCompatibilityService).synchronizeAssignments(
                event,
                List.of(new EventAssignmentTarget(personWithPriestMinistry, EventAssignmentType.PRIEST))
        );
    }

    @Test
    void shouldThrowBusinessExceptionWhenScalePersonHasCompatibleLegacyTypeButNoActiveMinistry() {
        when(repository.findById(1L)).thenReturn(Optional.of(event(1L)));
        when(locationRepository.findById(1L)).thenReturn(Optional.of(location(1L)));
        Priest priestWithoutActiveMinistry = person(new Priest(), 8L, "Padre");
        when(personMinistryEligibilityResolver.resolve(any()))
                .thenReturn(List.of(ministryNotAssigned(priestWithoutActiveMinistry, MinistryType.PRIEST)));

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
        when(personMinistryEligibilityResolver.resolve(any())).thenReturn(List.of());
        when(scaleMapper.toDto(eq(event), any(EventScaleAssignmentPlan.class))).thenReturn(new CelebrationEventScaleResponseDTO());

        service.updateEventScale(1L, new CelebrationEventScaleRequestDTO(1L, null, null, null, null, null));

        assertEquals(List.of(location), event.getLocations());
        assertTrue(event.getPeople().isEmpty());
    }

    @Test
    void shouldThrowBusinessExceptionWhenScaleListHasDuplicatedIds() {
        when(repository.findById(1L)).thenReturn(Optional.of(event(1L)));
        when(locationRepository.findById(1L)).thenReturn(Optional.of(location(1L)));

        CelebrationEventScaleRequestDTO request =
                new CelebrationEventScaleRequestDTO(1L, null, List.of(2L, 2L), null, null, null);

        assertThrows(BusinessException.class, () -> service.updateEventScale(1L, request));
        verifyNoInteractions(personMinistryEligibilityResolver);
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
        when(personMinistryEligibilityResolver.resolve(any())).thenReturn(List.of(eligible(priest, MinistryType.PRIEST)));
        when(scaleMapper.toDto(eq(event), any(EventScaleAssignmentPlan.class))).thenReturn(new CelebrationEventScaleResponseDTO());

        service.updateEventScale(1L, new CelebrationEventScaleRequestDTO(1L, 8L, null, null, null, null));

        assertEquals("encoded", priest.getPassword());
        assertEquals("34999999999", priest.getPhoneNumber());
    }

    @Test
    void shouldNotCreateEventWithScaleWhenScaleIsInvalid() {
        when(locationRepository.findById(1L)).thenReturn(Optional.of(location(1L)));
        when(personMinistryEligibilityResolver.resolve(any()))
                .thenReturn(List.of(ministryNotAssigned(person(new Reader(), 8L, "Leitor"), MinistryType.PRIEST)));

        assertThrows(BusinessException.class, () -> service.createEventWithScale(eventWithScaleRequest()));
        verify(repository, never()).save(any());
        verifyNoInteractions(eventAssignmentCompatibilityService);
    }

    @Test
    void shouldAllowSamePersonInMultipleAssignmentTypesWhenEligibleForBoth() {
        CelebrationEvent event = event(1L);
        Location location = location(1L);
        Priest priestAndReader = person(new Priest(), 8L, "Padre Leitor");
        CelebrationEventScaleResponseDTO response = new CelebrationEventScaleResponseDTO();

        when(repository.findById(1L)).thenReturn(Optional.of(event));
        when(locationRepository.findById(1L)).thenReturn(Optional.of(location));
        when(personMinistryEligibilityResolver.resolve(any())).thenReturn(List.of(
                eligible(priestAndReader, MinistryType.PRIEST),
                eligible(priestAndReader, MinistryType.READER)
        ));
        when(scaleMapper.toDto(eq(event), any(EventScaleAssignmentPlan.class))).thenReturn(response);

        CelebrationEventScaleRequestDTO request =
                new CelebrationEventScaleRequestDTO(1L, 8L, List.of(8L), null, null, null);

        assertSame(response, service.updateEventScale(1L, request));

        List<EventAssignmentTarget> expectedTargets = List.of(
                new EventAssignmentTarget(priestAndReader, EventAssignmentType.PRIEST),
                new EventAssignmentTarget(priestAndReader, EventAssignmentType.READER)
        );
        verify(eventAssignmentCompatibilityService).synchronizeAssignments(event, expectedTargets);
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

    private Location location(Long id) {
        return new Location(id, "Igreja Matriz", "Praça Central");
    }

    private <T extends Person> T person(T person, Long id, String name) {
        person.setId(id);
        person.setName(name);
        person.setPhoneNumber("34" + id);
        return person;
    }

    private ScaleParticipantEligibility eligible(Person person, MinistryType ministryType) {
        return new ScaleParticipantEligibility(person.getId(), ministryType, true, true, person);
    }

    private ScaleParticipantEligibility personNotFound(Long personId, MinistryType ministryType) {
        return new ScaleParticipantEligibility(personId, ministryType, false, false, null);
    }

    private ScaleParticipantEligibility ministryNotAssigned(Person person, MinistryType ministryType) {
        return new ScaleParticipantEligibility(person.getId(), ministryType, true, false, person);
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
        return projection(1L, nameMassOrEvent, eventDate, eventTime, churchName, ministerNames);
    }

    private EucharistScaleEventProjection projection(
            Long eventId,
            String nameMassOrEvent,
            LocalDate eventDate,
            LocalTime eventTime,
            String churchName,
            String ministerNames
    ) {
        return new EucharistScaleEventProjection() {
            @Override
            public Long getEventId() {
                return eventId;
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
