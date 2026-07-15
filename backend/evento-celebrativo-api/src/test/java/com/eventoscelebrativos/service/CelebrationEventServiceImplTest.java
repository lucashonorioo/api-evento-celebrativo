package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.CelebrationEventRequestDTO;
import com.eventoscelebrativos.dto.request.CelebrationEventScaleRequestDTO;
import com.eventoscelebrativos.dto.request.CelebrationEventWithScaleRequestDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventResponseDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventScaleResponseDTO;
import com.eventoscelebrativos.dto.response.EventScheduleQueryResponseDTO;
import com.eventoscelebrativos.dto.response.EucharistScaleEventResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.mapper.CelebrationEventMapper;
import com.eventoscelebrativos.mapper.CelebrationEventScaleMapper;
import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.Commentator;
import com.eventoscelebrativos.model.EucharisticMinister;
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
import org.junit.jupiter.api.Test;
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

        verify(repository).deleteById(1L);
    }

    @Test
    void shouldThrowResourceNotFoundWhenDeletingMissingEvent() {
        when(repository.existsById(99L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.deleteEventById(99L));
        verify(repository, never()).deleteById(anyLong());
    }

    @Test
    void shouldThrowDatabaseExceptionWhenDeletingReferencedEvent() {
        when(repository.existsById(1L)).thenReturn(true);
        doThrow(new DataIntegrityViolationException("constraint")).when(repository).flush();

        assertThrows(DatabaseException.class, () -> service.deleteEventById(1L));
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
        when(personRepository.findById(8L)).thenReturn(Optional.of(priest));
        when(personRepository.findById(2L)).thenReturn(Optional.of(reader));
        when(personRepository.findById(4L)).thenReturn(Optional.of(commentator));
        when(personRepository.findById(5L)).thenReturn(Optional.of(ministerOfTheWord));
        when(personRepository.findById(6L)).thenReturn(Optional.of(eucharisticMinister));
        when(repository.save(event)).thenReturn(event);
        when(scaleMapper.toDto(event)).thenReturn(response);

        assertSame(response, service.updateEventScale(1L, request));
        assertEquals(List.of(location), event.getLocations());
        assertEquals(List.of(priest, reader, commentator, ministerOfTheWord, eucharisticMinister), event.getPeople());
    }

    @Test
    void shouldCreateEventWithScale() {
        Location location = location(1L);
        Priest priest = person(new Priest(), 8L, "Padre");
        CelebrationEventScaleResponseDTO response = new CelebrationEventScaleResponseDTO();

        when(locationRepository.findById(1L)).thenReturn(Optional.of(location));
        when(personRepository.findById(8L)).thenReturn(Optional.of(priest));
        when(repository.save(any(CelebrationEvent.class))).thenAnswer(invocation -> {
            CelebrationEvent event = invocation.getArgument(0);
            event.setId(1L);
            return event;
        });
        when(scaleMapper.toDto(any(CelebrationEvent.class))).thenReturn(response);

        assertSame(response, service.createEventWithScale(eventWithScaleRequest()));
        verify(repository).save(argThat(event ->
                "Missa".equals(event.getNameMassOrEvent())
                        && EVENT_DATE.equals(event.getEventDate())
                        && EVENT_TIME.equals(event.getEventTime())
                        && event.getLocations().contains(location)
                        && event.getPeople().contains(priest)
        ));
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
        when(scaleMapper.toDto(event)).thenReturn(new CelebrationEventScaleResponseDTO());

        service.updateEventScale(1L, new CelebrationEventScaleRequestDTO(1L, null, null, null, null, null));

        assertEquals(List.of(newLocation), event.getLocations());
        assertTrue(event.getPeople().isEmpty());
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

    private Location location(Long id) {
        return new Location(id, "Igreja Matriz", "Praça Central");
    }

    private <T extends Person> T person(T person, Long id, String name) {
        person.setId(id);
        person.setName(name);
        person.setPhoneNumber("34" + id);
        return person;
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
