import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { execFileSync } from 'node:child_process';
import { runDiffCheck } from '../post-edit-check.mjs';

function createRepo() {
  const dir = mkdtempSync(join(tmpdir(), 'codex-hook-test-'));
  execFileSync('git', ['init'], { cwd: dir, stdio: 'ignore' });
  execFileSync('git', ['config', 'user.email', 'test@example.com'], { cwd: dir });
  execFileSync('git', ['config', 'user.name', 'Test'], { cwd: dir });
  writeFileSync(join(dir, 'file.txt'), 'ok\n');
  execFileSync('git', ['add', 'file.txt'], { cwd: dir });
  execFileSync('git', ['commit', '-m', 'initial'], { cwd: dir, stdio: 'ignore' });
  return dir;
}

test('returns null for a clean diff', () => {
  const repo = createRepo();
  writeFileSync(join(repo, 'file.txt'), 'changed\n');
  assert.equal(runDiffCheck(repo), null);
});

test('returns details for trailing whitespace', () => {
  const repo = createRepo();
  writeFileSync(join(repo, 'file.txt'), 'changed   \n');
  assert.match(runDiffCheck(repo), /trailing whitespace/i);
});

test('ignores directories that are not git repositories', () => {
  const dir = mkdtempSync(join(tmpdir(), 'codex-hook-non-git-'));
  assert.equal(runDiffCheck(dir), null);
});
