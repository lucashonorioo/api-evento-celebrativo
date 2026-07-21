import test from 'node:test';
import assert from 'node:assert/strict';
import {
  evaluateHookPayload,
  evaluatePatch,
  evaluateShellCommand,
  extractPatchEntries,
} from '../pre-tool-guard.mjs';

test('allows normal read-only git commands', () => {
  assert.equal(evaluateShellCommand('git status --short'), null);
  assert.equal(evaluateShellCommand('git diff --check'), null);
});

test('blocks destructive git commands', () => {
  assert.match(evaluateShellCommand('git reset --hard HEAD~1'), /descartar/i);
  assert.match(evaluateShellCommand('git clean -fdx'), /remover/i);
  assert.match(evaluateShellCommand('git push --force origin main'), /force push/i);
});

test('extracts patch paths', () => {
  const entries = extractPatchEntries(`*** Begin Patch\n*** Update File: src/app/test.ts\n*** Add File: src/app/new.ts\n*** End Patch`);
  assert.deepEqual(entries, [
    { operation: 'update', path: 'src/app/test.ts' },
    { operation: 'add', path: 'src/app/new.ts' },
  ]);
});

test('blocks sensitive files but allows examples', () => {
  assert.match(evaluatePatch('*** Begin Patch\n*** Update File: .env.local\n*** End Patch'), /\.env/i);
  assert.equal(evaluatePatch('*** Begin Patch\n*** Add File: .env.example\n*** End Patch'), null);
  assert.match(evaluatePatch('*** Begin Patch\n*** Add File: certs/private.key\n*** End Patch'), /chave/i);
});

test('blocks mutation of existing Flyway migrations', () => {
  const update = '*** Begin Patch\n*** Update File: src/main/resources/db/migration/V2__create_table.sql\n*** End Patch';
  const add = '*** Begin Patch\n*** Add File: src/main/resources/db/migration/V99__new_change.sql\n*** End Patch';
  assert.match(evaluatePatch(update), /Migration Flyway/i);
  assert.equal(evaluatePatch(add), null);
});

test('evaluates canonical hook payloads', () => {
  const reason = evaluateHookPayload({
    tool_name: 'Bash',
    tool_input: { command: 'git branch -D feature/test' },
  });
  assert.match(reason, /branch/i);
});
