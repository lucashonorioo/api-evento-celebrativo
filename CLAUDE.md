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

A infraestrutura de IA versionada (`.ai/`, `.claude/`, `.codex/`, `.agents/`, `AGENTS.md` e `CLAUDE.md`) também é escopo de engenharia: alterações nesses controles devem invalidar a revisão e passar pelo mesmo gate. Arquivos efêmeros/gerados permanecem fora conforme a política canônica.

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

## Graphify

A política canônica compartilhada está em `.ai/graphify/GRAPHIFY_POLICY.md`. Leia e siga esse arquivo para consulta, atualização, custo semântico, proveniência e critérios de encerramento.

Os hooks `PreToolUse` configurados em `.claude/settings.json` apenas reforçam a preferência de consulta; são auxiliares fail-open e não substituem a política canônica. Quando o usuário invocar `/graphify`, use a Skill `.claude/skills/graphify/SKILL.md`.
