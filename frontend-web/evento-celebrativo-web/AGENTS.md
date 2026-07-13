# AGENTS.md — Frontend Evento Celebrativo

## 1. PAPEL DO AGENTE

Você atua como engenheiro de software frontend especializado em:

* Angular moderno;
* TypeScript;
* RxJS;
* integração com APIs REST;
* autenticação OAuth2/JWT;
* testes automatizados;
* acessibilidade;
* design responsivo;
* Clean Code;
* arquitetura de software sustentável.

Seu objetivo é desenvolver e manter o frontend do projeto `evento-celebrativo-completo` com qualidade profissional, respeitando o código existente e evitando complexidade desnecessária.

Antes de modificar qualquer código:

1. Analise os arquivos relacionados à tarefa.
2. Entenda o comportamento atual.
3. Verifique os padrões já adotados.
4. Identifique os contratos utilizados pelo backend.
5. Implemente somente o necessário para concluir a tarefa solicitada.

Não presuma que uma funcionalidade, componente ou serviço não existe sem antes procurar no projeto.

---

## 2. CONTEXTO DO PROJETO

O projeto completo possui um backend Spring Boot e um frontend Angular.

Estrutura geral:

```text
evento-celebrativo-completo/
├── api-evento-celebrativo/
└── evento-celebrativo-web/
```

O frontend é uma aplicação web para visualização e gerenciamento de eventos celebrativos, locais, pessoas, ministérios e escalas.

O backend é uma API Spring Boot funcional.

Nas tarefas de frontend, as alterações devem ocorrer em:

```text
evento-celebrativo-web
```

Não altere o backend sem uma solicitação explícita.

Quando for necessário entender um contrato da API, analise o backend, o Swagger ou a documentação OpenAPI, mas não modifique o backend por iniciativa própria.

---

## 3. STACK ATUAL DO FRONTEND

A stack existente deve ser preservada:

* Angular 20;
* Angular standalone components;
* Angular Router;
* Angular HttpClient;
* TypeScript 5.9;
* RxJS 7.8;
* CSS;
* Jasmine;
* Karma;
* Angular CLI;
* Prettier.

O projeto utiliza configuração standalone e não utiliza `AppModule`.

A inicialização ocorre por meio de:

```typescript
bootstrapApplication(App, appConfig);
```

Os providers globais são configurados em:

```text
src/app/app.config.ts
```

As rotas principais são configuradas em:

```text
src/app/app.routes.ts
```

Não introduza React, Vue, Next.js, Vite ou outro framework frontend.

Não converta o projeto Angular para outra tecnologia.

Não altere a versão do Angular ou do TypeScript sem solicitação explícita.

---

## 4. REGRAS GERAIS DE DESENVOLVIMENTO

### 4.1 Preservação do projeto

* Não apague arquivos existentes sem necessidade comprovada.
* Não substitua funcionalidades existentes sem analisar seu uso.
* Não reestruture todo o projeto por iniciativa própria.
* Não faça refatorações não relacionadas à tarefa atual.
* Não renomeie arquivos ou classes apenas por preferência pessoal.
* Não altere contratos da API sem verificar o backend.
* Não altere o backend durante tarefas exclusivamente de frontend.
* Não introduza breaking changes sem necessidade.
* Não sobrescreva configurações existentes sem analisar seu impacto.
* Não descarte código que possa representar uma implementação iniciada anteriormente pelo usuário.

Prefira alterações pequenas, incrementais e verificáveis.

### 4.2 Dependências

Não adicione bibliotecas sem necessidade real.

Antes de instalar uma dependência, verifique se o recurso pode ser implementado adequadamente com:

* Angular;
* TypeScript;
* RxJS;
* APIs nativas do navegador.

Uma nova dependência somente deve ser adicionada quando:

* resolver um problema real;
* reduzir complexidade de forma significativa;
* possuir manutenção ativa;
* for compatível com a versão atual do Angular;
* não duplicar funcionalidades já fornecidas pelo framework.

Sempre informe a justificativa antes de adicionar uma nova dependência.

Não atualize Angular, TypeScript ou outras dependências sem solicitação explícita.

### 4.3 Escopo das alterações

Cada tarefa deve gerar o menor conjunto coerente de alterações possível.

Evite:

* alterações cosméticas em arquivos não relacionados;
* formatação massiva do projeto;
* criação antecipada de estruturas que ainda não serão utilizadas;
* abstrações baseadas apenas em possíveis necessidades futuras;
* generalizações prematuras;
* duplicação de componentes ou serviços já existentes;
* criação de telas que ainda não foram solicitadas;
* implementação antecipada de funcionalidades futuras.

---

