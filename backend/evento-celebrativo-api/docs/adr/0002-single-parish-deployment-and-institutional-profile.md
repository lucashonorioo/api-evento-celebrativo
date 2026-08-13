# ADR 0002: Uma Instalação = Uma Paróquia e Perfil Institucional (ParishProfile)

## 1. Status

Accepted

Data de aceitação: 2026-08-12

Primeira etapa implementada nesta mesma data: entidade `ParishProfile` (singleton, id=1), migration `V19__create_parish_profile.sql`, `GET /paroquia` (público) e `PUT /paroquia` (exclusivo `ROLE_ADMIN`).

## 2. Contexto

O Evento Celebrativo gerencia pessoas, ministérios, locais, celebrações e escalas de uma única paróquia por implantação. Até esta decisão, não havia nenhuma representação explícita da própria instituição (nome, diocese, contatos institucionais) — apenas dados operacionais (`Person`, `Location`, `CelebrationEvent`).

Um futuro "site público da paróquia" e telas administrativas institucionais (identidade, secretaria, horários de atendimento) precisam de um lugar canônico para esses dados, que hoje não existe.

## 3. Decisão

- Esta arquitetura adota explicitamente o modelo **uma instalação = uma paróquia**. Não haverá multi-tenancy: cada implantação tem seu próprio banco, suas próprias pessoas, contas, ministérios, celebrações e locais.
- Nenhuma entidade de domínio (`Person`, `PersonMinistry`, `UserAccount`, `CelebrationEvent`, `EventAssignment`, `Location`, `Notification`, etc.) recebe `parish_id` ou qualquer mecanismo de particionamento por tenant.
- Cria-se o conceito institucional `ParishProfile`, representando exclusivamente os dados institucionais da paróquia representada pela instalação: `name`, `diocese`, `institutionalPhone`, `institutionalEmail`, `officeAddress`, `officeHours`, além de `id`, `configured`, `createdAt` e `updatedAt`.
- `ParishProfile` **não é** `Person`, `PersonMinistry`, `Location`, `UserAccount` ou `Role`, e não substitui `Location` (que continua representando locais físicos onde eventos podem ocorrer, ex. Igreja Matriz, capelas).
- Responsabilidades de pessoas (pároco, secretaria, coordenadores) **não** são armazenadas como strings no `ParishProfile` (sem `pastorName`/`secretaryName`). Serão modeladas em etapa futura como relacionamento `Person` + `ParishStaffAssignment`, fora do escopo desta primeira etapa.
- `ParishProfile` é um singleton real: a única linha válida tem `id = 1`, sem `@GeneratedValue`. A migration `V19` já insere essa linha (`configured = false`) para eliminar corrida na primeira configuração e servir de ponto natural de lock (`SELECT ... FOR UPDATE`) para operações institucionais futuras.
- Enquanto `configured = false`, `GET /paroquia` retorna `404 PARISH_PROFILE_NOT_CONFIGURED` em vez de um DTO com campos nulos.
- `PUT /paroquia` (sem path variable, sem id no request) serve tanto para a primeira configuração quanto para atualizações posteriores; nesta primeira etapa é restrito a `ROLE_ADMIN`. A distinção entre `name`/`diocese` (futuramente só ADMIN) e os demais campos operacionais (futuramente ADMIN + secretaria) é uma regra de negócio já definida para etapas futuras, mas não implementada agora: não existe `ROLE_PARISH_SECRETARY`, nem qualquer nova `ROLE_*` além de `ROLE_ADMIN`/`ROLE_OPERATOR`.

## 4. Consequências positivas

- Isolamento simples por implantação: cada paróquia tem seu próprio banco e ambiente, sem risco de vazamento de dados entre tenants.
- Autorização mais simples: não é necessário verificar `parish_id` em nenhuma consulta ou regra de negócio existente.
- Banco menor e mais simples por instalação, sem colunas ou índices de particionamento.
- Ausência estrutural de vazamento entre paróquias (tenant leakage), porque não existe o conceito de múltiplos tenants no mesmo banco.
- Domínio mais simples de raciocinar: `ParishProfile` é um agregado isolado, sem acoplamento com `Person`/`UserAccount`/`Location`.

## 5. Trade-offs

- Atender várias paróquias exige implantações completamente separadas (banco, ambiente e configuração próprios por paróquia), não uma única instalação compartilhada.
- Uma futura versão SaaS multi-tenant (múltiplas paróquias em uma mesma instalação/banco) exigiria uma evolução arquitetural explícita e não trivial — não é impossível tecnicamente, apenas fora do escopo e do desenho atual, e exigiria introduzir particionamento por tenant em todas as entidades de domínio hoje sem `parish_id`.

## 6. Fora do escopo desta etapa

- `ParishStaffAssignment`, `PASTOR`, `PARISH_SECRETARY`, `PersonMinistry.coordinator`.
- `MinistryAuthorizationService`, `ParishAuthorizationService`, capacidades de usuário atual (current-user capabilities).
- Alterações em JWT, novas `ROLE_*`, endpoints ministeriais escopados por secretaria.
- Vínculo entre `ParishProfile` e `Location` (`@OneToOne` ou equivalente) — a futura página pública poderá combinar os dois sem alterar o significado de nenhuma das duas entidades.
- Alterações no frontend.

## 7. Critérios de aceite atendidos

- `ParishProfile` existe como singleton (`id = 1`, sem `@GeneratedValue`), com constraint de banco (`chk_tb_parish_profile_singleton_id`) que impede fisicamente qualquer outra linha.
- `GET /paroquia` é público e retorna somente os seis campos institucionais quando configurado; `404 PARISH_PROFILE_NOT_CONFIGURED` caso contrário.
- `PUT /paroquia` é restrito a `ROLE_ADMIN`, atualiza a mesma linha singleton e nunca cria uma segunda linha.
- Nenhuma entidade de domínio existente recebeu `parish_id`.
- Migrations `V1`–`V18` não foram alteradas; `V19` é puramente aditiva.
