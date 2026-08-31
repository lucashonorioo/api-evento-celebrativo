# Domínio atual — Person / Ministry / EventAssignment

Leia este arquivo somente em alterações que toquem `Person`, `PersonMinistry`, `EventAssignment`, `EventParticipationResponse` ou os adaptadores ministeriais relacionados.

A migração do modelo legado está concluída. O runtime atual não possui cutover `LEGACY/PARALLEL`, shadow-read, `PersonMinistryReadSourceProperties`, `EventAssignmentReadSourceProperties`, write-through legado nem `EventAssignmentCompatibilityService`. Referências restantes a estruturas antigas em migrations são histórico imutável.

Fontes atuais:

- `PersonMinistry` é a única fonte de classificação ministerial de uma `Person`; leitura via `PersonMinistryReadService`, escrita via `PersonMinistryCommandService`.
- `EventAssignment` é a única fonte das funções de uma pessoa em evento; unicidade `event_id + person_id + assignment_type`, permitindo múltiplas funções no mesmo evento.
- `EventAssignmentCommandService` é o mecanismo oficial de escrita/sincronização e limpa `EventParticipationResponse` quando a pessoa perde todas as funções no evento.
- `EventParticipationResponse` representa confirmação/recusa por `event_id + person_id` e é gerenciado por `EventParticipationResponseService`.

Invariantes:

- não recrie `LEGACY/PARALLEL`, shadow-read ou write-through legado;
- não consulte `tb_event_person` nem dependa de `person_type`; existem apenas no histórico de migrations;
- não reintroduza `Reader`, `Commentator`, `Priest`, `MinisterOfTheWord` ou `EucharisticMinister` como entidades JPA;
- controllers/services com nomes ministeriais legados que ainda existirem são adaptadores HTTP finos sobre `Person + PersonMinistry` e permanecem necessários enquanto não houver API genérica equivalente;
- migrations `V1`–`V9` são história do schema e não devem ser modificadas.

Antes de usar qualquer afirmação acima em uma mudança, confirme no código atual que ela continua válida; se o próprio requisito alterar esse domínio, atualize este documento junto com a implementação.
