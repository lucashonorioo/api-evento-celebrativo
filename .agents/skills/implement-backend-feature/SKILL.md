---
name: implement-backend-feature
description: Implemente features, correções ou refatorações focadas no backend Java/Spring Boot do Evento Celebrativo. Use quando a tarefa altera controller, DTO, service, repository, entidade, segurança ou testes backend; não use para mudanças apenas frontend.
---

# Implementar alteração backend

## Preparação

1. Leia o AGENTS da raiz e o AGENTS do backend.
2. Confirme o requisito, comportamento atual e compatibilidade esperada.
3. Localize todos os pontos do fluxo: controller, DTO, mapper, service, repository, entidade, segurança e testes.
4. Quando a tarefa for ampla ou incerta, peça ao `codebase_explorer` um mapa somente leitura.

## Implementação

1. Preserve contratos existentes salvo requisito explícito.
2. Mantenha controller focado em HTTP e service em regras de negócio.
3. Use DTOs e MapStruct conforme o padrão do projeto.
4. Defina transação e persistência conscientemente.
5. Trate erros por exceções de domínio e handler global.
6. Preserve autenticação e autorização; novos endpoints são protegidos por padrão.
7. Para schema, crie migration incremental e não edite migrations antigas.
8. Evite alterações não relacionadas e novas dependências.

## Testes

Inclua testes adequados à camada:

- service com Mockito;
- controller com MockMvc;
- repository com `@DataJpaTest`;
- autorização quando segurança mudar;
- regressão quando corrigir bug.

Execute primeiro testes específicos e depois `./mvnw.cmd -q test` quando possível.

## Revisão

Para mudança de risco médio/alto, peça revisão somente leitura ao `backend_reviewer`; inclua `security_reviewer` quando houver autenticação, roles, JWT, CORS ou exposição de endpoint.

## Entrega

Reporte arquivos, comportamento, decisões, comandos, resultados e limitações reais.
