// <!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
// fediim affected-tests — list (and optionally run) the tests a change can reach.
// Usage: node .fediim/tools/affected-tests.mjs [--run] [--light]
// Prints the affected test files, one per line. --run or --light then runs them: through
// .fediim/tools/run.mjs --light --compact when it exists, else directly. The test command is
// narrowed to those files for vitest/jest/mocha/pytest/node --test and for a package.json
// test:one script; any other runner gets the full test command. No affected test: exit 0.
// Graph: TS/JS relative import|require|export-from (.js resolves to .ts), Python relative
// from-imports, Kotlin imports by path suffix and the Foo.kt <-> FooTest.kt convention.
import { execFileSync, spawnSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, normalize } from 'node:path';

const TEST_CMD = "./gradlew check -q";
const DOC_RE = /[.](md|txt|rst|adoc)$/i;
const TEST_PATH_RE = /(^|\/)(tests?|__tests__)\/|[.](test|spec)[.]/i;
const TEST_NAME_RE = /(^|\/)test_[^/]*[.]py$|_test[.](py|go)$|Tests?[.]kt$/;
const LOCK_RE = /(^|\/)(package-lock[.]json|npm-shrinkwrap[.]json|pnpm-lock[.]yaml|yarn[.]lock|Cargo[.]lock|fediim[.]lock|poetry[.]lock|go[.]sum|Gemfile[.]lock|composer[.]lock)$|[.]snap$|^tests\/fixtures\/expected\//;
const GENERATED_RE = /(^|\/)(dist|build|out|coverage|node_modules|generated|__generated__)\/|[.](min[.](js|css)|generated[.]\w+)$|^[.](claude|agents|fediim)\/|^[.]github\/workflows\/fediim-|^(AGENTS|CLAUDE|GEMINI)[.]md$/;
const SOURCE_EXT_RE = /[.](?:[cm]?[jt]sx?|vue|svelte|kts?|java|py|go|rs|rb|swift|c|cc|cpp|h|hpp|cs|php|sh)$/;
const isTestFile = (file) => TEST_PATH_RE.test(file) || TEST_NAME_RE.test(file);
const isLockFile = (file) => LOCK_RE.test(file);
const isGeneratedFile = (file) => GENERATED_RE.test(file);
const isSourceFile = (file) => SOURCE_EXT_RE.test(file) && !isTestFile(file) && !isLockFile(file) && !isGeneratedFile(file);
function resolveDefaultBranch(runGit) {
  let def = '';
  try { def = (runGit(['symbolic-ref', '--short', 'refs/remotes/origin/HEAD']) || '').trim(); } catch (e) {}
  return def;
}
function resolveBaseRef(runGit, headRef) {
  const def = resolveDefaultBranch(runGit);
  const candidates = [...new Set([def, 'origin/main', 'main', 'origin/master', 'master'].filter(Boolean))];
  for (const ref of candidates) {
    try {
      const mb = (runGit(['merge-base', headRef || 'HEAD', ref]) || '').trim();
      if (mb) return mb;
    } catch (e) {}
  }
  return '';
}

const git = (args) => execFileSync('git', args, { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024, stdio: ['ignore', 'pipe', 'ignore'] });
function execute(cmd) {
  if (!cmd) { console.error('affected-tests: no test command configured'); process.exit(0); }
  const res = existsSync('.fediim/tools/run.mjs')
    ? spawnSync(process.execPath, ['.fediim/tools/run.mjs', '--light', '--compact', '--', cmd], { stdio: 'inherit' })
    : spawnSync('sh', ['-c', cmd], { stdio: 'inherit' });
  process.exit(res.status ?? 1);
}
const run = process.argv.includes('--run') || process.argv.includes('--light');
let root;
// Outside a git repository nothing can be mapped: run the whole suite (or list nothing).
try { root = git(['rev-parse', '--show-toplevel']).trim(); } catch { if (run) execute(TEST_CMD); process.exit(0); }
process.chdir(root);

const base = resolveBaseRef(git, 'HEAD');
const lines = (out) => out.split('\n').filter(Boolean);
const changed = new Set([...lines(git(['diff', '--name-only', base || 'HEAD'])), ...lines(git(['ls-files', '--others', '--exclude-standard']))]);
const files = lines(git(['ls-files', '--cached', '--others', '--exclude-standard'])).filter((f) => existsSync(f) && !isGeneratedFile(f));
const fileSet = new Set(files);