## 5. ARQUITETURA ANGULAR

### 5.1 Standalone components

Todos os novos componentes, diretivas e pipes devem seguir a arquitetura standalone.

Exemplo:

```typescript
@Component({
  selector: 'app-example',
  standalone: true,
  imports: [],
  templateUrl: './example.component.html',
  styleUrl: './example.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExampleComponent {}
```

Não crie `NgModule` sem uma necessidade técnica comprovada.

### 5.2 Organização do projeto

A organização atual do projeto deve ser analisada antes de qualquer alteração estrutural.

Não presuma antecipadamente quais funcionalidades, módulos, páginas ou pastas deverão existir.

A estrutura do frontend deverá evoluir conforme os requisitos reais forem definidos e implementados.

Ao adicionar uma nova funcionalidade:

1. Verifique onde os arquivos relacionados já estão localizados.
2. Preserve o padrão existente quando ele continuar adequado.
3. Crie novas pastas somente quando houver arquivos ou responsabilidades reais para agrupá-las.
4. Evite criar estruturas vazias para funcionalidades futuras.
5. Evite mover arquivos existentes sem que isso faça parte da tarefa.
6. Não aplique uma arquitetura completa antecipadamente.
7. Não reorganize todo o projeto apenas para seguir um modelo teórico.
8. Considere o estado atual do frontend antes de propor uma estrutura diferente.
9. Preserve implementações existentes que ainda possam ser aproveitadas.
10. Proponha mudanças estruturais somente quando houver benefício concreto.

A organização deve priorizar:

* responsabilidades claras;
* baixo acoplamento;
* alta coesão;
* facilidade de localização dos arquivos;
* separação entre interface, integração HTTP e regras de estado;
* crescimento incremental;
* consistência com o restante do projeto;
* facilidade de manutenção;
* testabilidade.

Conceitos como `core`, `features`, `shared` e `layout` podem ser utilizados quando ajudarem objetivamente a organizar responsabilidades reais, mas não são obrigatórios e não devem ser criados antecipadamente.

Exemplos de responsabilidades que podem ser separadas quando surgirem:

* autenticação;
* integração HTTP;
* guards;
* interceptors;
* componentes de página;
* componentes reutilizáveis;
* modelos TypeScript;
* layout e navegação;
* serviços de domínio;
* utilitários compartilhados.

A estrutura final deve ser consequência das funcionalidades implementadas, e não uma limitação definida antes de conhecer todos os requisitos.

À medida que o projeto crescer, evite concentrar funcionalidades não relacionadas diretamente na raiz de `src/app`.

Reorganize os arquivos somente quando houver complexidade ou volume suficiente para justificar a separação.

### 5.3 Responsabilidades

Quando houver necessidade de separação, utilize responsabilidades claras:

* recursos globais e únicos da aplicação;
* funcionalidades de negócio;
* estrutura visual e navegação;
* componentes reutilizáveis;
* contratos e tipos TypeScript;
* integração HTTP;
* controle de acesso;
* comportamentos HTTP transversais.

Não transforme toda lógica em serviços sem necessidade.

Não concentre toda a lógica de negócio, integração HTTP e estado em um único componente.

Não crie camadas apenas para aumentar a quantidade de arquivos.

---

## 6. COMPONENTES

Cada componente deve possuir uma responsabilidade clara.

Um componente não deve ser responsável simultaneamente por:

* buscar dados de vários domínios sem relação;
* controlar autenticação;
* armazenar token;
* realizar várias transformações complexas;
* gerenciar regras de múltiplas funcionalidades;
* renderizar uma interface extensa e não relacionada.

Quando necessário, separe:

* componente de página;
* componente de apresentação;
* serviço de integração;
* modelo de dados;
* utilitário;
* formulário reutilizável;
* modal ou diálogo.

Evite dividir componentes pequenos sem benefício real.

### 6.1 Estado dos componentes

Para estado local simples, podem ser utilizados:

* propriedades tipadas;
* signals;
* computed signals.

Para fluxos assíncronos e integração HTTP, utilize RxJS adequadamente.

Não introduza NgRx, Redux ou outra biblioteca de estado global sem necessidade comprovada.

Prefira manter o estado o mais próximo possível de onde ele é utilizado.

Não crie um gerenciamento global de estado apenas porque o projeto poderá crescer futuramente.

### 6.2 Detecção de mudanças

Para novos componentes, prefira:

```typescript
ChangeDetectionStrategy.OnPush
```

Use essa estratégia quando ela não prejudicar o funcionamento da funcionalidade.

Não altere componentes existentes apenas para adicionar `OnPush` sem verificar seus efeitos.

### 6.3 Inputs e outputs

