package com.eventoscelebrativos.service;

import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.exception.exceptions.MinistryCoordinationRequiresActiveMinistryException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.model.Ministry;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MinistryCoordinationServiceImplTest {

    @Mock
    private PersonRepository personRepository;

    @Mock
    private PersonMinistryRepository personMinistryRepository;

    @Mock
    private LegacyMinistryTypeResolver legacyMinistryTypeResolver;

    private MinistryCoordinationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MinistryCoordinationServiceImpl(
                personRepository,
                personMinistryRepository,
                legacyMinistryTypeResolver
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
        Long readerMinistryId = resolveMinistry("READER", MinistryType.READER);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person));
        when(personMinistryRepository.findByPersonIdAndMinistryId(1L, readerMinistryId))
                .thenReturn(Optional.of(ministry));

        service.grantCoordinator(1L, "READER");

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
        Long readerMinistryId = resolveMinistry("READER", MinistryType.READER);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person));
        when(personMinistryRepository.findByPersonIdAndMinistryId(1L, readerMinistryId))
                .thenReturn(Optional.of(ministry));

        service.grantCoordinator(1L, "READER");

        assertEquals(originalUpdatedAt, ministry.getUpdatedAt());
        verify(personMinistryRepository, never()).save(any());
    }

    @Test
    void shouldRejectGrantingCoordinatorWhenMinistryDoesNotExist() {
        Long readerMinistryId = resolveMinistry("READER", MinistryType.READER);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person(1L)));
        when(personMinistryRepository.findByPersonIdAndMinistryId(1L, readerMinistryId))
                .thenReturn(Optional.empty());

        assertThrows(MinistryCoordinationRequiresActiveMinistryException.class,
                () -> service.grantCoordinator(1L, "READER"));
        verify(personMinistryRepository, never()).save(any());
    }

    @Test
    void shouldRejectGrantingCoordinatorWhenMinistryIsInactive() {
        Person person = person(1L);
        PersonMinistry ministry = activeMinistry(person, MinistryType.READER);
        ministry.deactivate();
        Long readerMinistryId = resolveMinistry("READER", MinistryType.READER);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person));
        when(personMinistryRepository.findByPersonIdAndMinistryId(1L, readerMinistryId))
                .thenReturn(Optional.of(ministry));

        assertThrows(MinistryCoordinationRequiresActiveMinistryException.class,
                () -> service.grantCoordinator(1L, "READER"));
        verify(personMinistryRepository, never()).save(any());
    }

    @Test
    void shouldThrowResourceNotFoundWhenGrantingCoordinatorForNonexistentPerson() {
        resolveMinistry("READER", MinistryType.READER);
        when(personRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.grantCoordinator(99L, "READER"));
        verify(personMinistryRepository, never()).save(any());
    }

    @Test
    void shouldRejectInvalidMinistryTypeOnGrant() {
        rejectMinistryType("BISHOP");
        rejectMinistryType(null);
        rejectMinistryType("  ");

        assertThrows(BadRequestException.class, () -> service.grantCoordinator(1L, "BISHOP"));
        assertThrows(BadRequestException.class, () -> service.grantCoordinator(1L, null));
        assertThrows(BadRequestException.class, () -> service.grantCoordinator(1L, "  "));
    }

    @Test
    void shouldGrantCoordinatorOnDifferentPersonsForSameMinistry() {
        Person personA = person(1L);
        Person personB = person(2L);
        PersonMinistry ministryA = activeMinistry(personA, MinistryType.READER);
        PersonMinistry ministryB = activeMinistry(personB, MinistryType.READER);
        Long readerMinistryId = resolveMinistry("READER", MinistryType.READER);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(personA));
        when(personRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(personB));
        when(personMinistryRepository.findByPersonIdAndMinistryId(1L, readerMinistryId))
                .thenReturn(Optional.of(ministryA));
        when(personMinistryRepository.findByPersonIdAndMinistryId(2L, readerMinistryId))
                .thenReturn(Optional.of(ministryB));

        service.grantCoordinator(1L, "READER");
        service.grantCoordinator(2L, "READER");

        assertTrue(ministryA.getCoordinator());
        assertTrue(ministryB.getCoordinator());
    }

    @Test
    void shouldGrantCoordinatorOnMultipleMinistriesForSamePerson() {
        Person person = person(1L);
        PersonMinistry reader = activeMinistry(person, MinistryType.READER);
        PersonMinistry commentator = activeMinistry(person, MinistryType.COMMENTATOR);
        Long readerMinistryId = resolveMinistry("READER", MinistryType.READER);
        Long commentatorMinistryId = resolveMinistry("COMMENTATOR", MinistryType.COMMENTATOR);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person));
        when(personMinistryRepository.findByPersonIdAndMinistryId(1L, readerMinistryId))
                .thenReturn(Optional.of(reader));
        when(personMinistryRepository.findByPersonIdAndMinistryId(1L, commentatorMinistryId))
                .thenReturn(Optional.of(commentator));

        service.grantCoordinator(1L, "READER");
        service.grantCoordinator(1L, "COMMENTATOR");

        assertTrue(reader.getCoordinator());
        assertTrue(commentator.getCoordinator());
    }

    @Test
    void shouldNotRequireUserAccountToGrantCoordinator() {
        // Nenhuma interacao com UserAccount/UserAccountRole em nenhum ponto do fluxo: o service nem
        // possui essas dependencias injetadas, provando estruturalmente que nao sao exigidas.
        Person person = person(1L);
        PersonMinistry ministry = activeMinistry(person, MinistryType.READER);
        Long readerMinistryId = resolveMinistry("reader", MinistryType.READER);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person));
        when(personMinistryRepository.findByPersonIdAndMinistryId(1L, readerMinistryId))
                .thenReturn(Optional.of(ministry));

        service.grantCoordinator(1L, "reader");

        assertTrue(ministry.getCoordinator());
    }

    // ---- revokeCoordinator ----

    @Test
    void shouldRevokeCoordinator() {
        Person person = person(1L);
        PersonMinistry ministry = activeMinistry(person, MinistryType.READER);
        ministry.grantCoordination();
        Long readerMinistryId = resolveMinistry("READER", MinistryType.READER);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person));
        when(personMinistryRepository.findByPersonIdAndMinistryId(1L, readerMinistryId))
                .thenReturn(Optional.of(ministry));

        service.revokeCoordinator(1L, "READER");

        assertFalse(ministry.getCoordinator());
        verify(personMinistryRepository).save(ministry);
    }

    @Test
    void shouldBeIdempotentWhenRevokingCoordinatorAlreadyFalse() throws Exception {
        Person person = person(1L);
        PersonMinistry ministry = activeMinistry(person, MinistryType.READER);
        LocalDateTime originalUpdatedAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        setUpdatedAt(ministry, originalUpdatedAt);
        Long readerMinistryId = resolveMinistry("READER", MinistryType.READER);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person));
        when(personMinistryRepository.findByPersonIdAndMinistryId(1L, readerMinistryId))
                .thenReturn(Optional.of(ministry));

        service.revokeCoordinator(1L, "READER");

        assertEquals(originalUpdatedAt, ministry.getUpdatedAt());
        verify(personMinistryRepository, never()).save(any());
    }

    @Test
    void shouldBeIdempotentWhenRevokingCoordinatorOnMissingMinistry() {
        Long readerMinistryId = resolveMinistry("READER", MinistryType.READER);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person(1L)));
        when(personMinistryRepository.findByPersonIdAndMinistryId(1L, readerMinistryId))
                .thenReturn(Optional.empty());

        service.revokeCoordinator(1L, "READER");

        verify(personMinistryRepository, never()).save(any());
    }

    @Test
    void shouldBeIdempotentWhenRevokingCoordinatorOnInactiveMinistry() {
        Person person = person(1L);
        PersonMinistry ministry = activeMinistry(person, MinistryType.READER);
        ministry.deactivate();
        Long readerMinistryId = resolveMinistry("READER", MinistryType.READER);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(person));
        when(personMinistryRepository.findByPersonIdAndMinistryId(1L, readerMinistryId))
                .thenReturn(Optional.of(ministry));

        service.revokeCoordinator(1L, "READER");

        verify(personMinistryRepository, never()).save(any());
    }

    @Test
    void shouldThrowResourceNotFoundWhenRevokingCoordinatorForNonexistentPerson() {
        resolveMinistry("READER", MinistryType.READER);
        when(personRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.revokeCoordinator(99L, "READER"));
    }

    @Test
    void shouldRejectInvalidMinistryTypeOnRevoke() {
        rejectMinistryType("BISHOP");

        assertThrows(BadRequestException.class, () -> service.revokeCoordinator(1L, "BISHOP"));
    }

    private Long resolveMinistry(String rawMinistryType, MinistryType ministryType) {
        Ministry ministry = unitMinistry(ministryType);
        when(legacyMinistryTypeResolver.parseMinistryType(rawMinistryType)).thenReturn(ministryType);
        when(legacyMinistryTypeResolver.requireMinistry(ministryType)).thenReturn(ministry);
        return ministry.getId();
    }

    private void rejectMinistryType(String rawMinistryType) {
        when(legacyMinistryTypeResolver.parseMinistryType(rawMinistryType))
                .thenThrow(new BadRequestException("Tipo de ministerio invalido"));
    }

    private void setUpdatedAt(PersonMinistry ministry, LocalDateTime updatedAt) throws Exception {
        Field field = PersonMinistry.class.getDeclaredField("updatedAt");
        field.setAccessible(true);
        field.set(ministry, updatedAt);
    }
}
