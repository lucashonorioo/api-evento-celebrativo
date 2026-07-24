import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import test from 'node:test';
import {
  evaluateFileWrite,
  evaluateHookPayload,
  evaluateShellCommand,
  isPathTrackedByGit,
  isProtectedEnvFile,
  normalizePath,
} from '../pre-tool-guard.mjs';

test('normaliza caminhos Windows', () => {
  assert.equal(normalizePath('"backend\\src\\main\\App.java"'), 'backend/src/main/App.java');
});

test('bloqueia comandos Git destrutivos e permite inspeção', () => {
  assert.match(evaluateShellCommand('git reset --hard HEAD~1'), /descartar/);
  assert.match(evaluateShellCommand('git push origin main --force-with-lease'), /Force push/);
  assert.equal(evaluateShellCommand('git status --short'), null);
});

test('bloqueia git checkout de descarte em massa com ou sem referência explícita', () => {
  assert.match(evaluateShellCommand('git checkout -- .'), /descartar/);
  assert.match(evaluateShellCommand('git checkout HEAD -- .'), /descartar/);
  assert.match(evaluateShellCommand('git checkout main -- .'), /descartar/);
  assert.match(evaluateShellCommand('git checkout HEAD -- *'), /descartar/);
  assert.match(evaluateShellCommand('git checkout HEAD -- :/'), /descartar/);
});

test('permite git checkout legítimo de branch ou arquivo específico', () => {
  assert.equal(evaluateShellCommand('git checkout main'), null);
  assert.equal(evaluateShellCommand('git checkout -b feature/nova-tarefa'), null);
  assert.equal(evaluateShellCommand('git checkout -- src/App.java'), null);
  assert.equal(evaluateShellCommand('git checkout HEAD -- src/App.java'), null);
});

test('bloqueia git restore de descarte em massa, com ou sem flags', () => {
  assert.match(evaluateShellCommand('git restore .'), /descartar/);
  assert.match(evaluateShellCommand('git restore *'), /descartar/);
  assert.match(evaluateShellCommand('git restore --worktree .'), /descartar/);
  assert.match(evaluateShellCommand('git restore --staged --worktree .'), /descartar/);
});

test('permite git restore de arquivo específico', () => {
  assert.equal(evaluateShellCommand('git restore src/App.java'), null);
  assert.equal(evaluateShellCommand('git restore --staged src/App.java'), null);
});

test('bloqueia rm -rf de raiz absoluta com glob e variantes equivalentes', () => {
  assert.match(evaluateShellCommand('rm -rf /*'), /Remoção recursiva/);
  assert.match(evaluateShellCommand('rm -rf /'), /Remoção recursiva/);
  assert.match(evaluateShellCommand('rm -rf ~'), /Remoção recursiva/);
  assert.match(evaluateShellCommand('rm -rf ~/'), /Remoção recursiva/);
  assert.match(evaluateShellCommand('rm -rf .'), /Remoção recursiva/);
  assert.match(evaluateShellCommand('rm -rf ./'), /Remoção recursiva/);
  assert.match(evaluateShellCommand('rm -rf *'), /Remoção recursiva/);
});

test('permite rm -rf restrito a diretórios legítimos do projeto', () => {
  assert.equal(evaluateShellCommand('rm -rf node_modules'), null);
  assert.equal(evaluateShellCommand('rm -rf ./build'), null);
  assert.equal(evaluateShellCommand('rm -rf dist/'), null);
  assert.equal(evaluateShellCommand('rm -rf target/classes'), null);
});

test('avalia comandos da ferramenta PowerShell', () => {
  const reason = evaluateHookPayload({
    tool_name: 'PowerShell',
    tool_input: { command: 'Remove-Item -Recurse -Force C:\\' },
  });
  assert.match(reason, /bloquead/);
});

test('protege .env real e permite exemplos', () => {
  assert.equal(isProtectedEnvFile('frontend/.env'), true);
  assert.equal(isProtectedEnvFile('frontend/.env.production'), true);
  assert.equal(isProtectedEnvFile('frontend/.env.example'), false);
  assert.equal(isProtectedEnvFile('frontend/.env.sample'), false);
});

test('bloqueia edição de migration Flyway já rastreada pelo git', () => {
  const reason = evaluateFileWrite({
    toolName: 'Edit',
    filePath: 'backend/src/main/resources/db/migration/V1__init.sql',
    cwd: '/repo',
    projectDir: '/repo',
    isTracked: () => true,
  });
  assert.match(reason, /nova migration incremental/);
});