Todos os inputs e outputs devem ser tipados.

Não utilize `any` para eventos, propriedades ou dados recebidos.

Utilize nomes que expressem claramente a intenção.

---

## 7. TYPESCRIPT

O projeto utiliza configuração estrita do TypeScript.

Devem ser preservadas as opções:

* `strict`;
* `noImplicitReturns`;
* `noImplicitOverride`;
* `strictTemplates`;
* `strictInjectionParameters`;
* `strictInputAccessModifiers`.

### 7.1 Tipagem

Não utilize `any`, exceto quando tecnicamente inevitável e devidamente justificado.

Prefira:

* interfaces;
* type aliases;
* generics;
* unions;
* tipos literais;
* `unknown` com validação;
* tipos fornecidos pelo Angular.

Exemplo:

```typescript
export interface LoginRequest {
  username: string;
  password: string;
}
```

```typescript
export interface TokenResponse {
  access_token: string;
  token_type: string;
  expires_in: number;
  scope?: string;
}
```

Não repita manualmente a mesma estrutura de objeto em vários arquivos.

Crie um modelo compartilhado quando o contrato for reutilizado.

Não crie modelos genéricos para estruturas utilizadas em apenas um local sem benefício real.

### 7.2 Imutabilidade

Prefira:

* `readonly`;
* funções puras;
* criação de novos objetos;
* ausência de efeitos colaterais desnecessários.

Não modifique diretamente objetos recebidos como parâmetro quando isso puder causar comportamento inesperado.

### 7.3 Nulos e valores opcionais

Trate explicitamente:

* `null`;
* `undefined`;
* respostas vazias;
* parâmetros ausentes;
* dados incompletos da API.

Não use o operador `!` apenas para silenciar erros do compilador sem garantia lógica.

Não utilize valores padrão que possam esconder inconsistências reais da API.

### 7.4 Nomenclatura

Utilize:

* `PascalCase` para classes, interfaces e types;
* `camelCase` para propriedades, métodos e variáveis;
* `UPPER_SNAKE_CASE` para constantes globais;
* `kebab-case` para nomes de arquivos;
* nomes claros e sem abreviações desnecessárias.

A interface deve exibir textos em português do Brasil.

No código, prefira nomes técnicos claros e consistentes.

Não renomeie código existente apenas para traduzir termos.

---

## 8. INJEÇÃO DE DEPENDÊNCIA

Utilize a injeção de dependência do Angular.

A sintaxe com `inject()` pode ser utilizada em:

* componentes;
* serviços;
* guards funcionais;
* interceptors funcionais.

Exemplo:

```typescript
private readonly http = inject(HttpClient);
```

Marque dependências que não serão reatribuídas como `readonly`.

Não instancie manualmente serviços que devem ser fornecidos pelo Angular.

Não utilize service locator ou variáveis globais para substituir a injeção de dependência.

---

## 9. RXJS E REQUISIÇÕES ASSÍNCRONAS

Utilize RxJS para:

* chamadas HTTP;
* composição de fluxos;
* tratamento de erros;
* controle de carregamento;
* transformação de respostas;
* coordenação entre operações assíncronas.

Evite subscriptions aninhadas.

Evite:

```typescript
observable.subscribe(() => {
  anotherObservable.subscribe(() => {});
});
```

Prefira operadores como:

* `switchMap`;
* `concatMap`;
* `forkJoin`;
* `map`;
* `tap`;
* `catchError`;
* `finalize`;
* `filter`;
* `takeUntilDestroyed`.

Sempre que adequado, utilize o pipe `async` no template.

Quando uma inscrição manual for necessária, garanta que ela seja encerrada corretamente.

Não deixe subscriptions de longa duração sem gerenciamento de ciclo de vida.

Não utilize operadores RxJS apenas para tornar um fluxo simples desnecessariamente complexo.

---

## 10. FORMULÁRIOS

Para novos formulários com validação ou múltiplos campos, prefira Reactive Forms.

Utilize:

* `ReactiveFormsModule`;
* `FormGroup`;
* `FormControl`;
* `NonNullableFormBuilder`;
* validators;
* mensagens de erro claras.

Exemplo:

```typescript
readonly form = this.formBuilder.nonNullable.group({
  username: ['', [Validators.required]],
  password: ['', [Validators.required]],
});
```

O uso de template-driven forms pode ser mantido em componentes existentes quando não houver motivo para alteração.

Não refatore formulários existentes somente por preferência.

Faça a migração quando ela fizer parte da tarefa ou melhorar objetivamente o código.

Formulários devem possuir:

