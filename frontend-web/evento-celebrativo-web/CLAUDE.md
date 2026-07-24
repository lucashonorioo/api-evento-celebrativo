# Frontend Evento Celebrativo — Angular e TypeScript

## Escopo e fonte de verdade

Estas instruções se aplicam à aplicação Angular desta pasta e complementam o `CLAUDE.md` da raiz.

- Confirme versões, scripts e opções em `package.json`, `angular.json` e `tsconfig*.json`.
- A stack esperada inclui Angular 20, standalone components, Router, HttpClient, TypeScript estrito, RxJS, CSS, Jasmine, Karma, TestBed e Prettier; o repositório é a fonte definitiva.
- A aplicação usa bootstrap standalone; não introduza `AppModule` sem necessidade técnica comprovada.
- Não atualize Angular, TypeScript, Node, npm ou dependências sem solicitação explícita.

## Comandos

Confirme os scripts no `package.json`. Os comandos esperados são:

```powershell
npm start
npm run build
npm test -- --watch=false
```

Execute `npm install` somente quando as dependências instaladas forem insuficientes ou houver mudança real de dependência. Não altere o lockfile sem necessidade.

## Análise antes da implementação

1. localize rota, página, componente, serviço, model, formulário, guard, interceptor e testes relacionados;
2. confirme o contrato real no backend, OpenAPI ou testes;
3. procure componentes e padrões equivalentes antes de criar novos;
4. verifique autenticação, roles, estados de UI, acessibilidade e responsividade afetados;
5. implemente o menor conjunto coerente de alterações.

Não presuma que uma funcionalidade não existe com base em roadmap antigo; confira o código atual.

## Organização do código

- Novos componentes, diretivas e pipes devem ser standalone.
- Preserve a organização atual quando ela continuar clara.
- Separe página, apresentação, integração HTTP, models, estado e formulários quando isso melhorar coesão e testes.
- Evite concentrar integração HTTP, autenticação, estado e apresentação em um único componente.
- Não crie um serviço genérico com todos os endpoints nem abstrações HTTP prematuras.
- Prefira lazy loading em páginas e áreas maiores quando compatível com o padrão existente.
- Não reorganize todo o projeto para impor uma arquitetura teórica.

## Componentes, estado e desempenho

- Cada componente deve ter responsabilidade clara.
- Prefira `ChangeDetectionStrategy.OnPush` em componentes novos quando seguro e coerente com o projeto.
- Use properties tipadas, signals e computed signals para estado local simples quando fizer sentido.
- Use RxJS para HTTP e composição assíncrona.
- Mantenha estado próximo de onde é usado.
- Não introduza NgRx, Redux ou outro estado global sem necessidade comprovada e aprovação.
- Evite subscriptions aninhadas; prefira operadores de composição.
- Em subscriptions manuais duradouras, gerencie o ciclo de vida com `takeUntilDestroyed` ou padrão equivalente.
- Não execute funções custosas repetidamente no template; use valores derivados estáveis quando necessário.
- Em listas, use uma chave de rastreamento estável.

## TypeScript

- Preserve `strict` e as demais opções estritas do projeto.
- Não use `any` para contornar tipagem; use tipos específicos ou `unknown` com validação.
- Modele request e response separadamente quando os contratos diferirem.
- Trate explicitamente `null`, `undefined`, respostas vazias e dados incompletos.
- Evite non-null assertion (`!`) sem garantia lógica.
- Prefira `readonly`, imutabilidade e nomes expressivos.
- Use `PascalCase` para tipos/classes, `camelCase` para membros e `kebab-case` para arquivos, salvo convenção existente diferente.
- Textos da interface permanecem em português do Brasil; identificadores seguem o padrão técnico do projeto.

## Integração HTTP

- O backend é a fonte de verdade para endpoints, campos, roles, paginação e status HTTP.
- Não invente contrato para concluir uma tela.
- Centralize a URL base; não replique `localhost` ou endereços de ambiente em serviços.
- Serviços HTTP devem ser tipados, retornar `Observable` e não manipular DOM ou estado específico de página.
- Modele DTO de API, estado do formulário e view model separadamente quando suas responsabilidades diferirem.
- Para datas, diferencie data sem horário de timestamp e evite deslocamentos indevidos por timezone.
- Trate erros conforme o contrato real; não suponha status sem verificar backend ou testes.
- Use `catchError` somente para transformação, mensagem ou fallback seguro real; não silencie falhas.
- Mudança de contrato exige atualização coordenada do backend, consumidores, testes e documentação afetados.

