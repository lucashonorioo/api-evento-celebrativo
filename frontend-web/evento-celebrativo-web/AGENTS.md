# Frontend Evento Celebrativo — Angular e TypeScript

## Escopo

Estas regras se aplicam ao frontend localizado nesta pasta. Leia também o `AGENTS.md` da raiz do repositório.

O frontend fornece consultas públicas e funcionalidades autenticadas/administrativas para eventos, escalas, locais, pessoas e usuários.

## Fonte de verdade e stack

Antes de alterar código, confirme versões e scripts em `package.json`, `angular.json` e `tsconfig*.json`. Preserve a stack existente, incluindo quando presentes:

- Angular 20;
- standalone components;
- Angular Router e HttpClient;
- TypeScript estrito;
- RxJS;
- CSS;
- Jasmine, Karma e TestBed;
- Prettier.

A aplicação usa `bootstrapApplication` e não deve receber `AppModule` sem necessidade técnica comprovada.

Não atualize Angular, TypeScript ou dependências sem solicitação explícita.

## Comandos oficiais

Confirme os scripts no `package.json`. Os comandos esperados são:

```powershell
npm install
npm start
npm run build
npm test -- --watch=false
```

Não execute `npm install` se as dependências existentes já forem suficientes. Não altere lockfile sem uma mudança real de dependência.

## Análise antes da implementação

1. Localize rota, página, componente, serviço, model, guard/interceptor e testes relacionados.
2. Confirme o contrato real no backend, OpenAPI ou testes.
3. Procure componentes e padrões equivalentes antes de criar novos.
4. Verifique autenticação, roles, estados de UI e responsividade afetados.
5. Implemente o menor conjunto coerente de alterações.

Não presuma que uma funcionalidade ainda não existe com base em roadmaps antigos; confira o código atual.

## Arquitetura Angular

- Novos componentes, diretivas e pipes devem ser standalone.
- Preserve a organização atual quando ela continuar clara.
- Crie pastas e abstrações apenas quando houver responsabilidades reais.
- Evite concentrar integração HTTP, autenticação, estado e apresentação em um único componente.
- Separe página, componente de apresentação, serviço HTTP, model e formulário quando isso melhorar coesão e teste.
- Não reorganize todo o projeto para impor uma arquitetura teórica.
- Prefira lazy loading em páginas e áreas maiores quando compatível com o padrão existente.

## Componentes e estado

- Cada componente deve ter responsabilidade clara.
- Prefira `ChangeDetectionStrategy.OnPush` em novos componentes quando seguro.
- Use properties tipadas, signals e computed signals para estado local simples.
- Use RxJS para HTTP e composição assíncrona.
- Mantenha estado próximo de onde é usado.
- Não introduza NgRx, Redux ou estado global sem necessidade comprovada.
- Evite subscriptions aninhadas; use operadores de composição.
- Em subscriptions manuais de longa duração, gerencie o ciclo de vida com `takeUntilDestroyed` ou padrão equivalente.
- Não faça funções custosas repetidamente no template.

## TypeScript

- Preserve `strict` e as opções estritas do projeto.
- Não use `any` para contornar tipagem. Use `unknown` com validação quando a origem não for confiável.
- Modele request e response separadamente quando os contratos diferirem.
- Trate explicitamente `null`, `undefined`, respostas vazias e dados incompletos.
- Evite `!` sem garantia lógica.
- Prefira `readonly`, imutabilidade e nomes expressivos.
- Use `PascalCase` para tipos/classes, `camelCase` para membros e `kebab-case` para arquivos.
- Textos da interface devem permanecer em português do Brasil; nomes técnicos do código devem seguir o padrão existente.

## Integração HTTP e contratos

- O backend é a fonte de verdade do contrato.
- Não invente endpoints, campos, roles, paginação ou status codes.
- Centralize a URL base; não replique `localhost` em serviços.
- Serviços HTTP devem ser tipados, retornar `Observable` e não manipular DOM ou estado específico da página.
- Não crie um serviço genérico com todos os endpoints nem abstrações HTTP prematuras.
- Diferencie DTO de API, estado do formulário e view model quando necessário.
- Ao alterar contrato, use a Skill `change-api-contract` e coordene backend e frontend.
- Para datas, diferencie data sem horário de timestamp e evite mudanças de dia por timezone.

## Autenticação e autorização

