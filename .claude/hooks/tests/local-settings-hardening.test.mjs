import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const projectRoot = path.resolve(here, '../../..');

function readJson(relative) {
  return JSON.parse(readFileSync(path.join(projectRoot, relative), 'utf8'));
}

test('settings locais não concedem instalação arbitrária nem acesso ao ~/.claude do usuário', () => {
  for (const relative of [
    '.claude/settings.local.json',
    'backend/evento-celebrativo-api/.claude/settings.local.json',
    'frontend-web/evento-celebrativo-web/.claude/settings.local.json',
  ]) {
    const absolute = path.join(projectRoot, relative);
    if (!existsSync(absolute)) continue;

    const settings = readJson(relative);
    const permissions = settings.permissions?.allow ?? [];
    const serialized = permissions.join('\n');
    assert.doesNotMatch(serialized, /winget\s+install\s+\*/i, relative);
    assert.doesNotMatch(serialized, /Users[\\/]+TI[\\/]+\.claude/i, relative);

    assert.doesNotMatch(serialized, /(?<![A-Za-z])[A-Za-z]:[\\/]/, `${relative}: caminho absoluto Windows`);
    assert.doesNotMatch(serialized, /\/Users\/[^/]+\//i, `${relative}: caminho absoluto macOS`);
    assert.doesNotMatch(serialized, /\/home\/[^/]+\//i, `${relative}: caminho absoluto Linux`);
    assert.doesNotMatch(serialized, /taskkill\b[^\n]*\/{1,2}PID\s+\d+\b/i, `${relative}: taskkill preso a PID numérico`);
  }
});
