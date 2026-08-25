package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.PersonPhoneNumberConflictException;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.service.PersonAccountCoordinator;
import com.eventoscelebrativos.service.PersonCadastralUpdateService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class PersonCadastralUpdateServiceImpl implements PersonCadastralUpdateService {

    private static final int PHONE_NUMBER_LENGTH = 11;
    private static final String PHONE_NUMBER_UNIQUE_CONSTRAINT = "uk_tb_person_phone_number";

    private final PersonRepository personRepository;
    private final PersonAccountCoordinator personAccountCoordinator;

    public PersonCadastralUpdateServiceImpl(
            PersonRepository personRepository,
            PersonAccountCoordinator personAccountCoordinator
    ) {
        this.personRepository = personRepository;
        this.personAccountCoordinator = personAccountCoordinator;
    }

    @Override
    @Transactional
    public Person updateCadastral(
            Person person,
            String name,
            String phoneNumber,
            LocalDate birthdayDate
    ) {
        if (person == null || person.getId() == null) {
            throw new BusinessException("Pessoa persistida é obrigatória para atualização cadastral");
        }
        String normalizedName = requireName(name);
        requirePhoneNumber(phoneNumber);
        requireBirthdayDate(birthdayDate);

        personRepository.findByPhoneNumber(phoneNumber)
                .filter(other -> !other.getId().equals(person.getId()))
                .ifPresent(other -> {
                    throw new PersonPhoneNumberConflictException("Telefone já está associado a outra pessoa.");
                });

        person.updateCadastralData(normalizedName, phoneNumber, birthdayDate);

        Person saved = saveOrTranslatePhoneNumberConflict(person);
        personAccountCoordinator.synchronizeAccountAfterPersonUpdate(saved);
        return saved;
    }

    /**
     * Flush explicito: garante que a constraint {@code uk_tb_person_phone_number} seja verificada
     * aqui dentro do try/catch, em vez de vazar apenas no commit da transacao. E a garantia final
     * contra a corrida entre a checagem amigavel acima (nao bloqueante, sem lock) e o commit de outra
     * transacao concorrente disputando o mesmo telefone novo - cenario que antes dependia de deadlock
     * de gap lock do InnoDB (SELECT ... FOR UPDATE sobre um valor ainda inexistente) para ser
     * detectado.
     */
    private Person saveOrTranslatePhoneNumberConflict(Person person) {
        try {
            return personRepository.saveAndFlush(person);
        } catch (DataIntegrityViolationException e) {
            String rootCauseMessage = rootCauseMessage(e);
            if (rootCauseMessage != null && rootCauseMessage.toLowerCase().contains(PHONE_NUMBER_UNIQUE_CONSTRAINT)) {
                throw new PersonPhoneNumberConflictException("Telefone já está associado a outra pessoa.");
            }
            throw e;
        }
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage();
    }

    private String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("O campo nome não pode ser vazio");
        }
        return name.trim();
    }

    private void requirePhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new BadRequestException("O campo telefone não pode ser vazio");
        }
        if (!phoneNumber.matches("[0-9]{" + PHONE_NUMBER_LENGTH + "}")) {
            throw new BadRequestException("O telefone deve ter 11 dígitos com o DD");
        }
    }

    private void requireBirthdayDate(LocalDate birthdayDate) {
        if (birthdayDate == null) {
            throw new BadRequestException("O campo da data não pode ser vazio");
        }
        if (!birthdayDate.isBefore(LocalDate.now())) {
            throw new BadRequestException("A data de nascimento só pode ser no passado");
        }
    }
}
