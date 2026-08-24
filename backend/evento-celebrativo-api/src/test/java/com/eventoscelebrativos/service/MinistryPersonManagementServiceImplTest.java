package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.MinistryPersonCreateRequestDTO;
import com.eventoscelebrativos.dto.request.MinistryPersonUpdateRequestDTO;
import com.eventoscelebrativos.dto.response.MinistryPersonResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.mapper.MinistryPersonMapper;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.service.impl.MinistryPersonManagementServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MinistryPersonManagementServiceImplTest {

    @Mock
    private MinistryPersonMapper ministryPersonMapper;

    @Mock
    private PersonMinistryCommandService personMinistryCommandService;

    @Mock
    private PersonMinistryReadService personMinistryReadService;

    @Mock
    private PersonCadastralUpdateService personCadastralUpdateService;

    @InjectMocks
    private MinistryPersonManagementServiceImpl service;

    @Test
    void shouldListActivePeopleOfMinistry() {
        Person person = person(1L);
        MinistryPersonResponseDTO dto = responseDto(1L);
        Page<Person> page = new PageImpl<>(List.of(person), PageRequest.of(0, 10), 1);
        when(personMinistryReadService.findActivePeopleByMinistry(eq(MinistryType.READER), any())).thenReturn(page);
        when(ministryPersonMapper.toDto(person)).thenReturn(dto);

        Page<MinistryPersonResponseDTO> result = service.findPeople(MinistryType.READER, 0, 10);

        assertEquals(1, result.getTotalElements());
        assertSame(dto, result.getContent().get(0));
    }

    @Test
    void shouldRejectNegativePageOnFindPeople() {
        assertThrows(BadRequestException.class, () -> service.findPeople(MinistryType.READER, -1, 10));
        verifyNoInteractions(personMinistryReadService);
    }

    @Test
    void shouldRejectPageSizeAboveMaximumOnFindPeople() {
        assertThrows(BadRequestException.class, () -> service.findPeople(MinistryType.READER, 0, 101));
        assertThrows(BadRequestException.class, () -> service.findPeople(MinistryType.READER, 0, 0));
        verifyNoInteractions(personMinistryReadService);
    }

    @Test
    void shouldRejectNullMinistryTypeOnFindPeople() {
        assertThrows(BusinessException.class, () -> service.findPeople(null, 0, 10));
        verifyNoInteractions(personMinistryReadService);
    }

    @Test
    void shouldFindPersonByIdWithinMinistryScope() {
        Person person = person(10L);
        MinistryPersonResponseDTO dto = responseDto(10L);
        when(personMinistryCommandService.requireActiveMinistryPerson(10L, MinistryType.COMMENTATOR, "Pessoa"))
                .thenReturn(person);
        when(ministryPersonMapper.toDto(person)).thenReturn(dto);

        assertSame(dto, service.findPersonById(MinistryType.COMMENTATOR, 10L));
    }

    @Test
    void shouldPropagateResourceNotFoundWhenPersonOutsideMinistryScope() {
        when(personMinistryCommandService.requireActiveMinistryPerson(10L, MinistryType.COMMENTATOR, "Pessoa"))
                .thenThrow(new ResourceNotFoundException("Pessoa", 10L));

        assertThrows(ResourceNotFoundException.class, () -> service.findPersonById(MinistryType.COMMENTATOR, 10L));
    }

    @Test
    void shouldCreatePersonAndDelegateToCommandService() {
        MinistryPersonCreateRequestDTO request = new MinistryPersonCreateRequestDTO("Nova Pessoa", "34999999999", LocalDate.of(1990, 1, 1));
        Person mapped = person(null);
        Person saved = person(5L);
        MinistryPersonResponseDTO dto = responseDto(5L);
        when(ministryPersonMapper.toEntity(request)).thenReturn(mapped);
        when(personMinistryCommandService.create(mapped, MinistryType.READER)).thenReturn(saved);
        when(ministryPersonMapper.toDto(saved)).thenReturn(dto);

        assertSame(dto, service.create(MinistryType.READER, request));

        verify(personMinistryCommandService).create(mapped, MinistryType.READER);
    }

    @Test
    void shouldUpdatePersonWithinMinistryScope() {
        MinistryPersonUpdateRequestDTO request = new MinistryPersonUpdateRequestDTO("Nome Atualizado", LocalDate.of(1991, 2, 2));
        Person person = person(7L);
        Person saved = person(7L);
        MinistryPersonResponseDTO dto = responseDto(7L);
        when(personMinistryCommandService.requireActiveMinistryPersonForUpdate(7L, MinistryType.READER, "Pessoa"))
                .thenReturn(person);
        when(personCadastralUpdateService.updateCadastral(person)).thenReturn(saved);
        when(ministryPersonMapper.toDto(saved)).thenReturn(dto);

        assertSame(dto, service.update(MinistryType.READER, 7L, request));

        verify(ministryPersonMapper).updateFromDto(request, person);
    }

    @Test
    void shouldAddOrReactivateMinistryDelegatingToCommandService() {
        Person person = person(3L);
        MinistryPersonResponseDTO dto = responseDto(3L);
        when(personMinistryCommandService.addOrReactivateMinistry(3L, MinistryType.EUCHARISTIC_MINISTER)).thenReturn(person);
        when(ministryPersonMapper.toDto(person)).thenReturn(dto);

        assertSame(dto, service.addOrReactivateMinistry(MinistryType.EUCHARISTIC_MINISTER, 3L));
    }

    @Test
    void shouldRemoveMinistryDelegatingToCommandService() {
        service.removeMinistry(MinistryType.READER, 9L);

        verify(personMinistryCommandService).removeMinistry(9L, MinistryType.READER, "Pessoa");
    }

    @Test
    void shouldRejectNullMinistryTypeOnAllOperations() {
        assertThrows(BusinessException.class, () -> service.findPersonById(null, 1L));
        assertThrows(BusinessException.class, () -> service.create(null, new MinistryPersonCreateRequestDTO()));
        assertThrows(BusinessException.class, () -> service.update(null, 1L, new MinistryPersonUpdateRequestDTO()));
        assertThrows(BusinessException.class, () -> service.addOrReactivateMinistry(null, 1L));
        assertThrows(BusinessException.class, () -> service.removeMinistry(null, 1L));
        verifyNoInteractions(personMinistryCommandService, personMinistryReadService, personCadastralUpdateService);
    }

    private Person person(Long id) {
        Person person = new Person(
                "Pessoa " + id,
                "3497" + (id == null ? "0000000" : String.format("%07d", id)),
                LocalDate.of(1990, 1, 1)
        );
        ReflectionTestUtils.setField(person, "id", id);
        return person;
    }

    private MinistryPersonResponseDTO responseDto(Long id) {
        return new MinistryPersonResponseDTO(id, "Pessoa " + id, "34970000000", LocalDate.of(1990, 1, 1));
    }
}
