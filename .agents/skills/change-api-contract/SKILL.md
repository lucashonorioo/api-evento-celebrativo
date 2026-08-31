---
name: change-api-contract
description: Coordene mudança full stack em endpoint, path, request/response, paginação, status HTTP ou autenticação entre Spring e Angular.
---

# Alterar contrato da API

1. Leia `AGENTS.md` raiz, backend e frontend.
2. Localize producer e todos os consumers relevantes: segurança/controller/DTO/mapper/service/testes, models/HTTP/UI/formulários/testes e OpenAPI quando existir.
3. Registre contrato atual → desejado e classifique compatível/breaking.
4. Breaking change exige atualização coordenada ou transição explícita.
5. Implemente fonte de verdade + testes e atualize consumers tipados/estados de erro.
6. Valide status, serialização, validação, auth e ausência de consumer antigo.
7. Execute `validate-project` nas áreas afetadas e `review-change` no diff full stack.
8. Corrija `CHANGES_REQUIRED`, revalide/revise; conclua só com `PASS`/`PASS WITH NOTES`.

Contrato é no mínimo risco MEDIUM; auth/public exposure/dado sensível é HIGH.

Reporte antes/depois, compatibilidade, consumers, validações, Engineering Review e riscos residuais.
