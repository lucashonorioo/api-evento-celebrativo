package com.eventoscelebrativos.service;

import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.PersonPhoneNumberConflictException;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.service.impl.PersonCadastralUpdateServiceImpl;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Autoridade unica de atualizacao cadastral (secao 10 da especificacao). Cobre validacao,
 * unicidade de telefone, sincronizacao de conta e ausencia de PasswordEncoder/alteracao de
 * role/ministerio/active/enabled - responsabilidades que permanecem em outros services.
 */
@ExtendWith(MockitoExtension.class)
class PersonCadastralUpdateServiceImplTest {

    @Mock
    private PersonRepository personRepository;

    @Mock
    private PersonAccountCoordinator personAccountCoordinator;

    @InjectMocks
    private PersonCadastralUpdateServiceImpl service;

    @Test
    void shouldPersistAndSynchronizeAccountWhenPhoneNumberIsAvailable() {
        Person person = person(1L, "Alice", "34999999991");
        when(personRepository.findByPhoneNumber("34999999991")).thenReturn(Optional.of(person));
        when(personRepository.saveAndFlush(person)).thenReturn(person);

        Person saved = service.updateCadastral(person);

        assertSame(person, saved);
        verify(personRepository).saveAndFlush(person);
        verify(personAccountCoordinator).synchronizeAccountAfterPersonUpdate(person);
    }

    @Test
    void shouldNotConflictWhenPhoneNumberBelongsToSamePerson() {
        Person person = person(1L, "Alice", "34999999991");
        when(personRepository.findByPhoneNumber("34999999991")).thenReturn(Optional.of(person));
        when(personRepository.saveAndFlush(person)).thenReturn(person);

        service.updateCadastral(person);

        verify(personRepository).saveAndFlush(person);
    }

    @Test
    void shouldRejectPhoneNumberAlreadyUsedByAnotherPerson() {
        Person person = person(1L, "Alice", "34999999991");
        Person other = person(2L, "Bob", "34999999991");
        when(personRepository.findByPhoneNumber("34999999991")).thenReturn(Optional.of(other));

        assertThrows(PersonPhoneNumberConflictException.class, () -> service.updateCadastral(person));

        verify(personRepository, never()).saveAndFlush(any());
        verifyNoInteractions(personAccountCoordinator);
    }

    /**
     * Simula a corrida real que antes dependia de deadlock de gap lock do InnoDB: a checagem
     * amigavel (sem lock) nao encontra conflito porque a outra transacao concorrente ainda nao
     * commitou, mas o flush explicito colide com a constraint uk_tb_person_phone_number quando a
     * outra transacao vence a corrida primeiro.
     */
    @Test
    void shouldTranslateUniqueConstraintViolationOnFlushToPersonPhoneNumberConflict() {
        Person person = person(1L, "Alice", "34999999991");
        when(personRepository.findByPhoneNumber("34999999991")).thenReturn(Optional.empty());
        when(personRepository.saveAndFlush(person)).thenThrow(phoneNumberConstraintViolation());

        assertThrows(PersonPhoneNumberConflictException.class, () -> service.updateCadastral(person));

        verifyNoInteractions(personAccountCoordinator);
    }

    @Test
    void shouldRethrowUnrelatedDataIntegrityViolationOnFlush() {
        Person person = person(1L, "Alice", "34999999991");
        DataIntegrityViolationException unrelated = new DataIntegrityViolationException("unrelated constraint");
        when(personRepository.findByPhoneNumber("34999999991")).thenReturn(Optional.empty());
        when(personRepository.saveAndFlush(person)).thenThrow(unrelated);

        DataIntegrityViolationException thrown = assertThrows(
                DataIntegrityViolationException.class, () -> service.updateCadastral(person));

        assertSame(unrelated, thrown);
        verifyNoInteractions(personAccountCoordinator);
    }

    private DataIntegrityViolationException phoneNumberConstraintViolation() {
        SQLIntegrityConstraintViolationException sqlException = new SQLIntegrityConstraintViolationException(
                "Duplicate entry '34999999991' for key 'tb_person.uk_tb_person_phone_number'"
        );
        ConstraintViolationException hibernateException = new ConstraintViolationException(
                "could not execute statement", sqlException, "uk_tb_person_phone_number");
        return new DataIntegrityViolationException("could not execute statement", hibernateException);
    }

    @Test
    void shouldRejectBlankName() {
        Person person = person(1L, "   ", "34999999991");
        assertThrows(BadRequestException.class, () -> service.updateCadastral(person));
        verifyNoInteractions(personRepository, personAccountCoordinator);
    }

    @Test
    void shouldTrimName() {
        Person person = person(1L, "  Alice  ", "34999999991");
        when(personRepository.findByPhoneNumber("34999999991")).thenReturn(Optional.empty());
        when(personRepository.saveAndFlush(person)).thenReturn(person);

        service.updateCadastral(person);

        assertEquals("Alice", person.getName());
    }

    @Test
    void shouldRejectPhoneNumberWithWrongLength() {
        Person person = person(1L, "Alice", "12345");
        assertThrows(BadRequestException.class, () -> service.updateCadastral(person));
        verifyNoInteractions(personRepository, personAccountCoordinator);
    }

    @Test
    void shouldRejectPhoneNumberWithNonNumericCharacters() {
        Person person = person(1L, "Alice", "abcdefghijk");
        assertThrows(BadRequestException.class, () -> service.updateCadastral(person));
        verifyNoInteractions(personRepository, personAccountCoordinator);
    }

    @Test
    void shouldRejectNullBirthdayDate() {
        Person person = person(1L, "Alice", "34999999991");
        person.setBirthdayDate(null);
        assertThrows(BadRequestException.class, () -> service.updateCadastral(person));
        verifyNoInteractions(personRepository, personAccountCoordinator);
    }

    @Test
    void shouldRejectFutureBirthdayDate() {
        Person person = person(1L, "Alice", "34999999991");
        person.setBirthdayDate(LocalDate.now().plusDays(1));
        assertThrows(BadRequestException.class, () -> service.updateCadastral(person));
        verifyNoInteractions(personRepository, personAccountCoordinator);
    }

    @Test
    void shouldRejectUnpersistedPerson() {
        Person person = person(null, "Alice", "34999999991");
        assertThrows(BusinessException.class, () -> service.updateCadastral(person));
        verifyNoInteractions(personRepository, personAccountCoordinator);
    }

    @Test
    void shouldRejectNullPerson() {
        assertThrows(BusinessException.class, () -> service.updateCadastral(null));
        verifyNoInteractions(personRepository, personAccountCoordinator);
    }

    private Person person(Long id, String name, String phoneNumber) {
        Person person = new Person(name, phoneNumber, LocalDate.of(1990, 1, 10));
        ReflectionTestUtils.setField(person, "id", id);
        person.setActive(true);
        return person;
    }
}
