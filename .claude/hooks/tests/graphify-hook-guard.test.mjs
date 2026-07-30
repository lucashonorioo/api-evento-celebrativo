import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const scriptPath = path.resolve(here, '../graphify-hook-guard.mjs');

function runHook({ mode, input, path: pathOverride }) {
  // Usa o binário do node pelo caminho absoluto (process.execPath) para que a
  // ausência de PATH simulada abaixo afete apenas a resolução do graphify
  // dentro do script, não a do próprio node ao spawnar o subprocesso.
  return execFileSync(process.execPath, [scriptPath, mode], {
    input,
    encoding: 'utf8',
    env: pathOverride === undefined ? process.env : { ...process.env, PATH: pathOverride, Path: pathOverride },
  });
}

test('não bloqueia quando graphify não está no PATH (fail-open)', () => {
  const stdout = runHook({ mode: 'read', input: '{}', path: '' });
  assert.equal(stdout, '');
});

test('ignora modo desconhecido sem erro', () => {
  const stdout = runHook({ mode: 'unknown', input: '{}' });
  assert.equal(stdout, '');
});
