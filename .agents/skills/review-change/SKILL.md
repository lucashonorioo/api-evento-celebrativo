---
name: review-change
description: Execute Engineering Review independente e somente leitura sobre branch/PR/diff do Evento Celebrativo antes da conclusão/merge.
---

# Review change — Codex

Leia `.ai/review/ENGINEERING_REVIEW.md` e os `AGENTS.md` aplicáveis. Não edite durante a revisão.

## 1. Escopo e rodada

Determine base + staged/unstaged/untracked da tarefa, leia o diff completo e trace apenas dependências necessárias. Classifique `LOW|MEDIUM|HIGH`.

Abra uma rodada:

```text
node "$(git rev-parse --show-toplevel)/.codex/hooks/engineering-review-gate.mjs" --review-start - --risk <LOW|MEDIUM|HIGH> <reviewers...>
```

No PowerShell, resolva `$root = (& git rev-parse --show-toplevel).Trim()` e use `Join-Path`.

Guarde `roundId`. O wrapper resolve `CODEX_THREAD_ID` quando recebe `-`. Leia também `inferredRiskLevel` e `inferredRequiredReviewers`; `expectedReviewers` retornado pelo gate é autoritativo: execute todos, mesmo os inferidos.

## 2. Reviewers

Use apenas especialistas necessários; `codebase_explorer` é auxiliar e não participa da barreira. Execute reviewers reais em foreground, preferencialmente de forma sequencial. Não use polling/background textual.

Cada reviewer segue o protocolo `Finding:` + footer da política canônica. Ao retornar, persista **o texto exato**:

```text
--review-record-result - "<roundId>" <reviewer> [--agent-id "<id>"] --result-file <arquivo>
```

Use stdin/arquivo, nunca output grande em argv. Nunca invoque manualmente `--session-start`, `--subagent-start` ou `--subagent-stop`. `SubagentStart`/`SubagentStop` automáticos são integração opcional.

Consulte:

```text
--review-status - "<roundId>"
```

A barreira exige `pendingReviewers: []`.

Cada reviewer tem no máximo 2 tentativas. Após falha final, produza `ENGINEERING_REVIEW_FAILED` e:

```text
--review-fail - "<roundId>" "<motivo>"
```

Nunca converta falha técnica em PASS.

## 3. Veredito

Valide/deduplique findings e finalize exatamente um:

```text
--review-finish - "<roundId>" PASS
--review-finish - "<roundId>" PASS_WITH_NOTES
--review-finish - "<roundId>" CHANGES_REQUIRED
```

O gate bloqueia sucesso incompatível com `MEDIUM+`. LOW não bloqueia: normalmente vira `PASS WITH NOTES`; não altere código por iniciativa própria para corrigir LOW. Qualquer edição invalida o fingerprint e exige nova rodada. O estado persistido da rodada é a fonte de verdade.

Reporte escopo, risco, veredito, findings, validações e riscos residuais.
