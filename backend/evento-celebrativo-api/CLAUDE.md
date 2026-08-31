# Backend Evento Celebrativo — Java/Spring Boot

Estas regras complementam o `CLAUDE.md` da raiz.

## Fonte de verdade

Confirme stack, versões, profiles e dependências no `pom.xml`/configuração. Preserve Java 21, Spring Boot 3.x, Spring Web/Data JPA/Security/OAuth2, Bean Validation, MapStruct, Flyway, Maven Wrapper, JUnit 5/Mockito conforme realmente presentes. Não atualize versões/dependências sem pedido.

Comandos usuais, a partir do backend:

```powershell
.\mvnw.cmd -q -Dtest=NomeDoTeste test
.\mvnw.cmd -q test
.\mvnw.cmd clean compile
```

Use `spring-boot:run` somente quando validação runtime for necessária; nesse caso siga `.ai/runtime/PROCESS_LIFECYCLE.md`.

## Análise e arquitetura

Antes de editar, localize controller, DTOs, mapper, service, repository, entidade, segurança, migrations e testes relacionados; trace entrada → regra → persistência → resposta e procure padrão equivalente.

Preserve responsabilidades:

- controller: HTTP, validação estrutural, status/resposta;
- service/service.impl: casos de uso, domínio e transação;
- repository: persistência;
- model: entidades/invariantes persistentes;
- DTOs: contratos de entrada/saída;
- mapper: conversão, sem regra de negócio complexa;
- exception/handler: erros de domínio e resposta segura.

Não crie camada/interface/pattern nem mova pacotes em massa sem problema concreto que justifique.

## Contratos, domínio e erros

- Não exponha entidade JPA diretamente.
- Preserve endpoint, payload, serialização e status consumidos; mudança de contrato usa `change-api-contract`.
- Bean Validation cobre estrutura; regra dependente de estado fica no domínio/service.
- Diferencie inexistência, entrada inválida, conflito e acesso negado.
- Não capture exceção genericamente para esconder falha nem retorne sucesso/fallback artificial.
- Logs devem permitir diagnóstico sem expor dados sensíveis.


## Estado atual do domínio Person/PersonMinistry/EventAssignment

`PersonMinistry` é a única fonte atual de classificação ministerial; não recrie caminhos `LEGACY`/`PARALLEL`. Para `Person`, `PersonMinistry`, `EventAssignment`, `EventParticipationResponse` ou adaptadores ministeriais, leia `.ai/domain/PERSON_MINISTRY_EVENT_ASSIGNMENT.md` antes de editar.

## Persistência e desempenho

- Defina transações no limite do caso de uso; evite gravação parcial.
- Preserve constraints, relacionamentos e cascades; trate concorrência, unicidade/idempotência quando o domínio exigir.
- Avalie lazy/eager, N+1, paginação, ordenação, volume e queries customizadas.
- Evite `findAll()` em coleção potencialmente grande.
- Schema muda por migration incremental; não reescreva migration Flyway já versionada/aplicável.
- Mudança de dados considera compatibilidade, `null`, índices/backfill e rollout quando relevantes.

## Segurança

- Backend é a fonte definitiva de autorização.
- Endpoint novo é protegido por padrão; exposição pública é decisão explícita e testada.
- Não remova autenticação/autorização para fazer teste passar.
- Mudança em JWT, roles, claims, password encoding, CORS, filtros ou endpoint público exige cenários autenticado/não autenticado/sem permissão conforme aplicável.
- Nunca registre senha, token completo, secret ou detalhe interno sensível.

## Testes e qualidade

Use a camada mais econômica que prove o comportamento:

- service: JUnit/Mockito;
- controller: MockMvc;
- repository: `@DataJpaTest`;
- contexto Spring completo apenas quando integração real exigir.

Cubra sucesso e falhas relevantes ao risco, autorização quando afetada e regressão de bug quando viável. Não teste detalhe trivial, não enfraqueça assertions e não desabilite teste para obter verde.

Na conclusão, execute validações proporcionais à alteração e verifique também contratos frontend, segurança, migrations/profiles, consultas críticas, `git diff --check`, artefatos, debug e secrets.

Sem solicitação explícita, não altere frontend, contratos públicos, segurança, migrations antigas, dependências ou arquitetura geral.
