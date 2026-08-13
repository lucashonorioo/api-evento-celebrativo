package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.config.OpenApiConfig;
import com.eventoscelebrativos.dto.response.ParishStaffTeamResponseDTO;
import com.eventoscelebrativos.service.ParishStaffAssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/paroquia/equipe")
@Tag(name = "Equipe paroquial", description = "Responsabilidades institucionais paroquiais (PASTOR, PARISH_SECRETARY), "
        + "separadas de Person, UserAccount, UserAccountRole, PersonMinistry, EventAssignment e ParishProfile. Endpoints "
        + "exclusivamente administrativos: diferente de GET /paroquia, GET /paroquia/equipe nao e publico.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class ParishStaffAssignmentController {

    private final ParishStaffAssignmentService parishStaffAssignmentService;

    public ParishStaffAssignmentController(ParishStaffAssignmentService parishStaffAssignmentService) {
        this.parishStaffAssignmentService = parishStaffAssignmentService;
    }

    @Operation(
            summary = "Consulta a equipe paroquial ativa atual.",
            description = "pastor traz personId e name, ou null quando nao houver PASTOR ativo. secretaries e uma lista "
                    + "de personId e name, ordenada por name e depois personId. Nao expoe dados pessoais sensiveis. "
                    + "Endpoint administrativo, nao publico."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Equipe consultada com sucesso"),
            @ApiResponse(responseCode = "401", description = "Usuario nao autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuario sem permissao")
    })
    @GetMapping
    public ResponseEntity<ParishStaffTeamResponseDTO> findCurrentTeam() {
        return ResponseEntity.ok(parishStaffAssignmentService.findCurrentTeam());
    }

    @Operation(
            summary = "Concede ou reativa a responsabilidade PASTOR para uma pessoa.",
            description = "Exige Person.active=true e PersonMinistry(PRIEST).active=true (409 PASTOR_PRIEST_MINISTRY_REQUIRED "
                    + "quando ausente). So pode haver um PASTOR ativo por vez: se ja existir outra pessoa como PASTOR ativo, "
                    + "retorna 409 PARISH_ACTIVE_PASTOR_ALREADY_EXISTS, sem substituicao automatica. Idempotente: se a propria "
                    + "pessoa ja for PASTOR ativo, retorna sucesso sem alterar updatedAt."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "PASTOR concedido/reativado com sucesso (ou ja ativo, idempotente)"),
            @ApiResponse(responseCode = "401", description = "Usuario nao autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuario sem permissao"),
            @ApiResponse(responseCode = "404", description = "Pessoa nao encontrada"),
            @ApiResponse(responseCode = "409", description = "Pessoa inativa (PERSON_INACTIVE), PRIEST ausente "
                    + "(PASTOR_PRIEST_MINISTRY_REQUIRED) ou outro PASTOR ativo (PARISH_ACTIVE_PASTOR_ALREADY_EXISTS)")
    })
    @PutMapping(value = "/pastor/{personId}")
    public ResponseEntity<Void> grantPastor(@PathVariable Long personId) {
        parishStaffAssignmentService.grantPastor(personId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Desativa a responsabilidade PASTOR de uma pessoa.",
            description = "Nao remove a linha fisicamente, apenas marca active=false; historico e preservado. Idempotente: "
                    + "se a pessoa nao for PASTOR ativo (nunca foi ou ja esta inativo), retorna sucesso sem alterar nada."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "PASTOR desativado com sucesso (ou ja inativo, idempotente)"),
            @ApiResponse(responseCode = "401", description = "Usuario nao autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuario sem permissao"),
            @ApiResponse(responseCode = "404", description = "Pessoa nao encontrada")
    })
    @DeleteMapping(value = "/pastor/{personId}")
    public ResponseEntity<Void> revokePastor(@PathVariable Long personId) {
        parishStaffAssignmentService.revokePastor(personId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Concede ou reativa a responsabilidade PARISH_SECRETARY para uma pessoa.",
            description = "Exige apenas Person.active=true; nao exige conta de acesso, role nem ministerio. Pode haver "
                    + "varios PARISH_SECRETARY ativos simultaneamente. Idempotente: se a pessoa ja for PARISH_SECRETARY "
                    + "ativo, retorna sucesso sem alterar updatedAt."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "PARISH_SECRETARY concedido/reativado com sucesso (ou ja ativo, idempotente)"),
            @ApiResponse(responseCode = "401", description = "Usuario nao autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuario sem permissao"),
            @ApiResponse(responseCode = "404", description = "Pessoa nao encontrada"),
            @ApiResponse(responseCode = "409", description = "Pessoa inativa (PERSON_INACTIVE)")
    })
    @PutMapping(value = "/secretarios/{personId}")
    public ResponseEntity<Void> grantSecretary(@PathVariable Long personId) {
        parishStaffAssignmentService.grantSecretary(personId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Desativa a responsabilidade PARISH_SECRETARY de uma pessoa.",
            description = "Nao remove a linha fisicamente, apenas marca active=false; historico e preservado. Idempotente: "
                    + "se a pessoa nao for PARISH_SECRETARY ativo (nunca foi ou ja esta inativo), retorna sucesso sem alterar nada."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "PARISH_SECRETARY desativado com sucesso (ou ja inativo, idempotente)"),
            @ApiResponse(responseCode = "401", description = "Usuario nao autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuario sem permissao"),
            @ApiResponse(responseCode = "404", description = "Pessoa nao encontrada")
    })
    @DeleteMapping(value = "/secretarios/{personId}")
    public ResponseEntity<Void> revokeSecretary(@PathVariable Long personId) {
        parishStaffAssignmentService.revokeSecretary(personId);
        return ResponseEntity.noContent().build();
    }
}
