// <!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
// fediim pr-prep — format changed files in batch, then build + test (stamped).
// Usage: node .fediim/tools/pr-prep.mjs [--issue <n>]
// Order: lint fix -> format -> lint -> guards --static-only -> build + test -> archive plan -> stamp.
// Exit 0 = tree formatted, verified and (if --issue) plan archived; 1 = failed before archival.
import { execFileSync, execSync, spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, readdirSync, renameSync, unlinkSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { basename, dirname, join, resolve } from 'node:path';

const fs = { existsSync, readFileSync, readdirSync };
const path = { join, dirname };
const LINT = "";
const FORMAT_MAP = {"*.kt":"ktfmt","*.kts":"ktfmt"};
const FIX_MAP = {"*.kt":["ktlint -F"],"*.kts":["ktlint -F"]};
const RAW = {"build":"./gradlew compileAllTargets -q","test":"./gradlew check -q"};
const RUN = {"build":"./gradlew compileAllTargets -q","test":"./gradlew check -q"};
function hasPrettierConfig(dir) {
  for (;;) {
    let names = [];
    try { names = fs.readdirSync(dir); } catch (e) {}
    if (names.some((n) => n.startsWith('.prettierrc') || n.startsWith('prettier.config.'))) return true;
    if (names.includes('package.json')) {
      try {
        if ('prettier' in JSON.parse(fs.readFileSync(path.join(dir, 'package.json'), 'utf8'))) return true;
      } catch (e) {}
    }
    const parent = path.dirname(dir);
    if (parent === dir) return false;
    dir = parent;
  }
}
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
function makeStamp(req) {
  const cp = req('child_process');
  const fs = req('fs');
  const path = req('path');
  const isRealCi = () => Boolean(process.env.GITHUB_ACTIONS || (process.env.CI && !process.env.FEDIIM_AUTONOMOUS_LOCAL));
  const git = (cwd, args, env) => cp.execFileSync('git', args, { cwd, env: env ? { ...process.env, ...env } : process.env, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim();
  const top = (cwd) => git(cwd, ['rev-parse', '--show-toplevel']);
  const treeHash = (cwd) => {
    let idx = '';
    try {
      const root = top(cwd);
      const real = path.resolve(root, git(root, ['rev-parse', '--git-path', 'index']));
      idx = path.resolve(root, git(root, ['rev-parse', '--git-dir']), 'fediim-idx-' + process.pid);
      if (fs.existsSync(real)) fs.copyFileSync(real, idx);
      const env = { GIT_INDEX_FILE: idx };
      // Our own run state (stamp, build-slot locks, task bindings) must not change the hash.
      git(root, ['add', '-A', '--', '.', ':(exclude).fediim/run', ':(exclude).fediim/team', ':(exclude).fediim/task-sessions'], env);
      return git(root, ['write-tree'], env);
    } catch (e) {
      return '';
    } finally {
      if (idx) { try { fs.unlinkSync(idx); } catch (e) {} }
    }
  };
  const file = (root) => path.join(root, '.fediim', 'run', 'verified.json');
  const read = (root) => { try { return JSON.parse(fs.readFileSync(file(root), 'utf8')); } catch (e) { return null; } };
  // True when every command passed on exactly this tree. Real CI never short-circuits.
  const verifiedFor = (cwd, commands) => {
    if (isRealCi()) return false;
    try {
      const v = read(top(cwd));
      if (!v || !v.treeHash || !Array.isArray(v.commands) || !commands.every((c) => v.commands.includes(c))) return false;
      return treeHash(cwd) === v.treeHash;
    } catch (e) {
      return false;
    }
  };
  // ponytail: stamps the tree as it is after the pass; an edit racing the run is stamped too.
  const record = (cwd, command) => {
    try {
      const root = top(cwd);
      const after = treeHash(cwd);
      if (!after) return;
      const v = read(root);
      const commands = [...new Set([...(v && v.treeHash === after ? v.commands : []), command])].sort();
      fs.mkdirSync(path.dirname(file(root)), { recursive: true });
      const ignore = path.join(root, '.fediim', 'run', '.gitignore');
      if (!fs.existsSync(ignore)) fs.writeFileSync(ignore, '*\n');
      let headSha = '';
      try { headSha = git(root, ['rev-parse', 'HEAD']); } catch (e) {}
      fs.writeFileSync(file(root), JSON.stringify({ treeHash: after, headSha, commands, verifiedAt: new Date().toISOString() }, null, 2) + '\n');
    } catch (e) {}
  };
  return { treeHash, verifiedFor, record, isRealCi };
}
import { spawn as tmSpawn } from 'node:child_process';
import { existsSync as tmExists } from 'node:fs';
import { fileURLToPath as tmPath } from 'node:url';
const TELEMETRY_TOOL = tmPath(new URL('./telemetry.mjs', import.meta.url));
const telemetryOn = tmExists(TELEMETRY_TOOL);
function emitTelemetry(kind, name, attrs) {
  if (!telemetryOn) return;
  try { tmSpawn(process.execPath, [TELEMETRY_TOOL, 'span', kind, name, JSON.stringify(attrs)], { detached: true, stdio: 'ignore' }).unref(); } catch {}
}
// M91: one TOOL span per run, whatever the exit path.
const t0 = Date.now();
const skipped = [];
let ran = 0;
process.on('exit', (code) => emitTelemetry('TOOL', 'pr-prep', { 'fediim.command_kind': 'pr-prep', 'fediim.duration_ms': Date.now() - t0, 'fediim.exit_code': code, 'fediim.verified_cache_hit': ran === 0 && skipped.length > 0, 'fediim.compact_bytes_saved': 0 }));
const stamp = makeStamp(createRequire(import.meta.url));
const git = (args) => execFileSync('git', args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] });
const root = git(['rev-parse', '--show-toplevel']).trim();
process.chdir(root);

const issue = process.argv.includes('--issue') ? process.argv[process.argv.indexOf('--issue') + 1] : '';
if (issue !== '' && !/^\d+$/.test(issue)) { console.error('pr-prep: --issue needs a number'); process.exit(2); }

const base = resolveBaseRef(git, 'HEAD');
const changed = new Set();
if (base) for (const f of git(['diff', '--name-only', base]).split('\n')) if (f) changed.add(f);
for (const f of git(['ls-files', '--others', '--exclude-standard']).split('\n')) if (f) changed.add(f);

const globToRegex = (glob) => new RegExp('^' + glob.replace(/[.+^$}{)(|[\]\\]/g, '\\$&').replace(/\*/g, '[^/]*') + '$');
const files = [...changed].sort().filter((f) => existsSync(f));
const DOC_RE = /[.](md|txt|rst|adoc)$/i;
const docsOnly = files.length > 0 && files.every((f) => DOC_RE.test(f) || /^[.]fediim\/plans\//.test(f));
// M93: a lint fixer runs only where its tool is configured, never with the tool's defaults.
const readText = (f) => { try { return readFileSync(f, 'utf8'); } catch { return ''; } };
const TOOL_CONFIG = {
  eslint: (names, dir) => names.some((n) => n.startsWith('eslint.config.') || n.startsWith('.eslintrc')) || (names.includes('package.json') && readText(join(dir, 'package.json')).includes('"eslintConfig"')),
  biome: (names) => names.includes('biome.json') || names.includes('biome.jsonc'),
  ruff: (names, dir) => names.includes('ruff.toml') || names.includes('.ruff.toml') || (names.includes('pyproject.toml') && readText(join(dir, 'pyproject.toml')).includes('[tool.ruff')),
  ktlint: (names, dir) => names.includes('.editorconfig') && readText(join(dir, '.editorconfig')).includes('ktlint_'),
};
const hasToolConfig = (dir, tool) => {
  for (;;) {
    let names = [];
    try { names = readdirSync(dir); } catch { /* unreadable dir */ }
    if (TOOL_CONFIG[tool](names, dir)) return true;
    const parent = dirname(dir);
    if (parent === dir) return false;
    dir = parent;
  }
};
const configured = (cmd, file, toolGuard) => {
  const dir = dirname(resolve(file));
  // Prettier without a config would reformat with its defaults.
  if (cmd.includes('prettier')) return hasPrettierConfig(dir);
  const tool = toolGuard ? Object.keys(TOOL_CONFIG).find((t) => cmd.includes(t)) : undefined;
  return !tool || hasToolConfig(dir, tool);
};
const activeCopies = new Set();
const cleanCopies = () => {
  for (const p of activeCopies) { try { unlinkSync(p); } catch {} }
  activeCopies.clear();
};
process.on('exit', cleanCopies);
for (const sig of ['SIGINT', 'SIGTERM']) process.on(sig, () => { cleanCopies(); process.exit(130); });

const format = (cmd, files) => {
  for (let i = 0; i < files.length; i += 50) {
    const r = spawnSync('sh', ['-c', cmd + ' "$@"', 'sh', ...files.slice(i, i + 50)], { stdio: 'ignore' });
    if (r.error || r.status === 127 || r.status === 126) return false;
  }
  return true;
};
// #166: a file that was not formatter-clean (or fixer-clean) at the merge base is left
// alone, so a one-line edit never rewrites a legacy file. The base copies sit next to
// the files (the tool sees the same config) and are processed in one batch.
const batch = (map, verb, toolGuard) => {
  const byCommand = new Map();
  for (const file of files) {
    const glob = Object.keys(map).find((g) => globToRegex(g).test(basename(file)));
    if (!glob) continue;
    for (const cmd of [].concat(map[glob])) if (configured(cmd, file, toolGuard)) byCommand.set(cmd, [...(byCommand.get(cmd) ?? []), file]);
  }
  for (const [cmd, files] of byCommand) {
    const targets = [];
    const copies = [];
    let toolAvailable = true;
    try {
      for (const file of files) {
        let original = null;
        if (base) { try { original = execFileSync('git', ['show', base + ':' + file], { stdio: ['ignore', 'pipe', 'ignore'] }); } catch { /* new file */ } }
        if (original === null) { targets.push(file); continue; }
        const copy = join(dirname(file), '.fediim-fmt-' + process.pid + '-' + basename(file));
        writeFileSync(copy, original);
        activeCopies.add(copy);
        copies.push({ file, copy, original });
      }
      if (copies.length > 0) toolAvailable = format(cmd, copies.map((c) => c.copy));
      if (toolAvailable) {
        for (const { file, copy, original } of copies) {
          if (readFileSync(copy).equals(original)) targets.push(file);
        }
        if (targets.length > 0) toolAvailable = format(cmd, targets);
      }
    } finally {
      for (const { copy } of copies) {
        activeCopies.delete(copy);
        try { unlinkSync(copy); } catch {}
      }
    }
    if (!toolAvailable) console.log('pr-prep: skip ' + cmd + ' (tool unavailable)');
    else console.log('pr-prep: ' + verb + ' ' + targets.length + ' of ' + files.length + ' file(s) with ' + cmd);
  }
};
// The formatter runs last so it decides the final layout.
batch(FIX_MAP, 'fixed', true);
batch(FORMAT_MAP, 'formatted', false);

const results = [];
if (LINT) {
  if (docsOnly) {
    console.log('pr-prep: skip lint (docs-only diff)');
    results.push('lint skipped (docs-only)');
  } else {
    const r = spawnSync('sh', ['-c', LINT], { encoding: 'utf8' });
    if (r.status !== 0) { console.error(((r.stdout || '') + (r.stderr || '')).split('\n').slice(-40).join('\n')); console.error('pr-prep: lint failed: ' + LINT); process.exit(1); }
    results.push('lint `' + LINT + '` pass');
  }
}
if (existsSync('.fediim/ci/guards.mjs')) {
  const g = spawnSync(process.execPath, ['.fediim/ci/guards.mjs', '--static-only'], { stdio: 'inherit', env: { ...process.env, ...(base && !process.env.BASE_SHA ? { BASE_SHA: base } : {}) } });
  if (g.status !== 0) { console.error('pr-prep: guards.mjs --static-only failed'); process.exit(1); }
  results.push('guards --static-only pass');
}
for (const kind of ['build', 'test']) {
  if (!RAW[kind]) { console.log('pr-prep: skip ' + kind + ' (no command configured)'); continue; }
  if (docsOnly) { console.log('pr-prep: skip ' + kind + ' (docs-only diff)'); results.push(kind + ' skipped (docs-only)'); continue; }
  if (stamp.verifiedFor(root, [RAW[kind]])) { skipped.push(kind); console.log('pr-prep: skip ' + kind + ' (tree already verified)'); results.push(kind + ' `' + RAW[kind] + '` pass (tree already verified)'); continue; }
  ran++;
  console.log('pr-prep: run ' + kind + ': ' + RAW[kind]);
  try {
    execSync(RUN[kind], { stdio: 'inherit' });
  } catch {
    console.error('pr-prep: ' + kind + ' failed: ' + RAW[kind]);
    process.exit(1);
  }
  stamp.record(root, RAW[kind]);
  results.push(kind + ' `' + RAW[kind] + '` pass');
}
// Archive only after lint, guards, build and test are all green so failed runs keep the plan active.
if (issue && existsSync('.fediim/plans/' + issue + '.md')) {
  mkdirSync('.fediim/plans/archive', { recursive: true });
  const moved = spawnSync('git', ['mv', '.fediim/plans/' + issue + '.md', '.fediim/plans/archive/' + issue + '.md'], { stdio: 'ignore' });
  if (moved.status !== 0) renameSync('.fediim/plans/' + issue + '.md', '.fediim/plans/archive/' + issue + '.md');
  console.log('pr-prep: archived .fediim/plans/' + issue + '.md');
  for (const kind of ['build', 'test']) if (RAW[kind]) stamp.record(root, RAW[kind]);
}
const tree = stamp.treeHash(root).slice(0, 7);
console.log('pr-prep: tree verified (' + tree + ')');
let head = '';
try { head = git(['rev-parse', '--short=7', 'HEAD']).trim(); } catch { /* no commits */ }
console.log('\nLocal verification: ' + results.map((r) => r.split(' ')[0]).join(', ') + ' → pass @ ' + head + ' (tree ' + tree + ')');
for (const r of results) console.log('- ' + r);