const EXTS = ['', '.ts', '.tsx', '.js', '.jsx', '.mjs', '.cjs', '.mts', '.cts', '.json', '.py', '/index.ts', '/index.tsx', '/index.js', '/__init__.py'];
const SWAP = { '.js': ['.ts', '.tsx'], '.jsx': ['.tsx'], '.mjs': ['.mts'], '.cjs': ['.cts'] };
function resolveRel(from, spec) {
  const target = normalize(join(dirname(from), spec)).replace(/\\/g, '/');
  const stem = target.replace(/\.[^./]+$/, '');
  const swapped = (SWAP[target.slice(stem.length)] || []).map((e) => stem + e);
  return [...swapped, ...EXTS.map((e) => target + e)].find((c) => fileSet.has(c));
}
function resolvePyAbs(modPath) {
  return [modPath + '.py', modPath + '/__init__.py', 'src/' + modPath + '.py', 'src/' + modPath + '/__init__.py'].find((c) => fileSet.has(c));
}
const kotlinIndex = new Map();
const kotlinBySuffix = new Map();
for (const f of files) {
  if (!f.endsWith('.kt')) continue;
  const stem = f.replace(/\.kt$/, '');
  kotlinIndex.set(stem, f);
  let idx = -1;
  for (;;) {
    const seg = '/' + (idx < 0 ? stem : stem.slice(idx + 1));
    if (!kotlinBySuffix.has(seg)) kotlinBySuffix.set(seg, f);
    idx = stem.indexOf('/', idx + 1);
    if (idx < 0) break;
  }
}
function importsOf(file) {
  if (!/\.([cm]?[jt]sx?|py|kt)$/.test(file)) return [];
  let text;
  try { text = readFileSync(file, 'utf8'); } catch { return []; }
  const out = [];
  if (/\.([cm]?[jt]sx?)$/.test(file)) {
    for (const m of text.matchAll(/(?:\bfrom\s*|\bimport\s*\(?\s*|\brequire\(\s*)['"](\.{1,2}\/?[^'"]*)['"]/g)) out.push(resolveRel(file, m[1]));
  } else if (file.endsWith('.py')) {
    for (const m of text.matchAll(/^\s*from\s+(\.+)([\w.]*)\s+import\s+([\w, ]+)/gm)) {
      const dir = '../'.repeat(m[1].length - 1);
      const mod = m[2].replace(/\./g, '/');
      out.push(resolveRel(file, dir + (mod || '.')), ...(mod ? [] : m[3].split(',').map((n) => resolveRel(file, dir + n.trim()))));
    }
    for (const m of text.matchAll(/^\s*from\s+(?!\.)([\w.]+)\s+import\s+([\w, ]+)/gm)) {
      const baseMod = m[1].replace(/\./g, '/');
      out.push(resolvePyAbs(baseMod), ...m[2].split(',').map((n) => resolvePyAbs(baseMod + '/' + n.trim().split(/\s+as\s+/)[0])));
    }
    for (const m of text.matchAll(/^\s*import\s+([\w., ]+)/gm)) {
      for (const raw of m[1].split(',')) {
        const mod = raw.trim().split(/\s+as\s+/)[0];
        if (mod) out.push(resolvePyAbs(mod.replace(/\./g, '/')));
      }
    }
  } else if (file.endsWith('.kt')) {
    for (const m of text.matchAll(/^import\s+([\w.]+)/gm)) {
      const hit = kotlinBySuffix.get('/' + m[1].replace(/\./g, '/'));
      if (hit) out.push(hit);
    }
    const stem = file.replace(/Tests?\.kt$/, '').replace(/\.kt$/, '');
    const twin = kotlinIndex.get(stem);
    if (twin && twin !== file) out.push(twin);
  }
  return out.filter(Boolean);
}

// Reverse edges: file -> files that import it.
const importedBy = new Map();
for (const f of files) {
  for (const dep of importsOf(f)) {
    let list = importedBy.get(dep);
    if (!list) importedBy.set(dep, (list = []));
    list.push(f);
  }
}
const seen = new Set();
const queue = [...changed].filter((f) => fileSet.has(f));
while (queue.length) {
  const f = queue.pop();
  if (seen.has(f)) continue;
  seen.add(f);
  queue.push(...(importedBy.get(f) || []));
}
const tests = [...seen].filter(isTestFile).sort();
for (const t of tests) console.log(t);
if (!run) process.exit(0);
if (tests.length === 0) { console.error('affected-tests: no affected tests'); process.exit(0); }

let pkg = {};
try { pkg = JSON.parse(readFileSync('package.json', 'utf8')); } catch { /* not a node project */ }
const quoted = tests.map((t) => "'" + t + "'").join(' ');
let cmd = TEST_CMD;
if (pkg.scripts && pkg.scripts['test:one']) cmd = 'npm run test:one -- ' + quoted;
else if (/\b(vitest|jest|mocha|pytest)\b/.test(TEST_CMD)) cmd = TEST_CMD + ' ' + quoted;
else if (/\bnode\b.*--test\b/.test(TEST_CMD)) cmd = TEST_CMD.replace(/\s+\S*\*\S*/g, '') + ' ' + quoted;
execute(cmd);
