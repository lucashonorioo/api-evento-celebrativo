import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { copyFileSync, mkdirSync, mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
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

test('bloqueia travessia por segmento ".." como sanitização de entrada', () => {
  assert.match(evaluateFileWrite({
    toolName: 'Write',
    filePath: '../outside.txt',
    cwd: '/repo',
    projectDir: '/repo',
  }), /travessia/);
});

// Política revisada (ver ENGINEERING_REVIEW.md, F8): o guard não é um sandbox
// de filesystem. Escrita fora do projeto atual é permitida por padrão — a
// mesma tarefa pode legitimamente precisar escrever fixtures/relatórios em
// diretório temporário do SO, e a rota Bash nunca teve essa restrição, então
// mantê-la só em Edit/Write era uma assimetria sem ganho real de segurança
// (contornável trocando de ferramenta). Arquivos sensíveis continuam
// protegidos em qualquer local, dentro ou fora do projeto (ver teste abaixo).
test('permite escrita fora do projeto quando o destino não é sensível', () => {
  assert.equal(evaluateFileWrite({
    toolName: 'Write',
    filePath: '/tmp/outside.txt',
    cwd: '/repo',
    projectDir: '/repo',
  }), null);
  assert.equal(evaluateFileWrite({
    toolName: 'Write',
    filePath: 'C:/Users/tester/AppData/Local/Temp/relatorio.html',
    cwd: '/repo',
    projectDir: '/repo',
  }), null);
});

test('protege arquivo sensível mesmo fora do projeto atual', () => {
  assert.match(evaluateFileWrite({
    toolName: 'Write',
    filePath: '/tmp/.env',
    cwd: '/repo',
    projectDir: '/repo',
  }), /ambiente real/);
  assert.match(evaluateFileWrite({
    toolName: 'Write',
    filePath: '/tmp/secrets/id_rsa',
    cwd: '/repo',
    projectDir: '/repo',
  }), /sensível/);
});

test('Edit/Write e Bash aplicam exatamente a mesma política de workspace (sem assimetria)', () => {
  const editReason = evaluateHookPayload({
    tool_name: 'Write',
    tool_input: { file_path: '/tmp/relatorio-auditoria.txt' },
    cwd: '/repo',
  }, { projectDir: '/repo' });
  const bashReason = evaluateShellCommand('echo dados > /tmp/relatorio-auditoria.txt', {
    cwd: '/repo', projectDir: '/repo',
  });
  assert.equal(editReason, null);
  assert.equal(bashReason, null);
});

test('avalia payload real de Edit pelo file_path', () => {
  const reason = evaluateHookPayload({
    tool_name: 'Edit',
    tool_input: { file_path: 'secrets/id_rsa' },
    cwd: '/repo',
  }, { projectDir: '/repo' });
  assert.match(reason, /sensível/);
});

test('bloqueia variantes válidas do Git com opções globais e git.exe', () => {
  assert.match(evaluateShellCommand('git -C . reset --hard HEAD'), /descartar/);
  assert.match(evaluateShellCommand('git -c core.autocrlf=false reset --hard HEAD'), /descartar/);
  assert.match(evaluateShellCommand('git.exe -C . clean -fd'), /remover/);
  assert.match(evaluateShellCommand('"C:/Program Files/Git/bin/git.exe" -C . reset --hard HEAD'), /descartar/);
  assert.equal(evaluateShellCommand('git -C . status --short'), null);
});

test('proteção de arquivos sensíveis também cobre escritas comuns via shell', () => {
  const options = { cwd: '/repo', projectDir: '/repo', isTracked: () => true };
  assert.match(evaluateShellCommand('echo SECRET=123 > .env', options), /ambiente real/);
  assert.equal(evaluateShellCommand('echo EXAMPLE=1 > .env.example', options), null);
  assert.match(evaluateShellCommand('echo x > .git/config', options), /\.git/);
  assert.match(evaluateShellCommand('sed -i s/a/b/ src/main/resources/db/migration/V1__init.sql', options), /Migration Flyway/);
  assert.equal(evaluateShellCommand('cat .env', options), null, 'leitura sem mutação não deve ser falsamente bloqueada');
});

test('shell permite editar migration nova enquanto untracked e bloqueia a mesma path quando tracked', () => {
  const command = 'sed -i s/a/b/ src/main/resources/db/migration/V99__new.sql';
  assert.equal(evaluateShellCommand(command, { cwd: '/repo', projectDir: '/repo', isTracked: () => false }), null);
  assert.match(evaluateShellCommand(command, { cwd: '/repo', projectDir: '/repo', isTracked: () => true }), /Migration Flyway/);
});

test('bloqueia Git destrutivo dentro de wrappers e aliases comuns de shell', () => {
  for (const command of [
    'bash -c "git reset --hard HEAD"',
    'sh -c "git clean -fd"',
    'cmd /c "git reset --hard HEAD"',
    'cmd.exe /c "git push --force origin main"',
    'powershell -Command "git reset --hard HEAD"',
    'powershell.exe -c "git clean -fd"',
    'pwsh -Command "git push --force origin main"',
    'Invoke-Expression "git reset --hard HEAD"',
    'iex "git clean -fd"',
    'eval "git push --force origin main"',
    'G=git; $G reset --hard HEAD',
    "$G='git'; $G reset --hard HEAD",
    'git$(echo) reset --hard HEAD',
  ]) {
    assert.notEqual(evaluateShellCommand(command), null, command);
  }
});

test('detector de escrita sensível evita falso positivo em texto citado e cobre APIs de script', () => {
  const options = { cwd: '/repo', projectDir: '/repo', isTracked: () => true };
  assert.equal(evaluateShellCommand('grep "foo > \\.env" README.md', options), null);
  assert.equal(evaluateShellCommand('echo "Set-Content .env"', options), null);
  assert.match(evaluateShellCommand('node -e "require(\'fs\').writeFileSync(\'.env\', \'x\')"', options), /ambiente real/);
  assert.match(evaluateShellCommand('python -c "open(\'.git/config\', \'w\').write(\'x\')"', options), /\.git/);
});

test('migration nova staged mas ainda não commitada continua editável; após commit fica protegida', () => {
  const repository = mkdtempSync(path.join(tmpdir(), 'migration-stage-policy-'));
  execFileSync('git', ['init', '-q', repository]);
  execFileSync('git', ['-C', repository, 'config', 'user.email', 'test@example.com']);
  execFileSync('git', ['-C', repository, 'config', 'user.name', 'Hook Test']);
  writeFileSync(path.join(repository, 'README.md'), 'base\n');
  execFileSync('git', ['-C', repository, 'add', '.']);
  execFileSync('git', ['-C', repository, 'commit', '-q', '-m', 'initial']);

  const migrationDir = path.join(repository, 'src/main/resources/db/migration');
  mkdirSync(migrationDir, { recursive: true });
  const migration = path.join(migrationDir, 'V99__new.sql');
  writeFileSync(migration, 'CREATE TABLE x(id INT);\n');
  execFileSync('git', ['-C', repository, 'add', migration]);

  assert.equal(isPathTrackedByGit(migration, repository), false, 'staged não deve significar versionada em HEAD');
  assert.equal(evaluateFileWrite({ filePath: migration, cwd: repository, projectDir: repository }), null);

  execFileSync('git', ['-C', repository, 'commit', '-q', '-m', 'add migration']);
  assert.equal(isPathTrackedByGit(migration, repository), true);
  assert.match(evaluateFileWrite({ filePath: migration, cwd: repository, projectDir: repository }), /nova migration incremental/);
});

test('hook Claude falha fechado se o payload de segurança for inválido', async () => {
  const { spawnSync } = await import('node:child_process');
  const { fileURLToPath } = await import('node:url');
  const script = fileURLToPath(new URL('../pre-tool-guard.mjs', import.meta.url));
  const result = spawnSync(process.execPath, [script], {
    input: '{payload-json-invalido', encoding: 'utf8',
  });
  assert.equal(result.status, 2);
  assert.match(result.stderr, /operação bloqueada por segurança/i);
});

test('hook Claude resolve raiz Git quando cwd está em subpasta', () => {
  const repository = mkdtempSync(path.join(tmpdir(), 'claude-guard-root-'));
  mkdirSync(path.join(repository, 'nested'), { recursive: true });
  execFileSync('git', ['init'], { cwd: repository, stdio: 'ignore' });
  const reason = evaluateHookPayload({
    tool_name: 'Write', cwd: path.join(repository, 'nested'), tool_input: { file_path: path.join(repository, '.env') },
  });
  assert.match(reason, /ambiente real/i);
});

test('bloqueia aliases nativos, xargs, variantes longas de branch e wrappers remotos para Git destrutivo', () => {
  for (const command of [
    'alias gg=git; gg reset --hard HEAD',
    "$env:G='git'; & $env:G reset --hard HEAD",
    'set G=git && %G% reset --hard HEAD',
    'echo reset --hard HEAD | xargs git',
    'git branch --delete --force feature/test',
    'git branch -d -f feature/test',
    'git branch -fd feature/test',
    'ssh host "git reset --hard HEAD"',
    'docker exec app git push --force origin main',
  ]) {
    assert.notEqual(evaluateShellCommand(command), null, command);
  }
});

test('proteção sensível cobre shutil e API .NET do PowerShell sem bloquear arrow function ou texto citado', () => {
  const options = { cwd: '/repo', projectDir: '/repo', isTracked: () => true };
  assert.match(evaluateShellCommand("python -c \"import shutil; shutil.copy('src','.env')\"", options), /ambiente real/);
  assert.match(evaluateShellCommand("powershell -Command \"[System.IO.File]::WriteAllText('.env','x')\"", options), /ambiente real/);
  assert.equal(evaluateShellCommand("node -e \"const f=(x)=>x; console.log('.env')\"", options), null);
  assert.equal(evaluateShellCommand('grep "=> .env" README.md', options), null);
});

test('stdin vazio e payload semanticamente incompleto falham fechado no hook Claude', async () => {
  const { spawnSync } = await import('node:child_process');
  const { fileURLToPath } = await import('node:url');
  const script = fileURLToPath(new URL('../pre-tool-guard.mjs', import.meta.url));
  for (const input of ['', '{}', 'null', '[]']) {
    const result = spawnSync(process.execPath, [script], { input, encoding: 'utf8' });
    assert.equal(result.status, 2, JSON.stringify({ input, stderr: result.stderr }));
    assert.match(result.stderr, /bloqueada por segurança/i);
  }
});

test('bloqueia aliases Git inline/configurados, EncodedCommand e pathspecs destrutivos em massa', () => {
  for (const command of [
    'git -c alias.wipe="reset --hard" wipe',
    'git config alias.wipe "reset --hard"',
    'powershell -EncodedCommand ZwBpAHQAIAByAGUAcwBlAHQAIAAtAC0AaABhAHIAZAA=',
    'git checkout -- src/*',
    'git restore src/**',
  ]) {
    assert.notEqual(evaluateShellCommand(command), null, command);
  }
});

test('resolve alias Git já configurado antes de permitir subcomando desconhecido', () => {
  const repository = mkdtempSync(path.join(tmpdir(), 'git-alias-policy-'));
  execFileSync('git', ['init', '-q', repository]);
  execFileSync('git', ['-C', repository, 'config', 'alias.wipe', 'reset --hard']);
  assert.match(evaluateShellCommand('git wipe HEAD', { cwd: repository, projectDir: repository }), /Alias Git wipe|reset --hard/);
});

test('concatenação simples de variáveis não esconde arquivo sensível e leitura textual permanece permitida', () => {
  const options = { cwd: '/repo', projectDir: '/repo', isTracked: () => true };
  assert.match(evaluateShellCommand('p1=.e; p2=nv; echo SECRET=1 > "$p1$p2"', options), /ambiente real/);
  assert.match(evaluateShellCommand("$p1='.e'; $p2='nv'; Set-Content \"$p1$p2\" x", options), /ambiente real/);
  assert.equal(evaluateShellCommand('echo "documentation: git reset --hard is dangerous"', options), null);
  assert.equal(evaluateShellCommand('grep "git push --force" README.md', options), null);
});

test('bloqueia fabricação direta de eventos lifecycle do Engineering Review Gate via shell', () => {
  for (const command of [
    'node .ai/review/engineering-review-gate.mjs --subagent-start',
    'node .claude/hooks/engineering-review-gate.mjs --subagent-stop',
    'node .codex/hooks/engineering-review-gate.mjs --session-start',
  ]) {
    assert.match(evaluateShellCommand(command), /lifecycle|Engineering Review Gate/i, command);
  }
  assert.equal(evaluateShellCommand('node .ai/review/engineering-review-gate.mjs --review-status - abc'), null);
});

test('bloqueia tentativa direta de importar funções lifecycle internas do gate', () => {
  const command = `node -e "import('./.ai/review/engineering-review-gate.mjs').then(m => m.recordReviewerStop({}))"`;
  assert.match(evaluateShellCommand(command), /lifecycle|provenance/i);
});

// --- F1: Start-Process reconstrói o argv efetivo em vez de depender de ---
// --- justaposição literal de palavras (regressão + variantes novas) -----
test('Start-Process/saps reconstrói o argv efetivo e bloqueia git destrutivo em qualquer forma suportada', () => {
  for (const command of [
    "Start-Process git -ArgumentList 'reset','--hard' -Wait",
    "Start-Process git.exe -ArgumentList 'reset','--hard'",
    'Start-Process "git" -ArgumentList \'reset\',\'--hard\'',
    "Start-Process -FilePath git -ArgumentList 'reset','--hard'",
    "Start-Process git -ArgumentList @('reset','--hard')",
    'Start-Process git -ArgumentList "reset --hard"',
    "saps git -ArgumentList 'clean','-fd'",
    "Start-Process git -ArgumentList 'push','--force'",
    "Start-Process git -ArgumentList 'branch','--delete','-f','feature/x'",
  ]) {
    assert.notEqual(evaluateShellCommand(command), null, command);
  }
});

test('Start-Process sem FilePath resolvível ainda bloqueia quando o ArgumentList já sugere Git destrutivo (fallback conservador)', () => {
  assert.notEqual(evaluateShellCommand("Start-Process $git -ArgumentList 'reset','--hard'"), null);
});

test('Start-Process não gera falso positivo para lançamentos comuns e inofensivos', () => {
  assert.equal(evaluateShellCommand("Start-Process npm -ArgumentList 'install'"), null);
  assert.equal(evaluateShellCommand('Start-Process notepad.exe'), null);
  assert.equal(evaluateShellCommand("Start-Process git -ArgumentList 'status'"), null);
  assert.equal(evaluateShellCommand("Start-Process git -ArgumentList 'log','--oneline'"), null);
});

// --- F3: DELETE e FORCE de `git branch` são predicados independentes -----
// --- (cobrem qualquer combinação curta/longa/agrupada), não uma lista ----
// --- fechada de combinações reconhecidas -----------------------------
test('bloqueia exclusão forçada de branch em qualquer combinação de flags curta/longa/agrupada', () => {
  for (const command of [
    'git branch --delete -f feature/x',
    'git branch -d --force feature/x',
    'git branch -d -f feature/x',
    'git branch -fd feature/x',
    'git branch -df feature/x',
    'git branch -D feature/x',
    'git branch --delete --force feature/x',
  ]) {
    assert.match(evaluateShellCommand(command), /Exclusão forçada/, command);
  }
});

test('não bloqueia exclusão simples (sem force) de branch', () => {
  assert.equal(evaluateShellCommand('git branch -d feature/x'), null);
  assert.equal(evaluateShellCommand('git branch --delete feature/x'), null);
});

// --- F4: ANSI-C quoting do bash ($'...'/$"...") não deve esconder o -----
// --- comando do tokenizador -----------------------------------------
test('reconhece Git destrutivo dentro de ANSI-C quoting do bash', () => {
  for (const command of [
    "bash -c $'git reset --hard'",
    'bash -c $"git reset --hard"',
    "sh -c $'git clean -fd'",
    "bash -c $'git push --force origin main'",
    "bash -c $'git reset \\t--hard'",
  ]) {
    assert.notEqual(evaluateShellCommand(command), null, command);
  }
  assert.equal(evaluateShellCommand("bash -c $'echo hello world'"), null);
});

// --- F5: correlação mutação -> destino real, não menção textual solta ---
test('heredoc que apenas menciona padrão sensível como documentação é permitido', () => {
  const options = { cwd: '/repo', projectDir: '/repo', isTracked: () => true };
  const command = [
    "cat > relatorio.md << 'EOF'",
    'Este relatorio documenta que .env, .git/config e "git reset --hard" sao padroes sensiveis.',
    'EOF',
  ].join('\n');
  assert.equal(evaluateShellCommand(command, options), null);
});

test('heredoc cujo destino real é um caminho protegido continua bloqueado', () => {
  const options = { cwd: '/repo', projectDir: '/repo', isTracked: () => true };
  const command = ["cat > .env << 'EOF'", 'SECRET=1', 'EOF'].join('\n');
  assert.match(evaluateShellCommand(command, options), /ambiente real/);
});

// Achados da auditoria adversarial pós-correção (seção 16): um segundo
// redirecionamento no mesmo comando, e um heredoc aninhado dentro de um
// argumento de wrapper ainda citado, escapavam da correlação de destino.
test('detecta o alvo sensível quando há mais de um redirecionamento no mesmo comando (achado da auditoria pós-correção)', () => {
  const options = { cwd: '/repo', projectDir: '/repo', isTracked: () => true };
  const command = [
    "cat > relatorio.md << 'EOF1'",
    'texto qualquer',
    'EOF1',
    "cat > .env << 'EOF2'",
    'SECRET=1',
    'EOF2',
  ].join('\n');
  assert.match(evaluateShellCommand(command, options), /ambiente real/);
});

test('detecta heredoc aninhado dentro do argumento ainda citado de um wrapper (achado da auditoria pós-correção)', () => {
  const options = { cwd: '/repo', projectDir: '/repo', isTracked: () => true };
  const inner = "cat > .env << 'EOF'\nSECRET=1\nEOF";
  assert.match(evaluateShellCommand(`bash -c "${inner}"`, options), /ambiente real/);
});

test('heredoc executado por um interpretador (bash/python) continua analisado como código', () => {
  const options = { cwd: '/repo', projectDir: '/repo', isTracked: () => true };
  assert.notEqual(evaluateShellCommand(["bash << 'EOF'", 'git reset --hard', 'EOF'].join('\n'), options), null);
  assert.match(
    evaluateShellCommand(["python << 'EOF'", "open('.env', 'w').write('x')", 'EOF'].join('\n'), options),
    /ambiente real/,
  );
});

test('menção a padrão sensível dentro de JSON/string escrita em destino não sensível é permitida', () => {
  const options = { cwd: '/repo', projectDir: '/repo', isTracked: () => true };
  assert.equal(evaluateShellCommand('echo \'{".env": true}\' > output.json', options), null);
});

test('escrita real em arquivo sensível continua bloqueada mesmo sem menção textual explícita', () => {
  const options = { cwd: '/repo', projectDir: '/repo', isTracked: () => true };
  assert.match(evaluateShellCommand('printf "x" > .env', options), /ambiente real/);
  assert.match(evaluateShellCommand('Copy-Item src.txt .git/config', options), /\.git/);
});

// Achado da auditoria adversarial pós-correção (seção 16): fs.createWriteStream
// é uma API de escrita tão comum quanto writeFileSync, mas não estava coberta
// pelas regras de correlação de alvo.
test('fs.createWriteStream é reconhecido como destino real de escrita (achado da auditoria pós-correção)', () => {
  const options = { cwd: '/repo', projectDir: '/repo', isTracked: () => true };
  assert.match(
    evaluateShellCommand("node -e \"require('fs').createWriteStream('.env').write('x')\"", options),
    /ambiente real/,
  );
  assert.equal(
    evaluateShellCommand("node -e \"const s=require('fs').createWriteStream('output.txt'); s.write('.env mentioned here')\"", options),
    null,
  );
});

// --- F7: contenção de path usa segmento real, não prefixo de string -----
test('nome de arquivo começando com ".." mas sem segmento de travessia real não é falso positivo', () => {
  assert.equal(evaluateFileWrite({
    filePath: '...spread.env.txt', cwd: '/repo', projectDir: '/repo',
  }), null);
  assert.equal(evaluateFileWrite({
    filePath: '..hidden-report.md', cwd: '/repo', projectDir: '/repo',
  }), null);
});

test('travessia real continua bloqueada mesmo quando o segmento não é o primeiro do caminho', () => {
  assert.match(evaluateFileWrite({
    filePath: 'backend/../../outside.txt', cwd: '/repo', projectDir: '/repo',
  }), /travessia/);
});

// --- F2 (hardening): endurecimento contra cópia/indireção do módulo -----
// --- do gate, sem depender apenas do nome literal do arquivo ------------
test('bloqueia invocação de node -e que referencia funções lifecycle via concatenação/acesso computado', () => {
  for (const command of [
    `node -e "import('./x.mjs').then(m => m['record'+'ReviewerStart']())"`,
    `node -e "import('./x.mjs').then(m => m.recordReviewerStop({}))"`,
    `node -e "const f = 'record' + 'SessionBaseline'; require('./x.mjs')[f]()"`,
  ]) {
    assert.notEqual(evaluateShellCommand(command), null, command);
  }
});

test('bloqueia execução de uma cópia local do módulo do gate por conter as funções lifecycle, mesmo com outro nome de arquivo', () => {
  const dir = mkdtempSync(path.join(tmpdir(), 'gate-copy-hardening-'));
  const copyPath = path.join(dir, 'gate-copy.mjs');
  copyFileSync(
    path.join(fileURLToPath(new URL('../../..', import.meta.url)), '.ai/review/engineering-review-gate.mjs'),
    copyPath,
  );
  const normalizedCopyPath = copyPath.replaceAll('\\', '/');
  assert.match(
    evaluateShellCommand(`node ${normalizedCopyPath} --subagent-stop`),
    /lifecycle/i,
  );
});

test('inspeção de conteúdo do script resolve caminho relativo ao cwd real do comando, não ao cwd do processo do guard (achado da auditoria pós-correção)', () => {
  const dir = mkdtempSync(path.join(tmpdir(), 'gate-copy-cwd-'));
  copyFileSync(
    path.join(fileURLToPath(new URL('../../..', import.meta.url)), '.ai/review/engineering-review-gate.mjs'),
    path.join(dir, 'gate-copy.mjs'),
  );
  // Caminho relativo (não absoluto): só é encontrado corretamente se o cwd
  // informado no payload for de fato usado para resolvê-lo.
  assert.match(
    evaluateShellCommand('node gate-copy.mjs --subagent-stop', { cwd: dir }),
    /lifecycle/i,
  );
});

test('não bloqueia scripts Node comuns sem relação com o gate, nem menção em prosa/documentação', () => {
  const dir = mkdtempSync(path.join(tmpdir(), 'harmless-script-'));
  const scriptPath = path.join(dir, 'harmless.mjs').replaceAll('\\', '/');
  writeFileSync(scriptPath, "console.log('hello world');\n");
  assert.equal(evaluateShellCommand(`node ${scriptPath}`), null);
  assert.equal(
    evaluateShellCommand('echo "esta documentação menciona recordReviewerStart apenas como exemplo"'),
    null,
  );
});
