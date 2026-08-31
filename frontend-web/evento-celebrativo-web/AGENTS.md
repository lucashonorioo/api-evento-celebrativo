# Frontend Evento Celebrativo — Angular/TypeScript

Estas regras complementam o `AGENTS.md` da raiz.

## Fonte de verdade

Confirme stack/scripts em `package.json`, `angular.json` e `tsconfig*.json`. Preserve Angular 20, standalone/bootstrapApplication, Router/HttpClient, TypeScript estrito, RxJS, Jasmine/Karma/TestBed e Prettier conforme realmente presentes. Não atualize Angular/TypeScript/Node/dependências sem pedido.

Comandos usuais:

```powershell
npm test -- --watch=false
npm run build
```

Não execute `npm install` se as dependências existentes bastarem. Use `npm start`/`ng serve` somente quando validação runtime for necessária; nesse caso siga `.ai/runtime/PROCESS_LIFECYCLE.md`.

## Análise e arquitetura

Antes de editar, localize rota, página/componente, serviço HTTP, model, formulário, guard/interceptor, estilos e testes; confirme o contrato no backend/OpenAPI/testes e procure padrão equivalente.

- Novos componentes/diretivas/pipes são standalone.
- Separe apresentação, HTTP, estado e formulário quando houver responsabilidades reais.
- Preserve alta coesão/baixo acoplamento; não imponha arquitetura teórica nem estado global sem necessidade.
- Prefira lazy loading em áreas maiores quando coerente.
- Estado fica próximo do uso; signals/computed para estado local simples e RxJS para composição assíncrona.
- Evite subscriptions aninhadas; gerencie subscriptions duradouras com `takeUntilDestroyed` ou equivalente.
- Evite trabalho caro repetido no template e use `track` estável em listas.

## TypeScript e contratos

- Preserve `strict`; não use `any` para esconder incerteza. Use tipos específicos ou `unknown` validado.
- Trate `null`, `undefined`, vazio e dados incompletos explicitamente; evite `!` sem garantia.
- Modele request/response separadamente quando responsabilidades diferirem.
- Backend é a fonte de verdade para endpoints, campos, roles, paginação e status.
- Serviços HTTP são tipados, retornam `Observable` e não manipulam DOM/estado específico de página.
- Centralize URL base; não replique `localhost`.
- Diferencie data sem horário de timestamp e evite deslocamento de timezone.
- Mudança de contrato usa `change-api-contract`.

## Autenticação, segurança e erros

- Preserve OAuth2/JWT; centralize ciclo de vida do token e não espalhe `localStorage`.
- `401` = sessão ausente/expirada; `403` = autenticado sem permissão, conforme contrato real.
- Guards preferem `UrlTree` quando aplicável; UI por role não substitui autorização backend.
- Nunca registre senha/token nem envie token a origem externa.
- Não renderize HTML externo sem sanitização nem use bypass de segurança sem justificativa comprovada.
- Não silencie erro: `catchError` apenas para transformação, mensagem ou fallback realmente seguro.

## Formulários, UX, acessibilidade e responsividade

- Em formulário novo relevante, prefira Reactive Forms tipados/`NonNullableFormBuilder` quando adequado; não migre existente por preferência.
- Trate validação, mensagens, estado de envio e múltiplo submit.
- Fluxos assíncronos consideram loading, conteúdo, vazio, erro, acesso negado e sessão expirada conforme aplicável.
- Preserve HTML semântico, teclado, foco visível, labels, mensagens associadas e contraste; ARIA só quando semântica nativa não bastar.
- Preserve responsividade e evite `!important`, seletor global agressivo e dimensão fixa prejudicial.
- Não introduza biblioteca visual/framework/estado global sem necessidade e pedido apropriado.

## Testes e qualidade

Use Jasmine/Karma/TestBed conforme o projeto. Cubra comportamento observável relevante: serviços HTTP, auth, guards/interceptors, formulários, renderização por role, estados assíncronos, datas/paginação e regressões.

Não desabilite/apague/enfraqueça testes para obter verde. Na conclusão, valide conforme o risco: testes específicos, suíte/build, TypeScript/templates, rotas, contrato, acessibilidade/responsividade, `git diff --check`, logs e artefatos.

Sem solicitação explícita, não altere backend, contrato API, dependências, autenticação/guards ou arquitetura geral.
