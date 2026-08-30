package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Ministry;
import com.eventoscelebrativos.model.Person;

import java.util.List;
import java.util.Set;

/**
 * Fonte oficial de escrita dos CRUDs ministeriais: Person + PersonMinistry.
 * A classificação ministerial é sempre informada explicitamente pelo chamador
 * (nunca inferida por instanceof, person_type ou repository de subtipo).
 */
public interface PersonMinistryCommandService {

    Person create(Person person, Ministry ministry);

    Person create(Person person, MinistryType ministryType);

    Person requireActiveMinistryPerson(Long personId, Ministry ministry, String entityLabel);

    Person requireActiveMinistryPerson(Long personId, MinistryType ministryType, String entityLabel);

    /**
     * Igual a {@link #requireActiveMinistryPerson(Long, MinistryType, String)}, mas bloqueia a
     * {@link Person} (PESSIMISTIC_WRITE) antes de retorna-la. Use antes de aplicar um mapper que
     * possa alterar telefone ou outros dados sincronizados com {@link com.eventoscelebrativos.model.UserAccount},
     * garantindo que o lock de Person seja adquirido antes de qualquer mutacao e antes do lock de
     * UserAccount feito por {@link com.eventoscelebrativos.service.PersonAccountCoordinator}.
     */
    Person requireActiveMinistryPersonForUpdate(Long personId, Ministry ministry, String entityLabel);

    Person requireActiveMinistryPersonForUpdate(Long personId, MinistryType ministryType, String entityLabel);

    void removeMinistry(Long personId, Ministry ministry, String entityLabel);

    void removeMinistry(Long personId, MinistryType ministryType, String entityLabel);

    /**
     * Adiciona ou reativa o vinculo {@code PersonMinistry(personId, ministryType)}, sem afetar
     * nenhum outro ministerio da pessoa. Trava a {@link Person} (PESSIMISTIC_WRITE) antes de mutar.
     * Exige {@code Person.active=true} ({@link com.eventoscelebrativos.exception.exceptions.MinistryPersonInactiveException}
     * caso contrario). Semantica por estado atual do vinculo:
     * <ul>
     *     <li>inexistente: cria ativo, {@code coordinator=false};</li>
     *     <li>inativo: reativa ({@code coordinator} permanece {@code false} - reativacao nunca
     *     restaura coordenacao);</li>
     *     <li>ja ativo: no-op idempotente, preservando o estado atual de {@code coordinator}
     *     (inclusive quando {@code true}, concedido anteriormente por ADMIN).</li>
     * </ul>
     */
    Person addOrReactivateMinistry(Long personId, Ministry ministry);

    Person addOrReactivateMinistry(Long personId, MinistryType ministryType);

    /**
     * Aplica atomicamente o conjunto desejado de ministérios de uma pessoa já existente:
     * adiciona vínculos inexistentes, reativa vínculos inativos, preserva vínculos inalterados
     * e desativa vínculos ativos ausentes do conjunto desejado. Nenhuma mudança é aplicada se
     * qualquer vínculo a desativar possuir {@link com.eventoscelebrativos.model.EventAssignment}
     * do mesmo tipo.
     */
    PersonMinistrySyncResult syncMinistries(Long personId, Set<MinistryType> desiredMinistries);

    PersonMinistryCatalogSyncResult syncMinistriesById(Long personId, List<Long> desiredMinistryIds);
}
