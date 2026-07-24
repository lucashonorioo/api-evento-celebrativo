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
  assert.ok(Array.isArray(settings.hooks.PreToolUse));
  assert.ok(Array.isArray(settings.hooks.PostToolUse));

  const preMatchers = settings.hooks.PreToolUse.map((entry) => entry.matcher);
  assert.deepEqual(preMatchers, ['Bash|PowerShell', 'Edit|Write']);
  assert.equal(settings.hooks.PostToolUse[0].matcher, 'Edit|Write');
});

test('hooks usam command como string única (formato oficial) e apontam para scripts existentes', () => {
  const handlers = [
    ...settings.hooks.PreToolUse.flatMap((entry) => entry.hooks),
    ...settings.hooks.PostToolUse.flatMap((entry) => entry.hooks),
  ];

  for (const handler of handlers) {
    assert.equal(handler.type, 'command');
    assert.equal(typeof handler.command, 'string');
    assert.equal(handler.args, undefined, 'handler não deve usar "args" separado do "command" (fora do schema oficial de hooks)');
    assert.match(handler.command, /^node "\$\{CLAUDE_PROJECT_DIR\}\/\.claude\/hooks\/[\w-]+\.mjs"$/);

    const relative = handler.command
      .replace(/^node "/, '')
      .replace(/"$/, '')
      .replace('${CLAUDE_PROJECT_DIR}/.claude/', '');
    assert.doesNotThrow(() => readFileSync(path.resolve(projectClaudeDir, relative), 'utf8'));
  }
});