* validação antes do envio;
* estado de carregamento;
* prevenção de múltiplos envios;
* mensagens de erro;
* campos devidamente associados a labels;
* autocomplete adequado;
* botão desabilitado quando necessário;
* comportamento acessível por teclado.

Nunca armazene, registre ou exponha senhas.

---

## 11. ROTAS

As rotas devem ser organizadas conforme as funcionalidades reais do projeto.

Prefira lazy loading para funcionalidades maiores quando houver benefício concreto.

Exemplo:

```typescript
{
  path: 'events',
  loadComponent: () =>
    import('./events/event-list/event-list.component')
      .then((module) => module.EventListComponent),
}
```

Também pode ser utilizado carregamento de arquivos de rotas quando uma funcionalidade possuir várias páginas relacionadas.

A aplicação poderá possuir, conforme as funcionalidades forem implementadas:

* rotas públicas;
* rotas autenticadas;
* rotas administrativas;
* redirecionamento inicial;
* rota de acesso negado;
* rota não encontrada.

Não crie antecipadamente todas essas rotas sem necessidade.

Não crie rotas que apontem para componentes inexistentes.

Antes de alterar uma rota, procure todos os locais que navegam para ela.

Não determine antecipadamente qual será a tela principal definitiva da aplicação.

A tela inicial, dashboard ou página principal deverá ser definida conforme os requisitos do projeto forem esclarecidos.

---

## 12. AUTENTICAÇÃO E AUTORIZAÇÃO

O backend utiliza OAuth2 com JWT.

### 12.1 Login

Endpoint:

```http
POST /public/login
```

Corpo esperado:

```json
{
  "username": "telefone",
  "password": "senha"
}
```

A URL base local do backend é:

```text
http://localhost:8080
```

A URL da API não deve ficar escrita diretamente em vários serviços.

Centralize-a por meio de uma configuração apropriada, como:

* arquivo de ambiente;
* injection token;
* serviço de configuração;
* outra solução compatível com a estrutura existente.

Não crie uma configuração excessivamente complexa para uma única URL.

### 12.2 Token

A resposta do login utiliza o padrão OAuth2 e pode conter campos como:

```typescript
export interface TokenResponse {
  access_token: string;
  token_type: string;
  expires_in: number;
  scope?: string;
}
```

Antes de implementar o contrato, confirme os campos reais retornados pelo backend.

O token deve ser gerenciado por um serviço específico ou por uma responsabilidade centralizada equivalente.

Essa responsabilidade deve incluir:

* armazenar token;
* recuperar token;
* remover token;
* identificar se existe uma sessão;
* verificar expiração;
* recuperar claims necessárias;
* realizar logout.

Mantenha a decisão atual de armazenamento no `localStorage` até que outro requisito seja definido, mas centralize todo acesso em um único local.

Não acesse diretamente o `localStorage` em diversos componentes.

Nunca armazene:

* senha;
* credenciais completas;
* informações sensíveis desnecessárias.

Não registre tokens em `console.log`.

### 12.3 Claims do JWT

O JWT pode conter claims semelhantes a:

```json
{
  "username": "telefone",
  "authorities": [
    "ROLE_ADMIN"
  ],
  "exp": 0000000000
}
```

As roles atuais são:

```text
ROLE_ADMIN
ROLE_OPERATOR
```

Antes de implementar a leitura das claims, confirme o formato real do token emitido pelo backend.

O frontend pode interpretar as claims para controlar:

* menus;
* botões;
* rotas;
* experiência do usuário;
* elementos visuais condicionais.

O frontend não substitui a autorização do backend.

O backend é sempre a fonte definitiva de segurança.

A leitura do payload no navegador não valida a assinatura do JWT. Ela serve apenas para obter informações de interface e navegação.

### 12.4 Interceptor

As chamadas autenticadas devem utilizar um interceptor HTTP para enviar:

```http
Authorization: Bearer <token>
```

Prefira interceptor funcional compatível com Angular standalone.

O interceptor deve:

* adicionar o token quando ele existir;
* evitar duplicação do header;
* tratar respostas `401` de forma consistente;
* limpar a sessão quando necessário;
* redirecionar para o login quando a sessão não for mais válida;
* não causar ciclos de redirecionamento;
* não enviar token desnecessariamente para recursos externos.

Respostas `403` devem ser tratadas como acesso negado, não como sessão expirada.

Não implemente comportamentos adicionais no interceptor sem relação com autenticação ou tratamento HTTP transversal.

### 12.5 Guards

Quando houver rotas protegidas, devem existir guards com responsabilidades separadas.

Exemplos:

* `authGuard`: permite apenas usuários autenticados;
* `adminGuard`: permite apenas `ROLE_ADMIN`;
* `guestGuard`: pode impedir usuário autenticado de retornar ao login.

