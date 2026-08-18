package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.response.CelebrationEventResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.LifecycleConflictException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.mapper.CelebrationEventMapper;
import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.CelebrationEventStatus;
import com.eventoscelebrativos.model.EventAssignment;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.repository.CelebrationEventRepository;
import com.eventoscelebrativos.repository.EventAssignmentRepository;
import com.eventoscelebrativos.service.impl.AdminRoleMutexService;
import com.eventoscelebrativos.service.impl.CelebrationEventLifecycleServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CelebrationEventLifecycleServiceImplTest {

    private static final LocalDateTime EVENT_START_AT = LocalDateTime.of(2026, 8, 15, 19, 30);
    private static final LocalDateTime EVENT_END_AT = LocalDateTime.of(2026, 8, 15, 20, 30);
    private static final ZoneId APPLICATION_ZONE = ZoneId.of("America/Sao_Paulo");

    @Mock
    private CelebrationEventRepository celebrationEventRepository;

    @Mock
    private EventAssignmentRepository eventAssignmentRepository;

    @Mock
    private CelebrationEventMapper celebrationEventMapper;

    @Mock
    private PersonMinistryEligibilityResolver personMinistryEligibilityResolver;

    @Mock
    private PersonUnavailabilityConflictService personUnavailabilityConflictService;

    @Mock
    private AdminRoleMutexService adminRoleMutexService;

    @Mock
    private ScheduleConflictNotificationService scheduleConflictNotificationService;

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-08-15T21:00:00Z"), APPLICATION_ZONE);

    @InjectMocks
    private CelebrationEventLifecycleServiceImpl service;

    @Test
    void shouldCancelActiveFutureEvent() {
        CelebrationEvent event = activeEvent(1L);
        EventAssignment assignment = assignment(event, person(8L), EventAssignmentType.PRIEST);
        CelebrationEventResponseDTO response = response(1L, CelebrationEventStatus.CANCELLED);

        when(celebrationEventRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(event));
        when(eventAssignmentRepository.findAllByEventIdForUpdate(1L)).thenReturn(List.of(assignment));
        when(celebrationEventRepository.save(event)).thenReturn(event);
        when(celebrationEventMapper.toDto(event)).thenReturn(response);

        assertSame(response, service.cancel(1L));
        assertEquals(CelebrationEventStatus.CANCELLED, event.getStatus());

        InOrder inOrder = inOrder(celebrationEventRepository, eventAssignmentRepository, adminRoleMutexService, personUnavailabilityConflictService);
        inOrder.verify(celebrationEventRepository).findByIdForUpdate(1L);
        inOrder.verify(eventAssignmentRepository).findAllByEventIdForUpdate(1L);
        inOrder.verify(adminRoleMutexService).lockAdminRole();
        inOrder.verify(personUnavailabilityConflictService).lockPersonsInOrder(List.of(8L));
        verify(scheduleConflictNotificationService).reconcile(1L, 8L, LocalDateTime.of(2026, 8, 15, 18, 0));
    }

    @Test
    void shouldBeIdempotentWhenCancellingAlreadyCancelledEvent() {
        CelebrationEvent event = cancelledEvent(1L);
        CelebrationEventResponseDTO response = response(1L, CelebrationEventStatus.CANCELLED);

        when(celebrationEventRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(event));
        when(celebrationEventMapper.toDto(event)).thenReturn(response);

        assertSame(response, service.cancel(1L));

        verifyNoInteractions(eventAssignmentRepository, adminRoleMutexService, personUnavailabilityConflictService, scheduleConflictNotificationService);
        verify(celebrationEventRepository, never()).save(any());
    }

    @Test
    void shouldBeIdempotentWhenCancellingAlreadyCancelledEventEvenIfEventAlreadyStarted() {
        CelebrationEvent event = cancelledEvent(1L);
        event.setStartAt(LocalDateTime.of(2020, 1, 1, 0, 0));
        event.setEndAt(LocalDateTime.of(2020, 1, 1, 1, 0));
        CelebrationEventResponseDTO response = response(1L, CelebrationEventStatus.CANCELLED);

        when(celebrationEventRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(event));
        when(celebrationEventMapper.toDto(event)).thenReturn(response);

        assertSame(response, service.cancel(1L));
    }

    @Test
    void shouldRejectCancelWhenEventAlreadyStarted() {
        CelebrationEvent event = activeEvent(1L);
        event.setStartAt(LocalDateTime.of(2026, 8, 15, 18, 0));
        event.setEndAt(LocalDateTime.of(2026, 8, 15, 19, 0));

        when(celebrationEventRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(event));
        when(eventAssignmentRepository.findAllByEventIdForUpdate(1L)).thenReturn(List.of());

        LifecycleConflictException exception = assertThrows(LifecycleConflictException.class, () -> service.cancel(1L));
        assertEquals("CELEBRATION_EVENT_LIFECYCLE_ALREADY_STARTED", exception.getErrorCode());
        assertEquals(CelebrationEventStatus.ACTIVE, event.getStatus());
        verify(celebrationEventRepository, never()).save(any());
        verifyNoInteractions(scheduleConflictNotificationService);
    }

    @Test
    void shouldRejectCancelWhenEventNotFound() {
        when(celebrationEventRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.cancel(99L));
    }

    @Test
    void shouldRejectCancelWithInvalidId() {
        assertThrows(BusinessException.class, () -> service.cancel(0L));
        assertThrows(BusinessException.class, () -> service.cancel(null));
        verifyNoInteractions(celebrationEventRepository);
    }

    @Test
    void shouldReactivateCancelledFutureEventWithValidScale() {
        CelebrationEvent event = cancelledEvent(1L);
        Person priest = person(8L);
        EventAssignment assignment = assignment(event, priest, EventAssignmentType.PRIEST);
        CelebrationEventResponseDTO response = response(1L, CelebrationEventStatus.ACTIVE);

        when(celebrationEventRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(event));
        when(eventAssignmentRepository.findAllByEventIdForUpdate(1L)).thenReturn(List.of(assignment));
        when(personMinistryEligibilityResolver.resolve(any()))
                .thenReturn(List.of(new ScaleParticipantEligibility(8L, MinistryType.PRIEST, true, true, priest)));
        when(celebrationEventRepository.save(event)).thenReturn(event);
        when(celebrationEventMapper.toDto(event)).thenReturn(response);

        assertSame(response, service.reactivate(1L));
        assertEquals(CelebrationEventStatus.ACTIVE, event.getStatus());

        verify(adminRoleMutexService).lockAdminRole();
        verify(personUnavailabilityConflictService).lockPersonsInOrder(List.of(8L));
        verify(scheduleConflictNotificationService).reconcile(1L, 8L, LocalDateTime.of(2026, 8, 15, 18, 0));
    }

    @Test
    void shouldBeIdempotentWhenReactivatingAlreadyActiveEvent() {
        CelebrationEvent event = activeEvent(1L);
        CelebrationEventResponseDTO response = response(1L, CelebrationEventStatus.ACTIVE);

        when(celebrationEventRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(event));
        when(celebrationEventMapper.toDto(event)).thenReturn(response);

        assertSame(response, service.reactivate(1L));

        verifyNoInteractions(eventAssignmentRepository, adminRoleMutexService, personUnavailabilityConflictService,
                scheduleConflictNotificationService, personMinistryEligibilityResolver);
        verify(celebrationEventRepository, never()).save(any());
    }

    @Test
    void shouldRejectReactivateWhenEventAlreadyStarted() {
        CelebrationEvent event = cancelledEvent(1L);
        event.setStartAt(LocalDateTime.of(2026, 8, 15, 18, 0));
        event.setEndAt(LocalDateTime.of(2026, 8, 15, 19, 0));

        when(celebrationEventRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(event));
        when(eventAssignmentRepository.findAllByEventIdForUpdate(1L)).thenReturn(List.of());

        LifecycleConflictException exception = assertThrows(LifecycleConflictException.class, () -> service.reactivate(1L));
        assertEquals("CELEBRATION_EVENT_LIFECYCLE_ALREADY_STARTED", exception.getErrorCode());
        assertEquals(CelebrationEventStatus.CANCELLED, event.getStatus());
        verify(celebrationEventRepository, never()).save(any());
    }

    @Test
    void shouldRejectReactivateWhenAssignedPersonIsInactive() {
        CelebrationEvent event = cancelledEvent(1L);
        Person inactivePriest = person(8L);
        inactivePriest.setActive(false);
        EventAssignment assignment = assignment(event, inactivePriest, EventAssignmentType.PRIEST);

        when(celebrationEventRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(event));
        when(eventAssignmentRepository.findAllByEventIdForUpdate(1L)).thenReturn(List.of(assignment));
        when(personMinistryEligibilityResolver.resolve(any()))
                .thenReturn(List.of(new ScaleParticipantEligibility(8L, MinistryType.PRIEST, true, true, inactivePriest)));

        LifecycleConflictException exception = assertThrows(LifecycleConflictException.class, () -> service.reactivate(1L));
        assertEquals("CELEBRATION_EVENT_REACTIVATION_INVALID_SCALE", exception.getErrorCode());
        assertEquals(CelebrationEventStatus.CANCELLED, event.getStatus());
        verify(celebrationEventRepository, never()).save(any());
        verifyNoInteractions(scheduleConflictNotificationService);
    }

    @Test
    void shouldRejectReactivateWhenAssignedPersonLostActiveMinistry() {
        CelebrationEvent event = cancelledEvent(1L);
        Person reader = person(2L);
        EventAssignment assignment = assignment(event, reader, EventAssignmentType.READER);

        when(celebrationEventRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(event));
        when(eventAssignmentRepository.findAllByEventIdForUpdate(1L)).thenReturn(List.of(assignment));
        when(personMinistryEligibilityResolver.resolve(any()))
                .thenReturn(List.of(new ScaleParticipantEligibility(2L, MinistryType.READER, true, false, reader)));

        LifecycleConflictException exception = assertThrows(LifecycleConflictException.class, () -> service.reactivate(1L));
        assertEquals("CELEBRATION_EVENT_REACTIVATION_INVALID_SCALE", exception.getErrorCode());
        assertEquals(CelebrationEventStatus.CANCELLED, event.getStatus());
    }

    @Test
    void shouldRejectReactivateWhenAssignedPersonNoLongerExists() {
        CelebrationEvent event = cancelledEvent(1L);
        Person ghost = person(2L);
        EventAssignment assignment = assignment(event, ghost, EventAssignmentType.READER);

        when(celebrationEventRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(event));
        when(eventAssignmentRepository.findAllByEventIdForUpdate(1L)).thenReturn(List.of(assignment));
        when(personMinistryEligibilityResolver.resolve(any())).thenReturn(List.of());

        assertThrows(LifecycleConflictException.class, () -> service.reactivate(1L));
    }

    @Test
    void shouldReactivateEventWithoutAssignmentsWithoutCallingEligibilityResolver() {
        CelebrationEvent event = cancelledEvent(1L);
        CelebrationEventResponseDTO response = response(1L, CelebrationEventStatus.ACTIVE);

        when(celebrationEventRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(event));
        when(eventAssignmentRepository.findAllByEventIdForUpdate(1L)).thenReturn(List.of());
        when(celebrationEventRepository.save(event)).thenReturn(event);
        when(celebrationEventMapper.toDto(event)).thenReturn(response);

        assertSame(response, service.reactivate(1L));

        verifyNoInteractions(personMinistryEligibilityResolver, personUnavailabilityConflictService);
        verify(adminRoleMutexService).lockAdminRole();
    }

    @Test
    void shouldRejectReactivateWhenEventNotFound() {
        when(celebrationEventRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.reactivate(99L));
    }

    @Test
    void shouldRejectReactivateWithInvalidId() {
        assertThrows(BusinessException.class, () -> service.reactivate(-1L));
        verifyNoInteractions(celebrationEventRepository);
    }

    private CelebrationEvent activeEvent(Long id) {
        CelebrationEvent event = new CelebrationEvent(id, "Missa", EVENT_START_AT, EVENT_END_AT, true);
        return event;
    }

    private CelebrationEvent cancelledEvent(Long id) {
        CelebrationEvent event = activeEvent(id);
        event.cancel();
        return event;
    }

    private EventAssignment assignment(CelebrationEvent event, Person person, EventAssignmentType assignmentType) {
        return new EventAssignment(event, person, assignmentType);
    }

    private Person person(Long id) {
        Person person = new Person();
        person.setId(id);
        person.setName("Pessoa " + id);
        person.setPhoneNumber("34" + id);
        return person;
    }

    private CelebrationEventResponseDTO response(Long id, CelebrationEventStatus status) {
        return new CelebrationEventResponseDTO(id, "Missa", EVENT_START_AT, EVENT_END_AT, true, status);
    }
}
