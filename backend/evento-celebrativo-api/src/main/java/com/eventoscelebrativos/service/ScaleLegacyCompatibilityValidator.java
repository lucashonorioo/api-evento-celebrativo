package com.eventoscelebrativos.service;

import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.model.Person;
import org.springframework.stereotype.Component;

/**
 * Enquanto tb_event_person e a leitura LEGACY existirem, uma atribuicao so pode ser escrita se
 * tambem for representavel no subtipo Java legado - mesmo que a pessoa ja possua o PersonMinistry
 * solicitado. Esta e uma restricao temporaria de compatibilidade, nao a fonte de elegibilidade.
 */
@Component
public class ScaleLegacyCompatibilityValidator {

    public void validate(Person person, Class<? extends Person> expectedLegacyType, String roleName) {
        if (!expectedLegacyType.isInstance(person)) {
            throw new BusinessException(
                    "A pessoa informada para " + roleName + " possui a função ministerial ativa, "
                            + "mas seu cadastro ainda não é compatível com essa atribuição no modelo "
                            + "legado de escala"
            );
        }
    }
}