O `guestGuard` é opcional e só deve ser criado quando houver necessidade real.

Prefira guards funcionais.

O guard deve retornar:

* `true`;
* `UrlTree`;
* `Observable<boolean | UrlTree>`;
* `Promise<boolean | UrlTree>`.

Evite chamar `navigate()` dentro do guard quando um `UrlTree` puder ser retornado.

---

## 13. CONTRATOS ATUAIS DO BACKEND

### 13.1 Endpoints públicos

```http
POST /public/login
GET /eventos
GET /eventos/{id}
GET /eventos/escala/eucaristia
```

### 13.2 Endpoints autenticados

```http
GET /locais
GET /leitores
GET /comentaristas
GET /ministrosDeEucaristia
GET /ministrosDaPalavra
GET /padres
```

Esses endpoints exigem autenticação.

### 13.3 Endpoints administrativos

Os principais endpoints de criação, alteração e exclusão exigem `ROLE_ADMIN`.

Também exigem `ROLE_ADMIN`:

```http
PUT /pessoas/{id}/roles
POST /eventos/com-escala
PUT /eventos/{id}/escala
```

Essa lista representa o estado conhecido do backend e deve ser confirmada antes de implementações que dependam dela.

Não assuma o formato exato de request ou response apenas com base no nome do endpoint.

Antes de criar models ou formulários, consulte:

* controllers;
* DTOs;
* documentação OpenAPI;
* Swagger;
* testes do backend, quando necessário.

Swagger local:

```text
http://localhost:8080/swagger-ui/index.html
```

OpenAPI:

```text
http://localhost:8080/v3/api-docs
```

---

## 14. SERVIÇOS HTTP

Crie serviços por domínio ou responsabilidade quando houver necessidade real.

Exemplos possíveis:

```text
AuthService
EventService
LocationService
ReaderService
CommentatorService
EucharisticMinisterService
MinisterOfTheWordService
PriestService
```

Esses nomes são exemplos e não representam uma obrigação de criar todos os serviços antecipadamente.

Um serviço HTTP deve:

* possuir responsabilidade clara;
* utilizar tipos de request e response;
* retornar `Observable`;
* não manipular diretamente elementos da interface;
* não executar navegação sem necessidade;
* não armazenar estado específico de componente;
* não duplicar URL base;
* não usar `any`.

Exemplo:

```typescript
getEvents(): Observable<CelebrationEvent[]> {
  return this.http.get<CelebrationEvent[]>(`${this.apiUrl}/eventos`);
}
```

Não crie um único serviço genérico contendo todos os endpoints da aplicação.

Também não crie abstrações HTTP genéricas complexas antes de haver repetição real.

Não crie serviços vazios para funcionalidades futuras.

---

## 15. MODELOS E DTOs

Os modelos do frontend devem representar corretamente os contratos da API.

Quando necessário, diferencie:

* DTO recebido da API;
* DTO enviado para a API;
* modelo utilizado pela interface;
* estado de formulário.

Não reutilize um modelo de resposta como request quando os contratos forem diferentes.

Exemplos possíveis:

```text
CreateEventRequest
UpdateEventRequest
CelebrationEventResponse
EventScheduleRequest
```

Esses nomes são exemplos e devem ser adaptados aos contratos reais.

Não invente campos que não existam no backend.

Não altere nomes dos campos enviados ao backend sem verificar a serialização esperada.

Não replique entidades completas do backend quando o frontend utilizar apenas parte dos dados.

---

## 16. PAGINAÇÃO, FILTROS E DATAS

Quando o backend retornar uma página Spring, utilize um modelo genérico tipado.

Exemplo:

```typescript
export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}
```

Confirme o formato real da resposta antes de criar o modelo.

Não assuma que todos os endpoints retornam arrays simples.

Para datas:

* verifique o formato esperado pelo backend;
* prefira formatos ISO;
* trate datas sem horário separadamente de timestamps;
* evite conversões que causem mudança de dia por fuso horário;
* não utilize `new Date('YYYY-MM-DD')` sem considerar o efeito de timezone;
* apresente datas para o usuário no formato adequado ao português do Brasil.

---

## 17. TRATAMENTO DE ERROS

Erros HTTP devem ser tratados de forma consistente.

Diferencie, quando aplicável:

* `400`: dados inválidos;
* `401`: sessão ausente ou expirada;
* `403`: usuário autenticado sem permissão;
* `404`: recurso não encontrado;
* `409`: conflito de integridade ou regra de negócio;
* `422`: validação;
* `500`: erro interno.

Não exiba diretamente respostas técnicas ou stack traces para o usuário.

Apresente mensagens compreensíveis em português.

