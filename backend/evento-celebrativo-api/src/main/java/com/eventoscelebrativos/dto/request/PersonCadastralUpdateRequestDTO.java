package com.eventoscelebrativos.dto.request;

import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Base de DTOs de atualizacao ministerial que aceitam somente dados cadastrais.
 * Campos de conta (password/createAccess/accessRole) nao existem como propriedades desta classe;
 * quando presentes no JSON (mesmo com valor null, vazio ou false) sao capturados por
 * {@link JsonAnySetter} - sem depender da configuracao global de unknown properties - e rejeitados
 * explicitamente em {@link #rejectAccountFields()}.
 */
public abstract class PersonCadastralUpdateRequestDTO {

    private static final Set<String> FORBIDDEN_ACCOUNT_FIELDS = Set.of("password", "createAccess", "accessRole");

    private final Set<String> presentAccountFields = new LinkedHashSet<>();

    @JsonAnySetter
    private void captureUnknown(String name, Object value) {
        if (FORBIDDEN_ACCOUNT_FIELDS.contains(name)) {
            presentAccountFields.add(name);
        }
    }

    public void rejectAccountFields() {
        if (!presentAccountFields.isEmpty()) {
            throw new BadRequestException(
                    "Campos de conta nao sao permitidos na atualizacao cadastral: " + String.join(", ", presentAccountFields),
                    "ACCOUNT_FIELDS_NOT_ALLOWED_ON_PERSON_UPDATE"
            );
        }
    }
}
