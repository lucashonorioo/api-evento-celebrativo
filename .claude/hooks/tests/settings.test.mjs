import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const settingsPath = path.resolve(here, '../../settings.json');
const projectClaudeDir = path.resolve(here, '../..');
const settings = JSON.parse(readFileSync(settingsPath, 'utf8'));

test('settings registra os eventos e matchers esperados', () => {
  assert.ok(Array.isArray(settings.hooks.SessionStart));
  assert.ok(Array.isArray(settings.hooks.PreToolUse));
  assert.ok(Array.isArray(settings.hooks.PostToolUse));
  assert.ok(Array.isArray(settings.hooks.Stop));

  assert.equal(settings.hooks.SessionStart.length, 1);
  assert.equal(settings.hooks.SessionStart[0].matcher, undefined);

  const preMatchers = settings.hooks.PreToolUse.map((entry) => entry.matcher);
  assert.deepEqual(preMatchers, ['Bash|PowerShell', 'Edit|Write', 'Bash|Grep', 'Read|Glob']);
  assert.equal(settings.hooks.PostToolUse[0].matcher, 'Edit|Write');
  assert.equal(settings.hooks.Stop.length, 1);
  assert.equal(settings.hooks.Stop[0].matcher, undefined);
});

test('hooks command usam formato oficial, sem caminho absoluto de usuário, e apontam para scripts existentes', () => {
  const handlers = [
    ...settings.hooks.SessionStart.flatMap((entry) => entry.hooks),
    ...settings.hooks.PreToolUse.flatMap((entry) => entry.hooks),
    ...settings.hooks.PostToolUse.flatMap((entry) => entry.hooks),
    ...settings.hooks.Stop.flatMap((entry) => entry.hooks),
  ];

  for (const handler of handlers) {
    assert.equal(handler.type, 'command');
    assert.equal(typeof handler.command, 'string');
    assert.equal(handler.args, undefined, 'handler não deve usar "args" separado do "command"');
    assert.doesNotMatch(handler.command, /[A-Za-z]:[\\/]Users[\\/]/, 'command não deve conter caminho absoluto específico de usuário');
    assert.match(handler.command, /^node "\$\{CLAUDE_PROJECT_DIR\}\/\.claude\/hooks\/[\w-]+\.mjs"(?: [\w-]+)?$/);

    const relative = handler.command
      .replace(/^node "/, '')
      .replace(/"(?: [\w-]+)?$/, '')
      .replace('${CLAUDE_PROJECT_DIR}/.claude/', '');
    assert.doesNotThrow(() => readFileSync(path.resolve(projectClaudeDir, relative), 'utf8'));
  }
});
