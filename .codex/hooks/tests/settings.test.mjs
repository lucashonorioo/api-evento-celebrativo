import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const codexDir = path.resolve(here, '../..');
const projectRoot = path.resolve(codexDir, '..');
const hooks = JSON.parse(readFileSync(path.join(codexDir, 'hooks.json'), 'utf8'));
const config = readFileSync(path.join(codexDir, 'config.toml'), 'utf8');

test('Codex registra lifecycle completo do engineering review gate', () => {
  assert.deepEqual(Object.keys(hooks.hooks), [
    'SessionStart', 'PreToolUse', 'PostToolUse', 'SubagentStart', 'SubagentStop', 'Stop',
  ]);
  for (const event of ['SessionStart', 'SubagentStart', 'SubagentStop', 'Stop']) {
    const handlers = hooks.hooks[event].flatMap((entry) => entry.hooks);
    assert.equal(handlers.length, 1, event);
    assert.match(handlers[0].command, /engineering-review-gate\.mjs/);
    assert.match(handlers[0].commandWindows, /engineering-review-gate\.mjs/);
    assert.ok(Number.isFinite(handlers[0].timeout) && handlers[0].timeout > 0);
  }
});

test('Codex Pre/PostToolUse usa guards locais e timeout finito', () => {
  const pre = hooks.hooks.PreToolUse[0];
  assert.match(pre.matcher, /Bash/);
  assert.match(pre.matcher, /PowerShell/);
  assert.match(pre.matcher, /apply_patch/);
  assert.match(pre.hooks[0].command, /pre-tool-guard\.mjs/);
  assert.ok(pre.hooks[0].timeout <= 20);

  const post = hooks.hooks.PostToolUse[0];
  assert.match(post.matcher, /apply_patch/);
  assert.match(post.hooks[0].command, /post-edit-check\.mjs/);
  assert.ok(post.hooks[0].timeout <= 30);
});

test('scripts referenciados pelo Codex existem no bundle', () => {
  for (const relative of [
    '.codex/hooks/engineering-review-gate.mjs',
    '.codex/hooks/pre-tool-guard.mjs',
    '.codex/hooks/post-edit-check.mjs',
    '.ai/review/engineering-review-gate.mjs',
    '.ai/guards/safety-policy.mjs',
  ]) {
    assert.equal(existsSync(path.join(projectRoot, relative)), true, relative);
  }
});

test('config Codex usa nomes atuais e remove aliases/keys não comprovadas', () => {
  assert.match(config, /^max_concurrent_threads_per_session\s*=\s*5\s*$/m);
  assert.match(config, /^interrupt_message\s*=\s*true\s*$/m);
  assert.doesNotMatch(config, /^max_threads\s*=/m);
  assert.doesNotMatch(config, /^max_depth\s*=/m);
});

test('agentes Codex não usam chaves de configuração não documentadas', () => {
  const agentsDir = path.join(codexDir, 'agents');
  for (const name of [
    'backend-reviewer.toml',
    'frontend-reviewer.toml',
    'security-reviewer.toml',
    'test-reviewer.toml',
    'codebase-explorer.toml',
  ]) {
    const agent = readFileSync(path.join(agentsDir, name), 'utf8');
    assert.doesNotMatch(agent, /^nickname_candidates\s*=/m, name);
    assert.match(agent, /^name\s*=\s*".+"\s*$/m, name);
    assert.match(agent, /^description\s*=\s*".+"\s*$/m, name);
    assert.match(agent, /^developer_instructions\s*=\s*\"\"\"[\s\S]*\"\"\"\s*$/m, name);
  }
});
