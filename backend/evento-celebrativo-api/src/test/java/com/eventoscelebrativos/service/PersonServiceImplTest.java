package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.PersonRoleUpdateRequestDTO;
import com.eventoscelebrativos.dto.response.PersonRoleUpdateResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.mapper.PersonRoleUpdateMapper;
import com.eventoscelebrativos.model.Reader;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.repository.RoleRepository;
import com.eventoscelebrativos.service.impl.PersonServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersonServiceImplTest {

    @Mock
    private PersonRepository personRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PersonRoleUpdateMapper personRoleUpdateMapper;

    @InjectMocks
    private PersonServiceImpl service;

    @Test
    void shouldUpdatePersonRoleToAdmin() {
        Reader person = person("encoded-password");
        person.addRole(new Role(1L, "ROLE_OPERATOR"));
        Role adminRole = new Role(2L, "ROLE_ADMIN");

        when(personRepository.findById(1L)).thenReturn(Optional.of(person));
        when(roleRepository.findByAuthority("ROLE_ADMIN")).thenReturn(Optional.of(adminRole));
        when(personRepository.save(person)).thenReturn(person);
        when(personRoleUpdateMapper.toDto(person)).thenReturn(response("ROLE_ADMIN"));

        PersonRoleUpdateResponseDTO response = service.updatePersonRole(1L, new PersonRoleUpdateRequestDTO("ROLE_ADMIN"));

        assertEquals(1L, response.getId());
        assertEquals("Reader", response.getName());
        assertEquals("34999999991", response.getPhoneNumber());
        assertEquals(1, response.getRoles().size());
        assertEquals("ROLE_ADMIN", response.getRoles().get(0));
        assertTrue(person.hasRole("ROLE_ADMIN"));
        assertFalse(person.hasRole("ROLE_OPERATOR"));
    }

    @Test
    void shouldUpdatePersonRoleToOperator() {
        Reader person = person("encoded-password");
        person.addRole(new Role(2L, "ROLE_ADMIN"));
        Role operatorRole = new Role(1L, "ROLE_OPERATOR");

        when(personRepository.findById(1L)).thenReturn(Optional.of(person));
        when(roleRepository.findByAuthority("ROLE_OPERATOR")).thenReturn(Optional.of(operatorRole));
        when(personRepository.save(person)).thenReturn(person);
        when(personRoleUpdateMapper.toDto(person)).thenReturn(response("ROLE_OPERATOR"));

        PersonRoleUpdateResponseDTO response = service.updatePersonRole(1L, new PersonRoleUpdateRequestDTO("ROLE_OPERATOR"));

        assertEquals(1, response.getRoles().size());
        assertEquals("ROLE_OPERATOR", response.getRoles().get(0));
        assertTrue(person.hasRole("ROLE_OPERATOR"));
        assertFalse(person.hasRole("ROLE_ADMIN"));
    }

    @Test
    void shouldNotChangePasswordWhenUpdatingRole() {
        Reader person = person("encoded-password");
        Role adminRole = new Role(2L, "ROLE_ADMIN");

        when(personRepository.findById(1L)).thenReturn(Optional.of(person));
        when(roleRepository.findByAuthority("ROLE_ADMIN")).thenReturn(Optional.of(adminRole));
        when(personRepository.save(person)).thenReturn(person);
        when(personRoleUpdateMapper.toDto(person)).thenReturn(response("ROLE_ADMIN"));

        service.updatePersonRole(1L, new PersonRoleUpdateRequestDTO("ROLE_ADMIN"));

        ArgumentCaptor<Reader> captor = ArgumentCaptor.forClass(Reader.class);
        verify(personRepository).save(captor.capture());
        assertEquals("encoded-password", captor.getValue().getPassword());
    }

    @Test
    void shouldThrowBusinessExceptionWhenIdIsInvalid() {
        assertAll(
                () -> assertThrows(BusinessException.class, () -> service.updatePersonRole(null, new PersonRoleUpdateRequestDTO("ROLE_ADMIN"))),
                () -> assertThrows(BusinessException.class, () -> service.updatePersonRole(0L, new PersonRoleUpdateRequestDTO("ROLE_ADMIN"))),
                () -> assertThrows(BusinessException.class, () -> service.updatePersonRole(-1L, new PersonRoleUpdateRequestDTO("ROLE_ADMIN")))
        );

        verifyNoInteractions(personRepository, roleRepository);
    }

    @Test
    void shouldThrowResourceNotFoundWhenPersonDoesNotExist() {
        when(personRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.updatePersonRole(99L, new PersonRoleUpdateRequestDTO("ROLE_ADMIN")));

        verifyNoInteractions(roleRepository);
    }

    @Test
    void shouldThrowResourceNotFoundWhenRoleDoesNotExist() {
        Reader person = person("encoded-password");
        when(personRepository.findById(1L)).thenReturn(Optional.of(person));
        when(roleRepository.findByAuthority("ROLE_UNKNOWN")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.updatePersonRole(1L, new PersonRoleUpdateRequestDTO("ROLE_UNKNOWN")));

        verify(personRepository, never()).save(any());
    }

    private Reader person(String password) {
        Reader reader = new Reader();
        reader.setId(1L);
        reader.setName("Reader");
        reader.setPhoneNumber("34999999991");
        reader.setBirthdayDate(LocalDate.of(1990, 1, 10));
        reader.setPassword(password);
        reader.setPersonType("reader");
        return reader;
    }

    private PersonRoleUpdateResponseDTO response(String role) {
        return new PersonRoleUpdateResponseDTO(
                1L,
                "Reader",
                "34999999991",
                "reader",
                List.of(role)
        );
    }
}
