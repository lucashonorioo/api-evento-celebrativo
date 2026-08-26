package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.response.ParishStaffTeamResponseDTO;
import com.eventoscelebrativos.dto.response.PersonParishResponsibilitiesResponseDTO;
import com.eventoscelebrativos.exception.exceptions.LifecycleConflictException;
import com.eventoscelebrativos.exception.exceptions.ParishActivePastorAlreadyExistsException;
import com.eventoscelebrativos.exception.exceptions.ParishStaffIntegrityViolationException;
import com.eventoscelebrativos.exception.exceptions.PastorPriestMinistryRequiredException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.ParishProfile;
import com.eventoscelebrativos.model.ParishResponsibilityType;
import com.eventoscelebrativos.model.ParishStaffAssignment;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.repository.ParishProfileRepository;
import com.eventoscelebrativos.repository.ParishStaffAssignmentRepository;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.service.impl.ParishStaffAssignmentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.eventoscelebrativos.support.LegacyMinistryTestFactory.personMinistry;
import static com.eventoscelebrativos.support.LegacyMinistryTestFactory.unitMinistry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParishStaffAssignmentServiceImplTest {

    @Mock
    private ParishProfileRepository parishProfileRepository;

    @Mock
    private PersonRepository personRepository;

    @Mock
    private PersonMinistryRepository personMinistryRepository;

    @Mock
    private ParishStaffAssignmentRepository parishStaffAssignmentRepository;

    @Mock
    private LegacyMinistryTypeResolver legacyMinistryTypeResolver;

    private ParishStaffAssignmentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ParishStaffAssignmentServiceImpl(
                parishProfileRepository,
                personRepository,
                personMinistryRepository,
                parishStaffAssignmentRepository,
                legacyMinistryTypeResolver
        );
    }

    private Person activePerson(Long id, String name) {
        Person person = new Person(name, "34979" + String.format("%06d", id), LocalDate.of(1990, 1, 1));
        ReflectionTestUtils.setField(person, "id", id);
        person.activate();
        return person;
    }

    private Person inactivePerson(Long id) {
        Person person = new Person("Pessoa " + id, "34979" + String.format("%06d", id), LocalDate.of(1990, 1, 1));
        ReflectionTestUtils.setField(person, "id", id);
        person.deactivate();
        return person;
    }

    private PersonMinistry activePriestMinistry(Person person) {
        PersonMinistry ministry = personMinistry(person, MinistryType.PRIEST);
        ministry.setActive(true);
        return ministry;
    }

    private void mockParishProfileLock() {
        ParishProfile profile = mock(ParishProfile.class);
        when(parishProfileRepository.findByIdForUpdate(ParishProfile.SINGLETON_ID)).thenReturn(Optional.of(profile));
    }

    // ---- grantPastor ----

    @Test
    void shouldThrowResourceNotFoundWhenGrantingPastorToNonexistentPerson() {
        mockParishProfileLock();
        when(personRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.grantPastor(99L));
        verify(parishStaffAssignmentRepository, never()).save(any());
    }

    @Test
    void shouldRejectGrantingPastorToInactivePerson() {
        mockParishProfileLock();
        Person person = inactivePerson(1L);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person));
        when(parishStaffAssignmentRepository.findByPersonIdAndResponsibility(1L, ParishResponsibilityType.PASTOR))
                .thenReturn(Optional.empty());

        LifecycleConflictException exception = assertThrows(
                LifecycleConflictException.class, () -> service.grantPastor(1L));
        assertEquals("PERSON_INACTIVE", exception.getErrorCode());
        verify(parishStaffAssignmentRepository, never()).save(any());
    }

    @Test
    void shouldRejectGrantingPastorWithoutActivePriestMinistry() {
        mockParishProfileLock();
        Person person = activePerson(1L, "Padre Sem Ministerio");
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person));
        when(parishStaffAssignmentRepository.findByPersonIdAndResponsibility(1L, ParishResponsibilityType.PASTOR))
                .thenReturn(Optional.empty());
        Long priestMinistryId = mockPriestMinistry();
        when(personMinistryRepository.findByPersonIdAndMinistryId(1L, priestMinistryId)).thenReturn(Optional.empty());

        assertThrows(PastorPriestMinistryRequiredException.class, () -> service.grantPastor(1L));
        verify(parishStaffAssignmentRepository, never()).save(any());
    }

    @Test
    void shouldRejectGrantingPastorWhenPriestMinistryIsInactive() {
        mockParishProfileLock();
        Person person = activePerson(1L, "Padre Inativo No Ministerio");
        PersonMinistry inactivePriest = activePriestMinistry(person);
        inactivePriest.setActive(false);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person));
        when(parishStaffAssignmentRepository.findByPersonIdAndResponsibility(1L, ParishResponsibilityType.PASTOR))
                .thenReturn(Optional.empty());
        Long priestMinistryId = mockPriestMinistry();
        when(personMinistryRepository.findByPersonIdAndMinistryId(1L, priestMinistryId)).thenReturn(Optional.of(inactivePriest));

        assertThrows(PastorPriestMinistryRequiredException.class, () -> service.grantPastor(1L));
    }

    @Test
    void shouldGrantFirstPastorWhenNoneActive() {
        mockParishProfileLock();
        Person person = activePerson(1L, "Padre Miguel");
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person));
        when(parishStaffAssignmentRepository.findByPersonIdAndResponsibility(1L, ParishResponsibilityType.PASTOR))
                .thenReturn(Optional.empty());
        Long priestMinistryId = mockPriestMinistry();
        when(personMinistryRepository.findByPersonIdAndMinistryId(1L, priestMinistryId))
                .thenReturn(Optional.of(activePriestMinistry(person)));
        when(parishStaffAssignmentRepository.findByResponsibilityAndActiveTrue(ParishResponsibilityType.PASTOR))
                .thenReturn(List.of());

        service.grantPastor(1L);

        verify(parishStaffAssignmentRepository).save(any(ParishStaffAssignment.class));
    }

    @Test
    void shouldRejectSecondPastorWhenAnotherPersonIsAlreadyActivePastor() {
        mockParishProfileLock();
        Person person = activePerson(2L, "Padre Paulo");
        when(personRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(person));
        when(parishStaffAssignmentRepository.findByPersonIdAndResponsibility(2L, ParishResponsibilityType.PASTOR))
                .thenReturn(Optional.empty());
        Long priestMinistryId = mockPriestMinistry();
        when(personMinistryRepository.findByPersonIdAndMinistryId(2L, priestMinistryId))
                .thenReturn(Optional.of(activePriestMinistry(person)));

        Person otherPastor = activePerson(1L, "Padre Miguel");
        ParishStaffAssignment currentPastor = new ParishStaffAssignment(otherPastor, ParishResponsibilityType.PASTOR);
        when(parishStaffAssignmentRepository.findByResponsibilityAndActiveTrue(ParishResponsibilityType.PASTOR))
                .thenReturn(List.of(currentPastor));

        assertThrows(ParishActivePastorAlreadyExistsException.class, () -> service.grantPastor(2L));
        verify(parishStaffAssignmentRepository, never()).save(any());
    }

    @Test
    void shouldBeIdempotentWhenGrantingPastorToPersonAlreadyActivePastor() throws Exception {
        mockParishProfileLock();
        Person person = activePerson(1L, "Padre Miguel");
        ParishStaffAssignment existing = new ParishStaffAssignment(person, ParishResponsibilityType.PASTOR);
        LocalDateTime originalUpdatedAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        setUpdatedAt(existing, originalUpdatedAt);

        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person));
        when(parishStaffAssignmentRepository.findByPersonIdAndResponsibility(1L, ParishResponsibilityType.PASTOR))
                .thenReturn(Optional.of(existing));
        Long priestMinistryId = mockPriestMinistry();
        when(personMinistryRepository.findByPersonIdAndMinistryId(1L, priestMinistryId))
                .thenReturn(Optional.of(activePriestMinistry(person)));
        when(parishStaffAssignmentRepository.findByResponsibilityAndActiveTrue(ParishResponsibilityType.PASTOR))
                .thenReturn(List.of(existing));

        service.grantPastor(1L);

        assertEquals(originalUpdatedAt, existing.getUpdatedAt());
        verify(parishStaffAssignmentRepository, never()).save(any());
    }

    @Test
    void shouldReactivatePreviouslyRevokedPastorAssignment() {
        mockParishProfileLock();
        Person person = activePerson(1L, "Padre Miguel");
        ParishStaffAssignment existing = new ParishStaffAssignment(person, ParishResponsibilityType.PASTOR);
        existing.deactivate();

        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person));
        when(parishStaffAssignmentRepository.findByPersonIdAndResponsibility(1L, ParishResponsibilityType.PASTOR))
                .thenReturn(Optional.of(existing));
        Long priestMinistryId = mockPriestMinistry();
        when(personMinistryRepository.findByPersonIdAndMinistryId(1L, priestMinistryId))
                .thenReturn(Optional.of(activePriestMinistry(person)));
        when(parishStaffAssignmentRepository.findByResponsibilityAndActiveTrue(ParishResponsibilityType.PASTOR))
                .thenReturn(List.of());

        service.grantPastor(1L);

        assertTrue(existing.isActive());
        verify(parishStaffAssignmentRepository).save(existing);
    }

    @Test
    void shouldFollowLockOrderForGrantPastor() {
        mockParishProfileLock();
        Person person = activePerson(1L, "Padre Miguel");
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person));
        when(parishStaffAssignmentRepository.findByPersonIdAndResponsibility(1L, ParishResponsibilityType.PASTOR))
                .thenReturn(Optional.empty());
        Long priestMinistryId = mockPriestMinistry();
        when(personMinistryRepository.findByPersonIdAndMinistryId(1L, priestMinistryId))
                .thenReturn(Optional.of(activePriestMinistry(person)));
        when(parishStaffAssignmentRepository.findByResponsibilityAndActiveTrue(ParishResponsibilityType.PASTOR))
                .thenReturn(List.of());

        service.grantPastor(1L);

        InOrder inOrder = inOrder(parishProfileRepository, personRepository, parishStaffAssignmentRepository);
        inOrder.verify(parishProfileRepository).findByIdForUpdate(ParishProfile.SINGLETON_ID);
        inOrder.verify(personRepository).findByIdForUpdate(1L);
        inOrder.verify(parishStaffAssignmentRepository).findByPersonIdAndResponsibility(1L, ParishResponsibilityType.PASTOR);
        inOrder.verify(parishStaffAssignmentRepository).save(any());
    }

    @Test
    void shouldThrowIntegrityViolationWhenGrantingPastorWithMultipleActivePastorsAlready() {
        mockParishProfileLock();
        Person person = activePerson(1L, "Padre A");
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person));
        when(parishStaffAssignmentRepository.findByPersonIdAndResponsibility(1L, ParishResponsibilityType.PASTOR))
                .thenReturn(Optional.empty());
        Long priestMinistryId = mockPriestMinistry();
        when(personMinistryRepository.findByPersonIdAndMinistryId(1L, priestMinistryId))
                .thenReturn(Optional.of(activePriestMinistry(person)));

        Person personA = activePerson(1L, "Padre A");
        Person personB = activePerson(2L, "Padre B");
        ParishStaffAssignment corruptedA = new ParishStaffAssignment(personA, ParishResponsibilityType.PASTOR);
        ParishStaffAssignment corruptedB = new ParishStaffAssignment(personB, ParishResponsibilityType.PASTOR);
        when(parishStaffAssignmentRepository.findByResponsibilityAndActiveTrue(ParishResponsibilityType.PASTOR))
                .thenReturn(List.of(corruptedA, corruptedB));

        assertThrows(ParishStaffIntegrityViolationException.class, () -> service.grantPastor(1L));
        verify(parishStaffAssignmentRepository, never()).save(any());
    }

    // ---- revokePastor ----

    @Test
    void shouldRevokeActivePastor() {
        mockParishProfileLock();
        Person person = activePerson(1L, "Padre Miguel");
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person));
        ParishStaffAssignment existing = new ParishStaffAssignment(person, ParishResponsibilityType.PASTOR);
        when(parishStaffAssignmentRepository.findByPersonIdAndResponsibility(1L, ParishResponsibilityType.PASTOR))
                .thenReturn(Optional.of(existing));

        service.revokePastor(1L);

        assertFalse(existing.isActive());
        verify(parishStaffAssignmentRepository).save(existing);
    }

    @Test
    void shouldBeIdempotentWhenRevokingPastorNeverGranted() {
        mockParishProfileLock();
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(activePerson(1L, "Pessoa")));
        when(parishStaffAssignmentRepository.findByPersonIdAndResponsibility(1L, ParishResponsibilityType.PASTOR))
                .thenReturn(Optional.empty());

        service.revokePastor(1L);

        verify(parishStaffAssignmentRepository, never()).save(any());
    }

    @Test
    void shouldBeIdempotentWhenRevokingPastorAlreadyInactive() throws Exception {
        mockParishProfileLock();
        Person person = activePerson(1L, "Pessoa");
        ParishStaffAssignment existing = new ParishStaffAssignment(person, ParishResponsibilityType.PASTOR);
        existing.deactivate();
        LocalDateTime originalUpdatedAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        setUpdatedAt(existing, originalUpdatedAt);

        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person));
        when(parishStaffAssignmentRepository.findByPersonIdAndResponsibility(1L, ParishResponsibilityType.PASTOR))
                .thenReturn(Optional.of(existing));

        service.revokePastor(1L);

        assertEquals(originalUpdatedAt, existing.getUpdatedAt());
        verify(parishStaffAssignmentRepository, never()).save(any());
    }

    @Test
    void shouldThrowResourceNotFoundWhenRevokingPastorForNonexistentPerson() {
        mockParishProfileLock();
        when(personRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.revokePastor(99L));
    }

    @Test
    void shouldAllowRevokingSpecificPersonWhenMultiplePastorsAreActive() {
        mockParishProfileLock();
        Person person = activePerson(1L, "Padre A");
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person));
        ParishStaffAssignment existing = new ParishStaffAssignment(person, ParishResponsibilityType.PASTOR);
        when(parishStaffAssignmentRepository.findByPersonIdAndResponsibility(1L, ParishResponsibilityType.PASTOR))
                .thenReturn(Optional.of(existing));

        service.revokePastor(1L);

        assertFalse(existing.isActive());
        verify(parishStaffAssignmentRepository).save(existing);
        verify(parishStaffAssignmentRepository, never()).findByResponsibilityAndActiveTrue(any());
    }

    // ---- grantSecretary / revokeSecretary ----

    @Test
    void shouldGrantSecretaryWithoutRequiringAccount() {
        Person person = activePerson(1L, "Secretaria Sem Conta");
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person));
        when(parishStaffAssignmentRepository.findByPersonIdAndResponsibility(1L, ParishResponsibilityType.PARISH_SECRETARY))
                .thenReturn(Optional.empty());

        service.grantSecretary(1L);

        verify(parishStaffAssignmentRepository).save(any(ParishStaffAssignment.class));
        verify(parishProfileRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void shouldAllowMultipleActiveSecretariesSimultaneously() {
        Person personA = activePerson(1L, "Secretaria A");
        Person personB = activePerson(2L, "Secretaria B");
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(personA));
        when(personRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(personB));
        when(parishStaffAssignmentRepository.findByPersonIdAndResponsibility(1L, ParishResponsibilityType.PARISH_SECRETARY))
                .thenReturn(Optional.empty());
        when(parishStaffAssignmentRepository.findByPersonIdAndResponsibility(2L, ParishResponsibilityType.PARISH_SECRETARY))
                .thenReturn(Optional.empty());

        service.grantSecretary(1L);
        service.grantSecretary(2L);

        verify(parishStaffAssignmentRepository, times(2)).save(any(ParishStaffAssignment.class));
    }

    @Test
    void shouldRejectGrantingSecretaryToInactivePerson() {
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(inactivePerson(1L)));

        LifecycleConflictException exception = assertThrows(
                LifecycleConflictException.class, () -> service.grantSecretary(1L));
        assertEquals("PERSON_INACTIVE", exception.getErrorCode());
        verify(parishStaffAssignmentRepository, never()).save(any());
    }

    @Test
    void shouldBeIdempotentWhenGrantingSecretaryAlreadyActive() throws Exception {
        Person person = activePerson(1L, "Secretaria");
        ParishStaffAssignment existing = new ParishStaffAssignment(person, ParishResponsibilityType.PARISH_SECRETARY);
        LocalDateTime originalUpdatedAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        setUpdatedAt(existing, originalUpdatedAt);

        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person));
        when(parishStaffAssignmentRepository.findByPersonIdAndResponsibility(1L, ParishResponsibilityType.PARISH_SECRETARY))
                .thenReturn(Optional.of(existing));

        service.grantSecretary(1L);

        assertEquals(originalUpdatedAt, existing.getUpdatedAt());
        verify(parishStaffAssignmentRepository, never()).save(any());
    }

    @Test
    void shouldRemoveSecretary() {
        Person person = activePerson(1L, "Secretaria");
        ParishStaffAssignment existing = new ParishStaffAssignment(person, ParishResponsibilityType.PARISH_SECRETARY);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person));
        when(parishStaffAssignmentRepository.findByPersonIdAndResponsibility(1L, ParishResponsibilityType.PARISH_SECRETARY))
                .thenReturn(Optional.of(existing));

        service.revokeSecretary(1L);

        assertFalse(existing.isActive());
        verify(parishStaffAssignmentRepository).save(existing);
    }

    @Test
    void shouldBeIdempotentWhenRevokingSecretaryNeverGranted() {
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(activePerson(1L, "Pessoa")));
        when(parishStaffAssignmentRepository.findByPersonIdAndResponsibility(1L, ParishResponsibilityType.PARISH_SECRETARY))
                .thenReturn(Optional.empty());

        service.revokeSecretary(1L);

        verify(parishStaffAssignmentRepository, never()).save(any());
    }

    // ---- listagens ----

    @Test
    void shouldListResponsibilitiesByPerson() {
        Person person = activePerson(1L, "Pessoa");
        when(personRepository.findById(1L)).thenReturn(Optional.of(person));
        ParishStaffAssignment pastor = new ParishStaffAssignment(person, ParishResponsibilityType.PASTOR);
        pastor.deactivate();
        ParishStaffAssignment secretary = new ParishStaffAssignment(person, ParishResponsibilityType.PARISH_SECRETARY);
        when(parishStaffAssignmentRepository.findByPersonIdOrderByResponsibilityAsc(1L))
                .thenReturn(List.of(pastor, secretary));

        PersonParishResponsibilitiesResponseDTO response = service.findResponsibilitiesByPerson(1L);

        assertEquals(1L, response.getPersonId());
        assertEquals("Pessoa", response.getName());
        assertEquals(2, response.getResponsibilities().size());
        assertFalse(response.getResponsibilities().get(0).isActive());
        assertTrue(response.getResponsibilities().get(1).isActive());
    }

    @Test
    void shouldThrowResourceNotFoundWhenListingResponsibilitiesForNonexistentPerson() {
        when(personRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.findResponsibilitiesByPerson(99L));
    }

    @Test
    void shouldReturnCurrentTeamWithPastorAndOrderedSecretaries() {
        Person pastorPerson = activePerson(1L, "Padre Miguel");
        when(parishStaffAssignmentRepository.findByResponsibilityAndActiveTrue(ParishResponsibilityType.PASTOR))
                .thenReturn(List.of(new ParishStaffAssignment(pastorPerson, ParishResponsibilityType.PASTOR)));

        ParishStaffAssignmentRepository.ParishStaffMemberProjection pastorProjection =
                projection(1L, "Padre Miguel");
        ParishStaffAssignmentRepository.ParishStaffMemberProjection secretaryA = projection(2L, "Ana");
        ParishStaffAssignmentRepository.ParishStaffMemberProjection secretaryB = projection(3L, "Bruno");

        when(parishStaffAssignmentRepository.findActiveMembersByResponsibility(ParishResponsibilityType.PASTOR))
                .thenReturn(List.of(pastorProjection));
        when(parishStaffAssignmentRepository.findActiveMembersByResponsibility(ParishResponsibilityType.PARISH_SECRETARY))
                .thenReturn(List.of(secretaryA, secretaryB));

        ParishStaffTeamResponseDTO team = service.findCurrentTeam();

        assertEquals(1L, team.getPastor().getPersonId());
        assertEquals("Padre Miguel", team.getPastor().getName());
        assertEquals(2, team.getSecretaries().size());
        assertEquals("Ana", team.getSecretaries().get(0).getName());
        assertEquals("Bruno", team.getSecretaries().get(1).getName());
    }

    @Test
    void shouldReturnNullPastorWhenNoneActive() {
        when(parishStaffAssignmentRepository.findByResponsibilityAndActiveTrue(ParishResponsibilityType.PASTOR))
                .thenReturn(List.of());
        when(parishStaffAssignmentRepository.findActiveMembersByResponsibility(ParishResponsibilityType.PASTOR))
                .thenReturn(List.of());
        when(parishStaffAssignmentRepository.findActiveMembersByResponsibility(ParishResponsibilityType.PARISH_SECRETARY))
                .thenReturn(List.of());

        ParishStaffTeamResponseDTO team = service.findCurrentTeam();

        assertNull(team.getPastor());
        assertTrue(team.getSecretaries().isEmpty());
    }

    @Test
    void shouldThrowIntegrityViolationWhenFindCurrentTeamDetectsMultiplePastors() {
        Person personA = activePerson(1L, "Padre A");
        Person personB = activePerson(2L, "Padre B");
        when(parishStaffAssignmentRepository.findByResponsibilityAndActiveTrue(ParishResponsibilityType.PASTOR))
                .thenReturn(List.of(
                        new ParishStaffAssignment(personA, ParishResponsibilityType.PASTOR),
                        new ParishStaffAssignment(personB, ParishResponsibilityType.PASTOR)
                ));

        assertThrows(ParishStaffIntegrityViolationException.class, () -> service.findCurrentTeam());

        verify(parishStaffAssignmentRepository, never()).findActiveMembersByResponsibility(any());
    }

    private ParishStaffAssignmentRepository.ParishStaffMemberProjection projection(Long personId, String name) {
        ParishStaffAssignmentRepository.ParishStaffMemberProjection projection =
                mock(ParishStaffAssignmentRepository.ParishStaffMemberProjection.class);
        when(projection.getPersonId()).thenReturn(personId);
        when(projection.getName()).thenReturn(name);
        return projection;
    }

    private Long mockPriestMinistry() {
        Long ministryId = unitMinistry(MinistryType.PRIEST).getId();
        when(legacyMinistryTypeResolver.requireMinistry(MinistryType.PRIEST))
                .thenReturn(unitMinistry(MinistryType.PRIEST));
        return ministryId;
    }

    private void setUpdatedAt(ParishStaffAssignment assignment, LocalDateTime updatedAt) throws Exception {
        Field field = ParishStaffAssignment.class.getDeclaredField("updatedAt");
        field.setAccessible(true);
        field.set(assignment, updatedAt);
    }
}
