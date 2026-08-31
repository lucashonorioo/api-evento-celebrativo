---
name: validate-project
description: Valide alterações do Evento Celebrativo com testes, build e verificações proporcionais aos arquivos/comportamentos modificados.
---

# Validar projeto

1. Leia os `CLAUDE.md` aplicáveis; inspecione status/diff e preserve mudanças do usuário.
2. Execute `git diff --check`; procure arquivo inesperado, secret/dado sensível, debug, artefato, código morto e mudança fora do escopo.
3. Backend: testes específicos primeiro; depois suíte/compile/contexto, segurança, profiles, migrations, contratos e queries conforme o risco.
4. Frontend: testes específicos; `npm test -- --watch=false`, `npm run build` e validação de TypeScript/templates/rotas/contratos/a11y/responsividade conforme o risco.
5. Diferencie regressão da tarefa de falha preexistente/ambiente; não enfraqueça testes e nunca declare execução que não ocorreu.
6. Antes de retornar, encerre todo processo longo iniciado pelo agente conforme `.ai/runtime/PROCESS_LIFECYCLE.md`.

Retorne matriz curta `comando | resultado | observação` e riscos não validados.

`validate-project` não substitui `review-change`.
