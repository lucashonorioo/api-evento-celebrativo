# Backend Evento Celebrativo — Java e Spring Boot

## Escopo e fonte de verdade

Estas instruções se aplicam à API desta pasta e complementam o `CLAUDE.md` da raiz.

- Confirme versões, plugins, profiles e dependências no `pom.xml` e nos arquivos de configuração.
- A stack esperada inclui Java 21, Spring Boot 3.x, Spring Web, Spring Data JPA, Bean Validation, Spring Security/OAuth2, MapStruct, Flyway, Maven Wrapper, JUnit 5 e Mockito; o repositório é a fonte definitiva.
- Não atualize Java, Spring Boot, plugins ou dependências sem solicitação explícita.
- Não substitua bibliotecas ou padrões existentes apenas por preferência pessoal.

## Comandos

Execute a partir desta pasta e prefira o Maven Wrapper:

```powershell
.\mvnw.cmd clean compile
.\mvnw.cmd test
.\mvnw.cmd -q test
.\mvnw.cmd spring-boot:run
```

Para validação específica:

```powershell
.\mvnw.cmd -q -Dtest=NomeDoTeste test
```

## Análise antes da implementação

Antes de alterar um fluxo:

1. localize controller, DTOs, mapper, service, repository, entidade, segurança, migration e testes relacionados;
2. trace a requisição até a persistência e a resposta;
3. procure um caso equivalente já implementado;
4. confirme contrato HTTP, autorização, transações, constraints e efeitos sobre dados;
5. avalie paginação, concorrência, desempenho e compatibilidade quando relevantes;
6. implemente somente o necessário.

## Organização do código

Preserve a divisão de responsabilidades atual:

- `controller`: protocolo HTTP, validação de entrada, status e composição da resposta;
- `service`: contratos dos casos de uso;
- `service.impl`: regras de negócio e limites transacionais;
- `repository`: persistência com Spring Data JPA;
- `model`: entidades JPA e invariantes persistentes;
- `dto.request`: entrada da API;
- `dto.response`: saída da API;
- `mapper`: conversão entre entidades e DTOs, preferencialmente com MapStruct;
- `exception`: exceções de domínio e tratamento global.

Não crie novas camadas, interfaces ou pacotes nem mova código em massa sem benefício concreto.

## Controllers e contratos HTTP

- Não exponha entidades JPA diretamente.
- Use DTOs distintos quando request e response tiverem responsabilidades diferentes.
- Controllers validam entrada, aplicam semântica HTTP e delegam regras ao service.
- Não coloque consultas de repository nem regras complexas no controller.
- Preserve endpoints, payloads, nomes serializados e status já consumidos pelo frontend.
- Para novos paths, siga a nomenclatura existente e evite mudanças cosméticas em endpoints legados.
- Erros devem ser convertidos em respostas consistentes pelo tratamento global existente.
- Não devolva stack traces, mensagens internas do banco ou dados sensíveis.

## Services, domínio e transações

- Mantenha regras de negócio e validações dependentes de estado no service.
- Defina transações nos limites do caso de uso e evite gravações parciais.
- Use transações somente leitura em consultas quando o padrão atual e o benefício justificarem.
- Não espalhe lógica de domínio entre controller, mapper e repository.
- Não capture exceções genericamente para ocultar falhas.
- Diferencie recurso inexistente, entrada inválida, conflito de integridade e acesso negado.
- Não introduza locking, retry ou controle de concorrência sem cenário comprovado; ao existir risco real de atualização concorrente, trate-o explicitamente e teste o comportamento.

## Persistência e JPA

- Preserve constraints, relacionamentos, cascades e estratégia transacional existentes.
- Avalie lazy/eager loading, N+1, paginação, volume e ordenação antes de alterar consultas ou mapeamentos.
- Não use `findAll()` indiscriminadamente em fluxos potencialmente grandes.
- Dê nomes claros a consultas customizadas e crie testes de repository quando o comportamento for relevante.
- Não altere schema fora do mecanismo de migrations do projeto.
- Nunca modifique uma migration Flyway versionada que já possa ter sido aplicada; crie outra incremental.
- Considere compatibilidade, backfill, valores nulos, índices e rollback operacional em mudanças de dados.
- Não inclua credenciais ou segredos em `application*.properties`.

## Estado atual do domínio Person/PersonMinistry/EventAssignment

A migração do modelo legado (entidades por tipo de ministério, ex. `Reader` como entidade JPA, coluna `person_type`, tabela `tb_event_person`) para o modelo unificado está concluída. O runtime atual não possui mais cutover `LEGACY`/`PARALLEL`, shadow-read, `PersonMinistryReadSourceProperties`, `EventAssignmentReadSourceProperties`, write-through legado nem `EventAssignmentCompatibilityService` — nada disso existe mais no código; onde ainda aparecem, é apenas em migrations aplicadas (histórico imutável) ou em nomes de testes que hoje protegem comportamento atual sob um nome antigo.

