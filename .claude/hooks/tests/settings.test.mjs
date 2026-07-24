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

test('hooks usam exec form e apontam para scripts existentes', () => {
  const handlers = [
    ...settings.hooks.PreToolUse.flatMap((entry) => entry.hooks),
    ...settings.hooks.PostToolUse.flatMap((entry) => entry.hooks),
  ];

  for (const handler of handlers) {
    assert.equal(handler.type, 'command');
    assert.equal(handler.command, 'node');
    assert.ok(Array.isArray(handler.args));
    assert.equal(handler.args.length, 1);

    const relative = handler.args[0].replace('${CLAUDE_PROJECT_DIR}/.claude/', '');
    assert.doesNotThrow(() => readFileSync(path.resolve(projectClaudeDir, relative), 'utf8'));
  }
});