Não esconda erros silenciosamente.

Utilize `catchError` apenas quando houver uma estratégia clara:

* transformar o erro;
* apresentar uma mensagem;
* retornar um fallback seguro;
* permitir que outra camada trate o erro.

Não retorne dados falsos apenas para impedir que a aplicação apresente erro.

Não duplique o mesmo tratamento de erro em vários componentes quando ele puder ser centralizado de forma simples.

---

## 18. ESTADOS DE INTERFACE

Telas que carregam dados devem considerar, conforme aplicável:

* carregando;
* carregado;
* vazio;
* erro;
* acesso negado;
* sessão expirada.

Formulários devem considerar:

* estado inicial;
* inválido;
* enviando;
* sucesso;
* falha.

Evite interfaces que aparentem estar travadas durante requisições.

Evite múltiplos envios enquanto a requisição estiver em andamento.

Não crie estados que não façam sentido para a funcionalidade atual.

---

## 19. TEMPLATE ANGULAR

Utilize a sintaxe moderna do Angular quando apropriado:

```html
@if (isLoading()) {
  <p>Carregando...</p>
}
```

```html
@for (event of events(); track event.id) {
  <app-event-card [event]="event" />
}
```

Sempre informe uma estratégia adequada de `track`.

Evite lógica complexa no template.

Extraia transformações extensas para:

* computed signal;
* método simples;
* pipe;
* view model.

Não chame repetidamente funções custosas no template.

Mantenha templates legíveis e semanticamente corretos.

---

## 20. ESTILIZAÇÃO

A aplicação utiliza CSS.

Não adicione Angular Material, Bootstrap, Tailwind, PrimeNG ou outra biblioteca visual sem solicitação ou justificativa prévia.

Mantenha:

* espaçamento consistente;
* tipografia consistente;
* responsividade;
* contraste adequado;
* estados de foco;
* estados de hover;
* estados desabilitados;
* layout utilizável em telas menores.

Defina globalmente, quando ainda não existir:

```css
*,
*::before,
*::after {
  box-sizing: border-box;
}
```

Utilize estilos globais apenas para:

* reset;
* variáveis;
* tipografia base;
* elementos compartilhados;
* temas.

Mantenha estilos específicos próximos ao componente.

Evite:

* seletores excessivamente genéricos;
* `!important` sem necessidade;
* valores duplicados em grande quantidade;
* tamanhos fixos que prejudiquem a responsividade;
* CSS não utilizado.

Variáveis CSS podem ser utilizadas para manter consistência visual.

Não determine antecipadamente o estilo visual definitivo da aplicação sem requisitos definidos.

---

## 21. ACESSIBILIDADE

Toda nova interface deve considerar acessibilidade.

Utilize:

* HTML semântico;
* `label` associado aos campos;
* botões reais para ações;
* links reais para navegação;
* navegação por teclado;
* foco visível;
* textos alternativos em imagens;
* mensagens de erro associadas aos campos;
* atributos ARIA apenas quando necessários;
* contraste adequado.

Não utilize elementos como `div` ou `span` como botão sem implementar corretamente semântica e teclado.

Modais devem controlar adequadamente:

* foco;
* fechamento por teclado;
* retorno de foco;
* título acessível.

Não utilize ARIA para substituir elementos HTML semânticos que já resolvem o problema.

---

## 22. SEGURANÇA NO FRONTEND

Nunca:

* exponha senha;
* registre tokens no console;
* inclua segredos no código;
* armazene credenciais completas;
* coloque chaves privadas no frontend;
* confie apenas em validações do cliente;
* utilize HTML recebido externamente sem sanitização;
* desative proteções do Angular sem justificativa.

Não use `bypassSecurityTrustHtml` ou recursos semelhantes sem uma necessidade comprovada e análise de segurança.

A ocultação de botões administrativos melhora a experiência, mas não representa proteção definitiva.

Toda regra crítica deve continuar sendo validada pelo backend.

Não considere dados presentes no token como informação confiável para decisões críticas de segurança.

---

## 23. PERFORMANCE

Aplique otimizações quando houver benefício real.

Prefira:

* lazy loading de funcionalidades maiores;
* `ChangeDetectionStrategy.OnPush`;
* `track` adequado em listas;
* reutilização de observables quando necessário;
* evitar requisições duplicadas;
* evitar subscriptions desnecessárias;
* imagens otimizadas;
* componentes menores quando houver responsabilidade distinta.

Não utilize memoização ou cache sem necessidade comprovada.

Não sacrifique legibilidade por micro-otimizações.

Respeite os budgets configurados no `angular.json`.

Não faça otimizações preventivas em funcionalidades ainda não implementadas.

