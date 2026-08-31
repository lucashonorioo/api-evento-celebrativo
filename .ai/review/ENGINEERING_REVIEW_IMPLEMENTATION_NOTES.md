# Engineering Review Gate — notas de implementação

Este documento preserva contexto técnico de manutenção que não deve ser carregado em cada revisão. A política normativa dos reviewers é exclusivamente `ENGINEERING_REVIEW.md`.

## Persistência e lifecycle

- O fluxo primário registra resultados de reviewers reais em foreground via `--review-record-result`, usando stdin/arquivo para não transportar output grande em argv.
- `roundId`, reviewer esperado, fingerprint e footer válido são obrigatórios. Resultado idêntico pode ser idempotente; duplicata conflitante é rejeitada.
- `SubagentStart`/`SubagentStop` são integração opcional do runtime, não caminho primário nem prova criptográfica de proveniência.
- Timeout, reviewer desaparecido ou resultado inválido devem produzir falha técnica finita; nunca `PASS`.
- `review-await` é fallback limitado, não mecanismo normal de polling.

## Recovery conservador

Se baseline/state não puder ser confiavelmente reconstruído, o gate deve assumir estado não revisado e exigir nova rodada. Recovery nunca transforma working tree atual em aprovado. Quando o escopo anterior é desconhecido, mantenha postura conservadora definida pelo gate.

## Fronteira de segurança

O gate e `safety-policy.mjs` são guardrails determinísticos contra erro e bypass direto/casual; não são sandbox nem fronteira contra processo adversarial com execução arbitrária. Controles fortes para merge/deploy crítico permanecem externos: permissões/sandbox do SO, CI, branch protection e/ou revisão humana.

O scanner de shell deve preferir extração estrutural/argv/destino real a regex isolada. Formas dinâmicas que exigiriam interpretar linguagem arbitrária (por exemplo splatting PowerShell, template interpolation complexa, command substitution/concatenação dinâmica) não devem ser apresentadas como cobertas hermeticamente.

## Escrita e arquivos protegidos

- Travessia explícita `..` é bloqueada como sanitização.
- Escrita fora do projeto pode ser legítima; proteções sensíveis continuam válidas em qualquer destino.
- `.env` real, `.git` interno, credenciais/chaves e migrations versionadas são protegidos conforme a política do guard.
- Mencionar um padrão sensível em documentação/heredoc não deve bloquear quando ele não é o destino efetivo da mutação.

## Configuração de plataforma

Não invente campos de configuração que a plataforma não documenta. Em particular, mantenha controles específicos de Claude (como `maxTurns`) apenas onde suportados; no Codex, revalide a documentação antes de adicionar equivalentes.

## Manutenção

Alterações no gate, guards, hooks, reviewers, skills ou nesta política fazem parte da superfície de engenharia de IA e devem passar pelos testes próprios da infraestrutura + Engineering Review.
