package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.request.PersonRoleUpdateRequestDTO;
import com.eventoscelebrativos.dto.response.PersonAdminResponseDTO;
import com.eventoscelebrativos.dto.response.PersonRoleUpdateResponseDTO;
import com.eventoscelebrativos.service.PersonService;
import com.eventoscelebrativos.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
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

    @Operation(summary = "Lista pessoas para administracao de usuarios")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pessoas listadas com sucesso"),
            @ApiResponse(responseCode = "400", description = "Filtros ou paginacao invalidos"),
            @ApiResponse(responseCode = "401", description = "Usuario nao autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuario sem permissao")
    })
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @GetMapping
    public ResponseEntity<Page<PersonAdminResponseDTO>> findPeople(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String phoneNumber,
            @Parameter(description = "Tipo de pessoa: reader, commentator, minister_of_the_word, eucharistic_minister, priest")
            @RequestParam(required = false) String personType,
            @Parameter(description = "Perfil de acesso: ROLE_ADMIN ou ROLE_OPERATOR")
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<PersonAdminResponseDTO> people = personService.findPeople(
                name,
                phoneNumber,
                personType,
                role,
                page,
                size
        );
        return ResponseEntity.ok(people);
    }

    @Operation(summary = "Busca uma pessoa por ID para administracao de usuarios")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pessoa encontrada"),
            @ApiResponse(responseCode = "401", description = "Usuario nao autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuario sem permissao"),
            @ApiResponse(responseCode = "404", description = "Pessoa nao encontrada")
    })
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @GetMapping(value = "/{id}")
    public ResponseEntity<PersonAdminResponseDTO> findPersonById(@PathVariable Long id) {
        PersonAdminResponseDTO responseDTO = personService.findPersonById(id);
        return ResponseEntity.ok(responseDTO);
    }

    @Operation(summary = "Atualiza o perfil de acesso de uma pessoa")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Perfil de acesso invalido"),
            @ApiResponse(responseCode = "401", description = "Usuario nao autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuario sem permissao"),
            @ApiResponse(responseCode = "404", description = "Pessoa nao encontrada"),
            @ApiResponse(responseCode = "409", description = "Operacao administrativa conflitante")
    })
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @PutMapping(value = "/{id}/roles")
    public ResponseEntity<PersonRoleUpdateResponseDTO> updatePersonRole(
            @PathVariable Long id,
            @Valid @RequestBody PersonRoleUpdateRequestDTO requestDTO
    ) {
        PersonRoleUpdateResponseDTO responseDTO = personService.updatePersonRole(id, requestDTO);
        return ResponseEntity.ok(responseDTO);
    }
}
