package com.eventoscelebrativos.service;

import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.exception.exceptions.MinistryInactiveException;
import com.eventoscelebrativos.exception.exceptions.MinistryLegacyCompatibilityRequiredException;
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
import com.eventoscelebrativos.service.impl.PersonMinistryCommandServiceImpl;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.eventoscelebrativos.support.LegacyMinistryTestFactory.personMinistry;
import static com.eventoscelebrativos.support.LegacyMinistryTestFactory.unitMinistry;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersonMinistryCommandServiceImplTest {

    private static final String ENTITY_LABEL = "Leitor";

    @Mock
    private PersonRepository personRepository;

    @Mock
    private MinistryRepository ministryRepository;

    @Mock
    private PersonMinistryRepository personMinistryRepository;

    @Mock
    private EventAssignmentRepository eventAssignmentRepository;

    @Mock
    private ParishStaffAssignmentRepository parishStaffAssignmentRepository;

    @Mock
    private LegacyMinistryTypeResolver legacyMinistryTypeResolver;

    @Mock
    private EntityManager entityManager;

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneId.of("America/Sao_Paulo"));

    @InjectMocks
    private PersonMinistryCommandServiceImpl service;

    @BeforeEach
    void setUpLegacyMinistryResolver() {
        for (MinistryType ministryType : MinistryType.values()) {
            lenient().when(legacyMinistryTypeResolver.requireMinistry(ministryType))
                    .thenReturn(unitMinistry(ministryType));
        }
        lenient().when(legacyMinistryTypeResolver.requireMinistries(anyCollection()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Collection<MinistryType> ministryTypes = invocation.getArgument(0);
                    Map<MinistryType, Ministry> ministries = new EnumMap<>(MinistryType.class);
                    for (MinistryType ministryType : ministryTypes) {
                        ministries.put(ministryType, unitMinistry(ministryType));
                    }
                    return ministries;
                });
        lenient().when(legacyMinistryTypeResolver.requireTypesByPersistentMinistryId(anyCollection()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Collection<Long> ministryIds = invocation.getArgument(0);
                    Map<Long, MinistryType> types = new java.util.LinkedHashMap<>();
                    for (Long ministryId : ministryIds) {
                        types.put(ministryId, ministryTypeFor(ministryId));
                    }
                    return types;
                });
        lenient().when(legacyMinistryTypeResolver.requireMinistryType(any(Ministry.class)))
                .thenAnswer(invocation -> ministryTypeFor(invocation.<Ministry>getArgument(0)));
        lenient().when(legacyMinistryTypeResolver.requireEventAssignmentType(any(Ministry.class)))
                .thenAnswer(invocation -> EventAssignmentType.valueOf(ministryTypeFor(invocation.<Ministry>getArgument(0)).name()));
        lenient().when(ministryRepository.existsById(anyLong()))
                .thenAnswer(invocation -> isKnownMinistryId(invocation.getArgument(0)));
        lenient().when(ministryRepository.findAllById(any()))
                .thenAnswer(invocation -> {
                    Iterable<Long> ministryIds = invocation.getArgument(0);
                    List<Ministry> ministries = new ArrayList<>();
                    for (Long ministryId : ministryIds) {
                        if (isKnownMinistryId(ministryId)) {
                            ministries.add(unitMinistry(ministryTypeFor(ministryId)));
                        }
                    }
                    return ministries;
                });
        lenient().when(ministryRepository.findByIdForUpdate(anyLong()))
                .thenAnswer(invocation -> Optional.of(unitMinistry(ministryTypeFor(invocation.<Long>getArgument(0)))));
        lenient().when(ministryRepository.findAllByIdInForUpdate(anyCollection()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Collection<Long> ministryIds = invocation.getArgument(0);
                    return ministryIds.stream()
                            .map(id -> unitMinistry(ministryTypeFor(id)))
                            .toList();
                });
    }

    @Test
    void shouldCreatePersonAndMinistryAtomically() {
        Person reader = reader(null);
        Person saved = reader(1L);
        when(personRepository.save(reader)).thenReturn(saved);

        Person result = service.create(reader, MinistryType.READER);

        assertSame(saved, result);
        ArgumentCaptor<PersonMinistry> captor = ArgumentCaptor.forClass(PersonMinistry.class);
        verify(personMinistryRepository).save(captor.capture());
        assertSame(saved, captor.getValue().getPerson());
        assertEquals(MinistryType.READER, captor.getValue().getMinistryType());
    }

    @Test
    void shouldCreatePersonAndMinistryByPersistentMinistryAtomically() {
        Person reader = reader(null);
        Person saved = reader(1L);
        Ministry readerMinistry = unitMinistry(MinistryType.READER);
        when(personRepository.save(reader)).thenReturn(saved);

        Person result = service.create(reader, readerMinistry);

        assertSame(saved, result);
        ArgumentCaptor<PersonMinistry> captor = ArgumentCaptor.forClass(PersonMinistry.class);
        verify(personMinistryRepository).save(captor.capture());
        assertSame(saved, captor.getValue().getPerson());
        assertEquals(readerMinistry.getId(), captor.getValue().getMinistry().getId());
        assertEquals(MinistryType.READER, captor.getValue().getMinistryType());
    }

    @Test
    void shouldRejectCreateWithMissingArguments() {
        assertThrows(BusinessException.class, () -> service.create(null, MinistryType.READER));
        assertThrows(BusinessException.class, () -> service.create(reader(1L), (MinistryType) null));
        assertThrows(BusinessException.class, () -> service.create(null, unitMinistry(MinistryType.READER)));
        assertThrows(BusinessException.class, () -> service.create(reader(1L), (Ministry) null));
        verifyNoInteractions(personRepository);
    }

    @Test
    void shouldTranslateMinistryConstraintViolationOnCreateToDatabaseException() {
        Person reader = reader(null);
        Person saved = reader(1L);
        when(personRepository.save(reader)).thenReturn(saved);
        when(personMinistryRepository.save(any(PersonMinistry.class)))
                .thenThrow(new DataIntegrityViolationException("constraint"));

        assertThrows(DatabaseException.class, () -> service.create(reader, MinistryType.READER));
    }

    @Test
    void shouldReturnPersonWhenMinistryIsActive() {
        Person reader = reader(1L);
        PersonMinistry ministry = personMinistry(reader, MinistryType.READER);
        when(personRepository.findById(1L)).thenReturn(Optional.of(reader));
        whenPersonMinistry(1L, MinistryType.READER)
                .thenReturn(Optional.of(ministry));

        assertSame(reader, service.requireActiveMinistryPerson(1L, MinistryType.READER, ENTITY_LABEL));
    }

    @Test
    void shouldReturnPersonWhenPersistentMinistryIsActive() {
        Person reader = reader(1L);
        Ministry readerMinistry = unitMinistry(MinistryType.READER);
        PersonMinistry ministry = personMinistry(reader, MinistryType.READER);
        when(personRepository.findById(1L)).thenReturn(Optional.of(reader));
        when(personMinistryRepository.findByPersonIdAndMinistryId(1L, readerMinistry.getId()))
                .thenReturn(Optional.of(ministry));

        assertSame(reader, service.requireActiveMinistryPerson(1L, readerMinistry, ENTITY_LABEL));
    }

    @Test
    void shouldThrowResourceNotFoundWhenPersonDoesNotExist() {
        when(personRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.requireActiveMinistryPerson(99L, MinistryType.READER, ENTITY_LABEL));
    }

    @Test
    void shouldThrowResourceNotFoundWhenMinistryIsAbsent() {
        Person reader = reader(1L);
        when(personRepository.findById(1L)).thenReturn(Optional.of(reader));
        whenPersonMinistry(1L, MinistryType.READER)
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.requireActiveMinistryPerson(1L, MinistryType.READER, ENTITY_LABEL));
    }

    @Test
    void shouldThrowResourceNotFoundWhenPersonIsInactiveEvenWithActiveMinistry() {
        Person inactiveReader = reader(1L);
        inactiveReader.deactivate();
        when(personRepository.findById(1L)).thenReturn(Optional.of(inactiveReader));

        assertThrows(ResourceNotFoundException.class,
                () -> service.requireActiveMinistryPerson(1L, MinistryType.READER, ENTITY_LABEL));

        verifyNoInteractions(personMinistryRepository);
    }

    @Test
    void shouldThrowResourceNotFoundWhenMinistryIsInactive() {
        Person reader = reader(1L);
        PersonMinistry ministry = personMinistry(reader, MinistryType.READER);
        ministry.setActive(false);
        when(personRepository.findById(1L)).thenReturn(Optional.of(reader));
        whenPersonMinistry(1L, MinistryType.READER)
                .thenReturn(Optional.of(ministry));

        assertThrows(ResourceNotFoundException.class,
                () -> service.requireActiveMinistryPerson(1L, MinistryType.READER, ENTITY_LABEL));
    }

    @Test
    void shouldRejectInvalidIdOnRequireActiveMinistryPerson() {
        assertThrows(BusinessException.class, () -> service.requireActiveMinistryPerson(null, MinistryType.READER, ENTITY_LABEL));
        assertThrows(BusinessException.class, () -> service.requireActiveMinistryPerson(0L, MinistryType.READER, ENTITY_LABEL));
        assertThrows(BusinessException.class, () -> service.requireActiveMinistryPerson(-1L, MinistryType.READER, ENTITY_LABEL));
    }

    @Test
    void shouldRejectMissingPersistentMinistryOnRequireActiveMinistryPerson() {
        assertThrows(BusinessException.class,
                () -> service.requireActiveMinistryPerson(1L, (Ministry) null, ENTITY_LABEL));

        verifyNoInteractions(personRepository);
    }

    @Test
    void shouldLockPersonWithFindByIdForUpdateWhenRequiringActiveMinistryPersonForUpdate() {
        Person reader = reader(1L);
        PersonMinistry ministry = personMinistry(reader, MinistryType.READER);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reader));
        whenPersonMinistry(1L, MinistryType.READER)
                .thenReturn(Optional.of(ministry));

        assertSame(reader, service.requireActiveMinistryPersonForUpdate(1L, MinistryType.READER, ENTITY_LABEL));

        verify(personRepository).findByIdForUpdate(1L);
        verify(personRepository, never()).findById(anyLong());
    }

    @Test
    void shouldThrowResourceNotFoundWhenPersonDoesNotExistForUpdate() {
        when(personRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.requireActiveMinistryPersonForUpdate(99L, MinistryType.READER, ENTITY_LABEL));
    }

    @Test
    void shouldRejectInvalidIdOnRequireActiveMinistryPersonForUpdate() {
        assertThrows(BusinessException.class, () -> service.requireActiveMinistryPersonForUpdate(null, MinistryType.READER, ENTITY_LABEL));
        assertThrows(BusinessException.class, () -> service.requireActiveMinistryPersonForUpdate(0L, MinistryType.READER, ENTITY_LABEL));
        verifyNoInteractions(personRepository);
    }

    @Test
    void shouldDeactivateOnlyTheRequestedMinistryWhenNoAssignmentConflictExists() {
        Person reader = reader(1L);
        PersonMinistry readerMinistry = personMinistry(reader, MinistryType.READER);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reader));
        whenPersonMinistry(1L, MinistryType.READER)
                .thenReturn(Optional.of(readerMinistry));
        when(eventAssignmentRepository.existsActiveOrFutureByPersonIdAndAssignmentType(eq(1L), eq(EventAssignmentType.READER), any()))
                .thenReturn(false);

        service.removeMinistry(1L, MinistryType.READER, ENTITY_LABEL);

        assertFalse(readerMinistry.getActive());
        verify(personMinistryRepository).save(readerMinistry);
        verify(personRepository, never()).save(any());
        verify(personRepository, never()).delete(any());
        verify(personRepository, never()).deleteById(any());
    }

    @Test
    void shouldClearCoordinatorWhenRemovingMinistryThroughIndividualCrudFlow() {
        Person reader = reader(1L);
        PersonMinistry coordinatedMinistry = personMinistry(reader, MinistryType.READER);
        coordinatedMinistry.grantCoordination();
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reader));
        whenPersonMinistry(1L, MinistryType.READER)
                .thenReturn(Optional.of(coordinatedMinistry));
        when(eventAssignmentRepository.existsActiveOrFutureByPersonIdAndAssignmentType(eq(1L), eq(EventAssignmentType.READER), any()))
                .thenReturn(false);

        service.removeMinistry(1L, MinistryType.READER, ENTITY_LABEL);

        assertFalse(coordinatedMinistry.getActive());
        assertFalse(coordinatedMinistry.getCoordinator());
    }

    @Test
    void shouldBlockRemovalWhenPersonHasEventAssignmentOfSameType() {
        Person reader = reader(1L);
        PersonMinistry readerMinistry = personMinistry(reader, MinistryType.READER);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reader));
        whenPersonMinistry(1L, MinistryType.READER)
                .thenReturn(Optional.of(readerMinistry));
        when(eventAssignmentRepository.existsActiveOrFutureByPersonIdAndAssignmentType(eq(1L), eq(EventAssignmentType.READER), any()))
                .thenReturn(true);

        assertThrows(DatabaseException.class, () -> service.removeMinistry(1L, MinistryType.READER, ENTITY_LABEL));

        assertTrue(readerMinistry.getActive());
        verify(personMinistryRepository, never()).save(any());
    }

    @Test
    void shouldNotBlockRemovalWhenAssignmentIsOfADifferentType() {
        Person reader = reader(1L);
        PersonMinistry readerMinistry = personMinistry(reader, MinistryType.READER);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reader));
        whenPersonMinistry(1L, MinistryType.READER)
                .thenReturn(Optional.of(readerMinistry));
        when(eventAssignmentRepository.existsActiveOrFutureByPersonIdAndAssignmentType(eq(1L), eq(EventAssignmentType.READER), any()))
                .thenReturn(false);

        service.removeMinistry(1L, MinistryType.READER, ENTITY_LABEL);

        assertFalse(readerMinistry.getActive());
    }

    @Test
    void shouldThrowResourceNotFoundWhenRemovingMissingOrInactiveMinistry() {
        when(personRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.removeMinistry(99L, MinistryType.READER, ENTITY_LABEL));
        verifyNoInteractions(eventAssignmentRepository);
    }

    @Test
    void shouldBlockPriestRemovalWhenPersonIsActivePastor() {
        Person priest = reader(1L);
        PersonMinistry priestMinistry = personMinistry(priest, MinistryType.PRIEST);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(priest));
        whenPersonMinistry(1L, MinistryType.PRIEST)
                .thenReturn(Optional.of(priestMinistry));
        when(parishStaffAssignmentRepository.existsByPersonIdAndResponsibilityAndActiveTrue(1L, ParishResponsibilityType.PASTOR))
                .thenReturn(true);

        assertThrows(PastorPriestMinistryRequiredException.class,
                () -> service.removeMinistry(1L, MinistryType.PRIEST, "Padre"));

        assertTrue(priestMinistry.getActive());
        verify(personMinistryRepository, never()).save(any());
        verifyNoInteractions(eventAssignmentRepository);
    }

    @Test
    void shouldAllowPriestRemovalWhenPersonIsNotActivePastor() {
        Person priest = reader(1L);
        PersonMinistry priestMinistry = personMinistry(priest, MinistryType.PRIEST);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(priest));
        whenPersonMinistry(1L, MinistryType.PRIEST)
                .thenReturn(Optional.of(priestMinistry));
        when(parishStaffAssignmentRepository.existsByPersonIdAndResponsibilityAndActiveTrue(1L, ParishResponsibilityType.PASTOR))
                .thenReturn(false);
        when(eventAssignmentRepository.existsActiveOrFutureByPersonIdAndAssignmentType(eq(1L), eq(EventAssignmentType.PRIEST), any()))
                .thenReturn(false);

        service.removeMinistry(1L, MinistryType.PRIEST, "Padre");

        assertFalse(priestMinistry.getActive());
        verify(personMinistryRepository).save(priestMinistry);
    }

    @Test
    void shouldAddNewMinistriesWhenPersonHasNone() {
        Person reader = reader(1L);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reader));
        when(personMinistryRepository.findAllByPersonId(1L)).thenReturn(List.of());

        PersonMinistrySyncResult result = service.syncMinistries(1L, Set.of(MinistryType.READER, MinistryType.COMMENTATOR));

        assertSame(reader, result.person());
        assertEquals(Set.of(MinistryType.READER, MinistryType.COMMENTATOR), result.activeMinistries());
        assertEquals(Set.of(MinistryType.READER, MinistryType.COMMENTATOR), result.added());
        assertTrue(result.reactivated().isEmpty());
        assertTrue(result.deactivated().isEmpty());
        assertTrue(result.unchanged().isEmpty());
        ArgumentCaptor<PersonMinistry> captor = ArgumentCaptor.forClass(PersonMinistry.class);
        verify(personMinistryRepository, times(2)).save(captor.capture());
        assertTrue(captor.getAllValues().stream().allMatch(pm -> pm.getPerson() == reader));
    }

    @Test
    void shouldReactivateInactiveMinistryDuringSync() {
        Person reader = reader(1L);
        PersonMinistry inactiveMinistry = personMinistry(reader, MinistryType.READER);
        inactiveMinistry.setActive(false);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reader));
        when(personMinistryRepository.findAllByPersonId(1L)).thenReturn(List.of(inactiveMinistry));

        PersonMinistrySyncResult result = service.syncMinistries(1L, Set.of(MinistryType.READER));

        assertEquals(Set.of(MinistryType.READER), result.reactivated());
        assertTrue(inactiveMinistry.getActive());
        verify(personMinistryRepository).save(inactiveMinistry);
    }

    @Test
    void shouldDeactivateActiveMinistryAbsentFromDesiredSetDuringSync() {
        Person reader = reader(1L);
        PersonMinistry activeMinistry = personMinistry(reader, MinistryType.READER);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reader));
        when(personMinistryRepository.findAllByPersonId(1L)).thenReturn(List.of(activeMinistry));
        when(eventAssignmentRepository.existsActiveOrFutureByPersonIdAndAssignmentType(eq(1L), eq(EventAssignmentType.READER), any()))
                .thenReturn(false);

        PersonMinistrySyncResult result = service.syncMinistries(1L, Set.of());

        assertEquals(Set.of(MinistryType.READER), result.deactivated());
        assertTrue(result.activeMinistries().isEmpty());
        assertFalse(activeMinistry.getActive());
        verify(personMinistryRepository).save(activeMinistry);
    }

    @Test
    void shouldPreserveUnchangedMinistryDuringSync() {
        Person reader = reader(1L);
        PersonMinistry activeMinistry = personMinistry(reader, MinistryType.READER);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reader));
        when(personMinistryRepository.findAllByPersonId(1L)).thenReturn(List.of(activeMinistry));

        PersonMinistrySyncResult result = service.syncMinistries(1L, Set.of(MinistryType.READER));

        assertEquals(Set.of(MinistryType.READER), result.unchanged());
        assertTrue(result.added().isEmpty());
        assertTrue(result.reactivated().isEmpty());
        assertTrue(result.deactivated().isEmpty());
        verify(personMinistryRepository, never()).save(any());
    }

    @Test
    void shouldPreserveCoordinatorOnUnchangedMinistryDuringSync() {
        Person reader = reader(1L);
        PersonMinistry coordinatedMinistry = personMinistry(reader, MinistryType.READER);
        coordinatedMinistry.grantCoordination();
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reader));
        when(personMinistryRepository.findAllByPersonId(1L)).thenReturn(List.of(coordinatedMinistry));

        PersonMinistrySyncResult result = service.syncMinistries(1L, Set.of(MinistryType.READER));

        assertEquals(Set.of(MinistryType.READER), result.unchanged());
        assertTrue(coordinatedMinistry.getCoordinator());
        verify(personMinistryRepository, never()).save(any());
    }

    @Test
    void shouldClearCoordinatorWhenMinistryIsDeactivatedDuringSync() {
        Person reader = reader(1L);
        PersonMinistry coordinatedMinistry = personMinistry(reader, MinistryType.READER);
        coordinatedMinistry.grantCoordination();
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reader));
        when(personMinistryRepository.findAllByPersonId(1L)).thenReturn(List.of(coordinatedMinistry));
        when(eventAssignmentRepository.existsActiveOrFutureByPersonIdAndAssignmentType(eq(1L), eq(EventAssignmentType.READER), any()))
                .thenReturn(false);

        PersonMinistrySyncResult result = service.syncMinistries(1L, Set.of());

        assertEquals(Set.of(MinistryType.READER), result.deactivated());
        assertFalse(coordinatedMinistry.getActive());
        assertFalse(coordinatedMinistry.getCoordinator());
        verify(personMinistryRepository).save(coordinatedMinistry);
    }

    @Test
    void shouldNotRestoreCoordinatorWhenMinistryIsReactivatedDuringSync() {
        Person reader = reader(1L);
        PersonMinistry inactiveMinistry = personMinistry(reader, MinistryType.READER);
        inactiveMinistry.setActive(false);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reader));
        when(personMinistryRepository.findAllByPersonId(1L)).thenReturn(List.of(inactiveMinistry));

        PersonMinistrySyncResult result = service.syncMinistries(1L, Set.of(MinistryType.READER));

        assertEquals(Set.of(MinistryType.READER), result.reactivated());
        assertTrue(inactiveMinistry.getActive());
        assertFalse(inactiveMinistry.getCoordinator());
        verify(personMinistryRepository).save(inactiveMinistry);
    }

    @Test
    void shouldApplyAllFourCategoriesInSingleSync() {
        Person reader = reader(1L);
        PersonMinistry unchangedReader = personMinistry(reader, MinistryType.READER);
        PersonMinistry reactivatedCommentator = personMinistry(reader, MinistryType.COMMENTATOR);
        reactivatedCommentator.setActive(false);
        PersonMinistry deactivatedPriest = personMinistry(reader, MinistryType.PRIEST);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reader));
        when(personMinistryRepository.findAllByPersonId(1L))
                .thenReturn(List.of(unchangedReader, reactivatedCommentator, deactivatedPriest));
        when(eventAssignmentRepository.existsActiveOrFutureByPersonIdAndAssignmentType(eq(1L), eq(EventAssignmentType.PRIEST), any()))
                .thenReturn(false);

        PersonMinistrySyncResult result = service.syncMinistries(
                1L,
                Set.of(MinistryType.READER, MinistryType.COMMENTATOR, MinistryType.EUCHARISTIC_MINISTER)
        );

        assertEquals(Set.of(MinistryType.EUCHARISTIC_MINISTER), result.added());
        assertEquals(Set.of(MinistryType.COMMENTATOR), result.reactivated());
        assertEquals(Set.of(MinistryType.PRIEST), result.deactivated());
        assertEquals(Set.of(MinistryType.READER), result.unchanged());
        assertEquals(
                Set.of(MinistryType.READER, MinistryType.COMMENTATOR, MinistryType.EUCHARISTIC_MINISTER),
                result.activeMinistries()
        );
        assertTrue(reactivatedCommentator.getActive());
        assertFalse(deactivatedPriest.getActive());
    }

    @Test
    void shouldBlockSyncWhenDeactivatingPriestOfActivePastor() {
        Person priest = reader(1L);
        PersonMinistry priestMinistry = personMinistry(priest, MinistryType.PRIEST);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(priest));
        when(personMinistryRepository.findAllByPersonId(1L)).thenReturn(List.of(priestMinistry));
        when(eventAssignmentRepository.existsActiveOrFutureByPersonIdAndAssignmentType(eq(1L), eq(EventAssignmentType.PRIEST), any()))
                .thenReturn(false);
        when(parishStaffAssignmentRepository.existsByPersonIdAndResponsibilityAndActiveTrue(1L, ParishResponsibilityType.PASTOR))
                .thenReturn(true);

        assertThrows(PastorPriestMinistryRequiredException.class, () -> service.syncMinistries(1L, Set.of()));

        assertTrue(priestMinistry.getActive());
        verify(personMinistryRepository, never()).save(any());
    }

    @Test
    void shouldBlockSyncAndApplyNoMutationWhenDeactivationConflictsWithEventAssignment() {
        Person reader = reader(1L);
        PersonMinistry activeReaderMinistry = personMinistry(reader, MinistryType.READER);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reader));
        when(personMinistryRepository.findAllByPersonId(1L)).thenReturn(List.of(activeReaderMinistry));
        when(eventAssignmentRepository.existsActiveOrFutureByPersonIdAndAssignmentType(eq(1L), eq(EventAssignmentType.READER), any()))
                .thenReturn(true);

        assertThrows(DatabaseException.class, () -> service.syncMinistries(1L, Set.of()));

        assertTrue(activeReaderMinistry.getActive());
        verify(personMinistryRepository, never()).save(any());
    }

    @Test
    void shouldBlockEntireSyncWhenAnyDeactivationConflictsEvenIfOtherChangesWouldSucceed() {
        Person reader = reader(1L);
        PersonMinistry activeReaderMinistry = personMinistry(reader, MinistryType.READER);
        PersonMinistry activeCommentatorMinistry = personMinistry(reader, MinistryType.COMMENTATOR);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reader));
        when(personMinistryRepository.findAllByPersonId(1L))
                .thenReturn(List.of(activeReaderMinistry, activeCommentatorMinistry));
        when(eventAssignmentRepository.existsActiveOrFutureByPersonIdAndAssignmentType(eq(1L), eq(EventAssignmentType.READER), any()))
                .thenReturn(true);
        when(eventAssignmentRepository.existsActiveOrFutureByPersonIdAndAssignmentType(eq(1L), eq(EventAssignmentType.COMMENTATOR), any()))
                .thenReturn(false);

        assertThrows(DatabaseException.class,
                () -> service.syncMinistries(1L, Set.of(MinistryType.EUCHARISTIC_MINISTER)));

        assertTrue(activeReaderMinistry.getActive());
        assertTrue(activeCommentatorMinistry.getActive());
        verify(personMinistryRepository, never()).save(any());
    }

    @Test
    void shouldNotBlockSyncWhenConflictingAssignmentIsOfADifferentTypeDuringSync() {
        Person reader = reader(1L);
        PersonMinistry activeMinistry = personMinistry(reader, MinistryType.READER);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reader));
        when(personMinistryRepository.findAllByPersonId(1L)).thenReturn(List.of(activeMinistry));
        when(eventAssignmentRepository.existsActiveOrFutureByPersonIdAndAssignmentType(eq(1L), eq(EventAssignmentType.READER), any()))
                .thenReturn(false);

        PersonMinistrySyncResult result = service.syncMinistries(1L, Set.of());

        assertEquals(Set.of(MinistryType.READER), result.deactivated());
        assertFalse(activeMinistry.getActive());
    }

    @Test
    void shouldTreatEmptyDesiredSetAndNoExistingMinistriesAsNoOp() {
        Person reader = reader(1L);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reader));
        when(personMinistryRepository.findAllByPersonId(1L)).thenReturn(List.of());

        PersonMinistrySyncResult result = service.syncMinistries(1L, Set.of());

        assertTrue(result.activeMinistries().isEmpty());
        assertTrue(result.added().isEmpty());
        assertTrue(result.reactivated().isEmpty());
        assertTrue(result.deactivated().isEmpty());
        assertTrue(result.unchanged().isEmpty());
        verify(personMinistryRepository, never()).save(any());
        verifyNoInteractions(eventAssignmentRepository);
    }

    @Test
    void shouldRejectSyncWithInvalidId() {
        assertThrows(BusinessException.class, () -> service.syncMinistries(null, Set.of(MinistryType.READER)));
        assertThrows(BusinessException.class, () -> service.syncMinistries(0L, Set.of(MinistryType.READER)));
        assertThrows(BusinessException.class, () -> service.syncMinistries(-1L, Set.of(MinistryType.READER)));
        verifyNoInteractions(personRepository);
    }

    @Test
    void shouldRejectSyncWithNullDesiredSet() {
        assertThrows(BusinessException.class, () -> service.syncMinistries(1L, null));
        verifyNoInteractions(personRepository);
    }

    @Test
    void shouldThrowResourceNotFoundWhenSyncingMinistriesOfMissingPerson() {
        when(personRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.syncMinistries(99L, Set.of(MinistryType.READER)));
        verifyNoInteractions(personMinistryRepository, eventAssignmentRepository);
    }

    @Test
    void shouldRejectSyncWithMissingPersistentMinistryBeforePessimisticLock() {
        Person reader = reader(1L);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reader));
        when(ministryRepository.findAllById(List.of(42L))).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class, () -> service.syncMinistriesById(1L, List.of(42L)));

        verify(ministryRepository, never()).findAllByIdInForUpdate(anyCollection());
        verify(personMinistryRepository, never()).findAllByPersonId(anyLong());
    }

    @Test
    void shouldRejectSyncWhenDesiredPersistentMinistryIsInactive() {
        Person reader = reader(1L);
        Long readerMinistryId = ministryId(MinistryType.READER);
        Ministry inactiveReaderMinistry = unitMinistry(MinistryType.READER);
        inactiveReaderMinistry.deactivate();
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reader));
        when(ministryRepository.findAllById(List.of(readerMinistryId))).thenReturn(List.of(inactiveReaderMinistry));
        when(ministryRepository.findAllByIdInForUpdate(List.of(readerMinistryId))).thenReturn(List.of(inactiveReaderMinistry));
        when(personMinistryRepository.findAllByPersonId(1L)).thenReturn(List.of());

        assertThrows(MinistryInactiveException.class,
                () -> service.syncMinistriesById(1L, List.of(readerMinistryId)));

        verify(personMinistryRepository, never()).save(any());
    }

    @Test
    void shouldRejectSyncAddForMinistryWithoutLegacyMappingWithDomainException() {
        Person reader = reader(1L);
        Long arbitraryMinistryId = 42L;
        Ministry acolytes = ministry(arbitraryMinistryId, "Acolitos");
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reader));
        doReturn(List.of(acolytes)).when(ministryRepository).findAllById(List.of(arbitraryMinistryId));
        doReturn(List.of(acolytes)).when(ministryRepository).findAllByIdInForUpdate(List.of(arbitraryMinistryId));
        when(personMinistryRepository.findAllByPersonId(1L)).thenReturn(List.of());
        doThrow(new IllegalStateException("no legacy mapping"))
                .when(legacyMinistryTypeResolver)
                .requireTypesByPersistentMinistryId(Set.of(arbitraryMinistryId));

        assertThrows(MinistryLegacyCompatibilityRequiredException.class,
                () -> service.syncMinistriesById(1L, List.of(arbitraryMinistryId)));

        verify(personMinistryRepository, never()).save(any());
    }

    @Test
    void shouldTranslateConstraintViolationOnSyncAddToDatabaseException() {
        Person reader = reader(1L);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reader));
        when(personMinistryRepository.findAllByPersonId(1L)).thenReturn(List.of());
        when(personMinistryRepository.save(any(PersonMinistry.class)))
                .thenThrow(new DataIntegrityViolationException("constraint"));

        assertThrows(DatabaseException.class, () -> service.syncMinistries(1L, Set.of(MinistryType.READER)));
    }

    @Test
    void shouldCreateMinistryWhenNoneExistsOnAddOrReactivate() {
        Person reader = reader(1L);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reader));
        whenPersonMinistry(1L, MinistryType.COMMENTATOR)
                .thenReturn(Optional.empty());

        Person result = service.addOrReactivateMinistry(1L, MinistryType.COMMENTATOR);

        assertSame(reader, result);
        ArgumentCaptor<PersonMinistry> captor = ArgumentCaptor.forClass(PersonMinistry.class);
        verify(personMinistryRepository).save(captor.capture());
        assertSame(reader, captor.getValue().getPerson());
        assertEquals(MinistryType.COMMENTATOR, captor.getValue().getMinistryType());
        assertTrue(captor.getValue().getActive());
        assertFalse(captor.getValue().getCoordinator());
    }

    @Test
    void shouldReactivateInactiveMinistryOnAddOrReactivateWithoutRestoringCoordinator() {
        Person reader = reader(1L);
        PersonMinistry inactive = personMinistry(reader, MinistryType.READER);
        inactive.deactivate();
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reader));
        whenPersonMinistry(1L, MinistryType.READER)
                .thenReturn(Optional.of(inactive));

        service.addOrReactivateMinistry(1L, MinistryType.READER);

        assertTrue(inactive.getActive());
        assertFalse(inactive.getCoordinator());
        verify(personMinistryRepository).save(inactive);
    }

    @Test
    void shouldBeNoOpAndPreserveCoordinatorWhenMinistryAlreadyActiveOnAddOrReactivate() {
        Person reader = reader(1L);
        PersonMinistry active = personMinistry(reader, MinistryType.READER);
        active.grantCoordination();
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reader));
        whenPersonMinistry(1L, MinistryType.READER)
                .thenReturn(Optional.of(active));

        service.addOrReactivateMinistry(1L, MinistryType.READER);

        assertTrue(active.getActive());
        assertTrue(active.getCoordinator());
        verify(personMinistryRepository, never()).save(any());
    }

    @Test
    void shouldRejectAddOrReactivateWhenPersonIsInactive() {
        Person inactivePerson = reader(1L);
        inactivePerson.deactivate();
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(inactivePerson));

        assertThrows(com.eventoscelebrativos.exception.exceptions.MinistryPersonInactiveException.class,
                () -> service.addOrReactivateMinistry(1L, MinistryType.READER));

        verifyNoInteractions(personMinistryRepository);
    }

    @Test
    void shouldRejectAddOrReactivateWhenPersistentMinistryIsInactive() {
        Person reader = reader(1L);
        Long readerMinistryId = ministryId(MinistryType.READER);
        Ministry inactiveReaderMinistry = unitMinistry(MinistryType.READER);
        inactiveReaderMinistry.deactivate();
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reader));
        when(ministryRepository.findByIdForUpdate(readerMinistryId)).thenReturn(Optional.of(inactiveReaderMinistry));

        assertThrows(MinistryInactiveException.class,
                () -> service.addOrReactivateMinistry(1L, unitMinistry(MinistryType.READER)));

        verify(personMinistryRepository, never()).save(any());
    }

    @Test
    void shouldThrowResourceNotFoundWhenAddOrReactivateTargetsMissingPerson() {
        when(personRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.addOrReactivateMinistry(99L, MinistryType.READER));
    }

    @Test
    void shouldRejectAddOrReactivateWithInvalidArguments() {
        assertThrows(BusinessException.class, () -> service.addOrReactivateMinistry(null, MinistryType.READER));
        assertThrows(BusinessException.class, () -> service.addOrReactivateMinistry(0L, MinistryType.READER));
        assertThrows(BusinessException.class, () -> service.addOrReactivateMinistry(1L, (MinistryType) null));
        verifyNoInteractions(personRepository);
    }

    private Person reader(Long id) {
        Person reader = new Person("Reader", "34999999991", LocalDate.of(1990, 1, 1));
        ReflectionTestUtils.setField(reader, "id", id);
        return reader;
    }

    private org.mockito.stubbing.OngoingStubbing<Optional<PersonMinistry>> whenPersonMinistry(
            Long personId,
            MinistryType ministryType
    ) {
        return when(personMinistryRepository.findByPersonIdAndMinistryId(personId, ministryId(ministryType)));
    }

    private Long ministryId(MinistryType ministryType) {
        return unitMinistry(ministryType).getId();
    }

    private MinistryType ministryTypeFor(Ministry ministry) {
        return ministryTypeFor(ministry.getId());
    }

    private MinistryType ministryTypeFor(Long ministryId) {
        for (MinistryType ministryType : MinistryType.values()) {
            if (ministryId(ministryType).equals(ministryId)) {
                return ministryType;
            }
        }
        throw new IllegalArgumentException("Ministry de teste sem tipo legado correspondente");
    }

    private boolean isKnownMinistryId(Long ministryId) {
        for (MinistryType ministryType : MinistryType.values()) {
            if (ministryId(ministryType).equals(ministryId)) {
                return true;
            }
        }
        return false;
    }

    private Ministry ministry(Long id, String name) {
        Ministry ministry = new Ministry(name);
        ReflectionTestUtils.setField(ministry, "id", id);
        return ministry;
    }
}
