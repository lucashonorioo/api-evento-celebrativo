---
name: implement-backend-feature
description: Implementa feature, correção ou refatoração focada no backend Java/Spring Boot do Evento Celebrativo. Use quando a tarefa alterar controller, DTO, mapper, service, repository, entidade, segurança, migration ou testes backend; não use para mudanças somente frontend.
---

# Implementar alteração backend

## Preparação

1. Leia o `CLAUDE.md` da raiz e o do backend.
2. Confirme requisito, critérios de aceite, comportamento atual e compatibilidade esperada.
3. Localize controller, DTOs, mapper, service, repository, entidade, segurança, migrations, logs e testes relacionados.
4. Trace a requisição, efeitos sobre dados e resposta.
5. Para tarefa ampla ou incerta, use `codebase-explorer` para produzir um mapa somente leitura.

## Implementação

- Preserve contratos existentes salvo requisito explícito.
- Mantenha controller focado em HTTP e service nas regras de negócio.
- Use DTOs e MapStruct conforme o padrão existente.
- Defina transações, constraints e persistência conscientemente.
- Avalie paginação, N+1, concorrência e compatibilidade de dados quando aplicáveis.
- Trate falhas conhecidas por exceções de domínio e handler global.
- Preserve autenticação e autorização; endpoint novo é protegido por padrão.
- Para schema, crie migration incremental e não edite migrations versionadas existentes.
- Produza logs úteis e seguros; não deixe logs temporários.
- Evite dependências, abstrações e alterações fora do escopo.

## Testes

Escolha a camada adequada ao comportamento e risco:

- service com JUnit/Mockito;
- controller com MockMvc;
- repository com `@DataJpaTest`;
- autorização quando segurança mudar;
- regressão para correção de bug quando viável.

Execute primeiro testes específicos e depois a suíte backend proporcional ao risco.

## Gate de conclusão obrigatório

Depois da última alteração relevante de código:

1. use `validate-project`;
2. use `review-change` como revisão independente de engenharia;
3. se o veredito for `CHANGES_REQUIRED`, corrija somente achados acionáveis relacionados à tarefa;
4. repita validações afetadas e `review-change` depois de qualquer correção;
5. conclua somente com `PASS` ou `PASS WITH NOTES`.

O agente implementador integra as correções; os reviewers permanecem somente leitura. Não trate testes passando como substituto da revisão e não amplie a tarefa para dívida preexistente sem relação.

## Entrega

Reporte comportamento, arquivos relevantes, decisões, dados/migrations afetados, comandos/resultados, veredito da revisão de engenharia e limitações reais.
