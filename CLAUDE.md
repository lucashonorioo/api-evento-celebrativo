# Evento Celebrativo — instruções do repositório

## Contexto

Este monorepo contém uma aplicação para gerenciamento de eventos celebrativos, pessoas, locais, usuários, ministérios e escalas.

```text
backend/evento-celebrativo-api/        API Java e Spring Boot
frontend-web/evento-celebrativo-web/   aplicação Angular
```

Os `CLAUDE.md` aninhados acrescentam instruções específicas quando Claude trabalha em cada área.

## Fonte de verdade

- Confirme estrutura, versões, scripts e comportamento nos arquivos reais do repositório.
- Use `pom.xml`, `package.json`, configurações, migrations, código e testes antes de assumir detalhes.
- Não trate roadmaps, anotações antigas ou documentação desatualizada como implementação existente.
- Procure um padrão equivalente já adotado antes de introduzir uma nova convenção.

## Limites entre áreas

- Em tarefa exclusivamente backend, não altere frontend sem solicitação explícita.
- Em tarefa exclusivamente frontend, pode ler contratos e testes do backend, mas não deve alterá-lo por iniciativa própria.
- Em mudança de contrato HTTP, trate backend e frontend como uma alteração coordenada.
- Não altere código sem relação apenas porque os dois projetos estão no mesmo repositório.
- Execute comandos dentro da pasta do projeto correspondente, salvo script explícito da raiz.

## Fluxo de alteração

1. identifique requisito, critérios de aceite, área e comportamento afetados;
2. inspecione a implementação atual e padrões equivalentes;
3. trace contratos, segurança, persistência, estados de UI e testes relacionados;
4. avalie compatibilidade e riscos de dados, concorrência, desempenho e operação quando aplicável;
5. implemente uma alteração mínima e coerente;
6. atualize testes e documentação afetada;
7. execute `validate-project` com validações proporcionais ao risco;
8. depois da última alteração relevante, execute `review-change` conforme `.ai/review/ENGINEERING_REVIEW.md`;
9. trate `CHANGES_REQUIRED` dentro do escopo, revalide e revise novamente após correções;
10. conclua somente com `PASS` ou `PASS WITH NOTES` e reporte resultados verificados.

## Contratos backend e frontend

- O backend é a fonte definitiva de autenticação, autorização, validações de domínio e persistência.
- O frontend não deve inventar endpoints, campos, roles, status HTTP ou formatos de paginação.
- Antes de alterar request ou response, localize controller, DTO, mapper, service, configuração de segurança, testes e consumidores no frontend.
- Preserve compatibilidade de contratos em uso, salvo requisito explícito de mudança incompatível.
- Mudanças incompatíveis devem atualizar os consumidores afetados e deixar claro o impacto e a estratégia de migração.
- Atualize OpenAPI, exemplos ou documentação versionada quando eles fizerem parte do contrato alterado.

## Qualidade transversal

- Preserve invariantes de domínio, integridade de dados e limites transacionais.
- Não esconda falhas com fallback silencioso, dados falsos ou sucesso artificial.
- Logs devem ajudar a diagnosticar o fluxo sem expor dados sensíveis.
- Trate acessibilidade no frontend e segurança server-side como requisitos funcionais da alteração quando afetados.
- Não introduza otimização sem evidência; também não ignore consultas, loops ou renderizações claramente não escaláveis.

## Segurança e dados

- Não inclua segredos em código, exemplos, fixtures, logs ou documentação versionada.
- Não confie em ocultação de interface como autorização; o backend deve validar permissões.
- Alterações em autenticação, JWT, roles, CORS ou endpoints públicos exigem testes específicos de autorização.
- Não edite migrations Flyway versionadas que já possam ter sido aplicadas; crie uma nova migration incremental.
- Não execute limpeza ou operação destrutiva de banco sem solicitação explícita.

## Git

- Preserve alterações locais e não faça operações remotas sem pedido explícito.
- Quando solicitado, use branches no formato `tipo/objetivo-da-tarefa`.
- Prefixos adotados: `feature/`, `fix/`, `test/`, `chore/`, `docs/`, `refactor/` e `perf/`.
- Prefira mensagens de commit profissionais em inglês e com escopo coerente.

## Extensões do projeto

