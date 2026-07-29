package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.ParticipationResponseRequestDTO;
import com.eventoscelebrativos.dto.response.ParticipationResponseResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.exception.exceptions.ConflictException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.mapper.ParticipationResponseMapper;
import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.EventAssignment;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.EventParticipationResponse;
import com.eventoscelebrativos.model.ParticipationStatus;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.repository.CelebrationEventRepository;
import com.eventoscelebrativos.repository.EventAssignmentRepository;
import com.eventoscelebrativos.repository.EventParticipationResponseRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.service.impl.EventParticipationResponseServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventParticipationResponseServiceImplTest {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    @Mock
    private EventParticipationResponseRepository eventParticipationResponseRepository;

    @Mock
    private EventAssignmentRepository eventAssignmentRepository;

    @Mock
    private PersonRepository personRepository;

    @Mock
    private CelebrationEventRepository celebrationEventRepository;

    private final ParticipationResponseMapper mapper = new ParticipationResponseMapper() {
    };

    private EventParticipationResponseServiceImpl service;

    @BeforeEach
    void setUp() {
        service = newServiceAt(LocalDateTime.of(2026, 7, 1, 10, 0));
    }

    private EventParticipationResponseServiceImpl newServiceAt(LocalDateTime now) {
        Clock fixedClock = Clock.fixed(now.atZone(ZONE).toInstant(), ZONE);
        return new EventParticipationResponseServiceImpl(
                eventParticipationResponseRepository,
                eventAssignmentRepository,
                personRepository,
                celebrationEventRepository,
                mapper,
                fixedClock
        );
    }

    @Test
    void shouldIdentifyPersonByAuthenticatedPhoneNumber() {
        Person person = person(10L, "34970000001");
        CelebrationEvent event = futureEvent(15L);
        when(personRepository.findByPhoneNumber("34970000001")).thenReturn(Optional.of(person));
        when(celebrationEventRepository.findById(15L)).thenReturn(Optional.of(event));
        when(eventAssignmentRepository.findAllByEventIdAndPersonIdForUpdate(15L, 10L))
                .thenReturn(List.of(assignment(event, person)));
        when(eventParticipationResponseRepository.findByEventIdAndPersonId(15L, 10L)).thenReturn(Optional.empty());

        service.respond("34970000001", 15L, request("CONFIRMED", null));

        verify(personRepository).findByPhoneNumber("34970000001");
    }

    @Test
    void shouldConfirmParticipation() {
        Person person = person(10L, "34970000002");
        CelebrationEvent event = futureEvent(15L);
        mockAssignedPerson(person, event);
        when(eventParticipationResponseRepository.findByEventIdAndPersonId(15L, 10L)).thenReturn(Optional.empty());

        ParticipationResponseResponseDTO response = service.respond("34970000002", 15L, request("CONFIRMED", null));

        assertEquals(ParticipationStatus.CONFIRMED, response.getStatus());
        assertNull(response.getDeclineReason());
        verify(eventParticipationResponseRepository).save(any(EventParticipationResponse.class));
    }

    @Test
    void shouldDeclineParticipationWithReason() {
        Person person = person(10L, "34970000003");
        CelebrationEvent event = futureEvent(15L);
        mockAssignedPerson(person, event);
        when(eventParticipationResponseRepository.findByEventIdAndPersonId(15L, 10L)).thenReturn(Optional.empty());

        ParticipationResponseResponseDTO response = service.respond("34970000003", 15L, request("DECLINED", "Viagem"));

        assertEquals(ParticipationStatus.DECLINED, response.getStatus());
        assertEquals("Viagem", response.getDeclineReason());
    }

    @Test
    void shouldDiscardDeclineReasonWhenConfirming() {
        Person person = person(10L, "34970000004");
        CelebrationEvent event = futureEvent(15L);
        mockAssignedPerson(person, event);
        when(eventParticipationResponseRepository.findByEventIdAndPersonId(15L, 10L)).thenReturn(Optional.empty());

        ParticipationResponseResponseDTO response = service.respond("34970000004", 15L, request("CONFIRMED", "Motivo qualquer"));

        assertNull(response.getDeclineReason());
    }

    @Test
    void shouldTrimDeclineReason() {
        Person person = person(10L, "34970000005");
        CelebrationEvent event = futureEvent(15L);
        mockAssignedPerson(person, event);
        when(eventParticipationResponseRepository.findByEventIdAndPersonId(15L, 10L)).thenReturn(Optional.empty());

        ParticipationResponseResponseDTO response = service.respond("34970000005", 15L, request("DECLINED", "  Viagem  "));

        assertEquals("Viagem", response.getDeclineReason());
    }

    @Test
    void shouldTurnBlankDeclineReasonIntoNull() {
        Person person = person(10L, "34970000006");
        CelebrationEvent event = futureEvent(15L);
        mockAssignedPerson(person, event);
        when(eventParticipationResponseRepository.findByEventIdAndPersonId(15L, 10L)).thenReturn(Optional.empty());

        ParticipationResponseResponseDTO response = service.respond("34970000006", 15L, request("DECLINED", "   "));

        assertNull(response.getDeclineReason());
    }

    @Test
    void shouldRejectPendingStatus() {
        Person person = person(10L, "34970000007");
        CelebrationEvent event = futureEvent(15L);
        mockAssignedPerson(person, event);

        assertThrows(BadRequestException.class, () -> service.respond("34970000007", 15L, request("PENDING", null)));
    }

    @Test
    void shouldRejectNullStatus() {
        Person person = person(10L, "34970000008");
        CelebrationEvent event = futureEvent(15L);
        mockAssignedPerson(person, event);

        assertThrows(BadRequestException.class, () -> service.respond("34970000008", 15L, request(null, null)));
    }

    @Test
    void shouldRejectInvalidStatus() {
        Person person = person(10L, "34970000009");
        CelebrationEvent event = futureEvent(15L);
        mockAssignedPerson(person, event);

        assertThrows(BadRequestException.class, () -> service.respond("34970000009", 15L, request("UNKNOWN", null)));
    }

    @Test
    void shouldRejectDeclineReasonAboveFiveHundredCharacters() {
        Person person = person(10L, "34970000010");
        CelebrationEvent event = futureEvent(15L);
        mockAssignedPerson(person, event);
        String tooLong = "a".repeat(501);

        assertThrows(BadRequestException.class, () -> service.respond("34970000010", 15L, request("DECLINED", tooLong)));
    }

    @Test
    void shouldReturnNotFoundWhenPersonDoesNotExist() {
        when(personRepository.findByPhoneNumber("34970000011")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.respond("34970000011", 15L, request("CONFIRMED", null)));
    }

    @Test
    void shouldReturnNotFoundWhenEventDoesNotExist() {
        Person person = person(10L, "34970000012");
        when(personRepository.findByPhoneNumber("34970000012")).thenReturn(Optional.of(person));
        when(celebrationEventRepository.findById(15L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.respond("34970000012", 15L, request("CONFIRMED", null)));
    }

    @Test
    void shouldReturnConflictWhenPersonHasNoAssignmentInEvent() {
        Person person = person(10L, "34970000013");
        CelebrationEvent event = futureEvent(15L);
        when(personRepository.findByPhoneNumber("34970000013")).thenReturn(Optional.of(person));
        when(celebrationEventRepository.findById(15L)).thenReturn(Optional.of(event));
        when(eventAssignmentRepository.findAllByEventIdAndPersonIdForUpdate(15L, 10L)).thenReturn(List.of());

        assertThrows(ConflictException.class, () -> service.respond("34970000013", 15L, request("CONFIRMED", null)));
        verify(eventParticipationResponseRepository, never()).save(any());
    }

    @Test
    void shouldCreateFirstResponse() {
        Person person = person(10L, "34970000014");
        CelebrationEvent event = futureEvent(15L);
        mockAssignedPerson(person, event);
        when(eventParticipationResponseRepository.findByEventIdAndPersonId(15L, 10L)).thenReturn(Optional.empty());

        ParticipationResponseResponseDTO response = service.respond("34970000014", 15L, request("CONFIRMED", null));

        verify(eventParticipationResponseRepository, times(1)).save(any());
        assertEquals(LocalDateTime.of(2026, 7, 1, 10, 0), response.getRespondedAt());
    }

    @Test
    void shouldUpdateExistingResponseWhenStatusChanges() {
        Person person = person(10L, "34970000015");
        CelebrationEvent event = futureEvent(15L);
        mockAssignedPerson(person, event);
        EventParticipationResponse existing = new EventParticipationResponse(
                event, person, ParticipationStatus.CONFIRMED, null, LocalDateTime.of(2026, 6, 1, 9, 0)
        );
        when(eventParticipationResponseRepository.findByEventIdAndPersonId(15L, 10L)).thenReturn(Optional.of(existing));

        ParticipationResponseResponseDTO response = service.respond("34970000015", 15L, request("DECLINED", "Motivo"));

        assertEquals(ParticipationStatus.DECLINED, existing.getStatus());
        assertEquals("Motivo", existing.getDeclineReason());
        assertEquals(LocalDateTime.of(2026, 7, 1, 10, 0), existing.getRespondedAt());
        assertEquals(LocalDateTime.of(2026, 7, 1, 10, 0), response.getRespondedAt());
        verify(eventParticipationResponseRepository, never()).save(any());
    }

    @Test
    void shouldNotCreateDuplicateWhenResponseAlreadyExists() {
        Person person = person(10L, "34970000016");
        CelebrationEvent event = futureEvent(15L);
        mockAssignedPerson(person, event);
        EventParticipationResponse existing = new EventParticipationResponse(
                event, person, ParticipationStatus.CONFIRMED, null, LocalDateTime.of(2026, 6, 1, 9, 0)
        );
        when(eventParticipationResponseRepository.findByEventIdAndPersonId(15L, 10L)).thenReturn(Optional.of(existing));

        service.respond("34970000016", 15L, request("DECLINED", "Motivo"));

        verify(eventParticipationResponseRepository, never()).save(any());
    }

    @Test
    void shouldBeNoOpWhenSamePayloadIsSentAgain() {
        Person person = person(10L, "34970000017");
        CelebrationEvent event = futureEvent(15L);
        mockAssignedPerson(person, event);
        LocalDateTime originalRespondedAt = LocalDateTime.of(2026, 6, 1, 9, 0);
        EventParticipationResponse existing = new EventParticipationResponse(
                event, person, ParticipationStatus.DECLINED, "Viagem", originalRespondedAt
        );
        when(eventParticipationResponseRepository.findByEventIdAndPersonId(15L, 10L)).thenReturn(Optional.of(existing));

        ParticipationResponseResponseDTO response = service.respond("34970000017", 15L, request("DECLINED", " Viagem "));

        assertEquals(originalRespondedAt, existing.getRespondedAt());
        assertEquals(originalRespondedAt, response.getRespondedAt());
        verify(eventParticipationResponseRepository, never()).save(any());
    }

    @Test
    void shouldUpdateRespondedAtWhenPayloadReallyChanges() {
        Person person = person(10L, "34970000018");
        CelebrationEvent event = futureEvent(15L);
        mockAssignedPerson(person, event);
        LocalDateTime originalRespondedAt = LocalDateTime.of(2026, 6, 1, 9, 0);
        EventParticipationResponse existing = new EventParticipationResponse(
                event, person, ParticipationStatus.DECLINED, "Viagem", originalRespondedAt
        );
        when(eventParticipationResponseRepository.findByEventIdAndPersonId(15L, 10L)).thenReturn(Optional.of(existing));

        ParticipationResponseResponseDTO response = service.respond("34970000018", 15L, request("DECLINED", "Outro motivo"));

        assertEquals(LocalDateTime.of(2026, 7, 1, 10, 0), existing.getRespondedAt());
        assertEquals(LocalDateTime.of(2026, 7, 1, 10, 0), response.getRespondedAt());
    }

    @Test
    void shouldAcceptSinglePersonWithTwoFunctionsAsAssigned() {
        Person person = person(10L, "34970000019");
        CelebrationEvent event = futureEvent(15L);
        EventAssignment readerAssignment = new EventAssignment(event, person, EventAssignmentType.READER);
        EventAssignment commentatorAssignment = new EventAssignment(event, person, EventAssignmentType.COMMENTATOR);
        when(personRepository.findByPhoneNumber("34970000019")).thenReturn(Optional.of(person));
        when(celebrationEventRepository.findById(15L)).thenReturn(Optional.of(event));
        when(eventAssignmentRepository.findAllByEventIdAndPersonIdForUpdate(15L, 10L))
                .thenReturn(List.of(readerAssignment, commentatorAssignment));
        when(eventParticipationResponseRepository.findByEventIdAndPersonId(15L, 10L)).thenReturn(Optional.empty());

        service.respond("34970000019", 15L, request("CONFIRMED", null));

        verify(eventParticipationResponseRepository, times(1)).save(any());
    }

    @Test
    void shouldAllowResponseBeforeEventStart() {
        Person person = person(10L, "34970000020");
        CelebrationEvent event = eventAt(15L, LocalDate.of(2026, 7, 1), LocalTime.of(19, 0));
        mockAssignedPerson(person, event);
        when(eventParticipationResponseRepository.findByEventIdAndPersonId(15L, 10L)).thenReturn(Optional.empty());
        service = newServiceAt(LocalDateTime.of(2026, 7, 1, 18, 59, 59));

        service.respond("34970000020", 15L, request("CONFIRMED", null));

        verify(eventParticipationResponseRepository).save(any());
    }

    @Test
    void shouldBlockResponseExactlyAtEventStart() {
        Person person = person(10L, "34970000021");
        CelebrationEvent event = eventAt(15L, LocalDate.of(2026, 7, 1), LocalTime.of(19, 0));
        when(personRepository.findByPhoneNumber("34970000021")).thenReturn(Optional.of(person));
        when(celebrationEventRepository.findById(15L)).thenReturn(Optional.of(event));
        when(eventAssignmentRepository.findAllByEventIdAndPersonIdForUpdate(15L, 10L))
                .thenReturn(List.of(assignment(event, person)));
        service = newServiceAt(LocalDateTime.of(2026, 7, 1, 19, 0));

        assertThrows(ConflictException.class, () -> service.respond("34970000021", 15L, request("CONFIRMED", null)));
        verify(eventParticipationResponseRepository, never()).save(any());
    }

    @Test
    void shouldBlockResponseAfterEventStart() {
        Person person = person(10L, "34970000022");
        CelebrationEvent event = eventAt(15L, LocalDate.of(2026, 7, 1), LocalTime.of(19, 0));
        when(personRepository.findByPhoneNumber("34970000022")).thenReturn(Optional.of(person));
        when(celebrationEventRepository.findById(15L)).thenReturn(Optional.of(event));
        when(eventAssignmentRepository.findAllByEventIdAndPersonIdForUpdate(15L, 10L))
                .thenReturn(List.of(assignment(event, person)));
        service = newServiceAt(LocalDateTime.of(2026, 7, 1, 19, 0, 1));

        assertThrows(ConflictException.class, () -> service.respond("34970000022", 15L, request("CONFIRMED", null)));
    }

    @Test
    void shouldAcquirePessimisticLockOnAssignmentsBeforeRespondingToPreventDuplicates() {
        Person person = person(10L, "34970000023");
        CelebrationEvent event = futureEvent(15L);
        mockAssignedPerson(person, event);
        when(eventParticipationResponseRepository.findByEventIdAndPersonId(15L, 10L)).thenReturn(Optional.empty());

        service.respond("34970000023", 15L, request("CONFIRMED", null));

        verify(eventAssignmentRepository).findAllByEventIdAndPersonIdForUpdate(15L, 10L);
    }

    @Test
    void shouldOnlyAffectResponseOfAuthenticatedPerson() {
        Person personA = person(10L, "34970000024");
        Person personB = person(11L, "34970000025");
        CelebrationEvent event = futureEvent(15L);
        when(personRepository.findByPhoneNumber("34970000024")).thenReturn(Optional.of(personA));
        when(celebrationEventRepository.findById(15L)).thenReturn(Optional.of(event));
        when(eventAssignmentRepository.findAllByEventIdAndPersonIdForUpdate(15L, 10L))
                .thenReturn(List.of(assignment(event, personA)));
        when(eventParticipationResponseRepository.findByEventIdAndPersonId(15L, 10L)).thenReturn(Optional.empty());

        service.respond("34970000024", 15L, request("CONFIRMED", null));

        verify(eventAssignmentRepository, never()).findAllByEventIdAndPersonIdForUpdate(15L, personB.getId());
        verify(eventParticipationResponseRepository, never()).findByEventIdAndPersonId(15L, personB.getId());
    }

    @Test
    void shouldReadResponsesInBatchByPersonAndEventIds() {
        when(eventParticipationResponseRepository.findAllByPersonIdAndEventIdIn(10L, List.of(15L, 16L)))
                .thenReturn(List.of());

        Map<Long, ParticipationResponseSnapshot> result = service.findByPersonIdAndEventIds(10L, List.of(15L, 16L));

        assertEquals(0, result.size());
        verify(eventParticipationResponseRepository, times(1)).findAllByPersonIdAndEventIdIn(10L, List.of(15L, 16L));
    }

    @Test
    void shouldReadResponsesInBatchByEventId() {
        when(eventParticipationResponseRepository.findAllByEventId(15L)).thenReturn(List.of());

        Map<Long, ParticipationResponseSnapshot> result = service.findByEventId(15L);

        assertEquals(0, result.size());
        verify(eventParticipationResponseRepository, times(1)).findAllByEventId(15L);
    }

    private void mockAssignedPerson(Person person, CelebrationEvent event) {
        when(personRepository.findByPhoneNumber(person.getPhoneNumber())).thenReturn(Optional.of(person));
        when(celebrationEventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(eventAssignmentRepository.findAllByEventIdAndPersonIdForUpdate(event.getId(), person.getId()))
                .thenReturn(List.of(assignment(event, person)));
    }

    private EventAssignment assignment(CelebrationEvent event, Person person) {
        return new EventAssignment(event, person, EventAssignmentType.READER);
    }

    private Person person(Long id, String phoneNumber) {
        Person person = new Person();
        person.setId(id);
        person.setName("Person " + id);
        person.setPhoneNumber(phoneNumber);
        return person;
    }

    private CelebrationEvent futureEvent(Long id) {
        return eventAt(id, LocalDate.of(2026, 12, 31), LocalTime.of(19, 0));
    }

    private CelebrationEvent eventAt(Long id, LocalDate date, LocalTime time) {
        CelebrationEvent event = new CelebrationEvent(id, "Evento " + id, date, time, true);
        return event;
    }

    private ParticipationResponseRequestDTO request(String status, String declineReason) {
        return new ParticipationResponseRequestDTO(status, declineReason);
    }
}
