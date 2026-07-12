package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.request.PersonRoleUpdateRequestDTO;
import com.eventoscelebrativos.dto.response.PersonRoleUpdateResponseDTO;
import com.eventoscelebrativos.service.PersonService;
import com.eventoscelebrativos.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/pessoas")
@Tag(name = "Pessoas", description = "Gerenciamento de permissões de pessoas")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class PersonController {

    private final PersonService personService;

    public PersonController(PersonService personService) {
        this.personService = personService;
    }

    @Operation(summary = "Atualiza o perfil de acesso de uma pessoa")
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
