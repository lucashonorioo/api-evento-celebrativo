package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.EucharisticMinisterRequestDTO;
import com.eventoscelebrativos.dto.request.EucharisticMinisterUpdateRequestDTO;
import com.eventoscelebrativos.dto.response.EucharisticMinisterResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.mapper.EucharisticMinisterMapper;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.service.impl.EucharisticMinisterServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EucharisticMinisterServiceImplTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1992, 3, 12);
    private static final String FIND_ENTITY_LABEL = "Ministro De Eucaristia";
    private static final String MUTATION_ENTITY_LABEL = "Ministro de Eucaristia";

    @Mock
    private EucharisticMinisterMapper mapper;

    @Mock
    private PersonAccountCoordinator personAccountCoordinator;

    @Mock
    private PersonMinistryCommandService personMinistryCommandService;

    @Mock
    private PersonMinistryReadService personMinistryReadService;

    @Mock
    private PersonCadastralUpdateService personCadastralUpdateService;

    @InjectMocks
    private EucharisticMinisterServiceImpl service;

    @Test
    void shouldCreateEucharisticMinisterAndProvisionAccessAfterPersisting() {
        EucharisticMinisterRequestDTO request = request();
        Person entity = minister(null);
        Person saved = minister(1L);
        EucharisticMinisterResponseDTO response = response(1L);

        when(mapper.toEntity(request)).thenReturn(entity);
        when(personMinistryCommandService.create(entity, MinistryType.EUCHARISTIC_MINISTER)).thenReturn(saved);
        when(mapper.toDtoFromPerson(saved)).thenReturn(response);

        assertSame(response, service.createEucharisticMinister(request));

        verify(personMinistryCommandService).create(entity, MinistryType.EUCHARISTIC_MINISTER);
        verify(personAccountCoordinator).provisionAccess(saved, request);
    }

    @Test
    void shouldPropagateProvisioningFailureAfterPersonAlreadyCreated() {
        EucharisticMinisterRequestDTO request = request();
        Person entity = minister(null);
        Person saved = minister(1L);
        when(mapper.toEntity(request)).thenReturn(entity);
        when(personMinistryCommandService.create(entity, MinistryType.EUCHARISTIC_MINISTER)).thenReturn(saved);
        doThrow(new ResourceNotFoundException("Perfil de acesso", "ROLE_OPERATOR"))
                .when(personAccountCoordinator).provisionAccess(saved, request);

        assertThrows(ResourceNotFoundException.class, () -> service.createEucharisticMinister(request));
        verify(personMinistryCommandService).create(entity, MinistryType.EUCHARISTIC_MINISTER);
    }

    @Test
    void shouldFindEucharisticMinisterByIdWhenExists() {
        Person entity = minister(1L);
        EucharisticMinisterResponseDTO response = response(1L);
        when(personMinistryCommandService.requireActiveMinistryPerson(1L, MinistryType.EUCHARISTIC_MINISTER, FIND_ENTITY_LABEL)).thenReturn(entity);
        when(mapper.toDtoFromPerson(entity)).thenReturn(response);

        assertSame(response, service.findEucharisticMinistersById(1L));
    }

    @Test
    void shouldThrowWhenEucharisticMinisterIdIsInvalidOrMissing() {
        assertThrows(BusinessException.class, () -> service.findEucharisticMinistersById(null));
        assertThrows(BusinessException.class, () -> service.findEucharisticMinistersById(0L));
        when(personMinistryCommandService.requireActiveMinistryPerson(99L, MinistryType.EUCHARISTIC_MINISTER, FIND_ENTITY_LABEL))
                .thenThrow(new ResourceNotFoundException(FIND_ENTITY_LABEL, 99L));
        assertThrows(ResourceNotFoundException.class, () -> service.findEucharisticMinistersById(99L));
    }

    @Test
    void shouldUpdateAndDeleteEucharisticMinister() {
        Person entity = minister(1L);
        EucharisticMinisterResponseDTO response = response(1L);
        EucharisticMinisterUpdateRequestDTO request = updateRequest();

        when(personMinistryCommandService.requireActiveMinistryPersonForUpdate(1L, MinistryType.EUCHARISTIC_MINISTER, MUTATION_ENTITY_LABEL)).thenReturn(entity);
        when(personCadastralUpdateService.updateCadastral(entity)).thenReturn(entity);
        when(mapper.toDtoFromPerson(entity)).thenReturn(response);
        assertSame(response, service.updateEucharisticMinisters(1L, request));
        verify(mapper).updateEucharisticMinisterFromDto(request, entity);
        verifyNoInteractions(personAccountCoordinator);

        service.deleteEucharisticMinisterById(1L);
        verify(personMinistryCommandService).removeMinistry(1L, MinistryType.EUCHARISTIC_MINISTER, MUTATION_ENTITY_LABEL);
    }

    @Test
    void shouldListEucharisticMinistersUsingPersonMinistryWithoutCallingLegacyRepository() {
        Person readerWithEucharisticMinisterMinistry = reader(2L);
        EucharisticMinisterResponseDTO response = new EucharisticMinisterResponseDTO(
                2L,
                "Minister",
                "34999999993",
                BIRTHDAY
        );
        List<Person> people = List.of(readerWithEucharisticMinisterMinistry);
        List<EucharisticMinisterResponseDTO> responses = List.of(response);

        when(personMinistryReadService.findAllActivePeopleByMinistry(MinistryType.EUCHARISTIC_MINISTER))
                .thenReturn(people);
        when(mapper.toDtoPersonList(people)).thenReturn(responses);

        assertSame(responses, service.findAllEucharisticMinisters());

        verify(personMinistryReadService).findAllActivePeopleByMinistry(MinistryType.EUCHARISTIC_MINISTER);
        verify(mapper).toDtoPersonList(people);
        verifyNoInteractions(personMinistryCommandService);
    }

    @Test
    void shouldPreserveEucharisticMinisterOrderReturnedByPersonMinistryReadService() {
        Person first = minister(1L);
        Person second = reader(2L);
        List<Person> people = List.of(first, second);
        List<EucharisticMinisterResponseDTO> responses = List.of(response(1L), response(2L));

        when(personMinistryReadService.findAllActivePeopleByMinistry(MinistryType.EUCHARISTIC_MINISTER))
                .thenReturn(people);
        when(mapper.toDtoPersonList(people)).thenReturn(responses);

        assertSame(responses, service.findAllEucharisticMinisters());

        verifyNoInteractions(personMinistryCommandService);
    }

    @Test
    void shouldPropagateOfficialReadFailureWithoutFallback() {
        RuntimeException officialFailure = new IllegalStateException("official read failed");

        when(personMinistryReadService.findAllActivePeopleByMinistry(MinistryType.EUCHARISTIC_MINISTER))
                .thenThrow(officialFailure);

        assertSame(officialFailure, assertThrows(RuntimeException.class, () -> service.findAllEucharisticMinisters()));
        verifyNoInteractions(personMinistryCommandService, mapper);
    }

    @Test
    void shouldThrowWhenUpdatingOrDeletingMissingEucharisticMinister() {
        when(personMinistryCommandService.requireActiveMinistryPersonForUpdate(99L, MinistryType.EUCHARISTIC_MINISTER, MUTATION_ENTITY_LABEL))
                .thenThrow(new ResourceNotFoundException(MUTATION_ENTITY_LABEL, 99L));
        assertThrows(ResourceNotFoundException.class, () -> service.updateEucharisticMinisters(99L, updateRequest()));

        doThrow(new ResourceNotFoundException(MUTATION_ENTITY_LABEL, 99L))
                .when(personMinistryCommandService).removeMinistry(99L, MinistryType.EUCHARISTIC_MINISTER, MUTATION_ENTITY_LABEL);
        assertThrows(ResourceNotFoundException.class, () -> service.deleteEucharisticMinisterById(99L));
    }

    @Test
    void shouldThrowDatabaseExceptionWhenDeletingReferencedEucharisticMinister() {
        doThrow(new DatabaseException("Não é possível excluir este registro, pois ele possui vínculos com outros cadastros."))
                .when(personMinistryCommandService).removeMinistry(1L, MinistryType.EUCHARISTIC_MINISTER, MUTATION_ENTITY_LABEL);

        assertThrows(DatabaseException.class, () -> service.deleteEucharisticMinisterById(1L));
    }

    private EucharisticMinisterRequestDTO request() {
        return new EucharisticMinisterRequestDTO("Minister", "34999999993", BIRTHDAY, "raw-password");
    }

    private EucharisticMinisterUpdateRequestDTO updateRequest() {
        return new EucharisticMinisterUpdateRequestDTO("Minister", "34999999993", BIRTHDAY);
    }

    private Person minister(Long id) {
        Person minister = new Person("Minister", "34999999993", BIRTHDAY);
        ReflectionTestUtils.setField(minister, "id", id);
        return minister;
    }

    private Person reader(Long id) {
        Person reader = new Person("Minister", "34999999993", BIRTHDAY);
        ReflectionTestUtils.setField(reader, "id", id);
        return reader;
    }

    private EucharisticMinisterResponseDTO response(Long id) {
        return new EucharisticMinisterResponseDTO(id, "Minister", "34999999993", BIRTHDAY);
    }
}
