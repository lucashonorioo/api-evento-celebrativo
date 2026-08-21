import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const settingsPath = path.resolve(here, '../../settings.json');
const projectClaudeDir = path.resolve(here, '../..');
const settings = JSON.parse(readFileSync(settingsPath, 'utf8'));

test('settings registra os eventos e matchers esperados, incluindo lifecycle de subagents', () => {
  assert.ok(Array.isArray(settings.hooks.SessionStart));
  assert.ok(Array.isArray(settings.hooks.PreToolUse));
  assert.ok(Array.isArray(settings.hooks.PostToolUse));
  assert.ok(Array.isArray(settings.hooks.SubagentStart));
  assert.ok(Array.isArray(settings.hooks.SubagentStop));
  assert.ok(Array.isArray(settings.hooks.Stop));

  assert.equal(settings.hooks.SessionStart.length, 1);
  assert.equal(settings.hooks.SubagentStart.length, 1);
  assert.equal(settings.hooks.SubagentStop.length, 1);
  assert.equal(settings.hooks.Stop.length, 1);

  const preMatchers = settings.hooks.PreToolUse.map((entry) => entry.matcher);
  assert.deepEqual(preMatchers, ['Bash|PowerShell', 'Edit|Write', 'Bash|Grep', 'Read|Glob']);
  assert.equal(settings.hooks.PostToolUse[0].matcher, 'Edit|Write');
  assert.equal(settings.hooks.SubagentStart[0].matcher, undefined);
  assert.equal(settings.hooks.SubagentStop[0].matcher, undefined);
});

test('SubagentStart/Stop persistem resultados pelo engineering-review-gate', () => {
  const start = settings.hooks.SubagentStart[0].hooks[0].command;
  const stop = settings.hooks.SubagentStop[0].hooks[0].command;
  assert.match(start, /engineering-review-gate\.mjs" --subagent-start$/);
  assert.match(stop, /engineering-review-gate\.mjs" --subagent-stop$/);
});

test('hooks command usam formato oficial, sem caminho absoluto de usuário, e apontam para scripts existentes', () => {
  const hookGroups = [
    settings.hooks.SessionStart,
    settings.hooks.PreToolUse,
    settings.hooks.PostToolUse,
    settings.hooks.SubagentStart,
    settings.hooks.SubagentStop,
    settings.hooks.Stop,
  ];
  const handlers = hookGroups.flatMap((entries) => entries.flatMap((entry) => entry.hooks));

  for (const handler of handlers) {
    assert.equal(handler.type, 'command');
    assert.equal(typeof handler.command, 'string');
    assert.equal(handler.args, undefined, 'handler não deve usar "args" separado do "command"');
    assert.doesNotMatch(handler.command, /[A-Za-z]:[\\/]Users[\\/]/, 'command não deve conter caminho absoluto específico de usuário');
    assert.match(handler.command, /^node "\$\{CLAUDE_PROJECT_DIR\}\/\.claude\/hooks\/[\w-]+\.mjs"(?: --?[\w-]+| [\w-]+)?$/);

    const relative = handler.command
      .replace(/^node "/, '')
      .replace(/"(?: --?[\w-]+| [\w-]+)?$/, '')
      .replace('${CLAUDE_PROJECT_DIR}/.claude/', '');
    assert.doesNotThrow(() => readFileSync(path.resolve(projectClaudeDir, relative), 'utf8'));
  }
});