test('permite criação de nova migration untracked com Write', () => {
  const reason = evaluateFileWrite({
    toolName: 'Write',
    filePath: 'backend/src/main/resources/db/migration/V99__new_change.sql',
    cwd: '/repo',
    projectDir: '/repo',
    isTracked: () => false,
  });
  assert.equal(reason, null);
});

test('permite Edit em migration recém-criada e ainda untracked', () => {
  const reason = evaluateFileWrite({
    toolName: 'Edit',
    filePath: 'backend/src/main/resources/db/migration/V99__new_change.sql',
    cwd: '/repo',
    projectDir: '/repo',
    isTracked: () => false,
  });
  assert.equal(reason, null);
});

test('bloqueia Write que sobrescreveria migration já rastreada, mesmo sem Edit', () => {
  const reason = evaluateFileWrite({
    toolName: 'Write',
    filePath: 'backend/src/main/resources/db/migration/V1__init.sql',
    cwd: '/repo',
    projectDir: '/repo',
    isTracked: () => true,
  });
  assert.match(reason, /nova migration incremental/);
});

test('isPathTrackedByGit distingue arquivo rastreado, untracked e inexistente em repositório real', () => {
  const repository = mkdtempSync(path.join(tmpdir(), 'claude-hook-guard-test-'));
  execFileSync('git', ['init', '-q', repository]);
  execFileSync('git', ['-C', repository, 'config', 'user.email', 'test@example.com']);
  execFileSync('git', ['-C', repository, 'config', 'user.name', 'Hook Test']);

  const migrationDir = path.join(repository, 'src/main/resources/db/migration');
  mkdirSync(migrationDir, { recursive: true });

  const trackedFile = path.join(migrationDir, 'V1__init.sql');
  writeFileSync(trackedFile, 'CREATE TABLE t (id INT);\n');
  execFileSync('git', ['-C', repository, 'add', '.']);
  execFileSync('git', ['-C', repository, 'commit', '-q', '-m', 'initial']);

  const untrackedFile = path.join(migrationDir, 'V2__new.sql');
  writeFileSync(untrackedFile, 'CREATE TABLE t2 (id INT);\n');

  const missingFile = path.join(migrationDir, 'V3__missing.sql');

  assert.equal(isPathTrackedByGit(trackedFile, repository), true);
  assert.equal(isPathTrackedByGit(untrackedFile, repository), false);
  assert.equal(isPathTrackedByGit(missingFile, repository), false);
});

test('evaluateFileWrite com implementação real de isTracked bloqueia migration commitada e libera migration nova', () => {
  const repository = mkdtempSync(path.join(tmpdir(), 'claude-hook-guard-test-'));
  execFileSync('git', ['init', '-q', repository]);
  execFileSync('git', ['-C', repository, 'config', 'user.email', 'test@example.com']);
  execFileSync('git', ['-C', repository, 'config', 'user.name', 'Hook Test']);

  const migrationDir = path.join(repository, 'src/main/resources/db/migration');
  mkdirSync(migrationDir, { recursive: true });
  writeFileSync(path.join(migrationDir, 'V1__init.sql'), 'CREATE TABLE t (id INT);\n');
  execFileSync('git', ['-C', repository, 'add', '.']);
  execFileSync('git', ['-C', repository, 'commit', '-q', '-m', 'initial']);
  writeFileSync(path.join(migrationDir, 'V2__new.sql'), 'CREATE TABLE t2 (id INT);\n');

  const blocked = evaluateFileWrite({
    toolName: 'Edit',
    filePath: 'src/main/resources/db/migration/V1__init.sql',
    cwd: repository,
    projectDir: repository,
  });
  assert.match(blocked, /nova migration incremental/);

  const allowed = evaluateFileWrite({
    toolName: 'Edit',
    filePath: 'src/main/resources/db/migration/V2__new.sql',
    cwd: repository,
    projectDir: repository,
  });
  assert.equal(allowed, null);
});

test('bloqueia travessia e escrita fora do workspace', () => {
  assert.match(evaluateFileWrite({
    toolName: 'Write',
    filePath: '../outside.txt',
    cwd: '/repo',
    projectDir: '/repo',
  }), /travessia/);

  assert.match(evaluateFileWrite({
    toolName: 'Write',
    filePath: '/tmp/outside.txt',
    cwd: '/repo',
    projectDir: '/repo',
  }), /fora do workspace/);
});

test('avalia payload real de Edit pelo file_path', () => {
  const reason = evaluateHookPayload({
    tool_name: 'Edit',
    tool_input: { file_path: 'secrets/id_rsa' },
    cwd: '/repo',
  }, { projectDir: '/repo' });
  assert.match(reason, /sensível/);
});
