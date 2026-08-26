package com.eventoscelebrativos.service;

import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.Ministry;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.repository.MinistryRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Boundary temporario de compatibilidade entre contratos legados baseados em {@link MinistryType}
 * e o catalogo persistente {@link Ministry}. Deve desaparecer quando os boundaries ministeriais
 * tambem passarem a receber a identidade persistente de Ministry.
 */
@Component
public class LegacyMinistryTypeResolver {

    private static final Map<MinistryType, String> NORMALIZED_NAME_BY_TYPE = Map.of(
            MinistryType.PRIEST, "PRESBITEROS",
            MinistryType.READER, "LEITORES",
            MinistryType.COMMENTATOR, "COMENTARISTAS",
            MinistryType.MINISTER_OF_THE_WORD, "MINISTROS DA PALAVRA",
            MinistryType.EUCHARISTIC_MINISTER, "MINISTROS DA EUCARISTIA"
    );

    private static final Map<String, MinistryType> TYPE_BY_NORMALIZED_NAME = NORMALIZED_NAME_BY_TYPE.entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getValue, Map.Entry::getKey));

    private final MinistryRepository ministryRepository;

    public LegacyMinistryTypeResolver(MinistryRepository ministryRepository) {
        this.ministryRepository = ministryRepository;
    }

    public MinistryType parseMinistryType(String rawMinistryType) {
        if (rawMinistryType == null || rawMinistryType.isBlank()) {
            throw new BadRequestException("Tipo de ministerio invalido");
        }
        try {
            return MinistryType.valueOf(rawMinistryType.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Tipo de ministerio invalido: " + rawMinistryType);
        }
    }

    public Ministry requireMinistry(MinistryType ministryType) {
        Objects.requireNonNull(ministryType, "Tipo ministerial legado e obrigatorio");
        String normalizedName = requireNormalizedName(ministryType);
        return ministryRepository.findByNormalizedName(normalizedName)
                .orElseThrow(() -> inconsistentCatalog(ministryType, normalizedName));
    }

    public Map<MinistryType, Ministry> requireMinistries(Collection<MinistryType> ministryTypes) {
        Set<MinistryType> distinctTypes = ministryTypes.stream()
                .map(type -> Objects.requireNonNull(type, "Tipo ministerial legado e obrigatorio"))
                .collect(Collectors.toSet());
        if (distinctTypes.isEmpty()) {
            return Map.of();
        }

        Set<String> normalizedNames = distinctTypes.stream()
                .map(this::requireNormalizedName)
                .collect(Collectors.toSet());
        Map<String, Ministry> ministriesByNormalizedName = ministryRepository
                .findByNormalizedNameIn(normalizedNames)
                .stream()
                .collect(Collectors.toMap(Ministry::getNormalizedName, Function.identity()));

        Map<MinistryType, Ministry> result = new EnumMap<>(MinistryType.class);
        for (MinistryType type : distinctTypes) {
            String normalizedName = requireNormalizedName(type);
            Ministry ministry = ministriesByNormalizedName.get(normalizedName);
            if (ministry == null) {
                throw inconsistentCatalog(type, normalizedName);
            }
            result.put(type, ministry);
        }
        return Map.copyOf(result);
    }

    public Map<Long, MinistryType> requireTypesByMinistryId(Collection<Ministry> ministries) {
        Map<Long, MinistryType> result = new LinkedHashMap<>();
        for (Ministry ministry : ministries) {
            result.put(ministry.getId(), requireMinistryType(ministry));
        }
        return Map.copyOf(result);
    }

    public MinistryType requireMinistryType(Ministry ministry) {
        Objects.requireNonNull(ministry, "Ministerio e obrigatorio");
        return requireMinistryType(ministry.getNormalizedName());
    }

    public MinistryType requireMinistryType(String normalizedName) {
        MinistryType type = TYPE_BY_NORMALIZED_NAME.get(normalizedName);
        if (type == null) {
            throw new IllegalStateException(
                    "Ministerio persistente sem equivalente legado: " + normalizedName
            );
        }
        return type;
    }

    public EventAssignmentType requireEventAssignmentType(Ministry ministry) {
        return EventAssignmentType.valueOf(requireMinistryType(ministry).name());
    }

    private String requireNormalizedName(MinistryType ministryType) {
        String normalizedName = NORMALIZED_NAME_BY_TYPE.get(ministryType);
        if (normalizedName == null) {
            throw new IllegalStateException("Tipo ministerial legado sem mapeamento: " + ministryType);
        }
        return normalizedName;
    }

    private IllegalStateException inconsistentCatalog(MinistryType ministryType, String normalizedName) {
        return new IllegalStateException(
                "Catalogo de ministerios legado inconsistente: "
                        + ministryType.name()
                        + " nao encontrou Ministry com normalizedName="
                        + normalizedName
        );
    }
}