- Preserve o fluxo OAuth2/JWT existente.
- Centralize armazenamento, leitura, expiração e remoção do token.
- Não acesse `localStorage` diretamente em vários componentes.
- Nunca armazene senha nem registre tokens no console.
- Interceptors devem tratar autenticação e preocupações HTTP transversais, não regras de página.
- `401` representa ausência/expiração de sessão; `403` representa usuário autenticado sem permissão.
- Guards devem preferir retorno de `UrlTree` em vez de navegação imperativa quando aplicável.
- Roles no frontend controlam experiência de UI, não substituem autorização do backend.
- Não envie token para origens externas.

## Formulários

- Para formulários novos com validação ou múltiplos campos, prefira Reactive Forms tipados e `NonNullableFormBuilder` quando adequado.
- Inclua validação, mensagens claras, estado de envio e prevenção de múltiplos submits.
- Associe `label` aos campos e configure `autocomplete` apropriadamente.
- Não migre formulários existentes apenas por preferência.
- Não esconda falhas retornando dados falsos ou sucesso artificial.

## Rotas

- Antes de alterar uma rota, localize todos os links e navegações que a utilizam.
- Preserve guards e divisão entre rotas públicas, autenticadas e administrativas.
- Não crie rotas para componentes inexistentes.
- Use lazy loading conforme o padrão atual e o tamanho da funcionalidade.
- Não altere a rota inicial ou o layout global sem requisito explícito.

## Estados de interface e erros

Telas assíncronas devem considerar conforme aplicável:

- carregando;
- conteúdo carregado;
- vazio;
- erro;
- acesso negado;
- sessão expirada.

Regras:

- apresente mensagens compreensíveis, sem stack trace ou detalhes internos;
- trate `400`, `401`, `403`, `404`, `409`, `422` e `500` conforme o contrato real;
- não silencie erros sem estratégia;
- use `catchError` somente com transformação, mensagem ou fallback seguro real;
- evite interfaces que aparentem travamento durante requisições.

## Templates, CSS e acessibilidade

- Use a sintaxe moderna do Angular quando compatível com o código atual.
- Em listas, informe `track` estável.
- Prefira HTML semântico, botões reais para ações e links reais para navegação.
- Garanta navegação por teclado, foco visível, labels, mensagens associadas e contraste adequado.
- Use ARIA somente quando HTML semântico não resolver.
- Preserve CSS do projeto; não introduza biblioteca visual sem pedido e justificativa.
- Mantenha estilos específicos próximos ao componente e estilos globais apenas para fundamentos compartilhados.
- Evite `!important`, seletores globais agressivos e dimensões fixas que prejudiquem responsividade.
- Verifique pelo menos comportamento básico em telas menores nas alterações visuais.

## Segurança no frontend

- Não exponha secrets, senhas ou tokens.
- Não use `bypassSecurityTrustHtml` sem análise de segurança comprovada.
- Não renderize HTML externo sem sanitização.
- Não confie exclusivamente em validação cliente.
- Não remova guards, interceptors ou tratamento de sessão para simplificar testes.

## Testes

Use o padrão atual com Jasmine, Karma e TestBed.

Crie ou atualize testes para comportamentos relevantes, como:

- serviços HTTP, URL, método, body e erro;
- autenticação, armazenamento e expiração de token;
- guards e interceptors;
- formulários e validações;
- renderização por role;
- estados loading, vazio e erro;
- transformações de datas ou paginação;
- regressões de bugs.

Para testes HTTP, use as APIs de teste compatíveis com a versão atual, como `provideHttpClient`, `provideHttpClientTesting` e `HttpTestingController` quando aplicável.

Teste comportamento observável. Não desabilite ou apague testes sem identificar a causa.

## Validação antes de concluir

Escolha validações proporcionais à alteração:

```powershell
npm test -- --watch=false
npm run build
```

Também verifique:

- erros TypeScript e de template;
- imports e código não utilizados;
- rotas e links alterados;
- requisições e contratos;
- responsividade e acessibilidade básicas;
- ausência de logs sensíveis;
- `git diff --check`;
- ausência de `dist`, coverage ou outros artefatos não intencionais.

Compilação não substitui teste funcional. Se não puder validar no navegador, informe essa limitação.

## Proibições

Sem solicitação explícita, não:

- migre para outro framework;
- adicione biblioteca visual ou de estado;
- atualize dependências;
- altere backend;
- modifique contratos da API;
- desative autenticação ou guards;
- introduza `any` para silenciar erros;
- apague testes falhando;
- faça refatoração estrutural ampla;
- faça commit, push, merge ou rebase.
