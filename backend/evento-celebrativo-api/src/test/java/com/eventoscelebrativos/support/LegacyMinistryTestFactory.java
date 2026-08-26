package com.eventoscelebrativos.support;

import com.eventoscelebrativos.model.Ministry;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.repository.MinistryRepository;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

public final class LegacyMinistryTestFactory {

    private static final Map<MinistryType, String> NAME_BY_TYPE = Map.of(
            MinistryType.PRIEST, "Presbiteros",
            MinistryType.READER, "Leitores",
            MinistryType.COMMENTATOR, "Comentaristas",
            MinistryType.MINISTER_OF_THE_WORD, "Ministros da Palavra",
            MinistryType.EUCHARISTIC_MINISTER, "Ministros da Eucaristia"
    );

    private static final Map<MinistryType, String> NORMALIZED_NAME_BY_TYPE = Map.of(
            MinistryType.PRIEST, "PRESBITEROS",
            MinistryType.READER, "LEITORES",
            MinistryType.COMMENTATOR, "COMENTARISTAS",
            MinistryType.MINISTER_OF_THE_WORD, "MINISTROS DA PALAVRA",
            MinistryType.EUCHARISTIC_MINISTER, "MINISTROS DA EUCARISTIA"
    );

    private static final Map<MinistryType, Long> UNIT_MINISTRY_ID_BY_TYPE = Map.of(
            MinistryType.PRIEST, 10_001L,
            MinistryType.READER, 10_002L,
            MinistryType.COMMENTATOR, 10_003L,
            MinistryType.MINISTER_OF_THE_WORD, 10_004L,
            MinistryType.EUCHARISTIC_MINISTER, 10_005L
    );

    private LegacyMinistryTestFactory() {
    }

    public static PersonMinistry personMinistry(Person person, MinistryType ministryType) {
        return new PersonMinistry(person, unitMinistry(ministryType), ministryType);
    }

    public static PersonMinistry personMinistry(
            Person person,
            MinistryType ministryType,
            MinistryRepository ministryRepository
    ) {
        return new PersonMinistry(person, persistentMinistry(ministryType, ministryRepository), ministryType);
    }

    public static Ministry unitMinistry(MinistryType ministryType) {
        Ministry ministry = new Ministry(NAME_BY_TYPE.get(ministryType));
        ReflectionTestUtils.setField(ministry, "id", UNIT_MINISTRY_ID_BY_TYPE.get(ministryType));
        return ministry;
    }

    public static String normalizedName(MinistryType ministryType) {
        return NORMALIZED_NAME_BY_TYPE.get(ministryType);
    }

    public static Ministry persistentMinistry(MinistryType ministryType, MinistryRepository ministryRepository) {
        return ministryRepository.findByNormalizedName(normalizedName(ministryType))
                .orElseThrow(() -> new IllegalStateException("Ministry seed nao encontrado para " + ministryType));
    }
}
