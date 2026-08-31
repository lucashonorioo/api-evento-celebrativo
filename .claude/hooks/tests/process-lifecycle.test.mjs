import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const projectRoot = path.resolve(here, '../../..');

function read(relative) {
  return readFileSync(path.join(projectRoot, relative), 'utf8');
}

test('política de lifecycle encerra somente processos iniciados pelo agente', () => {
  const lifecycle = read('.ai/runtime/PROCESS_LIFECYCLE.md');

  assert.match(lifecycle, /nunca deve matar processos preexistentes do usuário/i);
  assert.match(lifecycle, /Start-Process -PassThru/i);
  assert.match(lifecycle, /preserve o PID/i);
  assert.match(lifecycle, /taskkill \/PID \$agentBackendPid\s+\/T \/F/i);
  assert.match(lifecycle, /taskkill \/PID \$agentFrontendPid\s+\/T \/F/i);
  assert.match(lifecycle, /Proibido:[\s\S]{0,400}taskkill \/IM java\.exe[\s\S]{0,400}Stop-Process -Name java\/node/i);
  assert.match(lifecycle, /try\/finally/i);
});

test('instruções principais encaminham processos longos para a política de lifecycle', () => {
  for (const relative of [
    'AGENTS.md',
    'CLAUDE.md',
    'backend/evento-celebrativo-api/AGENTS.md',
    'backend/evento-celebrativo-api/CLAUDE.md',
    'frontend-web/evento-celebrativo-web/AGENTS.md',
    'frontend-web/evento-celebrativo-web/CLAUDE.md',
  ]) {
    assert.match(read(relative), /\.ai\/runtime\/PROCESS_LIFECYCLE\.md/, relative);
  }
});

test('settings locais presentes não mantêm kill global nem taskkill preso a PID numérico', () => {
  for (const relative of [
    '.claude/settings.local.json',
    'backend/evento-celebrativo-api/.claude/settings.local.json',
    'frontend-web/evento-celebrativo-web/.claude/settings.local.json',
  ]) {
    const absolute = path.join(projectRoot, relative);
    if (!existsSync(absolute)) continue;

    const settings = JSON.parse(readFileSync(absolute, 'utf8'));
    const serialized = (settings.permissions?.allow ?? []).join('\n');
    assert.doesNotMatch(serialized, /taskkill\b[^\n]*\/{1,2}IM\s+(?:java|node)(?:\.exe)?/i, relative);
    assert.doesNotMatch(serialized, /Stop-Process\b[^\n]*-Name\s+(?:java|node)\b/i, relative);
    assert.doesNotMatch(serialized, /taskkill\b[^\n]*\/{1,2}PID\s+\d+\b/i, relative);
  }
});
