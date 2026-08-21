import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import path from 'node:path';

const protectedPathRules = [
  [/(^|\/)\.git(?:\/|$)/i, 'Arquivos internos de .git não devem ser alterados diretamente.'],
  [/(^|\/)(?:id_rsa|id_ed25519|credentials\.json|service-account[^/]*\.json)$/i, 'Arquivo de credencial ou identidade sensível está protegido.'],
  [/\.(?:pem|p12|pfx|key|keystore|jks)$/i, 'Arquivo de chave ou certificado privado está protegido.'],
];

const flywayMigrationPattern = /(^|\/)src\/main\/resources\/db\/migration\/V[^/]+\.sql$/i;
const MASS_TARGETS = new Set(['.', './', '*', ':/', '/']);
const GIT_GLOBAL_OPTIONS_WITH_VALUE = new Set([
  '-C', '-c', '--git-dir', '--work-tree', '--namespace', '--super-prefix', '--config-env', '--exec-path',
]);
const SHELL_WRAPPERS = new Set(['bash', 'sh', 'zsh', 'dash', 'ksh', 'cmd', 'cmd.exe', 'powershell', 'powershell.exe', 'pwsh', 'pwsh.exe', 'wsl', 'wsl.exe']);
const EVAL_WRAPPERS = new Set(['eval', 'iex', 'invoke-expression']);
// Interpretes que podem receber um heredoc como CÓDIGO a executar (stdin),
// em vez de apenas DADOS a escrever em um arquivo (ex: `cat > f <<EOF`).
// Usado por extractHeredocs para decidir entre analisar o corpo como comando
// ou tratá-lo como conteúdo opaco de escrita.
const INTERPRETER_LAUNCHERS = new Set([
  ...SHELL_WRAPPERS, ...EVAL_WRAPPERS,
  'python', 'python.exe', 'python3', 'python3.exe', 'node', 'node.exe',
]);
const MAX_WRAPPER_DEPTH = 4;
const POWERSHELL_ENCODED_FLAGS = new Set([
  '-e', '-en', '-enc', '-enco', '-encod', '-encode', '-encoded', '-encodedc',
  '-encodedco', '-encodedcom', '-encodedcomm', '-encodedcomma', '-encodedcomman', '-encodedcommand',
]);
const POWERSHELL_COMMAND_FLAGS = new Set([
  '-c', '-co', '-com', '-comm', '-comma', '-comman', '-command',
]);
const REVIEW_GATE_LIFECYCLE_FLAGS = new Set(['--session-start', '--subagent-start', '--subagent-stop']);
// Nomes exportados do módulo canônico do gate cuja invocação direta (fora do
// runtime de hooks) não deve ser possível fabricar. Usado tanto para o
// fast-path textual quanto para inspeção de conteúdo de arquivo (ver
// directReviewLifecycleInvocation) — é isso que impede que copiar o módulo
// para outro nome de arquivo evada a checagem.
const REVIEW_GATE_LIFECYCLE_SYMBOLS = ['recordReviewerStart', 'recordReviewerStop', 'recordSessionBaseline'];
// PowerShell: cmdlet/alias que lança um processo com argv reconstruído a
// partir de parâmetros nomeados em vez de justaposição literal de palavras.
const PROCESS_LAUNCHER_CMDLETS = new Set(['start-process', 'saps']);
// Flags do Start-Process que encerram a lista de valores de -ArgumentList
// quando não há outro sinal de fim (próxima flag nomeada).
const START_PROCESS_BOUNDARY_FLAGS = new Set([
  '-wait', '-nonewwindow', '-passthru', '-windowstyle', '-workingdirectory',
  '-verb', '-credential', '-loaduserprofile', '-redirectstandardinput',
  '-redirectstandardoutput', '-redirectstandarderror', '-filepath', '-argumentlist', '-args',
]);

