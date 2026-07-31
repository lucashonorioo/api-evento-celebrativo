package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.config.OpenApiConfig;
import com.eventoscelebrativos.dto.request.PersonUnavailabilityRequestDTO;
import com.eventoscelebrativos.dto.response.AdminUnavailabilityResponseDTO;
import com.eventoscelebrativos.dto.response.PersonUnavailabilityResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.service.PersonUnavailabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

@RestController
@RequestMapping(value = "/pessoas")
@Tag(name = "Indisponibilidades", description = "Gerenciamento de indisponibilidades prévias de pessoas para escalas")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class PersonUnavailabilityController {

    private static final DateTimeFormatter CANONICAL_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss")
                    .withResolverStyle(ResolverStyle.STRICT);

    private final PersonUnavailabilityService personUnavailabilityService;

    public PersonUnavailabilityController(PersonUnavailabilityService personUnavailabilityService) {
        this.personUnavailabilityService = personUnavailabilityService;
    }

    @Operation(summary = "Lista as indisponibilidades da pessoa autenticada que interceptam um intervalo [startAt, endAt). "
            + "A pessoa e determinada exclusivamente pelo token (Bearer JWT). Dia inteiro e enviado como "
            + "00:00 do dia ate 00:00 do dia seguinte.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Indisponibilidades listadas com sucesso"),
            @ApiResponse(responseCode = "400", description = "Intervalo ou paginacao invalidos"),
            @ApiResponse(responseCode = "401", description = "Usuario nao autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuario sem permissao"),
            @ApiResponse(responseCode = "404", description = "Pessoa nao encontrada")
    })
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_OPERATOR')")
    @GetMapping(value = "/me/indisponibilidades")
    public ResponseEntity<Page<PersonUnavailabilityResponseDTO>> findMyUnavailabilities(
            Authentication authentication,
            @Parameter(description = "Inicio do intervalo, inclusive. Formato ISO yyyy-MM-ddTHH:mm:ss")
            @RequestParam("startAt") String startAt,
            @Parameter(description = "Fim do intervalo, exclusive. Formato ISO yyyy-MM-ddTHH:mm:ss")
            @RequestParam("endAt") String endAt,
            @Parameter(description = "Numero da pagina, iniciando em 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Quantidade de registros por pagina. Maximo: 100")
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<PersonUnavailabilityResponseDTO> result = personUnavailabilityService.findMine(
                authentication.getName(),
                parseCanonicalDateTime(startAt),
                parseCanonicalDateTime(endAt),
                page,
                size
        );
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Cria uma indisponibilidade da pessoa autenticada. startAt deve ser agora ou futuro; "
            + "o intervalo [startAt, endAt) nao pode se sobrepor a outra indisponibilidade da mesma pessoa. Pode "
            + "conflitar com escalas futuras (o conflito passa a ser derivado e aparece nas consultas "
            + "administrativas); so e rejeitada quando conflita com um evento ja em andamento. Dia inteiro: "
            + "startAt=00:00 do dia, endAt=00:00 do dia seguinte.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Indisponibilidade criada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados invalidos, intervalo invertido/zero ou startAt no passado"),
            @ApiResponse(responseCode = "401", description = "Usuario nao autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuario sem permissao"),
            @ApiResponse(responseCode = "404", description = "Pessoa nao encontrada"),
            @ApiResponse(responseCode = "409", description = "Sobreposicao com outra indisponibilidade (UNAVAILABILITY_OVERLAP) ou "
                    + "conflito com evento ja em andamento (UNAVAILABILITY_CONFLICT_WITH_STARTED_ASSIGNMENT)")
    })
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_OPERATOR')")
    @PostMapping(value = "/me/indisponibilidades")
    public ResponseEntity<PersonUnavailabilityResponseDTO> createMyUnavailability(
            Authentication authentication,
            @Valid @RequestBody PersonUnavailabilityRequestDTO requestDTO
    ) {
        PersonUnavailabilityResponseDTO responseDTO = personUnavailabilityService.create(authentication.getName(), requestDTO);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(responseDTO.getId())
                .toUri();
        return ResponseEntity.created(location).body(responseDTO);
    }

    @Operation(summary = "Atualiza uma indisponibilidade propria da pessoa autenticada. Idempotente: reenviar o mesmo "
            + "intervalo e motivo normalizados nao altera o registro. Um periodo cujo startAt ja passou nao pode ser "
            + "atualizado por PUT, mesmo sem mudanca real.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Indisponibilidade atualizada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados invalidos, intervalo invertido/zero ou startAt no passado"),
            @ApiResponse(responseCode = "401", description = "Usuario nao autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuario sem permissao"),
            @ApiResponse(responseCode = "404", description = "Registro inexistente ou pertencente a outra pessoa"),
            @ApiResponse(responseCode = "409", description = "Sobreposicao com outra indisponibilidade (UNAVAILABILITY_OVERLAP) ou "
                    + "conflito com evento ja em andamento (UNAVAILABILITY_CONFLICT_WITH_STARTED_ASSIGNMENT)")
    })
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_OPERATOR')")
    @PutMapping(value = "/me/indisponibilidades/{id}")
    public ResponseEntity<PersonUnavailabilityResponseDTO> updateMyUnavailability(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody PersonUnavailabilityRequestDTO requestDTO
    ) {
        PersonUnavailabilityResponseDTO responseDTO =
                personUnavailabilityService.update(authentication.getName(), id, requestDTO);
        return ResponseEntity.ok(responseDTO);
    }

    @Operation(summary = "Remove uma indisponibilidade propria da pessoa autenticada. Nao afeta Person, "
            + "EventAssignment, EventParticipationResponse ou CelebrationEvent.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Indisponibilidade removida com sucesso"),
            @ApiResponse(responseCode = "401", description = "Usuario nao autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuario sem permissao"),
            @ApiResponse(responseCode = "404", description = "Registro inexistente ou pertencente a outra pessoa")
    })
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_OPERATOR')")
    @DeleteMapping(value = "/me/indisponibilidades/{id}")
    public ResponseEntity<Void> deleteMyUnavailability(Authentication authentication, @PathVariable Long id) {
        personUnavailabilityService.delete(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Consulta administrativa das pessoas indisponiveis em um intervalo [startAt, endAt). "
            + "Nao retorna reason, telefone, senha, roles, ministries ou assignments.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Consulta realizada com sucesso"),
            @ApiResponse(responseCode = "401", description = "Usuario nao autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuario sem permissao (exclusivo ROLE_ADMIN)")
    })
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @GetMapping(value = "/indisponibilidades")
    public ResponseEntity<AdminUnavailabilityResponseDTO> findUnavailablePeopleByRange(
            @Parameter(description = "Inicio do intervalo, inclusive. Formato ISO yyyy-MM-ddTHH:mm:ss")
            @RequestParam("startAt") String startAt,
            @Parameter(description = "Fim do intervalo, exclusive. Formato ISO yyyy-MM-ddTHH:mm:ss")
            @RequestParam("endAt") String endAt
    ) {
        AdminUnavailabilityResponseDTO responseDTO = personUnavailabilityService.findByDate(
                parseCanonicalDateTime(startAt),
                parseCanonicalDateTime(endAt)
        );
        return ResponseEntity.ok(responseDTO);
    }

    private LocalDateTime parseCanonicalDateTime(String value) {
        try {
            return LocalDateTime.parse(value, CANONICAL_DATE_TIME_FORMATTER);
        } catch (DateTimeParseException exception) {
            throw new BadRequestException(
                    "Data e hora inválidas. Use o formato yyyy-MM-dd'T'HH:mm:ss sem offset ou frações."
            );
        }
    }
}
