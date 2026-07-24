import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { buildHookOutput, runDiffCheck } from '../post-edit-check.mjs';

function createRepository() {
  const directory = mkdtempSync(path.join(tmpdir(), 'claude-hook-test-'));
  execFileSync('git', ['init', '-q', directory]);
  execFileSync('git', ['-C', directory, 'config', 'user.email', 'test@example.com']);
  execFileSync('git', ['-C', directory, 'config', 'user.name', 'Hook Test']);
  writeFileSync(path.join(directory, 'file.txt'), 'ok\n');
  execFileSync('git', ['-C', directory, 'add', 'file.txt']);
  execFileSync('git', ['-C', directory, 'commit', '-q', '-m', 'initial']);
  return directory;
}

test('retorna null quando o diff está limpo', () => {
  const repository = createRepository();
  writeFileSync(path.join(repository, 'file.txt'), 'alterado\n');
  assert.equal(runDiffCheck(repository), null);
});

test('detecta whitespace inválido e cria contexto adicional', () => {
  const repository = createRepository();
  writeFileSync(path.join(repository, 'file.txt'), 'alterado   \n');
  const issue = runDiffCheck(repository);
  assert.match(issue, /trailing whitespace/i);

  const output = buildHookOutput(issue);
  assert.equal(output.hookSpecificOutput.hookEventName, 'PostToolUse');
  assert.match(output.hookSpecificOutput.additionalContext, /whitespace/);
});