## Autenticação e autorização

- Preserve o fluxo JWT/OAuth2 existente.
- Centralize armazenamento, leitura, expiração e remoção do token.
- Não acesse `localStorage` diretamente em vários componentes.
- Nunca armazene senha nem registre tokens no console.
- Interceptors tratam preocupações HTTP transversais, não regras de página.
- Trate `401` como ausência ou expiração de sessão e `403` como falta de permissão do usuário autenticado.
- Guards devem retornar `UrlTree` quando aplicável, em vez de navegar imperativamente.
- Roles no frontend controlam a experiência de interface, mas não substituem a autorização do backend.
- Não envie token para origens externas.

## Formulários, rotas e estados de interface

- Para formulários novos com validação ou múltiplos campos, prefira Reactive Forms tipados e `NonNullableFormBuilder` quando adequado.
- Não migre formulários existentes apenas por preferência.
- Inclua validação, mensagens claras, estado de envio e prevenção de múltiplos submits.
- Associe `label` aos campos e configure `autocomplete` adequadamente.
- Antes de alterar uma rota, localize links, navegações, redirecionamentos e guards relacionados.
- Preserve a divisão entre rotas públicas, autenticadas e administrativas.
- Não crie rota para componente inexistente nem altere rota inicial ou layout global sem requisito explícito.
- Telas assíncronas devem tratar, conforme aplicável: carregamento, conteúdo, vazio, erro, acesso negado e sessão expirada.
- Não esconda falhas com dados falsos, sucesso artificial ou fallback silencioso.
- Mensagens de erro devem ser compreensíveis e não expor stack traces ou detalhes internos.

## Templates, CSS e acessibilidade

- Use a sintaxe moderna do Angular quando compatível com o código atual.
- Prefira HTML semântico, botões para ações e links para navegação.
- Preserve navegação por teclado, foco visível, labels, mensagens associadas e contraste adequado.
- Use ARIA somente quando HTML semântico não resolver.
- Mantenha estilos específicos próximos ao componente e estilos globais apenas para fundamentos compartilhados.
- Evite `!important`, seletores globais agressivos e dimensões fixas que prejudiquem responsividade.
- Verifique comportamento básico em telas menores quando houver alteração visual.
- Não introduza biblioteca visual sem pedido explícito, necessidade comprovada e justificativa.

## Segurança

- Não exponha segredos, senhas ou tokens.
- Não renderize HTML externo sem sanitização.
- Não use `bypassSecurityTrustHtml` ou APIs equivalentes sem análise de segurança comprovada.
- Não confie exclusivamente em validação cliente.
- Não remova guards, interceptors ou tratamento de sessão para simplificar testes.

## Testes

Use Jasmine, Karma e TestBed conforme o padrão atual. Cubra comportamentos relevantes, incluindo:

- serviços HTTP: URL, método, payload, resposta e erro;
- autenticação, armazenamento e expiração do token;
- guards e interceptors;
- formulários e validações;
- renderização condicionada por role;
- estados de carregamento, vazio e erro;
- transformações de datas e paginação;
- regressões de bugs.

Para HTTP, use APIs compatíveis com a versão atual, como `provideHttpClient`, `provideHttpClientTesting` e `HttpTestingController`, quando aplicável.

Teste comportamento observável. Não apague, desabilite ou enfraqueça testes para obter resultado verde.

## Validação final

Execute primeiro os testes específicos e amplie conforme o risco:

```powershell
npm test -- --watch=false
npm run build
```

Verifique também erros TypeScript e de template, imports não utilizados, rotas e links, contratos HTTP, responsividade, acessibilidade básica, `git diff --check` e ausência de logs, `dist`, coverage ou artefatos não intencionais.

Build não substitui teste funcional. Quando uma mudança visual ou de interação não puder ser validada em navegador, registre essa limitação.

Sem solicitação explícita, não altere backend, contratos da API, dependências, autenticação, guards, biblioteca visual/estado ou a arquitetura geral da aplicação.
