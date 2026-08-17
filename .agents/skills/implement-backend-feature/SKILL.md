---
name: implement-backend-feature
description: Implemente features, correções ou refatorações focadas no backend Java/Spring Boot do Evento Celebrativo. Use quando a tarefa altera controller, DTO, service, repository, entidade, segurança, migration ou testes backend; não use para mudanças apenas frontend.
---

# Implementar alteração backend

## Preparação

1. Leia o `AGENTS.md` da raiz e o `AGENTS.md` do backend.
2. Confirme requisito, critérios de aceite, comportamento atual e compatibilidade esperada.
3. Localize controller, DTOs, mapper, service, repository, entidade, segurança, migrations e testes relacionados.
4. Trace a requisição, efeitos sobre dados e resposta antes de editar.
5. Quando a tarefa for ampla ou incerta, use exploração somente leitura para mapear o fluxo antes da implementação.

## Implementação

1. Preserve contratos existentes salvo requisito explícito.
2. Mantenha controller focado em HTTP e service em regras de negócio.
3. Use DTOs e MapStruct conforme o padrão do projeto.
4. Defina transações, constraints e persistência conscientemente.
5. Avalie paginação, N+1, concorrência e compatibilidade de dados quando aplicáveis.
6. Trate falhas conhecidas por exceções de domínio e handler global.
7. Preserve autenticação e autorização; endpoint novo é protegido por padrão.
8. Para schema, crie migration incremental e não edite migrations versionadas existentes.
9. Evite alterações não relacionadas, abstrações prematuras e novas dependências sem necessidade comprovada.

## Testes

Inclua testes adequados à camada e ao risco:

- service com JUnit/Mockito;
- controller com MockMvc;
- repository com `@DataJpaTest`;
- autorização quando segurança mudar;
- regressão quando corrigir bug.

Execute primeiro testes específicos e depois a suíte backend proporcional ao risco.

## Gate de conclusão obrigatório

Depois da última alteração relevante de código:

1. execute `validate-project` com validações proporcionais ao risco;
2. execute `review-change` como revisão independente de engenharia;
3. se o veredito for `CHANGES_REQUIRED`, corrija somente achados acionáveis relacionados à tarefa;
4. repita as validações afetadas e execute `review-change` novamente depois de qualquer correção;
5. conclua apenas com `PASS` ou `PASS WITH NOTES`.

Não use testes passando como substituto da revisão. Não amplie o escopo para corrigir dívida preexistente sem relação com a tarefa.

## Entrega

Reporte comportamento implementado, arquivos relevantes, decisões técnicas, dados/migrations afetados, validações executadas, veredito da revisão de engenharia e limitações reais.
