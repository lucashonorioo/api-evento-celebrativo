package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.Person;

import java.time.LocalDate;

/**
 * Autoridade unica de atualizacao cadastral de {@link Person} (name, phoneNumber, birthdayDate),
 * usada por todos os fluxos de update cadastral: os cinco CRUDs ministeriais, o update
 * administrativo (PUT /pessoas/{id}) e o update de perfil pessoal (PUT /pessoas/me).
 * <p>
 * Substitui o antigo {@code PersonMinistryCommandService.save(Person)}, que so cobria os fluxos
 * ministeriais. Nao cria, habilita/desabilita conta, nem altera role, ministerio ou active - essas
 * responsabilidades permanecem em {@link PersonAccountCoordinator}, {@link UserAccountLifecycleService}
 * e {@link PersonMinistryCommandService}.
 */
public interface PersonCadastralUpdateService {

    /**
     * Valida nome/telefone/nascimento, garante unicidade de {@code Person.phoneNumber}, persiste a
     * {@link Person} e propaga telefone/username/tokenVersion para {@link com.eventoscelebrativos.model.UserAccount}
     * quando a pessoa possuir conta (via {@link PersonAccountCoordinator#synchronizeAccountAfterPersonUpdate(Person)}).
     * <p>
     * Pressupoe que o chamador ja bloqueou {@code person} (PESSIMISTIC_WRITE, tipicamente via
     * {@link com.eventoscelebrativos.repository.PersonRepository#findByIdForUpdate(Long)}). Os novos
     * valores cadastrais devem ser passados como candidatos para que este service valide antes de
     * mutar a entidade - garantindo que o lock de Person seja adquirido uma unica vez, antes do lock
     * de UserAccount.
     */
    Person updateCadastral(
            Person person,
            String name,
            String phoneNumber,
            LocalDate birthdayDate
    );
}
