# Backend Evento Celebrativo — Java e Spring Boot

## Escopo

Estas regras se aplicam ao backend localizado nesta pasta. Leia também o `AGENTS.md` da raiz do repositório.

O backend é uma API REST para eventos celebrativos, pessoas, locais, usuários, perfis de acesso, ministérios e escalas.

## Fonte de verdade e stack

Antes de alterar código, confirme versões e dependências no `pom.xml`. Preserve a stack existente, incluindo quando presentes:

- Java 21;
- Spring Boot 3.x;
- Spring Web;
- Spring Data JPA;
- Bean Validation;
- Spring Security, OAuth2 Authorization Server e Resource Server;
- MapStruct;
- Maven Wrapper;
- JUnit 5 e Mockito;
- Flyway.

Não atualize Java, Spring Boot, plugins ou dependências sem solicitação explícita.

## Comandos oficiais no Windows

Execute a partir desta pasta:

```powershell
.\mvnw.cmd clean compile
.\mvnw.cmd test
.\mvnw.cmd -q test
.\mvnw.cmd spring-boot:run
```

Prefira o Maven Wrapper. Não substitua por uma instalação global de Maven sem necessidade.

## Análise antes da implementação

1. Localize controller, DTOs, mapper, service, repository, entidade, segurança e testes relacionados.
2. Trace o fluxo real da requisição até a persistência.
3. Procure padrões equivalentes já usados no projeto.
4. Confirme contratos HTTP e regras de autorização existentes.
5. Identifique compatibilidade, transações, constraints e efeitos sobre dados.
6. Implemente somente o necessário.

## Arquitetura

Preserve a divisão atual de responsabilidades:

- `controller`: protocolo HTTP, validação de request, status e composição da resposta;
- `service`: contratos de negócio;
- `service.impl`: regras de negócio e limites transacionais;
- `repository`: persistência Spring Data JPA;
- `model`: entidades JPA e invariantes persistentes;
- `dto.request`: entrada da API;
- `dto.response`: saída da API;
- `mapper`: conversão entre entidade e DTO, preferencialmente MapStruct;
- `exception`: exceções de domínio e tratamento global.

Não crie camadas adicionais sem benefício concreto. Não mova pacotes em massa por preferência arquitetural.

## Controllers e contratos HTTP

- Não exponha entidades JPA diretamente.
- Use DTOs distintos quando request e response tiverem responsabilidades diferentes.
- Controllers devem validar entrada, aplicar semântica HTTP e delegar regras ao service.
- Não coloque consultas de repository ou regras complexas no controller.
- Preserve endpoints, payloads e status codes já validados, salvo requisito explícito.
- Para novos paths, use nomenclatura consistente com a API existente e evite mudanças cosméticas em endpoints legados.
- Erros devem ser convertidos em respostas consistentes pelo `GlobalExceptionHandler`.
- Não devolva stack traces, mensagens internas de banco ou dados sensíveis.

## Services e transações

- Coloque regras de negócio no service.
- Defina transações nos limites de caso de uso adequados.
- Use transações somente leitura em consultas quando o padrão atual justificar.
- Evite lógica de domínio espalhada entre controller, mapper e repository.
- Não capture exceções genericamente para ocultar falhas.
- Converta violações conhecidas em exceções de domínio ou respostas HTTP adequadas.

## Persistência e JPA

- Preserve constraints e relacionamentos existentes.
- Avalie carregamento lazy/eager, N+1, paginação e cascades antes de mudar mapeamentos.
- Não use `findAll()` indiscriminadamente em fluxos potencialmente grandes.
- Para consultas customizadas, prefira nomes claros e testes de repository quando o comportamento for relevante.
- Não altere schema diretamente sem seguir o mecanismo de migrations do projeto.
- Não modifique migrations Flyway versionadas já aplicáveis; crie uma nova migration incremental.
- Não inclua credenciais ou secrets em `application*.properties`.

## DTOs e MapStruct

- Mapeamentos devem ser explícitos quando nomes ou responsabilidades diferirem.
- Evite lógica de negócio complexa no mapper.
- Não reutilize DTO de response como request apenas para reduzir arquivos.
- Preserve serialização e nomes de campos consumidos pelo frontend.
- Ao alterar um contrato, use a Skill `change-api-contract` e atualize consumers e testes coordenadamente.

## Validação e erros

- Use Bean Validation para regras estruturais de entrada.
- Use validação de domínio no service para regras que dependem de estado ou persistência.
- Diferencie recurso inexistente, conflito de integridade, acesso negado e entrada inválida.
- Não retorne fallback silencioso que esconda inconsistências.
- Mensagens externas devem ser claras e seguras; detalhes técnicos ficam em logs adequados.

## Segurança

- Não remova autenticação ou autorização para fazer testes passarem.
- O backend é a fonte definitiva de permissões.
- Preserve o fluxo de login e os endpoints públicos existentes até requisito explícito.
- Novos endpoints são protegidos por padrão; exposição pública exige decisão intencional e teste.
- Alterações em roles, JWT, claims, password encoding, CORS ou filtros exigem revisão do `security_reviewer` em tarefas de risco médio ou alto.
- Cubra cenários autenticado, não autenticado e sem permissão quando alterar segurança.
- Nunca registre senhas, tokens completos ou secrets.

## Invariantes conhecidas a preservar

Confirme no código antes de usar, mas trate como compatibilidade esperada enquanto permanecerem implementadas:

- login público em `POST /public/login`;
- consultas públicas de eventos e escala eucarística definidas na configuração de segurança;
- CRUDs administrativos protegidos por `ROLE_ADMIN`;
- usuários novos recebem o perfil padrão definido pelo domínio;
- alteração de roles é restrita a administrador;
- conflito de integridade em exclusões vinculadas retorna resposta amigável, normalmente `409 Conflict`.

## Testes

Use o padrão existente:

- services: JUnit 5 e Mockito;
- controllers: MockMvc;
- repositories: `@DataJpaTest`;
- contexto Spring: somente quando a integração real for necessária.

Regras:

- use `@MockitoBean` quando o projeto estiver padronizado nele;
- teste comportamento observável e regras de negócio, não detalhes triviais;
- inclua sucesso, validação, inexistência, conflito e autorização conforme o risco;
- não enfraqueça assertions para fazer o teste passar;
- não exclua ou desabilite testes sem causa comprovada;
- ao corrigir bug, adicione teste de regressão sempre que viável.

## Validação antes de concluir

Escolha validações proporcionais à alteração:

```powershell
.\mvnw.cmd -q -Dtest=NomeDoTeste test
.\mvnw.cmd -q test
```

Também verifique:

- compilação;
- contexto Spring quando afetado;
- migrations e profiles relevantes;
- segurança dos endpoints alterados;
- contratos utilizados pelo frontend;
- `git diff --check`;
- ausência de arquivos gerados, logs e secrets.

Se não puder executar a suíte completa, informe quais testes foram executados e o risco restante.

## Proibições

Sem solicitação explícita, não:

- atualize dependências ou versões;
- altere contratos públicos;
- desative segurança;
- substitua MapStruct ou a arquitetura atual;
- introduza fallback silencioso;
- modifique migrations antigas;
- altere frontend;
- faça commit, push, merge ou rebase;
- execute limpeza destrutiva do repositório ou do banco.