export function normalizePath(value) {
  return String(value ?? '').trim().replaceAll('\\', '/').replace(/^['"]|['"]$/g, '');
}

export function gitProjectRoot(cwd = process.cwd()) {
  try {
    return execFileSync('git', ['-C', cwd, 'rev-parse', '--show-toplevel'], {
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'pipe'],
      timeout: 5000,
    }).trim();
  } catch {
    return null;
  }
}

export function isProtectedEnvFile(normalizedPath) {
  const name = normalizePath(normalizedPath).split('/').at(-1) ?? '';
  if (!/^\.env(?:\..+)?$/i.test(name)) return false;
  return !/^\.env\.(?:example|sample|template)$/i.test(name);
}

// O guard protege migrations que já existem em HEAD. Um arquivo apenas staged
// continua sendo uma migration nova e pode ser refinado antes do primeiro commit.
export function isPathVersionedByGitHead(absolutePath, projectDir) {
  const root = gitProjectRoot(projectDir);
  if (!root) return true; // fail-safe: erro real do Git não libera rewrite de migration.

  const relative = normalizePath(path.relative(root, path.resolve(absolutePath)));
  if (!relative || relative.startsWith('../') || path.isAbsolute(relative)) return true;

  try {
    execFileSync('git', ['-C', root, 'rev-parse', '--verify', 'HEAD'], {
      stdio: ['ignore', 'ignore', 'ignore'], timeout: 5000,
    });
  } catch (error) {
    // Repositório recém-inicializado e ainda sem commits: nada está versionado.
    if (Number.isInteger(error?.status)) return false;
    return true;
  }

  try {
    execFileSync('git', ['-C', root, 'cat-file', '-e', `HEAD:${relative}`], {
      stdio: ['ignore', 'ignore', 'ignore'], timeout: 5000,
    });
    return true;
  } catch (error) {
    if (Number.isInteger(error?.status)) return false;
    return true;
  }
}

// Compatibilidade com imports existentes. O nome novo explicita a semântica correta:
// apenas arquivos já presentes em HEAD são migrations "versionadas" para este guard.
export const isPathTrackedByGit = isPathVersionedByGitHead;

// Política de workspace (ver ENGINEERING_REVIEW.md, seção "Escopo de escrita
// e arquivos protegidos"): este guard NÃO é um sandbox de filesystem. Ele:
//   1. rejeita travessia de diretório explícita (segmento "..") no caminho
//      informado, como sanitização de entrada — não porque o destino final
//      precise estar dentro do projeto;
//   2. protege padrões sensíveis (segredos, .git interno, migrations
//      versionadas) em QUALQUER destino, dentro ou fora do projeto atual,
//      correlacionados ao repositório git mais próximo do destino real;
//   3. permite escrita legítima fora do projeto (fixtures, relatórios,
//      diretórios temporários do SO) — bloquear isso por padrão criaria
//      fricção real sem ganho de segurança, já que o mesmo agente pode
//      escrever fora do workspace via Bash de qualquer forma.
// Edit/Write e Bash chamam a mesma função (evaluateFileWrite) e portanto
// aplicam exatamente a mesma política — não há mais assimetria entre rotas.
function resolvePath(filePath, cwd, projectDir) {
  const normalized = normalizePath(filePath);
  const fallbackRoot = path.resolve(projectDir || cwd || process.cwd());
  if (!normalized) return { normalized, absolutePath: null, projectRoot: fallbackRoot, error: null };

  // Segmento ".." literal no caminho é bloqueado como sanitização de entrada:
  // torna o destino real menos previsível para quem revisa o comando, mesmo
  // que não exista mais uma fronteira de workspace a violar.
  if (normalized.split('/').includes('..')) {
    return {
      normalized, absolutePath: null, projectRoot: fallbackRoot,
      error: `Caminho com travessia de diretório está bloqueado: ${normalized}`,
    };
  }

  const absolutePath = path.isAbsolute(normalized)
    ? path.resolve(normalized)
    : path.resolve(cwd || fallbackRoot, normalized);

  return { normalized, absolutePath, projectRoot: fallbackRoot, error: null };
}

export function evaluateFileWrite({
  filePath,
  cwd = process.cwd(),
  projectDir = cwd,
  isTracked = isPathVersionedByGitHead,
}) {
  const resolved = resolvePath(filePath, cwd, projectDir);
  if (!resolved.normalized) return null;
  if (resolved.error) return resolved.error;

  if (isProtectedEnvFile(resolved.normalized)) {
    return `Arquivo de ambiente real não deve ser alterado pelo agente: ${resolved.normalized}`;
  }

  for (const [pattern, reason] of protectedPathRules) {
    if (pattern.test(resolved.normalized)) return `${reason} Caminho: ${resolved.normalized}`;
  }

  if (flywayMigrationPattern.test(resolved.normalized)) {
    // A migration pode pertencer a um repositório diferente do projeto atual
    // (ex: fixture de teste em outro repo temporário). Correlaciona com o
    // repositório git mais próximo do destino real, não sempre com o
    // projectDir do chamador.
    const nearestRoot = (resolved.absolutePath && gitProjectRoot(path.dirname(resolved.absolutePath)))
      || resolved.projectRoot;
    if (isTracked(resolved.absolutePath, nearestRoot)) {
      return `Migration Flyway versionada existente não deve ser alterada: ${resolved.normalized}. Crie uma nova migration incremental.`;
    }
  }

  return null;
}

export function extractPatchEntries(patchText) {
  const entries = [];
  const regex = /^\*\*\* (Add|Update|Delete) File:\s*(.+)$/gm;
  for (const match of String(patchText ?? '').matchAll(regex)) {
    entries.push({ operation: match[1].toLowerCase(), path: normalizePath(match[2]) });
  }
  return entries;
}

export function evaluatePatch(patchText, {
  cwd = process.cwd(),
  projectDir = cwd,
  isTracked = isPathVersionedByGitHead,
} = {}) {
  for (const entry of extractPatchEntries(patchText)) {
    const reason = evaluateFileWrite({ filePath: entry.path, cwd, projectDir, isTracked });
    if (reason) return reason;
  }
  return null;
}

function shellTokens(command) {
  const tokens = String(command ?? '').match(/"(?:\\.|[^"])*"|'(?:\\.|[^'])*'|&&|\|\||>>?|<<|[;|]|[^\s;&|<>]+/g) ?? [];
  return tokens.map((token) => token.replace(/^(['"])(.*)\1$/s, '$2'));
}

function executableBase(token) {
  const normalized = normalizePath(token).toLowerCase();
  return normalized.split('/').at(-1) ?? normalized;
}

function optionName(token) {
  return String(token ?? '').split('=', 1)[0];
}

function stripQuotedLiteralsAndComments(command) {
  const text = String(command ?? '');
  let output = '';
  let quote = null;
  let escaped = false;
  for (let index = 0; index < text.length; index += 1) {
    const char = text[index];
    if (escaped) { escaped = false; output += quote ? ' ' : char; continue; }
    if (char === '\\') { escaped = true; output += quote ? ' ' : char; continue; }
    if (quote) {
      if (char === quote) quote = null;
      output += ' ';
      continue;
    }
    if (char === '"' || char === "'") { quote = char; output += ' '; continue; }
    if (char === '#') {
      while (index < text.length && text[index] !== '\n') index += 1;
      output += '\n';
      continue;
    }
    output += char;
  }
  return output;
}

function expandSimpleVariables(command) {
  let text = String(command ?? '');
  const values = new Map();
  const assignmentPatterns = [
    /(?:^|[;&|]\s*)(?:export\s+)?([A-Za-z_]\w*)\s*=\s*(['"]?)([A-Za-z0-9_./:\\-]+)\2(?=\s|[;&|]|$)/gim,
    /\$([A-Za-z_]\w*)\s*=\s*(['"])([A-Za-z0-9_./:\\-]+)\2/gim,
    /\$env:([A-Za-z_]\w*)\s*=\s*(['"]?)([A-Za-z0-9_./:\\-]+)\2/gim,
    /(?:^|[;&|]\s*)set\s+([A-Za-z_]\w*)\s*=\s*([A-Za-z0-9_./:\\-]+)(?=\s|[;&|]|$)/gim,
  ];
  for (const pattern of assignmentPatterns) {
    for (const match of text.matchAll(pattern)) {
      const value = match[3] ?? match[2];
      if (value) values.set(match[1].toLowerCase(), value);
    }
  }
  for (let pass = 0; pass < 3; pass += 1) {
    let changed = false;
    for (const [name, value] of values) {
      const escaped = name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      const replacements = [
        new RegExp(`\\$env:${escaped}\\b`, 'gi'),
        new RegExp(`\\$\\{${escaped}\\}`, 'gi'),
        new RegExp(`\\$${escaped}\\b`, 'gi'),
        new RegExp(`%${escaped}%`, 'gi'),
      ];
      for (const pattern of replacements) {
        const next = text.replace(pattern, value);
        if (next !== text) { text = next; changed = true; }
      }
    }
    if (!changed) break;
  }
  return text;
}

function decodePowerShellEncoded(token) {
  const raw = String(token ?? '').trim();
  if (!/^[A-Za-z0-9+/]+={0,2}$/.test(raw) || raw.length % 4 === 1) return null;
  try {
    const buffer = Buffer.from(raw, 'base64');
    if (!buffer.length) return null;
    const utf16 = buffer.toString('utf16le').replace(/^﻿/, '');
    if (!utf16.trim()) return null;
    return utf16;
  } catch {
    return null;
  }
}

// Desembrulha ANSI-C quoting do bash (`$'...'`) e a variante de locale
// (`$"..."`), aplicando apenas o decode mínimo necessário para que o texto
// interno volte a ser reconhecível como comando literal pelo tokenizador.
// Não é um parser Bash completo: escapes desconhecidos são preservados.
function unwrapAnsiCQuoting(text) {
  const trimmed = String(text ?? '').trim();
  const match = /^\$(['"])([\s\S]*)\1$/.exec(trimmed);
  if (!match) return text;
  const escapes = { n: '\n', t: '\t', r: '\r', '\\': '\\', "'": "'", '"': '"', a: '\x07', b: '\b', f: '\f', v: '\v', '0': '\0' };
  return match[2].replace(/\\(.)/g, (whole, char) => escapes[char] ?? whole);
}

// Um heredoc pode carregar DADOS (escritos em um arquivo, ex: `cat > f <<EOF`)
// ou CÓDIGO (executado por um interpretador, ex: `bash <<EOF`/`python <<EOF`).
// Para o guard, isso importa porque:
//   - corpo de dados nunca deve ser escaneado por padrões de comando
//     destrutivo/lifecycle — é conteúdo de arquivo, não uma invocação real;
//   - corpo de código deve continuar sendo analisado normalmente.
// Retorna o texto com corpos-de-dados removidos (preservando a linha que
// abre o heredoc, onde vive o alvo real de um redirecionamento) e a lista de
// corpos-de-código a serem enfileirados para análise recursiva.
// Varredura de passagem única, ciente de aspas. Um "<<" que aparece dentro
// de uma aspa ainda aberta (ex: dentro do argumento de `bash -c "...<<EOF"`,
// antes desse argumento ser desembrulhado por collectShellFragments) NÃO é
// um heredoc real neste nível — é só texto dentro de uma string ainda para
// ser processada como fragmento próprio. Tratar esse caso corretamente é o
// que evita tanto perder um heredoc real aninhado quanto, na direção
// oposta, confundir o corpo ainda citado com comando executável.
function extractHeredocs(command) {
  const text = String(command ?? '');
  const codeFragments = [];
  let output = '';
  let quote = null;
  let escaped = false;
  let index = 0;

  while (index < text.length) {
    const char = text[index];

    if (!quote && char === '<' && text[index + 1] === '<') {
      const marker = /^<<-?\s*(['"]?)([A-Za-z_][A-Za-z0-9_]*)\1/.exec(text.slice(index));
      if (marker) {
        output += marker[0];
        const lineStart = output.lastIndexOf('\n') + 1;
        const beforeHeredocOnLine = output.slice(lineStart, output.length - marker[0].length);
        const launcher = executableBase(shellTokens(beforeHeredocOnLine)[0]);
        const isCode = INTERPRETER_LAUNCHERS.has(launcher);
        const delimiter = marker[2];

        let cursor = index + marker[0].length;
        while (cursor < text.length && text[cursor] !== '\n') { output += text[cursor]; cursor += 1; }
        if (cursor < text.length) { output += '\n'; cursor += 1; }

        const bodyLines = [];
        while (cursor < text.length) {
          const nextNewline = text.indexOf('\n', cursor);
          const line = nextNewline === -1 ? text.slice(cursor) : text.slice(cursor, nextNewline);
          cursor = nextNewline === -1 ? text.length : nextNewline + 1;
          if (line.trim() === delimiter) break;
          bodyLines.push(line);
        }
        if (isCode && bodyLines.length) codeFragments.push(bodyLines.join('\n'));

        index = cursor;
        continue;
      }
    }

    if (escaped) { escaped = false; output += char; index += 1; continue; }
    if (char === '\\') { escaped = true; output += char; index += 1; continue; }
    if (quote) {
      if (char === quote) quote = null;
      output += char; index += 1; continue;
    }
    if (char === '"' || char === "'") { quote = char; output += char; index += 1; continue; }
    output += char;
    index += 1;
  }

  return { text: output, codeFragments };
}

function directReviewLifecycleInvocation(command, cwd) {
  const expanded = expandSimpleVariables(command);
  const lower = expanded.toLowerCase().replaceAll('\\\\', '/');

  // Fast-path: invocação direta e literal do módulo canônico com uma flag ou
  // função lifecycle no mesmo comando. Cobre o caso comum sem precisar tocar
  // o filesystem.
  if (/engineering-review-gate\.mjs/.test(lower)) {
    if (new RegExp(`\\b(?:${REVIEW_GATE_LIFECYCLE_SYMBOLS.join('|')})\\b`).test(expanded)) {
      return 'Invocação manual de funções lifecycle internas do Engineering Review Gate está bloqueada; provenance deve vir do runtime de hooks.';
    }
    for (const flag of REVIEW_GATE_LIFECYCLE_FLAGS) {
      if (new RegExp(`(^|\\s)${flag.replaceAll('-', '\\-')}(?=\\s|$)`, 'i').test(expanded)) {
        return `Invocação manual de ${flag} do Engineering Review Gate está bloqueada; eventos lifecycle devem vir do runtime de hooks.`;
      }
    }
  }

  return directReviewLifecycleContentInvocation(expanded, cwd);
}

// Endurecimento contra a classe de bypass "copiar/importar o módulo por
// outro caminho": em vez de depender do nome literal do arquivo, procura
// invocações de um interpretador Node (via -e/--eval ou via um arquivo de
// script local) e inspeciona o CONTEÚDO efetivamente executado — seja a
// string inline, seja o arquivo referenciado no disco (o guard roda no
// mesmo host que o comando executaria, então pode ler o mesmo arquivo).
//
// Isso NÃO é um interpretador JavaScript: é uma normalização textual (remove
// aspas, parênteses, vírgulas e operadores de concatenação comuns antes de
// procurar os nomes protegidos) seguida de um scan de substring. Isso é
// suficiente para neutralizar ofuscação trivial por concatenação de string
// ou acesso computado a propriedade (`'record'+'ReviewerStart'`,
// `g['record'+'ReviewerStart']`) sem tentar avaliar semântica arbitrária.
//
// Limite reconhecido (ver ENGINEERING_REVIEW.md, "Limite de confiança dos
// eventos lifecycle"): isso eleva o custo de um bypass acidental ou pouco
// sofisticado. Não é, e não pretende ser, uma prova de que o conteúdo
// executado não invoca as funções protegidas por algum caminho que este scan
// textual não reconheça (ex: código gerado dinamicamente em runtime).
// Ignora ocorrências citadas como string literal (ex: o próprio array
// REVIEW_GATE_LIFECYCLE_SYMBOLS deste arquivo, ou documentação que menciona
// o nome da função como dado) — só um identificador "solto" no código
// (definição de função, chamada, acesso a propriedade) conta como uso real.
// Isso é o que distingue este guard mencionar os nomes protegidos (para
// detectá-los) de um script realmente definir/chamar essas funções.
function containsBareLifecycleSymbol(text) {
  const withoutQuotedLiterals = String(text ?? '')
    .replace(/'(?:[^'\\]|\\.)*'|"(?:[^"\\]|\\.)*"|`(?:[^`\\]|\\.)*`/g, ' ');
  return new RegExp(`\\b(?:${REVIEW_GATE_LIFECYCLE_SYMBOLS.join('|')})\\b`).test(withoutQuotedLiterals);
}

function directReviewLifecycleContentInvocation(expandedCommand, cwd) {
  // Concatenação/acesso computado (`'record'+'ReviewerStart'`,
  // `g['record'+'ReviewerStart']`) é neutralizado removendo aspas e `+`
  // antes do scan — depois disso, tudo que sobra já está "solto" por
  // definição, então containsBareLifecycleSymbol funciona sem exclusão de
  // citação (não há mais aspas na string normalizada).
  const normalizeForEval = (text) => String(text ?? '')
    .replace(/['"`]/g, '')
    .replace(/\s*\+\s*/g, '');

  // Tokeniza uma única vez (shellTokens já respeita aspas) e navega pelos
  // tokens diretamente, usando `;`/`&&`/`||` só como limite de "resto do
  // comando" — sem re-tokenizar uma string já des-citada, o que perderia o
  // agrupamento do valor de -e/--eval assim que ele contivesse espaços.
  const tokens = shellTokens(expandedCommand);
  const separators = new Set([';', '&&', '||']);

  for (let index = 0; index < tokens.length; index += 1) {
    if (!['node', 'node.exe'].includes(executableBase(tokens[index]))) continue;

    let end = index + 1;
    while (end < tokens.length && !separators.has(tokens[end])) end += 1;
    const rest = tokens.slice(index + 1, end);

    const evalFlagIndex = rest.findIndex((token) => ['-e', '--eval', '-p', '--print'].includes(token.toLowerCase()));
    if (evalFlagIndex >= 0 && rest[evalFlagIndex + 1] !== undefined) {
      const evalValue = rest.slice(evalFlagIndex + 1).join(' ');
      if (containsBareLifecycleSymbol(normalizeForEval(evalValue))) {
        return 'Invocação de node -e referenciando funções lifecycle internas do Engineering Review Gate está bloqueada, mesmo com ofuscação por concatenação/acesso computado.';
      }
      continue;
    }

    const scriptToken = rest.find((token) => !token.startsWith('-'));
    if (!scriptToken) continue;
    // Duas isenções por convenção de nome, não por arquivo específico:
    //   - o próprio módulo canônico (já coberto pelo fast-path acima, que
    //     bloqueia sua invocação com flag/função lifecycle explícita);
    //   - arquivos *.test.mjs/*.test.js: a suíte de testes deste projeto
    //     invoca as funções lifecycle diretamente para testá-las (contra
    //     estado isolado em repositório temporário, nunca contra a sessão
    //     real) — sem essa isenção, `node --test .../*.test.mjs` fica
    //     impossível de rodar. Um script de bypass batizado propositalmente
    //     como *.test.mjs para explorar esta isenção é uma evasão
    //     deliberada e sofisticada, fora do que este guard promete cobrir
    //     (ver ENGINEERING_REVIEW.md, "Guardrail, não sandbox").
    if (executableBase(scriptToken) === 'engineering-review-gate.mjs' || /\.test\.(?:mjs|js)$/i.test(scriptToken)) continue;
    const content = readLocalScriptSafely(scriptToken, cwd);
    if (content && containsBareLifecycleSymbol(content)) {
      return `Script Node referenciado (${scriptToken}) contém funções lifecycle internas do Engineering Review Gate; invocá-lo fora do runtime de hooks está bloqueado.`;
    }
  }
  return null;
}

// Resolve relativo ao cwd real do comando avaliado (não ao process.cwd() do
// próprio processo do guard) — sem isso, um comando com cwd diferente do
// diretório onde o hook roda encontraria/leria o arquivo errado, ou não o
// encontraria de todo, mascarando uma cópia real do módulo do gate.
function readLocalScriptSafely(scriptPathToken, cwd) {
  try {
    const candidate = normalizePath(scriptPathToken);
    if (!candidate || !/\.(?:mjs|cjs|js)$/i.test(candidate)) return null;
    const resolved = path.isAbsolute(candidate) ? candidate : path.resolve(cwd || process.cwd(), candidate);
    return readFileSync(resolved, 'utf8');
  } catch {
    return null;
  }
}

function configuredGitAlias(subcommand, cwd) {
  if (!/^[A-Za-z0-9._-]+$/.test(String(subcommand ?? ''))) return null;
  const root = gitProjectRoot(cwd);
  if (!root) return null;
  try {
    return execFileSync('git', ['-C', root, 'config', '--get', `alias.${subcommand}`], {
      encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'], timeout: 3000,
    }).trim() || null;
  } catch {
    return null;
  }
}

function inlineGitAliasConfiguration(fragment) {
  const text = String(fragment ?? '');
  if (/\bgit(?:\.exe)?\b[^\r\n;&|]*\s-c\s+alias\.[A-Za-z0-9._-]+\s*=/i.test(text)) {
    return 'Aliases inline do Git via -c alias.* estão bloqueados porque podem ocultar comandos destrutivos.';
  }
  if (/\bgit(?:\.exe)?\b[^\r\n;&|]*\sconfig\b[^\r\n;&|]*\balias\.[A-Za-z0-9._-]+\b/i.test(text)) {
    return 'Criação/alteração de aliases Git pelo agente está bloqueada; aliases podem ocultar operações destrutivas.';
  }
  return null;
}

function expandSimpleGitAliases(command) {
  let text = String(command ?? '');
  const aliases = new Set();
  const patterns = [
    /(?:^|[;&|]\s*)(?:export\s+)?([A-Za-z_]\w*)\s*=\s*['"]?git(?:\.exe)?['"]?(?=\s|[;&|]|$)/gi,
    /\balias\s+([A-Za-z_]\w*)\s*=\s*['"]?git(?:\.exe)?['"]?(?=\s|[;&|]|$)/gi,
    /\$([A-Za-z_]\w*)\s*=\s*['"]git(?:\.exe)?['"]/gi,
    /\$env:([A-Za-z_]\w*)\s*=\s*['"]?git(?:\.exe)?['"]?/gi,
    /(?:^|[;&|]\s*)set\s+([A-Za-z_]\w*)\s*=\s*git(?:\.exe)?(?=\s|[;&|]|$)/gi,
  ];
  for (const pattern of patterns) {
    for (const match of text.matchAll(pattern)) aliases.add(match[1]);
  }

  for (const name of aliases) {
    const escaped = name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    text = text
      .replace(new RegExp(`&\\s*\\$env:${escaped}\\b`, 'gi'), 'git')
      .replace(new RegExp(`\\$env:${escaped}\\b`, 'gi'), 'git')
      .replace(new RegExp(`\\$\\{${escaped}\\}`, 'g'), 'git')
      .replace(new RegExp(`\\$${escaped}\\b`, 'g'), 'git')
      .replace(new RegExp(`%${escaped}%`, 'gi'), 'git')
      .replace(new RegExp(`(^|[;&|]\\s*)${escaped}(?=\\s+(?:reset|clean|push|branch|checkout|restore|switch|stash|rm)\\b)`, 'gim'), '$1git');
  }
  return text;
}

// Reconstrói o argv efetivo de um lançamento de processo PowerShell
// (`Start-Process`/`saps`) a partir dos parâmetros nomeados -FilePath e
// -ArgumentList/-Args, em vez de assumir justaposição literal de palavras.
// Isso generaliza a classe do problema (qualquer subcomando/flag passado via
// -ArgumentList) em vez de tratar apenas o exemplo reproduzido no relatório.
function reconstructStartProcessInvocation(fragment) {
  const tokens = shellTokens(fragment);
  for (let index = 0; index < tokens.length; index += 1) {
    if (!PROCESS_LAUNCHER_CMDLETS.has(executableBase(tokens[index]))) continue;

    const rest = tokens.slice(index + 1);
    let filePathToken = null;
    const filePathFlagIndex = rest.findIndex((token) => ['-filepath'].includes(token.toLowerCase()));
    if (filePathFlagIndex >= 0) filePathToken = rest[filePathFlagIndex + 1];
    else {
      const firstPositional = rest.find((token) => !token.startsWith('-'));
      if (firstPositional) filePathToken = firstPositional;
    }

    const argListFlagIndex = rest.findIndex((token) => ['-argumentlist', '-args'].includes(token.toLowerCase()));
    let argumentTokens = [];
    let unparsedArgumentList = false;
    if (argListFlagIndex >= 0) {
      const span = [];
      for (let cursor = argListFlagIndex + 1; cursor < rest.length; cursor += 1) {
        if (START_PROCESS_BOUNDARY_FLAGS.has(rest[cursor].toLowerCase())) break;
        span.push(rest[cursor]);
      }
      const raw = span.join(' ');
      const parsed = parsePowerShellArgumentList(raw);
      argumentTokens = parsed.items;
      unparsedArgumentList = parsed.uncertain;
    }

    const filePathBase = filePathToken ? executableBase(filePathToken) : null;
    const filePathResolved = filePathBase && !/[$@]/.test(filePathToken) ? filePathBase : null;

    return {
      filePathToken,
      filePathResolved,
      argumentTokens,
      unparsedArgumentList,
      effectiveCommand: [filePathResolved ?? filePathToken ?? '', ...argumentTokens].join(' ').trim(),
    };
  }
  return null;
}

// Faz o parsing de um valor de -ArgumentList do PowerShell nas formas mais
// comuns: array literal `@(...)`, lista separada por vírgula (com ou sem
// aspas) e string única com múltiplos argumentos separados por espaço.
// `uncertain: true` sinaliza que a extração não é confiável o bastante para
// ser tratada como reconstrução completa (usado para acionar o fallback
// conservador em reconstructStartProcessInvocation/collectShellFragments).
function parsePowerShellArgumentList(raw) {
  const text = String(raw ?? '').trim();
  if (!text) return { items: [], uncertain: false };

  const arrayMatch = /^@\(([\s\S]*)\)$/.exec(text);
  const body = arrayMatch ? arrayMatch[1] : text;

  // Concatenação de variável ($a + $b) ou variável solta não tem valor
  // literal disponível estaticamente — reportar como incerto para acionar
  // avaliação conservadora, em vez de fingir que reconstruímos o argv real.
  if (/\$\w/.test(body)) return { items: [], uncertain: true };

  const items = [];
  const itemPattern = /'((?:[^'])*)'|"((?:[^"])*)"|([^,\s][^,]*)/g;
  for (const match of body.matchAll(itemPattern)) {
    const value = (match[1] ?? match[2] ?? match[3] ?? '').trim();
    if (!value) continue;
    // Um único item pode representar múltiplos argumentos separados por
    // espaço (ex: -ArgumentList "reset --hard"): reconstrução conservadora
    // trata isso como argumentos distintos para fins de detecção.
    items.push(...value.split(/\s+/).filter(Boolean));
  }
  return { items, uncertain: false };
}

function collectShellFragments(command, maxDepth = MAX_WRAPPER_DEPTH) {
  const root = expandSimpleVariables(expandSimpleGitAliases(command));
  const fragments = [];
  const queue = [{ text: root, depth: 0 }];
  const seen = new Set();

  const enqueue = (text, depth) => {
    const unwrapped = unwrapAnsiCQuoting(text);
    queue.push({ text: unwrapped, depth });
  };

  while (queue.length) {
    const { text: rawText, depth } = queue.shift();
    const { text, codeFragments } = extractHeredocs(String(rawText ?? ''));
    for (const codeFragment of codeFragments) enqueue(codeFragment, depth + 1);

    const normalized = text.trim();
    if (!normalized || seen.has(normalized)) continue;
    seen.add(normalized);
    fragments.push(normalized);
    if (depth >= maxDepth) continue;

    const tokens = shellTokens(normalized);
    for (let index = 0; index < tokens.length; index += 1) {
      const base = executableBase(tokens[index]);
      if (SHELL_WRAPPERS.has(base)) {
        const isPowerShell = base.startsWith('powershell') || base.startsWith('pwsh');
        if (isPowerShell) {
          const encodedIndex = tokens.findIndex((token, tokenIndex) => tokenIndex > index && POWERSHELL_ENCODED_FLAGS.has(token.toLowerCase()));
          if (encodedIndex >= 0) {
            const decoded = decodePowerShellEncoded(tokens[encodedIndex + 1]);
            // EncodedCommand é opaco demais para um guard textual. Se não for
            // possível decodificar com segurança, ele será bloqueado na avaliação.
            if (decoded) enqueue(decoded, depth + 1);
          }
        }
        const flagIndex = tokens.findIndex((token, tokenIndex) => {
          if (tokenIndex <= index) return false;
          const lowerToken = token.toLowerCase();
          if (base.startsWith('cmd')) return lowerToken === '/c';
          if (isPowerShell) return POWERSHELL_COMMAND_FLAGS.has(lowerToken);
          return lowerToken === '-c';
        });
        if (flagIndex >= 0 && tokens[flagIndex + 1]) {
          enqueue(tokens.slice(flagIndex + 1).join(' '), depth + 1);
        }
      }
      if (EVAL_WRAPPERS.has(base) && tokens[index + 1]) {
        enqueue(tokens.slice(index + 1).join(' '), depth + 1);
      }
      if (base === 'ssh' && tokens[index + 2]) {
        enqueue(tokens.slice(index + 2).join(' '), depth + 1);
      }
      if ((base === 'docker' || base === 'docker.exe') && tokens[index + 1]?.toLowerCase() === 'exec') {
        let child = index + 2;
        while (child < tokens.length && tokens[child].startsWith('-')) child += 1;
        if (tokens[child]) child += 1;
        if (tokens[child]) enqueue(tokens.slice(child).join(' '), depth + 1);
      }
      if (PROCESS_LAUNCHER_CMDLETS.has(base)) {
        const reconstructed = reconstructStartProcessInvocation(normalized);
        if (reconstructed?.effectiveCommand) enqueue(reconstructed.effectiveCommand, depth + 1);
        if (reconstructed?.unparsedArgumentList) {
          // Reconstrução não confiável: comportamento conservador quando o
          // texto bruto já sugere um subcomando Git destrutivo, em vez de
          // silenciosamente permitir por não conseguir reconstruir o argv.
          const hint = launchedProcessDestructiveHint(reconstructed.argumentTokens.join(' ') || normalized);
          if (hint) enqueue(hint, depth + 1);
        }
      }
    }

    const xargsMatch = /\|\s*xargs\s+git(?:\.exe)?\b([^;&|]*)/i.exec(normalized);
    if (xargsMatch) {
      const left = normalized.slice(0, xargsMatch.index);
      const leftTokens = shellTokens(left);
      const producer = executableBase(leftTokens[0]);
      if (['echo', 'printf'].includes(producer) && leftTokens.length > 1) {
        const produced = leftTokens.slice(1).filter((token) => !token.startsWith('%')).join(' ');
        enqueue(`git ${produced} ${xargsMatch[1] || ''}`.trim(), depth + 1);
      }
    }
  }

  return fragments;
}

// Usado apenas quando reconstructStartProcessInvocation não conseguiu
// resolver o FilePath/ArgumentList com confiança (ex: variável dinâmica).
// Não tenta provar que o processo lançado é Git; apenas detecta, de forma
// literal e conservadora, um par subcomando+flag já reconhecido como
// destrutivo em evaluateGitInvocation, e força uma reavaliação como se
// "git <argumentos>" tivesse sido chamado diretamente.
function launchedProcessDestructiveHint(text) {
  const normalized = String(text ?? '').toLowerCase();
  const destructivePairs = [
    [/\breset\b/, /--hard\b/],
    [/\bclean\b/, /(?:--force\b|-[a-z]*f)/],
    [/\bpush\b/, /(?:--force\b|-[a-z]*f)/],
    [/\bbranch\b/, /(?:-d|--delete|-f|--force)/],
  ];
  for (const [subcommandPattern, flagPattern] of destructivePairs) {
    if (subcommandPattern.test(normalized) && flagPattern.test(normalized)) {
      return `git ${text}`;
    }
  }
  return null;
}

function gitInvocations(fragment) {
  const tokens = shellTokens(fragment);
  const invocations = [];
  const separators = new Set([';', '&&', '||', '|']);

  for (let index = 0; index < tokens.length; index += 1) {
    if (!['git', 'git.exe'].includes(executableBase(tokens[index]))) continue;
    let cursor = index + 1;

    while (cursor < tokens.length && !separators.has(tokens[cursor]) && tokens[cursor].startsWith('-')) {
      const token = tokens[cursor];
      const name = optionName(token);
      if (GIT_GLOBAL_OPTIONS_WITH_VALUE.has(name) && !token.includes('=')) cursor += 2;
      else cursor += 1;
    }

    if (cursor >= tokens.length || separators.has(tokens[cursor])) continue;
    const subcommand = tokens[cursor].toLowerCase();
    const args = [];
    cursor += 1;
    while (cursor < tokens.length && !separators.has(tokens[cursor])) {
      args.push(tokens[cursor]);
      cursor += 1;
    }
    invocations.push({ subcommand, args });
  }
  return invocations;
}

function hasShortFlag(args, flag) {
  return args.some((arg) => /^-[^-]/.test(arg) && arg.slice(1).includes(flag));
}

function hasOption(args, ...options) {
  return args.some((arg) => options.some((option) => arg === option || arg.startsWith(`${option}=`)));
}

function hasMassTarget(args) {
  return args.some((arg) => {
    const normalized = normalizePath(arg);
    return MASS_TARGETS.has(normalized)
      || /[*?\[]/.test(normalized)
      || /^:\([^)]*(?:glob|top|icase|exclude)[^)]*\)/i.test(normalized);
  });
}

// DELETE e FORCE são conceitos independentes em `git branch`: cada um pode
// vir na forma curta, longa ou agrupada com outras flags de um único
// caractere (`-fd`, `-df`). Tratá-los como predicados separados, em vez de
// enumerar cada combinação válida como uma condição própria, cobre qualquer
// combinação (incluindo agrupamentos ainda não vistos) sem crescer a lista de
// regras a cada variante nova reportada.
function gitBranchDeletePresent(args) {
  return hasShortFlag(args, 'd') || hasShortFlag(args, 'D') || hasOption(args, '--delete');
}
function gitBranchForcePresent(args) {
  return hasShortFlag(args, 'f') || hasShortFlag(args, 'D') || hasOption(args, '--force');
}

function evaluateGitInvocation({ subcommand, args }) {
  if (subcommand === 'reset' && hasOption(args, '--hard')) return 'git reset --hard pode descartar alterações locais.';
  if (subcommand === 'clean' && (hasOption(args, '--force') || hasShortFlag(args, 'f'))) return 'git clean com force pode remover arquivos não rastreados.';
  if (subcommand === 'push' && (hasOption(args, '--force', '--force-with-lease', '--force-if-includes') || hasShortFlag(args, 'f'))) return 'Force push está bloqueado pela política do projeto.';
  if (subcommand === 'branch' && gitBranchDeletePresent(args) && gitBranchForcePresent(args)) return 'Exclusão forçada de branch está bloqueada.';
  if (subcommand === 'checkout' && (hasOption(args, '--force') || hasShortFlag(args, 'f') || hasMassTarget(args))) return 'Esse checkout pode descartar alterações locais em massa.';
  if (subcommand === 'restore' && hasMassTarget(args)) return 'Esse restore pode descartar alterações locais em massa.';
  if (subcommand === 'switch' && (hasOption(args, '--force', '--discard-changes') || hasShortFlag(args, 'f'))) return 'Esse switch pode descartar alterações locais.';
  if (subcommand === 'stash' && args[0]?.toLowerCase() === 'clear') return 'git stash clear remove todos os stashes e está bloqueado.';
  if (subcommand === 'rm' && hasMassTarget(args)) return 'git rm em massa está bloqueado.';
  return null;
}

function evaluateObfuscatedGit(fragment) {
  const expanded = expandSimpleVariables(expandSimpleGitAliases(String(fragment ?? '')));
  const first = executableBase(shellTokens(expanded)[0]);
  if (['echo', 'printf', 'grep', 'find', 'select-string', 'get-content', 'cat'].includes(first)
    && !/[|;&]/.test(stripQuotedLiteralsAndComments(expanded))) return null;
  const text = stripQuotedLiteralsAndComments(expanded);
  const gitPrefix = String.raw`\bgit(?:\.exe)?(?:\s|\$\([^)]*\)|` + '`[^`]*`' + String.raw`)+`;
  const rules = [
    [new RegExp(`${gitPrefix}reset\\b[^;&|\\r\\n]*--hard\\b`, 'i'), 'git reset --hard pode descartar alterações locais.'],
    [new RegExp(`${gitPrefix}clean\\b[^;&|\\r\\n]*(?:--force\\b|-[A-Za-z]*f[A-Za-z]*)`, 'i'), 'git clean com force pode remover arquivos não rastreados.'],
    [new RegExp(`${gitPrefix}push\\b[^;&|\\r\\n]*(?:--force(?:-with-lease|-if-includes)?\\b|-[A-Za-z]*f[A-Za-z]*)`, 'i'), 'Force push está bloqueado pela política do projeto.'],
  ];
  for (const [pattern, reason] of rules) if (pattern.test(text)) return reason;
  return null;
}

function evaluateSystemDestructiveCommand(command) {
  const text = String(command ?? '');
  const rules = [
    [/\brm\s+-[^\r\n]*r[^\r\n]*f[^\r\n]*\s+(?:\/\*?|~\/?|\.\/?|\*)\s*(?:$|[;&|])/i, 'Remoção recursiva da raiz, home ou diretório atual está bloqueada.'],
    [/\bRemove-Item\b[^\r\n]*(?:-Recurse[^\r\n]*-Force|-Force[^\r\n]*-Recurse)[^\r\n]*(?:\s\.\s*(?:$|[;|])|\s\*\s*(?:$|[;|])|[A-Z]:\\\s*(?:$|[;|]))/i, 'Remoção recursiva forçada em massa está bloqueada.'],
    [/\b(?:diskpart|format\s+[A-Z]:|shutdown\b|Stop-Computer\b|Restart-Computer\b)/i, 'Comando destrutivo de sistema ou desligamento está bloqueado.'],
  ];
  for (const [pattern, reason] of rules) if (pattern.test(text)) return reason;
  return null;
}

// --- Correlação mutação → destino real (ver ENGINEERING_REVIEW.md) --------
//
// Em vez de procurar padrões de path sensível em QUALQUER lugar do texto do
// comando (abordagem antiga, que confundia menção textual com alvo real de
// escrita), cada mutador reconhecido tem uma regra própria e pequena que
// extrai apenas o(s) argumento(s) que ele efetivamente usa como destino.
// Isso é o que permite diferenciar:
//   cat > relatorio.md <<'EOF' ... texto mencionando .env ... EOF   (permitido)
//   cat > .env <<'EOF' ... EOF                                     (bloqueado)
// já que só o primeiro caso tem ".env" como substring solta no texto, mas
// nenhum dos dois tem ".env" como ALVO real do redirecionamento.

// Um comando pode conter mais de um redirecionamento real (ex.: dois
// heredocs em sequência, `cmd1 > a.txt; cmd2 > b.txt`). Retornar só o
// primeiro alvo deixaria qualquer redirecionamento subsequente fora da
// checagem — coleta todos.
function redirectionTargets(fragment) {
  const text = String(fragment ?? '');
  const targets = [];
  let quote = null;
  let escaped = false;
  for (let index = 0; index < text.length; index += 1) {
    const char = text[index];
    if (escaped) { escaped = false; continue; }
    if (char === '\\') { escaped = true; continue; }
    if (quote) { if (char === quote) quote = null; continue; }
    if (char === '"' || char === "'") { quote = char; continue; }
    if (char !== '>') continue;
    const previous = text[index - 1] ?? '';
    const next = text[index + 1] ?? '';
    if (previous === '=' || next === '=') continue; // JS/TS =>, >= etc.
    let cursor = index + 1;
    if (text[cursor] === '>') cursor += 1; // >>
    while (text[cursor] === ' ') cursor += 1;
    const rest = text.slice(cursor);
    const [targetToken] = shellTokens(rest);
    if (targetToken) targets.push(targetToken);
  }
  return targets;
}

const FILE_CMDLET_TARGET_RULES = [
  // [conjunto de nomes de comando, índice do argumento-alvo entre os
  // posicionais não-flag: 'first' | 'last']
  [new Set(['set-content', 'add-content', 'out-file', 'new-item', 'tee']), 'first'],
  [new Set(['copy-item', 'move-item', 'rename-item', 'cp', 'mv', 'move', 'copy', 'ren']), 'last'],
  [new Set(['remove-item', 'rm', 'del', 'erase']), 'last'],
];

function fileCmdletTarget(fragment) {
  const tokens = shellTokens(fragment);
  const lower = tokens.map((token) => executableBase(token));
  for (const [names, which] of FILE_CMDLET_TARGET_RULES) {
    const commandIndex = lower.findIndex((token) => names.has(token));
    if (commandIndex < 0) continue;
    const namedTarget = tokens.findIndex((token, tokenIndex) => tokenIndex > commandIndex
      && ['-path', '-filepath', '-destination', '-literalpath'].includes(token.toLowerCase()));
    if (namedTarget >= 0 && tokens[namedTarget + 1]) return tokens[namedTarget + 1];

    const positionals = tokens.slice(commandIndex + 1).filter((token) => !token.startsWith('-'));
    if (!positionals.length) continue;
    return which === 'last' ? positionals.at(-1) : positionals[0];
  }
  return null;
}

// [regex do call-site, índice do grupo de captura que é o alvo real]
const SCRIPT_API_TARGET_RULES = [
  [/\b(?:writeFileSync|writeFile|appendFileSync|appendFile|unlinkSync|unlink|renameSync|rmSync|createWriteStream)\s*\(\s*['"]([^'"]+)['"]/i, 1],
  [/\bfs\.promises\.(?:writeFile|appendFile|rm|unlink|rename)\s*\(\s*['"]([^'"]+)['"]/i, 1],
  [/\b(?:copyFileSync|copyFile)\s*\(\s*['"][^'"]+['"]\s*,\s*['"]([^'"]+)['"]/i, 1],
  [/\bfs\.promises\.copyFile\s*\(\s*['"][^'"]+['"]\s*,\s*['"]([^'"]+)['"]/i, 1],
  [/\bopen\s*\(\s*['"]([^'"]+)['"]\s*,\s*['"][wax+][^'"]*['"]/i, 1],
  [/\bPath\s*\(\s*['"]([^'"]+)['"]\s*\)\s*\.\s*write_(?:text|bytes)\s*\(/i, 1],
  [/\bshutil\.(?:copy2?|copyfile|copytree|move)\s*\(\s*['"][^'"]+['"]\s*,\s*['"]([^'"]+)['"]/i, 1],
  [/\[System\.IO\.File\]::(?:WriteAllText|WriteAllLines|WriteAllBytes|AppendAllText|AppendAllLines)\s*\(\s*['"]([^'"]+)['"]/i, 1],
  [/\[System\.IO\.File\]::(?:Copy|Move)\s*\(\s*['"][^'"]+['"]\s*,\s*['"]([^'"]+)['"]/i, 1],
  [/\[System\.IO\.File\]::Delete\s*\(\s*['"]([^'"]+)['"]/i, 1],
];

function scriptApiTargets(fragment) {
  const text = String(fragment ?? '');
  const targets = [];
  for (const [pattern, group] of SCRIPT_API_TARGET_RULES) {
    const match = pattern.exec(text);
    if (match?.[group]) targets.push(match[group]);
  }
  return targets;
}

function sedPerlInPlaceTarget(fragment) {
  const tokens = shellTokens(fragment);
  const lower = tokens.map((token) => executableBase(token));
  for (let i = 0; i < lower.length; i += 1) {
    if (lower[i] !== 'sed' && lower[i] !== 'perl') continue;
    const rest = tokens.slice(i + 1);
    if (!rest.some((token) => /^-[A-Za-z]*i/.test(token))) continue;
    const positionals = rest.filter((token) => !token.startsWith('-'));
    if (positionals.length) return positionals.at(-1);
  }
  return null;
}

// Ponto único de extração: reúne todos os alvos reais de mutação
// reconhecidos nesta fragmento. Um fragmento sem nenhuma forma de mutação
// reconhecida retorna lista vazia — texto que apenas MENCIONA um padrão
// sensível, sem uma dessas formas, nunca chega a ser avaliado como escrita.
function extractMutationTargets(fragment) {
  const targets = [...redirectionTargets(fragment)];
  const cmdlet = fileCmdletTarget(fragment);
  if (cmdlet) targets.push(cmdlet);
  targets.push(...scriptApiTargets(fragment));
  const sedPerl = sedPerlInPlaceTarget(fragment);
  if (sedPerl) targets.push(sedPerl);
  return [...new Set(targets)];
}

function hasStructuredMutation(fragment) {
  return extractMutationTargets(fragment).length > 0;
}

function evaluateProtectedShellWrite(fragments, options) {
  for (const rawFragment of fragments) {
    const fragment = expandSimpleVariables(rawFragment);
    for (const target of extractMutationTargets(fragment)) {
      const reason = evaluateFileWrite({ filePath: target, ...options });
      if (reason) return reason;
    }
  }
  return null;
}

export function evaluateShellCommand(command, {
  cwd = process.cwd(),
  projectDir = cwd,
  isTracked = isPathVersionedByGitHead,
  aliasDepth = 0,
} = {}) {
  // Ponto único de extração de fragmentos: já resolve, recursivamente,
  // wrappers de shell, EncodedCommand, Start-Process e heredocs (corpo de
  // dado removido; corpo de código de um interpretador vira um fragmento
  // próprio). Todo o resto desta função opera sobre essa lista, nunca sobre
  // o texto bruto — assim nenhuma checagem revê um heredoc de dado inteiro
  // como se fosse comando, nem perde um heredoc de código por engano.
  const fragments = collectShellFragments(command);
  const rootFragment = fragments[0] ?? '';

  for (const fragment of fragments) {
    const lifecycleReason = directReviewLifecycleInvocation(fragment, cwd);
    if (lifecycleReason) return lifecycleReason;
  }

  const rawTokens = shellTokens(rootFragment);
  for (let index = 0; index < rawTokens.length; index += 1) {
    const base = executableBase(rawTokens[index]);
    if (!(base.startsWith('powershell') || base.startsWith('pwsh'))) continue;
    const encodedIndex = rawTokens.findIndex((token, tokenIndex) => tokenIndex > index && POWERSHELL_ENCODED_FLAGS.has(token.toLowerCase()));
    if (encodedIndex >= 0 && !decodePowerShellEncoded(rawTokens[encodedIndex + 1])) {
      return 'PowerShell EncodedCommand opaco/inválido está bloqueado porque não pode ser inspecionado com segurança.';
    }
  }

  for (const fragment of fragments) {
    const aliasConfigReason = inlineGitAliasConfiguration(fragment);
    if (aliasConfigReason) return aliasConfigReason;
    for (const invocation of gitInvocations(fragment)) {
      const reason = evaluateGitInvocation(invocation);
      if (reason) return reason;
      const alias = configuredGitAlias(invocation.subcommand, cwd);
      if (alias) {
        const expandedAlias = alias.startsWith('!')
          ? alias.slice(1)
          : `git ${alias} ${invocation.args.join(' ')}`.trim();
        if (aliasDepth >= 3) return `Alias Git ${invocation.subcommand} possui expansão recursiva/opaca demais para inspeção segura.`;
        const aliasReason = evaluateShellCommand(expandedAlias, { cwd, projectDir, isTracked, aliasDepth: aliasDepth + 1 });
        if (aliasReason) return `Alias Git ${invocation.subcommand} expande para operação bloqueada: ${aliasReason}`;
      }
    }
    const obfuscated = evaluateObfuscatedGit(fragment);
    if (obfuscated) return obfuscated;
  }

  const systemReason = evaluateSystemDestructiveCommand(rootFragment);
  if (systemReason) return systemReason;

  return evaluateProtectedShellWrite(fragments, { cwd, projectDir, isTracked });
}
