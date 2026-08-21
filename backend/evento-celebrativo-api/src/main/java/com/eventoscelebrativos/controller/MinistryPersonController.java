package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.config.OpenApiConfig;
import com.eventoscelebrativos.dto.request.MinistryPersonCreateRequestDTO;
import com.eventoscelebrativos.dto.request.MinistryPersonUpdateRequestDTO;
import com.eventoscelebrativos.dto.response.MinistryPersonResponseDTO;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.service.MinistryPersonManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * Administracao de pessoas escopada a um unico {@link MinistryType}
 * ({@code /ministerios/{ministryType}/pessoas}). Toda a autorizacao e centralizada em
 * {@code @ministryAuthorizationService.canManageMinistry(#ministryType)}: ROLE_ADMIN tem override
 * global; ROLE_OPERATOR precisa coordenar (PersonMinistry.active=true e coordinator=true) exatamente
 * o ministryType do path. Nao substitui nem abre os CRUDs administrativos globais existentes
 * (GET/PUT /pessoas/{id}, PUT /pessoas/{id}/ministries, coordenacao, conta, roles), que continuam
 * exclusivos de ROLE_ADMIN.
 */
@RestController
@RequestMapping(value = "/ministerios/{ministryType}/pessoas")
@Tag(name = "Ministérios - Pessoas", description = "Administração de pessoas escopada a um ministério")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class MinistryPersonController {

    private final MinistryPersonManagementService ministryPersonManagementService;

    public MinistryPersonController(MinistryPersonManagementService ministryPersonManagementService) {
        this.ministryPersonManagementService = ministryPersonManagementService;
    }

    @Operation(
            summary = "Lista pessoas ativas do ministério informado.",
            description = "Retorna somente Person.active=true com PersonMinistry(ministryType).active=true. "
                    + "Ordenado por nome e id. Tamanho de página máximo: 100. Permitido para ROLE_ADMIN (qualquer "
                    + "ministryType) ou ROLE_OPERATOR com PersonMinistry(ministryType).active=true e coordinator=true."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pessoas listadas com sucesso"),
            @ApiResponse(responseCode = "400", description = "Paginação inválida"),
            @ApiResponse(responseCode = "401", description = "Usuário não autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuário sem autoridade sobre o ministério informado")
    })
    @PreAuthorize("@ministryAuthorizationService.canManageMinistry(#ministryType)")
    @GetMapping
    public ResponseEntity<Page<MinistryPersonResponseDTO>> findPeople(
            @PathVariable MinistryType ministryType,
            @Parameter(description = "Número da página, iniciando em 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Quantidade de registros por página. Máximo: 100")
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(ministryPersonManagementService.findPeople(ministryType, page, size));
    }

    @Operation(
            summary = "Busca uma pessoa por id dentro do escopo do ministério informado.",
            description = "Só encontra a pessoa se ela possuir PersonMinistry(ministryType).active=true; "
                    + "caso contrário, retorna 404 mesmo que a pessoa exista globalmente (evita vazamento cross-scope)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pessoa encontrada"),
            @ApiResponse(responseCode = "401", description = "Usuário não autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuário sem autoridade sobre o ministério informado"),
            @ApiResponse(responseCode = "404", description = "Pessoa inexistente ou fora do escopo do ministério")
    })
    @PreAuthorize("@ministryAuthorizationService.canManageMinistry(#ministryType)")
    @GetMapping(value = "/{personId}")
    public ResponseEntity<MinistryPersonResponseDTO> findPersonById(
            @PathVariable MinistryType ministryType,
            @PathVariable Long personId
    ) {
        return ResponseEntity.ok(ministryPersonManagementService.findPersonById(ministryType, personId));
    }

    @Operation(
            summary = "Cria uma pessoa nova já vinculada ao ministério informado.",
            description = "Cria somente Person + PersonMinistry(ministryType).active=true, coordinator=false. "
                    + "Nunca cria UserAccount. Aceita somente name, phoneNumber e birthdayDate; qualquer outro campo "
                    + "no corpo (inclusive password, createAccess, accessRole, role, roles, active, coordinator ou "
                    + "campo desconhecido) é rejeitado, mesmo quando enviado como null, vazio ou false."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Pessoa criada e vinculada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou campo não permitido informado"),
            @ApiResponse(responseCode = "401", description = "Usuário não autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuário sem autoridade sobre o ministério informado"),
            @ApiResponse(responseCode = "409", description = "Telefone já associado a outra pessoa")
    })
    @PreAuthorize("@ministryAuthorizationService.canManageMinistry(#ministryType)")
    @PostMapping
    public ResponseEntity<MinistryPersonResponseDTO> create(
            @PathVariable MinistryType ministryType,
            @Valid @RequestBody MinistryPersonCreateRequestDTO requestDTO
    ) {
        MinistryPersonResponseDTO responseDTO = ministryPersonManagementService.create(ministryType, requestDTO);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(responseDTO.getId()).toUri();
        return ResponseEntity.status(HttpStatus.CREATED).location(location).body(responseDTO);
    }

    @Operation(
            summary = "Atualiza name e birthdayDate de uma pessoa dentro do escopo do ministério informado.",
            description = "Aceita somente name e birthdayDate. phoneNumber e qualquer campo de conta, role, "
                    + "ministério ou status são rejeitados, mesmo quando enviados como null, vazio ou false. A "
                    + "pessoa precisa possuir PersonMinistry(ministryType).active=true; caso contrário, 404."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pessoa atualizada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou campo não permitido informado"),
            @ApiResponse(responseCode = "401", description = "Usuário não autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuário sem autoridade sobre o ministério informado"),
            @ApiResponse(responseCode = "404", description = "Pessoa inexistente ou fora do escopo do ministério")
    })
    @PreAuthorize("@ministryAuthorizationService.canManageMinistry(#ministryType)")
    @PutMapping(value = "/{personId}")
    public ResponseEntity<MinistryPersonResponseDTO> update(
            @PathVariable MinistryType ministryType,
            @PathVariable Long personId,
            @Valid @RequestBody MinistryPersonUpdateRequestDTO requestDTO
    ) {
        return ResponseEntity.ok(ministryPersonManagementService.update(ministryType, personId, requestDTO));
    }

    @Operation(
            summary = "Adiciona ou reativa o vínculo de uma pessoa já existente ao ministério informado.",
            description = "Não altera nenhum outro ministério da pessoa. Sem vínculo: cria ativo, coordinator=false. "
                    + "Vínculo inativo: reativa (coordinator permanece false - reativação nunca restaura "
                    + "coordenação). Vínculo já ativo: no-op idempotente, preservando coordinator como está. Exige "
                    + "Person.active=true (409 caso contrário)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vínculo criado, reativado ou já ativo (idempotente)"),
            @ApiResponse(responseCode = "401", description = "Usuário não autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuário sem autoridade sobre o ministério informado"),
            @ApiResponse(responseCode = "404", description = "Pessoa inexistente"),
            @ApiResponse(responseCode = "409", description = "Pessoa inativa")
    })
    @PreAuthorize("@ministryAuthorizationService.canManageMinistry(#ministryType)")
    @PutMapping(value = "/{personId}/vinculo")
    public ResponseEntity<MinistryPersonResponseDTO> addOrReactivateMinistry(
            @PathVariable MinistryType ministryType,
            @PathVariable Long personId
    ) {
        return ResponseEntity.ok(ministryPersonManagementService.addOrReactivateMinistry(ministryType, personId));
    }

    @Operation(
            summary = "Remove (desativa) o vínculo de uma pessoa com o ministério informado.",
            description = "Não exclui a Person, não desativa a Person globalmente, não altera UserAccount nem "
                    + "outros ministérios. Desativa também a coordenação daquele vínculo, se houver. Bloqueada por "
                    + "409 quando a pessoa possuir compromisso operacional ativo/futuro (evento ACTIVE) daquela "
                    + "função; um assignment exclusivamente de evento CANCELLED não bloqueia sozinho. PRIEST não "
                    + "pode ser removido de PASTOR ativo (409)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Vínculo removido com sucesso"),
            @ApiResponse(responseCode = "401", description = "Usuário não autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuário sem autoridade sobre o ministério informado"),
            @ApiResponse(responseCode = "404", description = "Pessoa inexistente ou fora do escopo do ministério"),
            @ApiResponse(responseCode = "409", description = "Compromisso operacional ativo/futuro ou invariante PASTOR/PRIEST")
    })
    @PreAuthorize("@ministryAuthorizationService.canManageMinistry(#ministryType)")
    @DeleteMapping(value = "/{personId}/vinculo")
    public ResponseEntity<Void> removeMinistry(
            @PathVariable MinistryType ministryType,
            @PathVariable Long personId
    ) {
        ministryPersonManagementService.removeMinistry(ministryType, personId);
        return ResponseEntity.noContent().build();
    }
}
