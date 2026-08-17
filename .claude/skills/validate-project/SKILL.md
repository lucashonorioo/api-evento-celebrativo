---
name: validate-project
description: Valida alterações do Evento Celebrativo antes da conclusão, escolhendo testes, build e verificações conforme os arquivos modificados. Use após implementação ou antes de declarar a tarefa pronta; não use para substituir investigação de falha desconhecida.
---

# Validar projeto

## Preparação

1. Leia os `CLAUDE.md` aplicáveis.
2. Inspecione `git status --short`, `git diff --name-only` e o diff relevante.
3. Preserve todas as mudanças existentes do usuário.
4. Identifique backend, frontend, documentação, configuração e migrations afetados.
5. Selecione validações pelo comportamento e risco, não apenas pela extensão dos arquivos.

## Verificações comuns

Execute `git diff --check` e revise arquivos inesperados, secrets, dados pessoais, logs, comentários temporários, artefatos gerados, código morto e mudanças fora do escopo.

## Backend

Quando houver alterações backend:

1. execute testes específicos afetados;
2. execute `./mvnw.cmd -q test` quando possível;
3. valide compilação, contexto, segurança, profiles, migrations, contratos e consultas conforme a mudança.

No Windows, use `./mvnw.cmd` ou `.\mvnw.cmd` conforme o shell.

## Frontend

Quando houver alterações frontend:

1. execute testes específicos quando disponíveis;
2. execute `npm test -- --watch=false`;
3. execute `npm run build`;
4. verifique TypeScript, templates, rotas, contratos, acessibilidade e responsividade da área alterada;
5. valide a interação em navegador quando o risco visual ou funcional justificar e o ambiente permitir.

## Falhas e resultado

- Diferencie regressão causada pela tarefa de falha preexistente ou limitação do ambiente.
- Preserve a saída relevante e investigue a causa antes de declarar conclusão.
- Não corrija indiscriminadamente falhas fora do escopo.
- Não enfraqueça testes nem declare sucesso sem execução.

Entregue uma matriz curta com comando, resultado e observação, além dos riscos não validados.

## Relação com a revisão de engenharia

`validate-project` comprova build, testes e verificações executáveis; ela não substitui `review-change`. Quando houver alteração de implementação, a conclusão exige validação seguida de revisão independente conforme `.ai/review/ENGINEERING_REVIEW.md`.
