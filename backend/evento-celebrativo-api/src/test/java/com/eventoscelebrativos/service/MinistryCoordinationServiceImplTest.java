package com.eventoscelebrativos.service;

import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.exception.exceptions.MinistryCoordinationRequiresActiveMinistryException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.model.Ministry;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.repository.MinistryRepository;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.service.impl.MinistryCoordinationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static com.eventoscelebrativos.support.LegacyMinistryTestFactory.personMinistry;
import static com.eventoscelebrativos.support.LegacyMinistryTestFactory.unitMinistry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MinistryCoordinationServiceImplTest {

    @Mock
    private PersonRepository personRepository;

    @Mock
    private MinistryRepository ministryRepository;

    @Mock
    private PersonMinistryRepository personMinistryRepository;

    private MinistryCoordinationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MinistryCoordinationServiceImpl(
                personRepository,
                ministryRepository,
                personMinistryRepository
        );
    }

    private Person person(Long id) {
        Person person = new Person("Pessoa " + id, "34978" + String.format("%06d", id), LocalDate.of(1990, 1, 1));
        ReflectionTestUtils.setField(person, "id", id);
        return person;
    }

    private PersonMinistry activeMinistry(Person person, MinistryType type) {
        return personMinistry(person, type);
    }

    // ---- grantCoordinator ----

    @Test
    void shouldGrantCoordinatorOnActiveMinistry() {
        Person person = person(1L);
        PersonMinistry ministry = activeMinistry(person, MinistryType.READER);
        Long readerMinistryId = mockMinistry(MinistryType.READER).getId();
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person));
        when(personMinistryRepository.findByPersonIdAndMinistryId(1L, readerMinistryId))
                .thenReturn(Optional.of(ministry));

        service.grantCoordinator(1L, readerMinistryId);

        assertTrue(ministry.getCoordinator());
        verify(personMinistryRepository).save(ministry);
    }

    @Test
    void shouldBeIdempotentWhenGrantingCoordinatorAlreadyGranted() throws Exception {
        Person person = person(1L);
        PersonMinistry ministry = activeMinistry(person, MinistryType.READER);
        ministry.grantCoordination();
        LocalDateTime originalUpdatedAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        setUpdatedAt(ministry, originalUpdatedAt);
        Long readerMinistryId = mockMinistry(MinistryType.READER).getId();
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person));
        when(personMinistryRepository.findByPersonIdAndMinistryId(1L, readerMinistryId))
                .thenReturn(Optional.of(ministry));

        service.grantCoordinator(1L, readerMinistryId);

        assertEquals(originalUpdatedAt, ministry.getUpdatedAt());
        verify(personMinistryRepository, never()).save(any());
    }

    @Test
    void shouldRejectGrantingCoordinatorWhenMembershipDoesNotExist() {
        Long readerMinistryId = mockMinistry(MinistryType.READER).getId();
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person(1L)));
        when(personMinistryRepository.findByPersonIdAndMinistryId(1L, readerMinistryId))
                .thenReturn(Optional.empty());

        assertThrows(MinistryCoordinationRequiresActiveMinistryException.class,
                () -> service.grantCoordinator(1L, readerMinistryId));
        verify(personMinistryRepository, never()).save(any());
    }

    @Test
    void shouldRejectGrantingCoordinatorWhenMinistryIsInactive() {
        Person person = person(1L);
        PersonMinistry ministry = activeMinistry(person, MinistryType.READER);
        ministry.deactivate();
        Long readerMinistryId = mockMinistry(MinistryType.READER).getId();
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person));
        when(personMinistryRepository.findByPersonIdAndMinistryId(1L, readerMinistryId))
                .thenReturn(Optional.of(ministry));

        assertThrows(MinistryCoordinationRequiresActiveMinistryException.class,
                () -> service.grantCoordinator(1L, readerMinistryId));
        verify(personMinistryRepository, never()).save(any());
    }

    @Test
    void shouldThrowResourceNotFoundWhenGrantingCoordinatorForNonexistentPerson() {
        Long readerMinistryId = mockMinistry(MinistryType.READER).getId();
        when(personRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.grantCoordinator(99L, readerMinistryId));
        verify(personMinistryRepository, never()).save(any());
    }

    @Test
    void shouldThrowResourceNotFoundWhenGrantingCoordinatorForNonexistentMinistry() {
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person(1L)));
        when(ministryRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.grantCoordinator(1L, 999L));

        verifyNoInteractions(personMinistryRepository);
    }

    @Test
    void shouldRejectInvalidMinistryIdOnGrant() {
        assertThrows(BadRequestException.class, () -> service.grantCoordinator(1L, null));
        assertThrows(BadRequestException.class, () -> service.grantCoordinator(1L, 0L));
        assertThrows(BadRequestException.class, () -> service.grantCoordinator(1L, -1L));

        verifyNoInteractions(ministryRepository, personRepository, personMinistryRepository);
    }

    @Test
    void shouldGrantCoordinatorOnDifferentPersonsForSameMinistry() {
        Person personA = person(1L);
        Person personB = person(2L);
        PersonMinistry ministryA = activeMinistry(personA, MinistryType.READER);
        PersonMinistry ministryB = activeMinistry(personB, MinistryType.READER);
        Long readerMinistryId = mockMinistry(MinistryType.READER).getId();
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(personA));
        when(personRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(personB));
        when(personMinistryRepository.findByPersonIdAndMinistryId(1L, readerMinistryId))
                .thenReturn(Optional.of(ministryA));
        when(personMinistryRepository.findByPersonIdAndMinistryId(2L, readerMinistryId))
                .thenReturn(Optional.of(ministryB));

        service.grantCoordinator(1L, readerMinistryId);
        service.grantCoordinator(2L, readerMinistryId);

        assertTrue(ministryA.getCoordinator());
        assertTrue(ministryB.getCoordinator());
    }

    @Test
    void shouldGrantCoordinatorOnMultipleMinistriesForSamePerson() {
        Person person = person(1L);
        PersonMinistry reader = activeMinistry(person, MinistryType.READER);
        PersonMinistry commentator = activeMinistry(person, MinistryType.COMMENTATOR);
        Long readerMinistryId = mockMinistry(MinistryType.READER).getId();
        Long commentatorMinistryId = mockMinistry(MinistryType.COMMENTATOR).getId();
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person));
        when(personMinistryRepository.findByPersonIdAndMinistryId(1L, readerMinistryId))
                .thenReturn(Optional.of(reader));
        when(personMinistryRepository.findByPersonIdAndMinistryId(1L, commentatorMinistryId))
                .thenReturn(Optional.of(commentator));

        service.grantCoordinator(1L, readerMinistryId);
        service.grantCoordinator(1L, commentatorMinistryId);

        assertTrue(reader.getCoordinator());
        assertTrue(commentator.getCoordinator());
    }

    @Test
    void shouldNotRequireUserAccountToGrantCoordinator() {
        // Nenhuma interacao com UserAccount/UserAccountRole em nenhum ponto do fluxo: o service nem
        // possui essas dependencias injetadas, provando estruturalmente que nao sao exigidas.
        Person person = person(1L);
        PersonMinistry ministry = activeMinistry(person, MinistryType.READER);
        Long readerMinistryId = mockMinistry(MinistryType.READER).getId();
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person));
        when(personMinistryRepository.findByPersonIdAndMinistryId(1L, readerMinistryId))
                .thenReturn(Optional.of(ministry));

        service.grantCoordinator(1L, readerMinistryId);

        assertTrue(ministry.getCoordinator());
    }

    // ---- revokeCoordinator ----

    @Test
    void shouldRevokeCoordinator() {
        Person person = person(1L);
        PersonMinistry ministry = activeMinistry(person, MinistryType.READER);
        ministry.grantCoordination();
        Long readerMinistryId = mockMinistry(MinistryType.READER).getId();
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person));
        when(personMinistryRepository.findByPersonIdAndMinistryId(1L, readerMinistryId))
                .thenReturn(Optional.of(ministry));

        service.revokeCoordinator(1L, readerMinistryId);

        assertFalse(ministry.getCoordinator());
        verify(personMinistryRepository).save(ministry);
    }

    @Test
    void shouldBeIdempotentWhenRevokingCoordinatorAlreadyFalse() throws Exception {
        Person person = person(1L);
        PersonMinistry ministry = activeMinistry(person, MinistryType.READER);
        LocalDateTime originalUpdatedAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        setUpdatedAt(ministry, originalUpdatedAt);
        Long readerMinistryId = mockMinistry(MinistryType.READER).getId();
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person));
        when(personMinistryRepository.findByPersonIdAndMinistryId(1L, readerMinistryId))
                .thenReturn(Optional.of(ministry));

        service.revokeCoordinator(1L, readerMinistryId);

        assertEquals(originalUpdatedAt, ministry.getUpdatedAt());
        verify(personMinistryRepository, never()).save(any());
    }

    @Test
    void shouldBeIdempotentWhenRevokingCoordinatorOnMissingMinistryMembership() {
        Long readerMinistryId = mockMinistry(MinistryType.READER).getId();
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person(1L)));
        when(personMinistryRepository.findByPersonIdAndMinistryId(1L, readerMinistryId))
                .thenReturn(Optional.empty());

        service.revokeCoordinator(1L, readerMinistryId);

        verify(personMinistryRepository, never()).save(any());
    }

    @Test
    void shouldBeIdempotentWhenRevokingCoordinatorOnInactiveMinistry() {
        Person person = person(1L);
        PersonMinistry ministry = activeMinistry(person, MinistryType.READER);
        ministry.deactivate();
        Long readerMinistryId = mockMinistry(MinistryType.READER).getId();
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person));
        when(personMinistryRepository.findByPersonIdAndMinistryId(1L, readerMinistryId))
                .thenReturn(Optional.of(ministry));

        service.revokeCoordinator(1L, readerMinistryId);

        verify(personMinistryRepository, never()).save(any());
    }

    @Test
    void shouldThrowResourceNotFoundWhenRevokingCoordinatorForNonexistentPerson() {
        Long readerMinistryId = mockMinistry(MinistryType.READER).getId();
        when(personRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.revokeCoordinator(99L, readerMinistryId));
    }

    @Test
    void shouldThrowResourceNotFoundWhenRevokingCoordinatorForNonexistentMinistry() {
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person(1L)));
        when(ministryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.revokeCoordinator(1L, 999L));

        verifyNoInteractions(personMinistryRepository);
    }

    @Test
    void shouldRejectInvalidMinistryIdOnRevoke() {
        assertThrows(BadRequestException.class, () -> service.revokeCoordinator(1L, null));
        assertThrows(BadRequestException.class, () -> service.revokeCoordinator(1L, 0L));
        assertThrows(BadRequestException.class, () -> service.revokeCoordinator(1L, -1L));

        verifyNoInteractions(ministryRepository, personRepository, personMinistryRepository);
    }

    private Ministry mockMinistry(MinistryType ministryType) {
        Ministry ministry = unitMinistry(ministryType);
        lenient().when(ministryRepository.findById(ministry.getId())).thenReturn(Optional.of(ministry));
        lenient().when(ministryRepository.findByIdForUpdate(ministry.getId())).thenReturn(Optional.of(ministry));
        return ministry;
    }

    private void setUpdatedAt(PersonMinistry ministry, LocalDateTime updatedAt) throws Exception {
        Field field = PersonMinistry.class.getDeclaredField("updatedAt");
        field.setAccessible(true);
        field.set(ministry, updatedAt);
    }
}
