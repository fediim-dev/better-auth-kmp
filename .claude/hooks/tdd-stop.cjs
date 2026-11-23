const fs = require('fs');
const path = require('path');
const cp = require('child_process');

let payload = {};
try {
  const stdinText = fs.readFileSync(0, 'utf-8');
  payload = JSON.parse(stdinText);
} catch (err) {
  // ignore
}

const cwd = payload.cwd || process.cwd();
function realPath(p) {
  let dir = p;
  let rest = '';
  while (!fs.existsSync(dir) && path.dirname(dir) !== dir) {
    rest = path.join(path.basename(dir), rest);
    dir = path.dirname(dir);
  }
  try {
    return path.join(fs.realpathSync(dir), rest);
  } catch (err) {
    return p;
  }
}
let repoRoot = '';
{
  let dir = realPath(cwd);
  for (;;) {
    if (fs.existsSync(path.join(dir, '.git'))) { repoRoot = dir; break; }
    const parent = path.dirname(dir);
    if (parent === dir) break;
    dir = parent;
  }
}
if (!repoRoot) {
  try {
    repoRoot = require('child_process').execFileSync('git', ['-C', cwd, 'rev-parse', '--show-toplevel'], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim() || cwd;
  } catch (err) {
    repoRoot = cwd;
  }
}
repoRoot = realPath(repoRoot);
const tddRequiredPath = path.resolve(repoRoot, '.fediim', 'hooks', 'tdd-required');

const TEST_CMD = "./gradlew check -q";
const RAW_TEST_CMD = "./gradlew check -q";
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
const stamp = makeStamp(require);
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

const DOC_STOP_RE = /[.](md|txt|rst|adoc)$/i;
function isDocOnlyStop(root) {
  if (stamp.isRealCi()) return false;
  const runGit = (args) => {
    try { return cp.execFileSync('git', ['-C', root, ...args], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).replace(/\r?\n$/, ''); }
    catch (e) { return null; }
  };
  if (runGit(['rev-parse', '--git-dir']) === null) return false;
  const status = runGit(['status', '--porcelain', '-uall']);
  let files = status ? status.split('\n').filter(Boolean).map((l) => l.slice(3).split(' -> ').pop().replace(/^"|"$/g, '')) : [];
  if (!files.length) {
    const base = resolveBaseRef(runGit, 'HEAD');
    const diff = base ? runGit(['diff', '--name-only', base, 'HEAD']) : '';
    if (diff) files = diff.split('\n').filter(Boolean);
  }
  return files.length > 0 && files.every((f) => DOC_STOP_RE.test(f) || /^[.]fediim\/plans\//.test(f));
}
const tddRetriesPath = (root) => path.join(root, '.fediim', 'run', 'tdd-stop-retries.json');
function readTddRetries(root) {
  try { return JSON.parse(fs.readFileSync(tddRetriesPath(root), 'utf8')); } catch (e) { return { treeHash: '', count: 0 }; }
}
function writeTddRetries(root, treeHash, count) {
  try {
    const dir = path.join(root, '.fediim', 'run');
    fs.mkdirSync(dir, { recursive: true });
    const ig = path.join(dir, '.gitignore');
    if (!fs.existsSync(ig)) fs.writeFileSync(ig, '*\n');
    fs.writeFileSync(tddRetriesPath(root), JSON.stringify({ treeHash, count }) + '\n');
  } catch (e) {}
}
function clearTddRetries(root) {
  try { if (fs.existsSync(tddRetriesPath(root))) fs.unlinkSync(tddRetriesPath(root)); } catch (e) {}
}
function runTddGate(runCmd, root, rawCmd, partial, stamp, handleBusy) {
  const curHash = stamp.treeHash(root) || 'no-git';
  if (!stamp.isRealCi()) {
    if (payload.stop_hook_active === true || payload.continuation_active === true) {
      const flag = payload.stop_hook_active === true ? 'stop_hook_active' : 'continuation_active';
      return { ok: true, notice: 'TDD Gate: ' + flag + ' is set \u2014 allowing stop so you can inspect and recover.' };
    }
    const prev = readTddRetries(root);
    if (prev.treeHash === curHash && prev.count >= 2) {
      return { ok: true, notice: 'TDD Gate: stopped blocking after 2 consecutive failures on the same tree \u2014 inspect diagnostics and fix before pr-prep.' };
    }
  }
  try {
    cp.execSync(runCmd, { cwd: root, encoding: 'utf8', maxBuffer: 16 * 1024 * 1024, stdio: ['ignore', 'pipe', 'pipe'] });
    if (!partial) stamp.record(root, rawCmd);
    clearTddRetries(root);
    return { ok: true };
  } catch (err) {
    if (handleBusy && err && err.status === 75) return { ok: true, busy: true };
    const prev = readTddRetries(root);
    writeTddRetries(root, curHash, (prev.treeHash === curHash ? prev.count : 0) + 1);
    const combined = String((err && err.stdout) || '') + '\n' + String((err && err.stderr) || '');
    const tail = combined.split(/\r?\n/).filter((l) => l.trim()).slice(-25).join('\n');
    return { ok: false, diag: 'TDD Gate: Tests failed (' + runCmd + ').' + (tail ? '\n' + tail : '') };
  }
}

// M87+: skip docs-only or already-verified trees; on an unverified tree run
// affected-tests.mjs when present, with a 2-retry circuit breaker and diagnostics.
if (TEST_CMD && fs.existsSync(tddRequiredPath) && !isDocOnlyStop(repoRoot) && !stamp.verifiedFor(repoRoot, [RAW_TEST_CMD])) {
  const partial = !stamp.isRealCi() && fs.existsSync(path.join(repoRoot, '.fediim', 'tools', 'affected-tests.mjs'));
  const runCmd = partial ? 'node .fediim/tools/affected-tests.mjs --light' : TEST_CMD;
  const res = runTddGate(runCmd, repoRoot, RAW_TEST_CMD, partial, stamp, false);
  if (res.busy) {
    console.error("build slot busy (another session) — TDD check skipped; rerun the tests");
    process.exit(0);
  }
  if (res.notice) {
    console.error(res.notice);
    process.exit(0);
  }
  if (!res.ok) {
    console.error(res.diag);
    process.exit(2);
  }
}
process.exit(0);
