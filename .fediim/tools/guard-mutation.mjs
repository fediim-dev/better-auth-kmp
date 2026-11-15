// <!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
// fediim guard-mutation — prove a test fails when a new guard branch is disabled.
// Usage: node .fediim/tools/guard-mutation.mjs <mutations.json | -> [--cmd <test>] [--build <build>]
// Input: JSON array of { "id", "file" (repo-relative), "find", "replace" }; "-" reads stdin.
// Each "find" must occur exactly once in a file with no uncommitted changes. Files are restored after every row.
// Exit: 0 all rows CAUGHT, final run green, tree unchanged; 1 otherwise; 2 refused (bad input, dirty file, red baseline).
import { execSync, execFileSync } from 'node:child_process';
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { resolve, relative, isAbsolute } from 'node:path';

const BAKED_TEST = "./gradlew check -q";
const BAKED_BUILD = "./gradlew compileAllTargets -q";

function refuse(message) {
  console.error('[guard-mutation] ' + message);
  process.exit(2);
}

const args = process.argv.slice(2);
let testCmd = BAKED_TEST;
let buildCmd = BAKED_BUILD;
let input;
for (let i = 0; i < args.length; i++) {
  if (args[i] === '--cmd' || args[i] === '--build') {
    if (args[i + 1] === undefined) refuse(args[i] + ' needs a value');
    if (args[i] === '--cmd') testCmd = args[++i]; else buildCmd = args[++i];
  } else if (input === undefined) {
    input = args[i];
  } else {
    refuse('unexpected argument: ' + args[i]);
  }
}
if (input === undefined) refuse('usage: node guard-mutation.mjs <mutations.json | -> [--cmd <test>] [--build <build>]');

let mutations;
try {
  mutations = JSON.parse(readFileSync(input === '-' ? 0 : input, 'utf8'));
} catch (err) {
  refuse('cannot read mutations from ' + input + ': ' + String(err.message).split('\n')[0]);
}
if (!Array.isArray(mutations) || mutations.length === 0) refuse('mutations must be a non-empty JSON array of { id, file, find, replace }');
const seen = new Set();
mutations.forEach((m, i) => {
  for (const key of ['id', 'file', 'find', 'replace']) {
    if (!m || typeof m[key] !== 'string') refuse('mutation #' + (i + 1) + ': "' + key + '" must be a string');
  }
  if (m.id === '' || m.file === '' || m.find === '') refuse('mutation #' + (i + 1) + ': id, file and find must not be empty');
  if (seen.has(m.id)) refuse('duplicate mutation id: ' + m.id);
  seen.add(m.id);
});
if (!testCmd) refuse('no test command: set [commands].test in fediim.toml and run fediim sync, or pass --cmd "<test>"');

let root;
try {
  root = execFileSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim();
} catch {
  refuse('not inside a git repository');
}

function git(...gitArgs) {
  return execFileSync('git', gitArgs, { cwd: root, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] });
}

const paths = new Map();
for (const m of mutations) {
  const abs = resolve(root, m.file);
  const rel = relative(root, abs);
  if (rel.startsWith('..') || isAbsolute(rel)) refuse(m.id + ': ' + m.file + ' is outside the repository');
  if (!existsSync(abs)) refuse(m.id + ': ' + m.file + ' does not exist');
  try {
    git('diff', '--quiet', 'HEAD', '--', rel);
  } catch {
    refuse(m.file + ' has uncommitted changes; commit them first (a WIP commit is fine)');
  }
  const count = readFileSync(abs, 'utf8').split(m.find).length - 1;
  if (count !== 1) refuse(m.id + ': "find" must occur exactly once in ' + m.file + ' but occurs ' + count + ' times');
  paths.set(m.id, abs);
}

function run(cmd) {
  try {
    execSync(cmd, { cwd: root, stdio: 'pipe', shell: true });
    return { ok: true, out: '' };
  } catch (err) {
    return { ok: false, out: String(err.stdout || '') + String(err.stderr || '') };
  }
}

// Build (if set) then test; the first failure wins.
function runAll() {
  if (buildCmd) {
    const built = run(buildCmd);
    if (!built.ok) return built;
  }
  return run(testCmd);
}

function tail(out) {
  return out.split(/\r?\n/).filter(Boolean).slice(-20).join('\n');
}

const baseline = runAll();
if (!baseline.ok) {
  console.error(tail(baseline.out));
  refuse('baseline is red; mutations can only be judged from a green baseline');
}
// After the baseline: build/test output (dist, coverage) is then already present.
const treeBefore = git('status', '--porcelain');

let pending = null;
function restorePending() {
  if (pending) writeFileSync(pending.abs, pending.original);
  pending = null;
}
process.on('SIGINT', () => { restorePending(); process.exit(130); });
process.on('SIGTERM', () => { restorePending(); process.exit(143); });

const notCaught = [];
for (const m of mutations) {
  const abs = paths.get(m.id);
  const original = readFileSync(abs);
  pending = { abs, original };
  let result;
  try {
    writeFileSync(abs, original.toString('utf8').replace(m.find, () => m.replace));
    const built = buildCmd ? run(buildCmd) : { ok: true };
    if (!built.ok) result = 'NOT CAUGHT (build failed)';
    else result = run(testCmd).ok ? 'NOT CAUGHT' : 'CAUGHT';
  } finally {
    restorePending();
  }
  if (result !== 'CAUGHT') notCaught.push(m.id);
  console.log(m.id + ' ' + m.file + ' ' + result);
}

const final = runAll();
if (!final.ok) console.error(tail(final.out));
const tree = git('status', '--porcelain') === treeBefore ? 'clean' : 'dirty';
const caught = mutations.length - notCaught.length;
console.log('SUMMARY caught=' + caught + '/' + mutations.length + ' not-caught=' + (notCaught.join(',') || 'none') + ' final=' + (final.ok ? 'green' : 'red') + ' tree=' + tree);
process.exit(notCaught.length === 0 && final.ok && tree === 'clean' ? 0 : 1);
