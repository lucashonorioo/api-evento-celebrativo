# Projeto Evento Celebrativo — Instruções do repositório

## Objetivo e estrutura

Este repositório contém uma aplicação para gerenciamento de eventos celebrativos, pessoas, locais, usuários, ministérios e escalas.

Estrutura esperada:

```text
backend/evento-celebrativo-api/        API Java/Spring Boot
frontend-web/evento-celebrativo-web/   aplicação Angular
```

Use os arquivos reais do repositório como fonte de verdade. Confirme versões no `pom.xml`, `package.json`, arquivos de configuração e testes antes de assumir detalhes.

## Carregamento das instruções no monorepo

Antes de modificar backend, leia e siga:

```text
backend/evento-celebrativo-api/AGENTS.md
```

Antes de modificar frontend, leia e siga:

```text
frontend-web/evento-celebrativo-web/AGENTS.md
```

Em uma alteração full stack, leia ambos antes de editar. Esta leitura é obrigatória mesmo quando a sessão foi iniciada na raiz do monorepo.

## Limites entre áreas

- Em tarefa exclusivamente backend, não altere frontend sem solicitação explícita.
- Em tarefa exclusivamente frontend, pode ler controllers, DTOs, OpenAPI e testes do backend para confirmar contratos, mas não deve alterá-los por iniciativa própria.
- Em mudanças de contrato da API, trate backend e frontend como uma única alteração coordenada e use a Skill `change-api-contract`.
- Não altere simultaneamente código sem relação apenas porque os dois projetos estão no mesmo repositório.

## Fluxo obrigatório

1. Identifique o escopo e a área afetada.
2. Leia o AGENTS especializado e os arquivos diretamente relacionados.
3. Procure implementação equivalente antes de criar uma nova estrutura.
4. Confirme contratos e comportamento existente por código e testes.
5. Implemente uma alteração mínima e coerente.
6. Atualize testes relevantes.
7. Execute validações proporcionais ao risco.
8. Revise o diff e reporte resultados reais.

## Contratos backend/frontend

- O backend é a fonte definitiva de autenticação, autorização e regras de negócio.
- O frontend não deve inventar campos, endpoints, roles ou formatos de paginação.
- Antes de alterar requests ou responses, localize controller, DTO, mapper, service, testes e consumers no frontend.
- Preserve compatibilidade quando o contrato já estiver em uso, salvo requisito explícito de breaking change.
- Mudanças incompatíveis devem incluir plano de migração ou atualização coordenada dos consumers.

## Segurança

- Nunca inclua secrets em código, exemplos, fixtures, logs ou documentação versionada.
- Não reduza segurança para simplificar desenvolvimento ou testes.
- Não confie em ocultação de UI como autorização; o backend deve validar permissões.
- Alterações em autenticação, JWT, roles, CORS ou endpoints públicos exigem revisão específica de segurança e testes de autorização.

## Persistência e migrations

- Antes de alterar banco, confira o mecanismo atual de migrations e o estado dos schemas.
- Não edite uma migration Flyway versionada que já possa ter sido aplicada. Crie uma nova migration incremental.
- Não execute operações destrutivas ou limpeza de banco sem solicitação explícita.

## Git

- Não faça commit, push, merge, rebase, PR ou exclusão de branch sem solicitação explícita.
- Não descarte alterações existentes do usuário.
- Quando solicitado, use branches no formato `tipo/objetivo-da-tarefa`.
- Prefixos usuais: `feature/`, `fix/`, `test/`, `chore/`, `docs/`, `refactor/`, `perf/`.
- Prefira commits profissionais em inglês e com escopo coerente.

## Uso das Skills

- Bug ou teste falhando: `investigate-bug`.
- Feature ou correção backend: `implement-backend-feature`.
- Feature ou correção frontend: `implement-frontend-feature`.
- Mudança de request/response/endpoint: `change-api-contract`.
- Build e testes finais: `validate-project`.
- Revisão de branch ou diff: `review-change`.

## Delegação para Subagents

Use subagents em tarefas grandes quando houver frentes independentes. Exemplos:

- `codebase_explorer`: mapear fluxo e arquivos antes da implementação;
- `backend_reviewer`: revisar Java/Spring;
- `frontend_reviewer`: revisar Angular/TypeScript/acessibilidade;
- `test_reviewer`: encontrar lacunas ou fragilidade de testes;
- `security_reviewer`: revisar autenticação, autorização e exposição de dados.

Aguarde os subagents necessários e consolide as evidências antes de editar. Evite subagents em alterações pequenas e evite escritas paralelas.

## Definição de concluído

Uma tarefa só está concluída quando:

- o requisito foi atendido sem alterações fora do escopo;
- contratos e segurança foram preservados;
- testes relevantes foram atualizados;
- build/testes aplicáveis foram executados ou a limitação foi documentada;
- `git diff --check` não aponta problemas;
- o diff foi revisado quanto a arquivos inesperados e segredos;
- a resposta final informa exatamente o que foi validado.
