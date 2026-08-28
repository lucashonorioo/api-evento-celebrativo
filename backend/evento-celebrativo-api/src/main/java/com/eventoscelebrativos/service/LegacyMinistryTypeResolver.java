package com.eventoscelebrativos.service;

import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.LegacyMinistryTypeMapping;
import com.eventoscelebrativos.model.Ministry;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.repository.LegacyMinistryTypeMappingRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Boundary temporario de compatibilidade entre contratos legados baseados em {@link MinistryType}
 * e o catalogo persistente {@link Ministry}. Deve desaparecer quando os boundaries ministeriais
 * tambem passarem a receber a identidade persistente de Ministry.
 */
@Component
public class LegacyMinistryTypeResolver {

    private final LegacyMinistryTypeMappingRepository mappingRepository;

    public LegacyMinistryTypeResolver(LegacyMinistryTypeMappingRepository mappingRepository) {
        this.mappingRepository = mappingRepository;
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
        return mappingRepository.findByMinistryType(ministryType)
                .map(LegacyMinistryTypeMapping::getMinistry)
                .orElseThrow(() -> inconsistentCatalog(ministryType));
    }

    public Map<MinistryType, Ministry> requireMinistries(Collection<MinistryType> ministryTypes) {
        Set<MinistryType> distinctTypes = ministryTypes.stream()
                .map(type -> Objects.requireNonNull(type, "Tipo ministerial legado e obrigatorio"))
                .collect(Collectors.toSet());
        if (distinctTypes.isEmpty()) {
            return Map.of();
        }

        Map<MinistryType, Ministry> ministriesByType = mappingRepository
                .findByMinistryTypeIn(distinctTypes)
                .stream()
                .collect(Collectors.toMap(
                        LegacyMinistryTypeMapping::getMinistryType,
                        LegacyMinistryTypeMapping::getMinistry
                ));

        Map<MinistryType, Ministry> result = new EnumMap<>(MinistryType.class);
        for (MinistryType type : distinctTypes) {
            Ministry ministry = ministriesByType.get(type);
            if (ministry == null) {
                throw inconsistentCatalog(type);
            }
            result.put(type, ministry);
        }
        return Map.copyOf(result);
    }

    public Map<Long, MinistryType> requireTypesByMinistryId(Collection<Ministry> ministries) {
        List<Long> ministryIds = ministries.stream()
                .map(ministry -> {
                    Objects.requireNonNull(ministry, "Ministerio e obrigatorio");
                    Long ministryId = ministry.getId();
                    if (ministryId == null || ministryId <= 0) {
                        throw new IllegalStateException("Ministerio persistente sem id valido");
                    }
                    return ministryId;
                })
                .distinct()
                .toList();
        return requireTypesByPersistentMinistryId(ministryIds);
    }

    public Map<Long, MinistryType> requireTypesByPersistentMinistryId(Collection<Long> ministryIds) {
        List<Long> distinctIds = ministryIds.stream()
                .map(ministryId -> {
                    if (ministryId == null || ministryId <= 0) {
                        throw new IllegalStateException("Ministerio persistente sem id valido");
                    }
                    return ministryId;
                })
                .distinct()
                .toList();
        if (distinctIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, MinistryType> mappedTypesByMinistryId = mappingRepository
                .findByMinistryIdIn(distinctIds)
                .stream()
                .collect(Collectors.toMap(
                        LegacyMinistryTypeMapping::getMinistryId,
                        LegacyMinistryTypeMapping::getMinistryType,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        Map<Long, MinistryType> result = new LinkedHashMap<>();
        for (Long ministryId : distinctIds) {
            MinistryType ministryType = mappedTypesByMinistryId.get(ministryId);
            if (ministryType == null) {
                throw noLegacyEquivalent(ministryId);
            }
            result.put(ministryId, ministryType);
        }
        return Map.copyOf(result);
    }

    public MinistryType requireMinistryType(Ministry ministry) {
        Objects.requireNonNull(ministry, "Ministerio e obrigatorio");
        Long ministryId = ministry.getId();
        if (ministryId == null || ministryId <= 0) {
            throw new IllegalStateException("Ministerio persistente sem id valido");
        }
        return mappingRepository.findByMinistryId(ministryId)
                .map(LegacyMinistryTypeMapping::getMinistryType)
                .orElseThrow(() -> noLegacyEquivalent(ministryId));
    }

    public EventAssignmentType requireEventAssignmentType(Ministry ministry) {
        return EventAssignmentType.valueOf(requireMinistryType(ministry).name());
    }

    private IllegalStateException inconsistentCatalog(MinistryType ministryType) {
        return new IllegalStateException(
                "Catalogo de ministerios legado inconsistente: "
                        + ministryType.name()
                        + " nao encontrou mapping persistente"
        );
    }

    private IllegalStateException noLegacyEquivalent(Long ministryId) {
        return new IllegalStateException(
                "Ministerio persistente sem equivalente legado: ministryId=" + ministryId
        );
    }
}
