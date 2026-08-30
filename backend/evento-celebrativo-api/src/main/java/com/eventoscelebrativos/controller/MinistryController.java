package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.config.OpenApiConfig;
import com.eventoscelebrativos.dto.request.MinistryRequestDTO;
import com.eventoscelebrativos.dto.request.MinistryStatusUpdateRequestDTO;
import com.eventoscelebrativos.dto.response.MinistryResponseDTO;
import com.eventoscelebrativos.service.MinistryAdministrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping(value = "/ministerios")
@Tag(name = "Ministerios", description = "Administracao do catalogo dinamico de ministerios")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class MinistryController {

    private final MinistryAdministrationService ministryAdministrationService;

    public MinistryController(MinistryAdministrationService ministryAdministrationService) {
        this.ministryAdministrationService = ministryAdministrationService;
    }

    @Operation(summary = "Lista o catalogo de ministerios")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ministerios listados com sucesso"),
            @ApiResponse(responseCode = "401", description = "Usuario nao autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuario sem permissao")
    })
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @GetMapping
    public ResponseEntity<List<MinistryResponseDTO>> findAll() {
        return ResponseEntity.ok(ministryAdministrationService.findAll());
    }

    @Operation(summary = "Busca um ministerio por ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ministerio encontrado"),
            @ApiResponse(responseCode = "401", description = "Usuario nao autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuario sem permissao"),
            @ApiResponse(responseCode = "404", description = "Ministerio nao encontrado")
    })
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @GetMapping(value = "/{ministryId}")
    public ResponseEntity<MinistryResponseDTO> findById(@PathVariable Long ministryId) {
        return ResponseEntity.ok(ministryAdministrationService.findById(ministryId));
    }

    @Operation(summary = "Cria um ministerio no catalogo")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Ministerio criado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Nome invalido"),
            @ApiResponse(responseCode = "401", description = "Usuario nao autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuario sem permissao"),
            @ApiResponse(responseCode = "409", description = "Nome semanticamente duplicado")
    })
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @PostMapping
    public ResponseEntity<MinistryResponseDTO> create(@Valid @RequestBody MinistryRequestDTO requestDTO) {
        MinistryResponseDTO responseDTO = ministryAdministrationService.create(requestDTO);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(responseDTO.getId())
                .toUri();
        return ResponseEntity.status(HttpStatus.CREATED).location(location).body(responseDTO);
    }

    @Operation(summary = "Renomeia um ministerio do catalogo")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ministerio renomeado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Nome invalido"),
            @ApiResponse(responseCode = "401", description = "Usuario nao autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuario sem permissao"),
            @ApiResponse(responseCode = "404", description = "Ministerio nao encontrado"),
            @ApiResponse(responseCode = "409", description = "Nome semanticamente duplicado")
    })
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @PutMapping(value = "/{ministryId}")
    public ResponseEntity<MinistryResponseDTO> rename(
            @PathVariable Long ministryId,
            @Valid @RequestBody MinistryRequestDTO requestDTO
    ) {
        return ResponseEntity.ok(ministryAdministrationService.rename(ministryId, requestDTO));
    }

    @Operation(summary = "Ativa ou desativa um ministerio do catalogo")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Request invalido"),
            @ApiResponse(responseCode = "401", description = "Usuario nao autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuario sem permissao"),
            @ApiResponse(responseCode = "404", description = "Ministerio nao encontrado"),
            @ApiResponse(responseCode = "409", description = "Ministerio possui vinculos ativos")
    })
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @PutMapping(value = "/{ministryId}/status")
    public ResponseEntity<MinistryResponseDTO> updateStatus(
            @PathVariable Long ministryId,
            @Valid @RequestBody MinistryStatusUpdateRequestDTO requestDTO
    ) {
        return ResponseEntity.ok(ministryAdministrationService.updateStatus(ministryId, requestDTO));
    }
}