Fontes oficiais atuais:

- `PersonMinistry` é a única fonte de classificação ministerial de uma `Person`; leia via `PersonMinistryReadService` e escreva via `PersonMinistryCommandService`.
- `EventAssignment` é a única fonte das funções atribuídas a uma pessoa em um evento; a unicidade é `event_id + person_id + assignment_type` — uma pessoa pode exercer várias funções no mesmo evento.
- `EventAssignmentCommandService` é o mecanismo oficial de escrita e sincronização de assignments (inclui a limpeza de `EventParticipationResponse` quando a pessoa perde todas as funções no evento).
- `EventParticipationResponse` registra a confirmação ou recusa de participação da pessoa no evento; é única por `event_id + person_id` (não por função) e é gerenciada por `EventParticipationResponseService`.

Regras para qualquer alteração neste domínio:

- não recrie caminhos `LEGACY`/`PARALLEL` nem qualquer mecanismo de shadow-read;
- não consulte `tb_event_person` nem dependa da coluna `person_type` — ambos foram removidos por migration e existem apenas para reconstruir bancos antigos;
- não reintroduza subclasses ministeriais como entidades JPA (`Reader`, `Commentator`, `Priest`, `MinisterOfTheWord`, `EucharisticMinister`); os controllers/services com esses nomes que ainda existem são adaptadores HTTP finos ativos sobre `Person`+`PersonMinistry`, não um modelo de dados paralelo, e continuam necessários enquanto não houver API genérica equivalente para criar pessoa, editar dados de terceiros e definir senha/role inicial;
- as migrations `V1`–`V9` preservam a evolução histórica do schema e não devem ser alteradas.

## DTOs, MapStruct e validação

- Use Bean Validation para regras estruturais de entrada.
- Mapeamentos devem ser explícitos quando nomes ou responsabilidades diferirem.
- Evite lógica de negócio complexa no mapper.
- Não reutilize DTO de response como request apenas para reduzir arquivos.
- Preserve serialização e nomes de campos consumidos pelo frontend.
- Mensagens externas devem ser claras e seguras; detalhes técnicos ficam em logs adequados.
- Não use fallback silencioso que esconda inconsistência de dados ou contrato.

## Segurança

- O backend é a fonte definitiva de permissões.
- Novos endpoints ficam protegidos por padrão; torná-los públicos exige decisão explícita e teste.
- Não remova autenticação ou autorização para fazer testes passarem.
- Alterações em JWT, roles, claims, password encoding, CORS, filtros ou endpoints públicos exigem cobertura de usuário autenticado, não autenticado e sem permissão, conforme aplicável.
- Nunca registre senhas, tokens completos, credenciais ou detalhes internos sensíveis.
- Validação do cliente não substitui validação e autorização server-side.

## Compatibilidade atual

Confirme no código e nos testes antes de usar, mas preserve enquanto estiver implementado:

- login público em `POST /public/login`;
- consultas públicas de eventos e escalas definidas na configuração de segurança;
- operações administrativas protegidas por role apropriada, atualmente `ROLE_ADMIN` quando assim definido;
- perfil padrão atribuído a novos usuários;
- alteração de roles restrita a administrador;
- conflito de integridade em exclusões vinculadas tratado com resposta amigável, normalmente `409 Conflict`.

## Logs e observabilidade

- Registre contexto suficiente para diagnosticar falhas, sem duplicar stack traces nem expor tokens, senhas ou payloads sensíveis.
- Preserve o padrão de logging existente e níveis coerentes (`debug`, `info`, `warn`, `error`).
- Não deixe `System.out`, logs temporários ou mensagens de depuração no diff final.
- Não engula exceções apenas para reduzir ruído de log.

## Testes

Use o padrão existente:

- services: JUnit 5 e Mockito;
- controllers: MockMvc;
- repositories: `@DataJpaTest`;
- contexto Spring completo somente quando a integração real for necessária.

Regras:

- use `@MockitoBean` quando o projeto estiver padronizado nele;
- teste comportamento observável e regras de negócio, não detalhes triviais;
- cubra sucesso, validação, inexistência, conflito, autorização e limites conforme o risco;
- adicione teste de regressão para correção de bug quando viável;
- não enfraqueça assertions nem exclua ou desabilite testes para obter resultado verde.

## Validação final

Execute primeiro o teste mais específico e amplie conforme o risco. Verifique também:

- compilação e contexto Spring quando afetados;
- profiles, migrations e integração com banco relevantes;
- contratos utilizados pelo frontend;
- segurança dos endpoints alterados;
- consultas potencialmente N+1 ou sem paginação;
- `git diff --check`;
- ausência de artefatos, logs temporários e segredos.

Sem solicitação explícita, não altere frontend, versões, contratos públicos, segurança, migrations antigas ou a arquitetura existente.
