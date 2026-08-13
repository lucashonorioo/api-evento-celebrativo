package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.response.ParishStaffAssignmentDTO;
import com.eventoscelebrativos.dto.response.ParishStaffMemberDTO;
import com.eventoscelebrativos.dto.response.ParishStaffTeamResponseDTO;
import com.eventoscelebrativos.dto.response.PersonParishResponsibilitiesResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.exception.exceptions.LifecycleConflictException;
import com.eventoscelebrativos.exception.exceptions.ParishActivePastorAlreadyExistsException;
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
import com.eventoscelebrativos.service.ParishStaffAssignmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ParishStaffAssignmentServiceImpl implements ParishStaffAssignmentService {

    private final ParishProfileRepository parishProfileRepository;
    private final PersonRepository personRepository;
    private final PersonMinistryRepository personMinistryRepository;
    private final ParishStaffAssignmentRepository parishStaffAssignmentRepository;

    public ParishStaffAssignmentServiceImpl(
            ParishProfileRepository parishProfileRepository,
            PersonRepository personRepository,
            PersonMinistryRepository personMinistryRepository,
            ParishStaffAssignmentRepository parishStaffAssignmentRepository
    ) {
        this.parishProfileRepository = parishProfileRepository;
        this.personRepository = personRepository;
        this.personMinistryRepository = personMinistryRepository;
        this.parishStaffAssignmentRepository = parishStaffAssignmentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PersonParishResponsibilitiesResponseDTO findResponsibilitiesByPerson(Long personId) {
        validateId(personId);
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa", personId));
        List<ParishStaffAssignmentDTO> responsibilities = parishStaffAssignmentRepository
                .findByPersonIdOrderByResponsibilityAsc(personId).stream()
                .map(a -> new ParishStaffAssignmentDTO(a.getResponsibility(), a.isActive(), a.getCreatedAt(), a.getUpdatedAt()))
                .toList();
        return new PersonParishResponsibilitiesResponseDTO(person.getId(), person.getName(), responsibilities);
    }

    @Override
    @Transactional(readOnly = true)
    public ParishStaffTeamResponseDTO findCurrentTeam() {
        ParishStaffMemberDTO pastor = parishStaffAssignmentRepository
                .findActiveMembersByResponsibility(ParishResponsibilityType.PASTOR).stream()
                .findFirst()
                .map(m -> new ParishStaffMemberDTO(m.getPersonId(), m.getName()))
                .orElse(null);
        List<ParishStaffMemberDTO> secretaries = parishStaffAssignmentRepository
                .findActiveMembersByResponsibility(ParishResponsibilityType.PARISH_SECRETARY).stream()
                .map(m -> new ParishStaffMemberDTO(m.getPersonId(), m.getName()))
                .toList();
        return new ParishStaffTeamResponseDTO(pastor, secretaries);
    }

    @Override
    @Transactional
    public void grantPastor(Long personId) {
        validateId(personId);

        // Lock order (mutações de PASTOR): ParishProfile(id=1) -> Person -> ParishStaffAssignment ->
        // validação de PRIEST -> leitura/validação do PASTOR atual -> mutação.
        parishProfileRepository.findByIdForUpdate(ParishProfile.SINGLETON_ID)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil da paróquia", ParishProfile.SINGLETON_ID));
        Person person = personRepository.findByIdForUpdate(personId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa", personId));
        Optional<ParishStaffAssignment> existing = parishStaffAssignmentRepository
                .findByPersonIdAndResponsibilityForUpdate(personId, ParishResponsibilityType.PASTOR);

        if (!person.isActive()) {
            throw personInactive();
        }
        if (!hasActivePriestMinistry(personId)) {
            throw new PastorPriestMinistryRequiredException();
        }
        if (existing.isPresent() && existing.get().isActive()) {
            return;
        }
        if (parishStaffAssignmentRepository.findFirstByResponsibilityAndActiveTrue(ParishResponsibilityType.PASTOR).isPresent()) {
            throw new ParishActivePastorAlreadyExistsException();
        }

        if (existing.isPresent()) {
            existing.get().activate();
            parishStaffAssignmentRepository.save(existing.get());
        } else {
            parishStaffAssignmentRepository.save(new ParishStaffAssignment(person, ParishResponsibilityType.PASTOR));
        }
    }

    @Override
    @Transactional
    public void revokePastor(Long personId) {
        validateId(personId);

        // Mesmo lock order de grantPastor: ParishProfile(id=1) -> Person -> ParishStaffAssignment.
        parishProfileRepository.findByIdForUpdate(ParishProfile.SINGLETON_ID)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil da paróquia", ParishProfile.SINGLETON_ID));
        personRepository.findByIdForUpdate(personId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa", personId));
        Optional<ParishStaffAssignment> existing = parishStaffAssignmentRepository
                .findByPersonIdAndResponsibilityForUpdate(personId, ParishResponsibilityType.PASTOR);

        if (existing.isEmpty() || !existing.get().isActive()) {
            return;
        }
        existing.get().deactivate();
        parishStaffAssignmentRepository.save(existing.get());
    }

    @Override
    @Transactional
    public void grantSecretary(Long personId) {
        validateId(personId);

        // Lock order (PARISH_SECRETARY): Person -> ParishStaffAssignment -> mutação.
        Person person = personRepository.findByIdForUpdate(personId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa", personId));
        if (!person.isActive()) {
            throw personInactive();
        }
        Optional<ParishStaffAssignment> existing = parishStaffAssignmentRepository
                .findByPersonIdAndResponsibilityForUpdate(personId, ParishResponsibilityType.PARISH_SECRETARY);

        if (existing.isPresent() && existing.get().isActive()) {
            return;
        }
        if (existing.isPresent()) {
            existing.get().activate();
            parishStaffAssignmentRepository.save(existing.get());
        } else {
            parishStaffAssignmentRepository.save(new ParishStaffAssignment(person, ParishResponsibilityType.PARISH_SECRETARY));
        }
    }

    @Override
    @Transactional
    public void revokeSecretary(Long personId) {
        validateId(personId);

        personRepository.findByIdForUpdate(personId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa", personId));
        Optional<ParishStaffAssignment> existing = parishStaffAssignmentRepository
                .findByPersonIdAndResponsibilityForUpdate(personId, ParishResponsibilityType.PARISH_SECRETARY);

        if (existing.isEmpty() || !existing.get().isActive()) {
            return;
        }
        existing.get().deactivate();
        parishStaffAssignmentRepository.save(existing.get());
    }

    private boolean hasActivePriestMinistry(Long personId) {
        return personMinistryRepository.findByPersonIdAndMinistryType(personId, MinistryType.PRIEST)
                .map(PersonMinistry::getActive)
                .map(Boolean.TRUE::equals)
                .orElse(false);
    }

    private LifecycleConflictException personInactive() {
        return new LifecycleConflictException("Somente pessoa ativa pode receber responsabilidade paroquial.", "PERSON_INACTIVE");
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new BadRequestException("O Id deve ser positivo e não nulo");
        }
    }
}