- `.ai/review/ENGINEERING_REVIEW.md` é a política canônica de revisão de engenharia compartilhada com o Codex.
- Use uma skill quando a tarefa corresponder ao workflow descrito nela.
- Use subagents para exploração ou revisão isolada quando o volume de contexto ou o risco justificar; mantenha a edição e a decisão final no agente principal.
- Hooks são guardrails determinísticos e não substituem análise de impacto nem testes.

## Definição de concluído

Uma tarefa está concluída quando:

- o requisito e os critérios de aceite foram atendidos sem alterações fora do escopo;
- contratos, segurança, dados e comportamento existente foram preservados ou alterados intencionalmente;
- testes relevantes foram atualizados;
- validações aplicáveis foram executadas ou a limitação foi documentada;
- `git diff --check` não aponta problemas;
- o diff não contém arquivos inesperados, artefatos, logs temporários ou segredos;
- a revisão de engenharia terminou em `PASS` ou `PASS WITH NOTES`;
- não restam `BLOCKER`, `HIGH` ou `MEDIUM` introduzidos pela tarefa e ainda acionáveis dentro do escopo;
- a resposta final informa exatamente o que foi e não foi validado e o veredito da revisão.

## Graphify — consulta e atualização do grafo

Este projeto tem um grafo de conhecimento em `graphify-out/` (nós centrais, estrutura de comunidades e relacionamentos entre arquivos). Os hooks `PreToolUse` do Graphify já configurados em `.claude/settings.json` (`graphify hook-guard search` para `Bash`/`Grep` e `graphify hook-guard read` para `Read`/`Glob`) reforçam automaticamente a consulta prioritária ao grafo; as regras abaixo formalizam quando e como usá-lo.

### Consulta prioritária

- Antes de responder perguntas sobre arquitetura, dependências, relacionamentos, classes, métodos, chamadas, implementações, impacto de alterações, fluxos de execução ou localização de código, consulte primeiro o Graphify: `graphify query "<pergunta>"` para contexto amplo, `graphify path "<A>" "<B>"` para a relação entre dois pontos, ou `graphify explain "<conceito>"` para um conceito específico. Essas consultas retornam um subgrafo focado, normalmente bem menor que `GRAPH_REPORT.md` ou uma busca ampla com grep.
- Antes de usar buscas amplas com `Grep`, `Glob`, `find`, leitura sequencial de vários arquivos ou exploração repetitiva do projeto, tente obter a informação por meio de uma consulta ao Graphify.
- Use os resultados do Graphify para identificar os arquivos e símbolos relevantes e, somente depois, leia diretamente os arquivos específicos necessários para confirmar detalhes de implementação ou realizar uma edição.
- Não trate o Graphify como substituto absoluto da leitura de código. Confirme diretamente o conteúdo exato antes de editar um arquivo ou tomar uma decisão que dependa de detalhes de implementação.
- Quando o Graphify não encontrar informação suficiente, estiver desatualizado ou não representar corretamente determinado conteúdo, use normalmente `Grep`, `Glob` e leitura direta de arquivos.
- Se `graphify-out/wiki/index.md` existir, use-o para navegação ampla em vez de explorar o código-fonte diretamente.
- Leia `graphify-out/GRAPH_REPORT.md` inteiro apenas para revisão arquitetural ampla ou quando `query`/`path`/`explain` não trouxerem contexto suficiente.

### Atualização incremental

A atualização do grafo tem dois custos bem diferentes: reextração de código via AST é gratuita (sem LLM); atualização semântica de documentos consome tokens (uma única atualização de um documento já custou 52.225 tokens de entrada). Por isso, distinga sempre alteração técnica/estrutural de alteração documental antes de decidir o que atualizar.

#### 1. Código e estrutura técnica (`graphify update .`)

Execute `graphify update .` (reextração incremental via AST; sem custo de LLM) uma única vez ao final da tarefa quando houver alteração relevante em:
- código-fonte;
- testes;
- entidades, DTOs, services, repositories e controllers;
- APIs e contratos;
- schemas e migrations;
- scripts;
- configurações que alterem o funcionamento da aplicação;
- estrutura, nomes ou localização de arquivos relevantes;
- dependências ou relacionamentos entre componentes.

