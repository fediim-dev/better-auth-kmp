// <!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
// fediim review-package — write the diff a reviewer reads to a file.
// Usage: node .fediim/tools/review-package.mjs <plan> <base> <head>
// Writes .fediim/run/<n>/review-<base7>..<head7>.diff with fixed sections
// ## Verification (.fediim/run/verified.json vs the current tree), ## Commits, ## Stat,
// ## Diff (-U3; lockfiles, snapshots and goldens collapse to a one-line notice, their
// counts stay in ## Stat) and prints the path. Exit 3 = base is not
// an ancestor of head, or the range is empty; 2 = bad input.
import { execFileSync, spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';

const TOOL = 'review-package';
import { spawn as tmSpawn } from 'node:child_process';
import { existsSync as tmExists } from 'node:fs';
import { fileURLToPath as tmPath } from 'node:url';
const TELEMETRY_TOOL = tmPath(new URL('./telemetry.mjs', import.meta.url));
const telemetryOn = tmExists(TELEMETRY_TOOL);
function emitTelemetry(kind, name, attrs) {
  if (!telemetryOn) return;
  try { tmSpawn(process.execPath, [TELEMETRY_TOOL, 'span', kind, name, JSON.stringify(attrs)], { detached: true, stdio: 'ignore' }).unref(); } catch {}
}
const PLAN_PATH_RE = /(?:^|\/)\.fediim\/plans\/(?:archive\/)?([^/]+)\.md$/;
function runDir(plan) {
  const m = PLAN_PATH_RE.exec(plan.replace(/\\/g, '/'));
  if (!m) { console.error('[' + TOOL + '] not a plan path (.fediim/plans/[archive/]<n>.md): ' + plan); process.exit(2); }
  const dir = '.fediim/run/' + m[1];
  mkdirSync(dir, { recursive: true });
  const ignore = '.fediim/run/.gitignore';
  if (!existsSync(ignore)) writeFileSync(ignore, '*\n');
  return dir;
}
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
function makeStamp(req) {
  const cp = req('child_process');
  const fs = req('fs');
  const path = req('path');
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
  // True when every command passed on exactly this tree. CI never short-circuits.
  const verifiedFor = (cwd, commands) => {
    if (process.env.CI) return false;
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
  return { treeHash, verifiedFor, record };
}

const [plan, baseRef, headRef] = process.argv.slice(2);
if (!plan || !baseRef || !headRef) {
  console.error('usage: node review-package.mjs <plan> <base> <head>');
  process.exit(2);
}
const git = (...args) => execFileSync('git', args, { encoding: 'utf8', maxBuffer: 256 * 1024 * 1024 });
const resolveRef = (ref) => {
  try { return git('rev-parse', '--verify', ref + '^{commit}').trim(); } catch {
    console.error('[review-package] cannot resolve ' + ref);
    process.exit(2);
  }
};
const dir = runDir(plan);
const base = resolveRef(baseRef);
const head = resolveRef(headRef);
if (spawnSync('git', ['merge-base', '--is-ancestor', base, head]).status !== 0) {
  console.error('[review-package] ' + baseRef + ' is not an ancestor of ' + headRef);
  process.exit(3);
}
if (Number(git('rev-list', '--count', base + '..' + head).trim()) === 0) {
  console.error('[review-package] empty range ' + baseRef + '..' + headRef);
  process.exit(3);
}
const range = base + '..' + head;
const section = (title, body) => '## ' + title + '\n' + body.trimEnd() + '\n';
const verification = () => {
  let v;
  try { v = JSON.parse(readFileSync('.fediim/run/verified.json', 'utf8')); } catch { return 'not verified (no .fediim/run/verified.json)'; }
  const current = makeStamp(createRequire(import.meta.url)).treeHash(process.cwd());
  return 'tree ' + String(v.treeHash).slice(0, 7) + ' — ' + (current && current === v.treeHash ? 'matches the current tree' : 'does NOT match the current tree (stale)') + '\ncommands: ' + (v.commands || []).join(' ; ');
};
const lockFiles = git('diff', '--no-ext-diff', '--name-only', range).split('\n').filter((f) => f && isLockFile(f));
const diff = git('diff', '--no-ext-diff', '--no-color', '-U3', range, '--', '.', ...lockFiles.map((f) => ':(exclude,literal)' + f));
const text = [
  section('Verification', verification()),
  section('Commits', git('log', '--abbrev=7', '--format=%h %s', range)),
  section('Stat', git('diff', '--no-ext-diff', '--stat', range)),
  section('Diff', (lockFiles.length ? '(' + lockFiles.length + ' lockfile/snapshot/golden file(s) collapsed to ## Stat: ' + lockFiles.join(', ') + ')\n' : '') + diff),
].join('\n');
const file = dir + '/review-' + base.slice(0, 7) + '..' + head.slice(0, 7) + '.diff';
// M91: a CHAIN span per review package; fix_cycle = review packages already written for this plan.
const numstat = git('diff', '--no-ext-diff', '--numstat', range).split('\n').filter(Boolean);
const planId = PLAN_PATH_RE.exec(plan.replace(/\\/g, '/'))[1];
emitTelemetry('CHAIN', 'review-package', {
  ...(/^\d+$/.test(planId) ? { 'fediim.issue': Number(planId) } : {}),
  'fediim.fix_cycle': readdirSync(dir).filter((f) => /^review-.*\.diff$/.test(f)).length,
  'fediim.pr_loc': numstat.reduce((n, l) => n + (Number(l.split('\t')[0]) || 0) + (Number(l.split('\t')[1]) || 0), 0),
  'fediim.pr_files': numstat.length,
});
writeFileSync(file, text);
console.log(file);