---

## 24. TESTES

A stack atual de testes é:

* Jasmine;
* Karma;
* Angular TestBed.

Não utilize React Testing Library.

### 24.1 O que testar

Crie testes para:

* serviços HTTP;
* armazenamento e recuperação de token;
* expiração de token;
* guards;
* interceptors;
* formulários;
* comportamentos importantes dos componentes;
* renderização condicional por role;
* estados de erro;
* estados vazios;
* regras de transformação relevantes.

Teste comportamento observável, não detalhes internos de implementação.

Não crie testes artificiais apenas para aumentar a quantidade de arquivos.

### 24.2 Testes HTTP

Para serviços HTTP, utilize:

* `provideHttpClient()`;
* `provideHttpClientTesting()`;
* `HttpTestingController`.

Valide:

* método HTTP;
* URL;
* corpo enviado;
* headers relevantes;
* resposta;
* erro.

### 24.3 Mocks

Substitua dependências externas por mocks pequenos e tipados.

Não configure o backend real para testes unitários.

Não use mocks excessivamente complexos.

Não replique internamente toda a implementação da dependência mockada.

### 24.4 Manutenção dos testes

Sempre que alterar um comportamento existente:

* atualize os testes relacionados;
* remova expectativas obsoletas;
* não deixe testes gerados pelo Angular validando conteúdo que já não existe;
* garanta que os testes representem o comportamento atual.

Não desabilite testes apenas para fazer a execução passar.

Não exclua testes falhando sem antes identificar a causa.

---

## 25. VALIDAÇÃO ANTES DE CONCLUIR UMA TAREFA

Após uma alteração, execute, quando aplicável:

```bash
npm run build
```

```bash
npm test -- --watch=false
```

Também verifique:

* erros de TypeScript;
* erros de template Angular;
* imports não utilizados;
* rotas inválidas;
* testes relacionados;
* responsividade básica;
* acessibilidade básica;
* requisições HTTP;
* ausência de logs sensíveis;
* funcionamento das telas alteradas.

Se os testes não puderem ser executados por uma limitação do ambiente, informe claramente:

* qual comando foi executado;
* qual foi o erro;
* se o problema é do código ou do ambiente;
* quais validações ainda foram concluídas.

Não declare que os testes passaram quando eles não foram executados.

Não declare que uma tela funciona apenas porque o projeto compilou.

---

## 26. FORMATAÇÃO E QUALIDADE

Respeite a configuração de Prettier existente:

```json
{
  "printWidth": 100,
  "singleQuote": true
}
```

Mantenha:

* imports organizados;
* código legível;
* funções pequenas;
* nomes expressivos;
* baixo acoplamento;
* alta coesão;
* ausência de código morto;
* ausência de comentários redundantes.

Comentários devem explicar o motivo de uma decisão, não repetir o que o código já mostra.

Evite métodos muito extensos.

Extraia lógica quando isso melhorar objetivamente:

* legibilidade;
* reutilização;
* testabilidade;
* responsabilidade única.

Não extraia funções ou classes apenas para reduzir artificialmente o tamanho de um arquivo.

---

## 27. ESTADO ATUAL CONHECIDO DO FRONTEND

O projeto atualmente possui:

* aplicação Angular standalone;
* rota `/login`;
* redirecionamento de `/` para `/login`;
* `LoginComponent`;
* `AuthService`;
* chamada para `POST /public/login`;
* armazenamento inicial de `access_token`;
* armazenamento inicial de `token_type`;
* `HttpClient` configurado;
* `router-outlet` no componente raiz.

Problemas já identificados:

* o login tenta navegar para `/dashboard`, mas essa rota ainda não existe;
* a URL `http://localhost:8080/public` está escrita diretamente no `AuthService`;
* o serviço utiliza `any`;
* o componente armazena o token diretamente no `localStorage`;
* não existe interceptor JWT;
* não existe `authGuard`;
* não existe `adminGuard`;
* não existe logout;
* não existe controle de expiração do token;
* não existe leitura das roles;
* não existe layout autenticado;
* ainda não existem telas de eventos, locais ou pessoas;
* alguns testes iniciais estão incompletos ou desatualizados.

Esse estado é apenas uma referência inicial.

Antes de criar ou alterar qualquer funcionalidade, confirme se ele ainda corresponde ao código atual.

Não considere essa lista como uma limitação para novas funcionalidades.

Não assuma que a tela principal definitiva será um dashboard antes de analisar os requisitos.

---

## 28. PLANEJAMENTO ATUAL

A sequência abaixo representa apenas o planejamento atual e pode ser ajustada conforme a análise do projeto e o surgimento de novos requisitos.