Não execute após cada arquivo individual: agrupe as alterações da tarefa e atualize uma única vez ao final. Execute uma atualização intermediária apenas quando o grafo atualizado for necessário para continuar corretamente a análise ainda dentro da mesma tarefa.

#### 2. Documentação arquitetural relevante (atualização semântica)

A atualização semântica de documentos só deve ser realizada quando houver alteração material em documentos que representem conhecimento real do sistema, como: arquitetura; modelo de domínio; contratos de API; fluxos de negócio; decisões técnicas; integrações; segurança; regras de dados; ou documentação funcional/técnica importante para compreender o projeto. Agrupe várias alterações documentais antes de fazer uma única atualização semântica.

#### 3. Arquivos que não devem provocar atualização automática

Não execute atualização estrutural nem semântica do Graphify apenas porque houve alteração em:
- `CLAUDE.md` ou `.claude/CLAUDE.md`;
- arquivos dentro de `.claude/` ou `.agents/`;
- configurações de permissões;
- hooks;
- skills e instruções de agentes;
- arquivos de configuração do próprio Graphify;
- arquivos dentro de `graphify-out/`;
- documentação operacional sobre uso de ferramentas;
- instruções de Git;
- notas temporárias;
- arquivos de ambiente local;
- arquivos que não representam arquitetura, domínio ou funcionamento da aplicação.

Esses arquivos só devem provocar atualização caso a alteração também represente conhecimento técnico real do sistema que precise ser consultável no grafo (por exemplo, um ADR ou roadmap de arquitetura dentro de `docs/`, avaliado pela regra 2 acima — não pela mera localização do arquivo).

#### 4. Antes de uma atualização semântica cara

Não execute automaticamente a atualização semântica completa apenas porque um arquivo Markdown, JSON ou de configuração foi alterado. Antes de iniciá-la, avalie:
- o documento contém conhecimento relevante sobre o sistema?
- esse conhecimento será útil em futuras consultas ao código?
- a informação ainda não está representada no grafo?
- o benefício esperado justifica o consumo de tokens?

Se a resposta a qualquer uma dessas perguntas for negativa, não atualize semanticamente o documento.

#### 5. Prioridade para atualização estrutural barata

Quando apenas código foi alterado, priorize `graphify update .` (sem custo de LLM). Não inicie extração semântica de documentos sem necessidade comprovada pela regra 2.

#### Antes de concluir a tarefa

- Verifique se houve mudança relevante para o grafo segundo as regras acima.
- Execute somente o tipo de atualização realmente necessário (estrutural, semântica ou nenhuma) — não atualize o grafo apenas para cumprir mecanicamente esta regra.
- Não execute `graphify .` (reconstrução completa), salvo reconstrução explicitamente solicitada pelo usuário ou recuperação de um grafo inválido.
- A própria atualização dos arquivos em `graphify-out/` não deve disparar outra atualização do grafo.
- Se nenhuma atualização foi necessária, informe isso e explique brevemente o motivo.
- Se uma atualização foi executada, confirme que terminou com sucesso; se falhar, informe o erro claramente e não afirme que o grafo está atualizado.

### Saúde do grafo

- Relatórios podem indicar `dangling-endpoint edges`, `collapsed edges` ou nós isolados; isso não é automaticamente um bug no código-fonte e não deve motivar alteração no projeto apenas para eliminar esses números.
- Considere esses números como possíveis limitações do extrator AST/LLM. Investigue-os somente quando: estiverem prejudicando uma consulta concreta; apontarem para uma relação importante ausente; o usuário solicitar explicitamente uma auditoria do grafo; ou houver evidência de que representam um problema real do código.

### Uso eficiente de tokens

- Não leia `graphify-out/graph.json` inteiro para responder perguntas normais.
- Não leia `graphify-out/GRAPH_REPORT.md` inteiro repetidamente quando uma consulta direcionada (`query`/`path`/`explain`) for suficiente.
- Prefira consultas específicas e direcionadas ao grafo em vez de perguntas genéricas que retornem grandes volumes de informação.
- Use o grafo para restringir a quantidade de arquivos que precisam ser lidos diretamente.
- Não reconstrua o grafo completo apenas para garantir que ele esteja atualizado; prefira `graphify update .`.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
