import assert from 'node:assert/strict';
import { chmodSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { tmpdir } from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import { buildWindowsBatchCommand } from '../graphify-hook-guard.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const projectRoot = path.resolve(here, '../../..');
const read = (relative) => readFileSync(path.join(projectRoot, relative), 'utf8');

test('Graphify usa versão pinada do repositório e não instala latest implicitamente', () => {
  const version = read('.claude/skills/graphify/.graphify_version').trim();
  const skill = read('.claude/skills/graphify/SKILL.md');
  assert.match(version, /^\d+\.\d+\.\d+(?:[-+][A-Za-z0-9.-]+)?$/);
  assert.match(skill, /\.graphify_version/);
  assert.match(skill, /graphifyy==\$GRAPHIFY_VERSION/);
  assert.doesNotMatch(skill, /pip\s+install\s+--upgrade\s+graphifyy/i);
  assert.doesNotMatch(skill, /uv\s+tool\s+install\s+--upgrade\s+graphifyy/i);
});

test('provider semântico externo exige opt-in explícito além da chave', () => {
  const policy = read('.claude/skills/graphify/SKILL.md') + '\n' + read('.claude/skills/graphify/references/build-pipeline.md');
  assert.match(policy, /GRAPHIFY_ALLOW_EXTERNAL_LLM/);
  assert.match(policy, /Presence of `GEMINI_API_KEY`\/`GOOGLE_API_KEY` alone is \*\*not consent/i);
  assert.match(policy, /both[^\n]*conditions/i);
});

test('respostas geradas pela IA não são persistidas automaticamente no grafo fonte', () => {
  const query = read('.claude/skills/graphify/references/query.md');
  assert.match(query, /Do \*\*not\*\* automatically persist/i);
  assert.match(query, /explicitly asks to persist/i);
  assert.match(query, /derived AI work memory/i);
  assert.match(query, /re-verify factual claims against source-backed graph nodes/i);
});

test('graphify hook tem timeout próprio e permanece fail-open', () => {
  const hook = read('.claude/hooks/graphify-hook-guard.mjs');
  assert.match(hook, /VERSION_TIMEOUT_MS\s*=\s*500/);
  assert.match(hook, /GUARD_TIMEOUT_MS\s*=\s*2500/);
  assert.match(hook, /TOTAL_BUDGET_MS\s*=\s*5000/);
  assert.match(hook, /graphify\.cmd/);
  assert.match(hook, /graphify\.bat/);
  assert.match(hook, /ADVISORY_OUTPUT_LIMIT/);
  assert.match(hook, /pinnedVersion/);
  assert.match(hook, /actualVersion !== expectedVersion/);
  assert.match(hook, /fail-open/i);
  assert.match(hook, /Graphify advisory \(não bloqueante/);
});

test('SKILL principal do Graphify usa progressive disclosure para o pipeline pesado', () => {
  const skill = read('.claude/skills/graphify/SKILL.md');
  const buildReference = read('.claude/skills/graphify/references/build-pipeline.md');
  const lineCount = skill.split(/\r?\n/).length;
  assert.ok(lineCount < 500, `SKILL.md deveria permanecer abaixo de 500 linhas, recebeu ${lineCount}`);
  assert.match(skill, /read `references\/build-pipeline\.md` in full now/i);
  assert.match(buildReference, /### Step 2 - Detect files/);
  assert.match(buildReference, /### Step 9 - Save manifest/);
  const updateReference = read('.claude/skills/graphify/references/update.md');
  assert.match(updateReference, /references\/build-pipeline\.md/);
});

test('AGENTS e CLAUDE apontam para uma única política canônica do Graphify', () => {
  const agents = read('AGENTS.md');
  const claude = read('CLAUDE.md');
  const canonical = read('.ai/graphify/GRAPHIFY_POLICY.md');
  for (const instructions of [agents, claude]) {
    assert.match(instructions, /\.ai\/graphify\/GRAPHIFY_POLICY\.md/);
  }
  assert.match(canonical, /graphify query/);
  assert.match(canonical, /graphify update \./);
  assert.match(canonical, /API key não equivale a consentimento/i);
  assert.doesNotMatch(agents, /GRAPH_REPORT\.md` como mapa inicial/i);
});

// Isolamento de PATH: os candidatos são tentados na ordem
// graphify.exe/.cmd/.bat/graphify (Windows). Se a máquina de teste já tiver
// um `graphify.exe` real instalado em algum diretório do PATH do sistema,
// prefixar o PATH com o binDir do fixture NÃO impede que ele seja encontrado
// — .exe é tentado antes de .cmd, então o binário real (que responde rápido)
// vence a corrida antes do fixture falso e lento sequer ser tentado. Isso
// tornava este teste dependente de silenciosamente existir ou não graphify
// real instalado na máquina (raiz da flakiness relatada na auditoria: em
// algumas execuções o teste "passava rápido" sem a mensagem de fail-open
// esperada, porque nunca exercitava o caminho travado). Restringir PATH
// exclusivamente ao binDir do fixture garante que somente o binário falso
// seja resolvível, independente do que estiver instalado na máquina.
function isolatedGraphifyEnv(binDir, projectRoot) {
  return { ...process.env, PATH: binDir, CLAUDE_PROJECT_DIR: projectRoot };
}

test('graphify travado falha aberto dentro do orçamento interno em POSIX e Windows batch', () => {
  const binDir = mkdtempSync(path.join(tmpdir(), 'fake-graphify-'));
  const bin = path.join(binDir, process.platform === 'win32' ? 'graphify.cmd' : 'graphify');
  if (process.platform === 'win32') {
    writeFileSync(bin, `@echo off\r\nif "%1"=="--version" (echo graphify 0.9.28& exit /b 0)\r\nping 127.0.0.1 -n 11 >nul\r\n`);
  } else {
    writeFileSync(bin, `#!/bin/sh\nif [ "$1" = "--version" ]; then echo "graphify 0.9.28"; exit 0; fi\nsleep 10\n`);
    chmodSync(bin, 0o755);
  }
  const hook = path.join(projectRoot, '.claude/hooks/graphify-hook-guard.mjs');
  const started = Date.now();
  const result = spawnSync(process.execPath, [hook, 'read'], {
    input: JSON.stringify({ cwd: projectRoot, tool_name: 'Read', tool_input: { file_path: 'README.md' } }),
    encoding: 'utf8',
    env: isolatedGraphifyEnv(binDir, projectRoot),
    timeout: 7000,
  });
  const elapsed = Date.now() - started;
  assert.equal(result.status, 0, result.stderr);
  assert.ok(elapsed < 6000, `Graphify hook excedeu orçamento: ${elapsed}ms`);
  assert.match(result.stderr, /fail-open|ignorado/i);
});

test('graphify travado no Windows não deixa o processo-filho (ping) da árvore sobrevivendo após o timeout', { skip: process.platform !== 'win32' ? 'específico de Windows: taskkill/process tree' : false }, () => {
  const binDir = mkdtempSync(path.join(tmpdir(), 'fake-graphify-orphan-'));
  const bin = path.join(binDir, 'graphify.cmd');
  const marker = `-n 37 -w 1`; // contagem de pings incomum, usada para identificar nosso processo com segurança
  writeFileSync(bin, `@echo off\r\nif "%1"=="--version" (echo graphify 0.9.28& exit /b 0)\r\nping 127.0.0.1 ${marker}\r\n`);

  const countPings = () => (spawnSync('tasklist', ['/FI', 'IMAGENAME eq PING.EXE', '/FO', 'CSV']).stdout.toString().match(/"PING\.EXE"/gi) ?? []).length;
  const baseline = countPings();

  const hook = path.join(projectRoot, '.claude/hooks/graphify-hook-guard.mjs');
  const result = spawnSync(process.execPath, [hook, 'read'], {
    input: JSON.stringify({ cwd: projectRoot, tool_name: 'Read', tool_input: { file_path: 'README.md' } }),
    encoding: 'utf8',
    env: isolatedGraphifyEnv(binDir, projectRoot),
    timeout: 7000,
  });
  assert.equal(result.status, 0, result.stderr);

  // O hook já retornou; se a árvore tivesse sido morta apenas no nível do
  // cmd.exe (comportamento anterior a esta correção), o `ping` filho ainda
  // estaria rodando por vários segundos depois (contagem de 37 pings). Damos
  // uma folga curta para o taskkill assíncrono do SO concluir e comparamos
  // a contagem de processos PING.EXE com o baseline anterior à chamada.
  const deadline = Date.now() + 2000;
  let after = countPings();
  while (after > baseline && Date.now() < deadline) after = countPings();
  assert.ok(after <= baseline, `processo(s) ping.exe da árvore do graphify travado sobreviveram ao timeout do hook (before=${baseline}, after=${after})`);
});


test('comando batch do Graphify não injeta aspas literais nos argumentos constantes', () => {
  assert.equal(buildWindowsBatchCommand('graphify.cmd', ['--version']), 'graphify.cmd --version');
  assert.equal(buildWindowsBatchCommand('graphify.bat', ['hook-guard', 'read']), 'graphify.bat hook-guard read');
  assert.throws(() => buildWindowsBatchCommand('graphify.cmd', ['unsafe & command']), /argumento inesperado/);
});

test('política documenta explicitamente que o advisory automático do Graphify é Claude-only por enquanto', () => {
  const canonical = read('.ai/graphify/GRAPHIFY_POLICY.md');
  assert.match(canonical, /hook advisory automático.*apenas no Claude Code/is);
  assert.match(canonical, /No Codex.*Skill explícita/is);
  assert.match(canonical, /não sugerir uma paridade automática que não existe/i);
});
