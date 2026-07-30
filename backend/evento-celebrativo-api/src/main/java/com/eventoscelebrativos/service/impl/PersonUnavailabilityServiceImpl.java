package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.request.PersonUnavailabilityRequestDTO;
import com.eventoscelebrativos.dto.response.AdminUnavailabilityResponseDTO;
import com.eventoscelebrativos.dto.response.PersonUnavailabilityResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.mapper.PersonUnavailabilityMapper;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonUnavailability;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.repository.PersonUnavailabilityRepository;
import com.eventoscelebrativos.service.PersonUnavailabilityConflictService;
import com.eventoscelebrativos.service.PersonUnavailabilityService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;

@Service
public class PersonUnavailabilityServiceImpl implements PersonUnavailabilityService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_REASON_LENGTH = 500;

    private final PersonUnavailabilityRepository personUnavailabilityRepository;
    private final PersonRepository personRepository;
    private final PersonUnavailabilityConflictService personUnavailabilityConflictService;
    private final PersonUnavailabilityMapper personUnavailabilityMapper;
    private final Clock clock;

    public PersonUnavailabilityServiceImpl(
            PersonUnavailabilityRepository personUnavailabilityRepository,
            PersonRepository personRepository,
            PersonUnavailabilityConflictService personUnavailabilityConflictService,
            PersonUnavailabilityMapper personUnavailabilityMapper,
            Clock clock
    ) {
        this.personUnavailabilityRepository = personUnavailabilityRepository;
        this.personRepository = personRepository;
        this.personUnavailabilityConflictService = personUnavailabilityConflictService;
        this.personUnavailabilityMapper = personUnavailabilityMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PersonUnavailabilityResponseDTO> findMine(
            String phoneNumber,
            LocalDate startDate,
            LocalDate endDate,
            int page,
            int size
    ) {
        validateQueryRange(startDate, endDate, page, size);
        Person person = findAuthenticatedPerson(phoneNumber);

        Page<PersonUnavailability> result = personUnavailabilityRepository.findByPersonIdIntersecting(
                person.getId(),
                startDate,
                endDate,
                PageRequest.of(page, size)
        );

        return result.map(personUnavailabilityMapper::toDto);
    }

    @Override
    @Transactional
    public PersonUnavailabilityResponseDTO create(String phoneNumber, PersonUnavailabilityRequestDTO requestDTO) {
        LocalDate startDate = requestDTO.getStartDate();
        LocalDate endDate = requestDTO.getEndDate();
        validateTemporalRule(startDate, endDate);
        String reason = normalizeReason(requestDTO.getReason());

        Person person = lockAuthenticatedPerson(phoneNumber);

        personUnavailabilityConflictService.validateNoOverlap(person.getId(), startDate, endDate, null);
        personUnavailabilityConflictService.validateNoAssignmentConflict(person.getId(), startDate, endDate);

        PersonUnavailability entity = new PersonUnavailability(person, startDate, endDate, reason);
        PersonUnavailability saved = personUnavailabilityRepository.save(entity);

        return personUnavailabilityMapper.toDto(saved);
    }

    @Override
    @Transactional
    public PersonUnavailabilityResponseDTO update(String phoneNumber, Long id, PersonUnavailabilityRequestDTO requestDTO) {
        LocalDate startDate = requestDTO.getStartDate();
        LocalDate endDate = requestDTO.getEndDate();
        validateTemporalRule(startDate, endDate);
        String reason = normalizeReason(requestDTO.getReason());

        Person person = lockAuthenticatedPerson(phoneNumber);

        PersonUnavailability existing = personUnavailabilityRepository.findByIdAndPersonId(id, person.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Indisponibilidade", id));

        boolean unchanged = existing.getStartDate().equals(startDate)
                && existing.getEndDate().equals(endDate)
                && Objects.equals(existing.getReason(), reason);

        if (unchanged) {
            return personUnavailabilityMapper.toDto(existing);
        }

        personUnavailabilityConflictService.validateNoOverlap(person.getId(), startDate, endDate, id);
        personUnavailabilityConflictService.validateNoAssignmentConflict(person.getId(), startDate, endDate);

        existing.setStartDate(startDate);
        existing.setEndDate(endDate);
        existing.setReason(reason);
        PersonUnavailability saved = personUnavailabilityRepository.save(existing);

        return personUnavailabilityMapper.toDto(saved);
    }

    @Override
    @Transactional
    public void delete(String phoneNumber, Long id) {
        Person person = lockAuthenticatedPerson(phoneNumber);

        PersonUnavailability existing = personUnavailabilityRepository.findByIdAndPersonId(id, person.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Indisponibilidade", id));

        personUnavailabilityRepository.delete(existing);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUnavailabilityResponseDTO findByDate(LocalDate date) {
        if (date == null) {
            throw new BadRequestException("A data é obrigatória");
        }
        return new AdminUnavailabilityResponseDTO(
                date,
                personUnavailabilityConflictService.findUnavailablePeopleOnDate(date)
        );
    }

    private Person findAuthenticatedPerson(String phoneNumber) {
        return personRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa", phoneNumber));
    }

    private Person lockAuthenticatedPerson(String phoneNumber) {
        return personRepository.findByPhoneNumberForUpdate(phoneNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa", phoneNumber));
    }

    private void validateTemporalRule(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new BadRequestException("As datas inicial e final são obrigatórias");
        }
        if (startDate.isAfter(endDate)) {
            throw new BadRequestException("A data inicial não pode ser posterior à data final");
        }
        if (startDate.isBefore(LocalDate.now(clock))) {
            throw new BadRequestException("A data inicial não pode ser anterior à data atual");
        }
    }

    private void validateQueryRange(LocalDate startDate, LocalDate endDate, int page, int size) {
        if (startDate == null || endDate == null) {
            throw new BadRequestException("As datas inicial e final são obrigatórias");
        }
        if (startDate.isAfter(endDate)) {
            throw new BadRequestException("A data inicial não pode ser posterior à data final");
        }
        if (page < 0) {
            throw new BadRequestException("O número da página deve ser maior ou igual a zero");
        }
        if (size <= 0 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("O tamanho da página deve ser maior que zero e menor ou igual a 100");
        }
    }

    private String normalizeReason(String reason) {
        if (reason == null) {
            return null;
        }
        String trimmed = reason.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_REASON_LENGTH) {
            throw new BadRequestException("O motivo deve ter no máximo 500 caracteres");
        }
        return trimmed;
    }
}
