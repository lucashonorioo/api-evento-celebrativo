import assert from 'node:assert/strict';
import test from 'node:test';
import {
  evaluateFileWrite,
  evaluateHookPayload,
  evaluateShellCommand,
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

test('bloqueia edição de migration Flyway versionada', () => {
  const reason = evaluateFileWrite({
    toolName: 'Edit',
    filePath: 'backend/src/main/resources/db/migration/V1__init.sql',
    cwd: '/repo',
    projectDir: '/repo',
    pathExists: () => true,
  });
  assert.match(reason, /nova migration incremental/);
});

test('permite criação de nova migration com Write', () => {
  const reason = evaluateFileWrite({
    toolName: 'Write',
    filePath: 'backend/src/main/resources/db/migration/V99__new_change.sql',
    cwd: '/repo',
    projectDir: '/repo',
    pathExists: () => false,
  });
  assert.equal(reason, null);
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
