---
name: review-change
description: Execute Engineering Review independente e somente leitura sobre branch/PR/diff do Evento Celebrativo antes da conclusão/merge.
---

# Review change — Claude Code

Leia `.ai/review/ENGINEERING_REVIEW.md` e os `CLAUDE.md` aplicáveis. Não edite durante a revisão.

## 1. Escopo e rodada

Determine base + staged/unstaged/untracked, leia o diff completo, trace somente dependências necessárias e classifique `LOW|MEDIUM|HIGH`.

Abra:

```text
node "${CLAUDE_PROJECT_DIR}/.claude/hooks/engineering-review-gate.mjs" --review-start - --risk <LOW|MEDIUM|HIGH> <reviewers...>
```

O wrapper usa `CLAUDE_CODE_SESSION_ID` quando recebe `-`. Guarde `roundId`; leia `inferredRiskLevel`, `inferredRequiredReviewers` e `expectedReviewers`. A lista esperada é autoritativa; execute todos.

## 2. Reviewers

Execute especialistas reais em foreground; prefira sequencial quando necessário. Não use `run_in_background`, Ctrl+B, `TaskOutput`, polling ou espera textual.

Cada reviewer segue `Finding:` + footer da política. Assim que retornar, persista o texto exato:

```text
--review-record-result - "<roundId>" <reviewer> [--agent-id "<id>"] --result-file "<arquivo>"
```

Nunca invoque manualmente `--session-start`, `--subagent-start` ou `--subagent-stop`.

Cheque:

```text
--review-status - "<roundId>"
```

A barreira exige `pendingReviewers: []`. `--review-await - "<roundId>" 5000` é somente fallback único/limitado quando lifecycle automático real ainda estiver em trânsito.

Cada reviewer tem no máximo **2 tentativas totais**. Após falha final:

```text
--review-fail - "<roundId>" "<motivo>"
```

Falha técnica nunca vira PASS.

## 3. Veredito

Valide/deduplique findings e finalize um:

```text
--review-finish - "<roundId>" PASS
--review-finish - "<roundId>" PASS_WITH_NOTES
--review-finish - "<roundId>" CHANGES_REQUIRED
```

O gate bloqueia sucesso incompatível com `MEDIUM+`. Não corrija LOW automaticamente; normalmente conclua `PASS WITH NOTES`. Qualquer edição invalida o fingerprint e exige nova revisão. A fonte de verdade é o estado persistido da rodada.

Reporte escopo, risco, veredito, findings, validações e riscos residuais.
