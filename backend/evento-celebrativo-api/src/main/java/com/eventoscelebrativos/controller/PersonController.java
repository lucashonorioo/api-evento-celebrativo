package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.request.PersonRoleUpdateRequestDTO;
import com.eventoscelebrativos.dto.response.PersonRoleUpdateResponseDTO;
import com.eventoscelebrativos.service.PersonService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/pessoas")
public class PersonController {

    private final PersonService personService;

    public PersonController(PersonService personService) {
        this.personService = personService;
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    @PutMapping(value = "/{id}/roles")
    public ResponseEntity<PersonRoleUpdateResponseDTO> updatePersonRole(
            @PathVariable Long id,
            @Valid @RequestBody PersonRoleUpdateRequestDTO requestDTO
    ) {
        PersonRoleUpdateResponseDTO responseDTO = personService.updatePersonRole(id, requestDTO);
        return ResponseEntity.ok(responseDTO);
    }
}
