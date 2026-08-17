---
name: change-api-contract
description: Coordena mudança full stack em endpoint, path, request, response, paginação, status HTTP ou autenticação entre backend Spring e frontend Angular. Use quando um contrato compartilhado muda; não use para implementação interna sem impacto nos consumers.
---

# Alterar contrato da API

## Princípio

Trate o contrato como interface compartilhada. Não atualize apenas um lado e não presuma consumidores.

## Fluxo

1. Leia os `CLAUDE.md` da raiz, backend e frontend.
2. Localize producers e consumers: controller/segurança, DTOs, mapper, service, testes, models, serviços HTTP, componentes, formulários e OpenAPI quando existir.
3. Registre contrato atual e contrato desejado.
4. Classifique compatibilidade e impacto.
5. Para breaking change, defina atualização coordenada ou transição.
6. Implemente a fonte de verdade e seus testes.
7. Atualize todos os consumers tipados.
8. Valide status, serialização, validação, autorização e erros.
9. Revise o diff full stack para localizar consumer antigo ou comportamento incompatível.

## Gate de conclusão obrigatório

Mudança de contrato é, no mínimo, risco médio:

1. use `validate-project` para backend e frontend afetados;
2. use `review-change` sobre o diff coordenado;
3. trate `CHANGES_REQUIRED` dentro do escopo;
4. após correções, repita validações afetadas e a revisão;
5. conclua somente com `PASS` ou `PASS WITH NOTES`.

Alterações de autenticação, autorização, exposição pública ou dados sensíveis são alto risco e devem incluir `security-reviewer`.

## Entrega

Informe antes/depois do contrato, compatibilidade, consumers atualizados, validações executadas, veredito da revisão de engenharia e riscos residuais.
