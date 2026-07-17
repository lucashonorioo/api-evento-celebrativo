package com.eventoscelebrativos.service;

import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.model.Commentator;
import com.eventoscelebrativos.model.EucharisticMinister;
import com.eventoscelebrativos.model.MinisterOfTheWord;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Priest;
import com.eventoscelebrativos.model.Reader;
import org.springframework.stereotype.Component;

@Component
public class MinistryTypeResolver {

    public MinistryType resolve(Person person) {
        if (person instanceof Reader) {
            return MinistryType.READER;
        }
        if (person instanceof Commentator) {
            return MinistryType.COMMENTATOR;
        }
        if (person instanceof Priest) {
            return MinistryType.PRIEST;
        }
        if (person instanceof MinisterOfTheWord) {
            return MinistryType.MINISTER_OF_THE_WORD;
        }
        if (person instanceof EucharisticMinister) {
            return MinistryType.EUCHARISTIC_MINISTER;
        }
        throw new BusinessException("Tipo de pessoa sem funÃ§Ã£o ministerial compatÃ­vel");
    }
}
