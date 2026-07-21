---
name: validate-project
description: Valide alterações do Evento Celebrativo antes de concluir uma tarefa, escolhendo testes, build e verificações conforme arquivos modificados. Use após implementação ou antes de declarar a branch pronta; não use como substituto da investigação de uma falha ainda desconhecida.
---

# Validar projeto

## Preparação

1. Leia os AGENTS aplicáveis.
2. Inspecione `git status --short` e `git diff --name-only`.
3. Não altere nem descarte mudanças existentes do usuário.
4. Identifique backend, frontend, documentação ou configuração afetados.

## Verificações comuns

Execute:

```text
git diff --check
```

Revise:

- arquivos inesperados;
- secrets e dados pessoais;
- logs de depuração;
- artefatos gerados;
- código morto;
- mudanças fora do escopo.

## Backend

Quando houver alterações backend:

1. rode testes específicos afetados;
2. rode `./mvnw.cmd -q test` quando possível;
3. valide compilação, contexto, segurança e migrations conforme a mudança.

No Windows, use `./mvnw.cmd` ou `.\mvnw.cmd` conforme o shell.

## Frontend

Quando houver alterações frontend:

1. rode testes específicos quando disponíveis;
2. rode `npm test -- --watch=false`;
3. rode `npm run build`;
4. verifique TypeScript, templates, rotas, acessibilidade e responsividade da área alterada.

## Falhas

- Não tente corrigir indiscriminadamente toda falha preexistente.
- Diferencie regressão causada pela tarefa de problema anterior ou limitação do ambiente.
- Se uma validação falhar, preserve a saída relevante e investigue a causa antes de declarar conclusão.
- Nunca diga que passou se não executou.

## Resultado

Entregue uma matriz curta com comando, resultado e observação. Informe riscos não validados.