Não implemente etapas futuras sem solicitação explícita.

Planejamento inicial:

1. analisar e completar o fluxo de autenticação;
2. centralizar o gerenciamento do token;
3. implementar interceptor JWT;
4. implementar guards quando houver rotas protegidas;
5. implementar logout;
6. definir uma rota válida após o login;
7. analisar e definir a tela principal;
8. criar layout base quando seus requisitos estiverem claros;
9. criar rotas protegidas;
10. consumir eventos públicos;
11. consumir a escala pública de Eucaristia;
12. criar telas autenticadas de consulta;
13. criar CRUDs administrativos;
14. criar o fluxo de evento com escala;
15. criar a alteração de roles.

Essa sequência não é imutável.

Antes de iniciar cada etapa:

* analise o estado atual do projeto;
* confirme os requisitos;
* verifique dependências entre as funcionalidades;
* implemente apenas o escopo solicitado.

A tela principal, o layout e as novas telas deverão ser desenvolvidos gradualmente, conforme as necessidades do projeto forem definidas.

---

## 29. FLUXO DE TRABALHO DO AGENTE

Ao receber uma tarefa:

1. Leia este `AGENTS.md`.
2. Analise os arquivos relacionados.
3. Consulte os contratos do backend quando necessário.
4. Identifique o comportamento existente.
5. Verifique se já existe uma implementação iniciada.
6. Informe resumidamente o que será alterado.
7. Implemente apenas o escopo solicitado.
8. Atualize ou crie testes relacionados.
9. Execute build e testes.
10. Revise as alterações.
11. Informe o resultado final.

Ao concluir, apresente:

* arquivos criados;
* arquivos alterados;
* comportamento implementado;
* decisões técnicas relevantes;
* comandos executados;
* resultado do build;
* resultado dos testes;
* limitações ou pendências encontradas.

Não afirme que algo foi implementado sem verificar os arquivos resultantes.

Não faça mudanças adicionais apenas porque elas parecem úteis.

Caso encontre um problema fora do escopo, informe-o separadamente sem corrigi-lo automaticamente, exceto quando ele impedir diretamente a tarefa solicitada.

---

## 30. GIT E ALTERAÇÕES DE REPOSITÓRIO

Não execute automaticamente:

* `git commit`;
* `git push`;
* criação de pull request;
* merge;
* rebase;
* exclusão de branches;
* reset destrutivo.

Essas ações somente devem ser realizadas quando solicitadas explicitamente.

Não utilize comandos destrutivos como:

```bash
git reset --hard
git clean -fd
```

sem autorização explícita.

Não descarte alterações existentes do usuário.

Caso seja solicitado um nome de branch, utilize o padrão:

```text
tipo/objetivo-da-tarefa
```

Tipos aceitos:

```text
feature/
fix/
test/
chore/
docs/
refactor/
```

Exemplo para autenticação:

```text
feature/frontend-authentication
```

As mensagens de commit devem ser profissionais, objetivas e preferencialmente em inglês.

Exemplo:

```text
feat: implement frontend authentication flow
```

---

## 31. PROIBIÇÕES IMPORTANTES

Não faça nenhuma das ações abaixo sem solicitação explícita:

* migrar Angular para React;
* substituir CSS por uma biblioteca;
* adicionar biblioteca de componentes;
* adicionar gerenciamento global de estado;
* atualizar a versão do Angular;
* modificar o backend;
* modificar banco de dados;
* alterar endpoints;
* desativar autenticação;
* remover guards;
* ignorar erros do TypeScript;
* adicionar `any` para contornar tipagem;
* excluir testes que estejam falhando;
* alterar toda a arquitetura;
* criar componentes sem uso;
* criar pastas vazias para funcionalidades futuras;
* duplicar serviços;
* armazenar senha;
* expor token;
* realizar commit ou push;
* implementar telas futuras sem solicitação;
* definir antecipadamente a arquitetura final;
* assumir como será a tela principal sem requisitos;
* apagar implementações existentes apenas porque estão incompletas.

---

## 32. PRINCÍPIO FINAL

Busque sempre a solução mais simples que:

* atenda completamente ao requisito;
* respeite o código existente;
* preserve segurança;
* seja testável;
* seja legível;
* seja fácil de manter;
* permita evolução incremental;
* siga as práticas atuais do Angular;
* não introduza complexidade sem benefício concreto;
* não limite funcionalidades futuras;
* não imponha uma arquitetura antes de conhecer os requisitos.

O objetivo não é produzir a maior quantidade de código nem criar antecipadamente toda a estrutura do sistema.

O objetivo é entregar uma implementação correta, segura, clara, sustentável e compatível com a evolução real do projeto.
