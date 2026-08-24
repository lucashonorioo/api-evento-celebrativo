package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.PersonUnavailabilityRequestDTO;
import com.eventoscelebrativos.dto.response.AdminUnavailabilityPersonDTO;
import com.eventoscelebrativos.dto.response.AdminUnavailabilityResponseDTO;
import com.eventoscelebrativos.dto.response.PersonUnavailabilityResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.exception.exceptions.TemporalPrecisionNotSupportedException;
import com.eventoscelebrativos.mapper.PersonUnavailabilityMapper;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonUnavailability;
import com.eventoscelebrativos.repository.EventAssignmentRepository;
import com.eventoscelebrativos.repository.NotificationRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.repository.PersonUnavailabilityRepository;
import com.eventoscelebrativos.service.impl.AdminRoleMutexService;
import com.eventoscelebrativos.service.impl.PersonUnavailabilityServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonUnavailabilityServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime TODAY = LocalDateTime.of(2026, 8, 1, 0, 0);

    @Mock
    private PersonUnavailabilityRepository personUnavailabilityRepository;

    @Mock
    private PersonRepository personRepository;

    @Mock
    private PersonUnavailabilityConflictService personUnavailabilityConflictService;

    @Mock
    private EventAssignmentRepository eventAssignmentRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private AdminRoleMutexService adminRoleMutexService;

    @Mock
    private ScheduleConflictNotificationService scheduleConflictNotificationService;

    private final PersonUnavailabilityMapper mapper = new PersonUnavailabilityMapper() {
        @Override
        public PersonUnavailabilityResponseDTO toDto(PersonUnavailability entity) {
            if (entity == null) {
                return null;
            }
            return new PersonUnavailabilityResponseDTO(entity.getId(), entity.getStartAt(), entity.getEndAt(), entity.getReason());
        }
    };

    private PersonUnavailabilityServiceImpl service;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(TODAY.atZone(ZONE).toInstant(), ZONE);
        service = new PersonUnavailabilityServiceImpl(
                personUnavailabilityRepository, personRepository, eventAssignmentRepository, notificationRepository,
                personUnavailabilityConflictService, adminRoleMutexService, scheduleConflictNotificationService,
                mapper, fixedClock);
    }

    @Test
    void shouldIdentifyPersonByAuthenticatedPhoneNumberWhenListing() {
        Person person = person(10L, "34970000001");
        when(personRepository.findById(10L)).thenReturn(Optional.of(person));
        when(personUnavailabilityRepository.findByPersonIdIntersecting(eq(10L), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.findMine(10L, TODAY, TODAY.plusDays(5), 0, 10);

        verify(personRepository).findById(10L);
    }

    @Test
    void shouldThrowResourceNotFoundWhenAuthenticatedPersonDoesNotExistForListing() {
        when(personRepository.findById(10L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.findMine(10L, TODAY, TODAY.plusDays(1), 0, 10));
    }

    @Test
    void shouldRejectListingWhenStartDateAfterEndDate() {
        assertThrows(BadRequestException.class,
                () -> service.findMine(10L, TODAY.plusDays(2), TODAY, 0, 10));
    }

    @Test
    void shouldRejectListingWithInvalidPagination() {
        assertThrows(BadRequestException.class, () -> service.findMine(10L, TODAY, TODAY.plusDays(1), -1, 10));
        assertThrows(BadRequestException.class, () -> service.findMine(10L, TODAY, TODAY.plusDays(1), 0, 0));
        assertThrows(BadRequestException.class, () -> service.findMine(10L, TODAY, TODAY.plusDays(1), 0, 101));
    }

    @Test
    void shouldCreateUnavailabilityAfterLockingPersonAndValidating() {
        Person person = person(10L, "34970000002");
        PersonUnavailabilityRequestDTO request = new PersonUnavailabilityRequestDTO(TODAY, TODAY.plusDays(2), "  Viagem  ");
        when(personRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(person));
        when(personUnavailabilityRepository.save(any(PersonUnavailability.class))).thenAnswer(invocation -> {
            PersonUnavailability entity = invocation.getArgument(0);
            entity.setId(1L);
            return entity;
        });

        PersonUnavailabilityResponseDTO result = service.create(10L, request);

        assertEquals("Viagem", result.getReason());
        verify(personUnavailabilityConflictService).validateNoOverlap(10L, TODAY, TODAY.plusDays(2), null);
    }

    @Test
    void shouldNormalizeBlankReasonToNull() {
        Person person = person(10L, "34970000003");
        when(personRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(person));
        when(personUnavailabilityRepository.save(any(PersonUnavailability.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PersonUnavailabilityResponseDTO result = service.create(
                10L, new PersonUnavailabilityRequestDTO(TODAY, TODAY.plusHours(1), "   "));

        assertNull(result.getReason());
    }

    @Test
    void shouldRejectReasonLongerThan500Characters() {
        String tooLong = "a".repeat(501);

        assertThrows(BadRequestException.class,
                () -> service.create(10L, new PersonUnavailabilityRequestDTO(TODAY, TODAY.plusHours(1), tooLong)));
    }

    @Test
    void shouldRejectInvertedDateRangeOnCreate() {
        assertThrows(BadRequestException.class, () -> service.create(
                10L, new PersonUnavailabilityRequestDTO(TODAY.plusDays(2), TODAY, null)));
    }

    @Test
    void shouldRejectZeroDurationRangeOnCreate() {
        assertThrows(BadRequestException.class, () -> service.create(
                10L, new PersonUnavailabilityRequestDTO(TODAY, TODAY, null)));
    }

    @Test
    void shouldRejectFractionalStartAtOnCreate() {
        PersonUnavailabilityRequestDTO request = new PersonUnavailabilityRequestDTO(
                TODAY.plusDays(1).withNano(100_000_000),
                TODAY.plusDays(2),
                null
        );

        TemporalPrecisionNotSupportedException exception =
                assertThrows(TemporalPrecisionNotSupportedException.class,
                        () -> service.create(10L, request));

        assertEquals("TEMPORAL_PRECISION_NOT_SUPPORTED", exception.getErrorCode());
        verify(personRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void shouldRejectFractionalEndAtOnUpdateBeforeIdempotencyComparison() {
        PersonUnavailabilityRequestDTO request = new PersonUnavailabilityRequestDTO(
                TODAY.plusDays(1),
                TODAY.plusDays(2).withNano(123_000_000),
                null
        );

        assertThrows(
                TemporalPrecisionNotSupportedException.class,
                () -> service.update(10L, 1L, request)
        );

        verify(personRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void shouldRejectFractionalSecondsOnQueryRange() {
        assertThrows(
                TemporalPrecisionNotSupportedException.class,
                () -> service.findMine(
                        10L,
                        TODAY.withNano(123_456_000),
                        TODAY.plusDays(1),
                        0,
                        10
                )
        );

        verify(personRepository, never()).findById(any());
    }

    @Test
    void shouldRejectStartDateBeforeTodayOnCreate() {
        assertThrows(BadRequestException.class, () -> service.create(
                10L, new PersonUnavailabilityRequestDTO(TODAY.minusDays(1), TODAY, null)));
    }

    @Test
    void shouldAllowStartDateEqualToTodayOnCreate() {
        Person person = person(10L, "34970000007");
        when(personRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(person));
        when(personUnavailabilityRepository.save(any(PersonUnavailability.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(10L, new PersonUnavailabilityRequestDTO(TODAY, TODAY.plusHours(1), null));
    }

    @Test
    void shouldRejectStartAtOneSecondBeforeCurrentInstantOnCreate() {
        LocalDateTime pastStart = TODAY.minusSeconds(1);

        assertThrows(BadRequestException.class, () -> service.create(
                10L, new PersonUnavailabilityRequestDTO(pastStart, pastStart.plusHours(1), null)));

        verify(personRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void shouldAllowStartAtOneSecondAfterCurrentInstantOnCreate() {
        Person person = person(10L, "34970000010");
        when(personRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(person));
        when(personUnavailabilityRepository.save(any(PersonUnavailability.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LocalDateTime futureStart = TODAY.plusSeconds(1);

        service.create(10L, new PersonUnavailabilityRequestDTO(futureStart, futureStart.plusHours(1), null));
    }

    @Test
    void shouldNormalizeClockNanosToSecondPrecisionOnCreate() {
        Clock nanosClock = Clock.fixed(TODAY.atZone(ZONE).toInstant().plusNanos(900_000_000), ZONE);
        PersonUnavailabilityServiceImpl nanosService = new PersonUnavailabilityServiceImpl(
                personUnavailabilityRepository, personRepository, eventAssignmentRepository, notificationRepository,
                personUnavailabilityConflictService, adminRoleMutexService, scheduleConflictNotificationService,
                mapper, nanosClock);

        LocalDateTime pastStart = TODAY.minusSeconds(1);
        assertThrows(BadRequestException.class, () -> nanosService.create(
                10L, new PersonUnavailabilityRequestDTO(pastStart, pastStart.plusHours(1), null)));

        Person person = person(10L, "34970000011");
        when(personRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(person));
        when(personUnavailabilityRepository.save(any(PersonUnavailability.class))).thenAnswer(invocation -> invocation.getArgument(0));

        nanosService.create(10L, new PersonUnavailabilityRequestDTO(TODAY, TODAY.plusHours(1), null));
        nanosService.create(10L, new PersonUnavailabilityRequestDTO(TODAY.plusSeconds(1), TODAY.plusHours(2), null));
    }

    @Test
    void shouldUpdateOwnRecordWhenChanged() {
        Person person = person(10L, "34970000008");
        PersonUnavailability existing = existing(1L, person, TODAY, TODAY.plusDays(1), "Antigo");
        when(personRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(person));
        when(personUnavailabilityRepository.findByIdAndPersonId(1L, 10L)).thenReturn(Optional.of(existing));
        when(personUnavailabilityRepository.save(existing)).thenReturn(existing);

        PersonUnavailabilityResponseDTO result = service.update(
                10L, 1L, new PersonUnavailabilityRequestDTO(TODAY, TODAY.plusDays(3), "Novo"));

        assertEquals(TODAY.plusDays(3), result.getEndAt());
        assertEquals("Novo", result.getReason());
        verify(personUnavailabilityConflictService).validateNoOverlap(10L, TODAY, TODAY.plusDays(3), 1L);
    }

    @Test
    void shouldBeIdempotentWhenUpdateRequestMatchesCurrentState() {
        Person person = person(10L, "34970000009");
        PersonUnavailability existing = existing(1L, person, TODAY, TODAY.plusDays(1), "Motivo");
        when(personRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(person));
        when(personUnavailabilityRepository.findByIdAndPersonId(1L, 10L)).thenReturn(Optional.of(existing));

        PersonUnavailabilityResponseDTO result = service.update(
                10L, 1L, new PersonUnavailabilityRequestDTO(TODAY, TODAY.plusDays(1), "Motivo"));

        assertEquals(1L, result.getId());
        verify(personUnavailabilityRepository, never()).save(any());
        verify(personUnavailabilityConflictService, never()).validateNoOverlap(anyLong(), any(), any(), any());
    }

    @Test
    void shouldReturn404WithoutRevealingOwnershipWhenUpdatingOthersRecord() {
        Person person = person(10L, "34970000010");
        when(personRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(person));
        when(personUnavailabilityRepository.findByIdAndPersonId(99L, 10L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.update(
                10L, 99L, new PersonUnavailabilityRequestDTO(TODAY, TODAY.plusHours(1), null)));
    }

    @Test
    void shouldRejectUpdateWhenStartDateAlreadyPassedEvenIfUnchanged() {
        assertThrows(BadRequestException.class, () -> service.update(
                10L, 1L, new PersonUnavailabilityRequestDTO(TODAY.minusDays(1), TODAY, null)));

        verify(personUnavailabilityRepository, never()).findByIdAndPersonId(anyLong(), anyLong());
    }

    @Test
    void shouldDeleteOwnRecord() {
        Person person = person(10L, "34970000012");
        PersonUnavailability existing = existing(1L, person, TODAY, TODAY.plusDays(1), null);
        when(personRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(person));
        when(personUnavailabilityRepository.findByIdAndPersonId(1L, 10L)).thenReturn(Optional.of(existing));

        service.delete(10L, 1L);

        verify(personUnavailabilityRepository).delete(existing);
    }

    @Test
    void shouldReturn404WithoutRevealingOwnershipWhenDeletingOthersRecord() {
        Person person = person(10L, "34970000013");
        when(personRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(person));
        when(personUnavailabilityRepository.findByIdAndPersonId(99L, 10L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.delete(10L, 99L));
        verify(personUnavailabilityRepository, never()).delete(any());
    }

    @Test
    void shouldReturnAdminUnavailabilityGroupedByRange() {
        List<AdminUnavailabilityPersonDTO> people = List.of(
                new AdminUnavailabilityPersonDTO(4L, "Arthur Costa", List.of()));
        when(personUnavailabilityConflictService.findUnavailablePeopleOnRange(TODAY, TODAY.plusDays(2))).thenReturn(people);

        AdminUnavailabilityResponseDTO result = service.findByDate(TODAY, TODAY.plusDays(2));

        assertEquals(TODAY, result.getStartAt());
        assertEquals(TODAY.plusDays(2), result.getEndAt());
        assertSame(people, result.getPeople());
    }

    private Person person(Long id, String phoneNumber) {
        Person person = new Person("Person " + id, phoneNumber, LocalDate.of(1990, 1, 1));
        ReflectionTestUtils.setField(person, "id", id);
        return person;
    }

    private PersonUnavailability existing(Long id, Person person, LocalDateTime startAt, LocalDateTime endAt, String reason) {
        PersonUnavailability unavailability = new PersonUnavailability(person, startAt, endAt, reason);
        unavailability.setId(id);
        return unavailability;
    }
}
