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
const STEP_RE = /^([ST]\d+)\s+\x60?([^\s\x60:]+)(?::\d+(?:-\d+)?)?\x60?(?:\s+\([^)]+\))?\s*(?:::|[—–-]\s)/;
const STEP_LIKE_RE = /^(?:[-*]\s+)?\**[ST]\d+\b/;
const PLAN_FILE_RE = /^\.fediim\/plans\/(?:archive\/)?[^/]+\.md$/;
const stepMatch = (line) => line.replace(/^[-*]\s+/, '').replace(/^\*\*([ST]\d+)\*\*/, '$1').match(STEP_RE);
function planFormatErrors(text) {
  const errors = [];
  let inSection = false;
  for (const rawLine of String(text).split(/\r?\n/)) {
    const line = rawLine.trim();
    if (/^##\s+(Steps|Tests)\b/.test(line)) { inSection = true; continue; }
    if (/^##\s+/.test(line)) { inSection = false; continue; }
    if (/^AC:/.test(line)) {
      for (const entry of line.slice(3).split(/[·;]/).map((e) => e.trim()).filter(Boolean)) {
        const m = entry.match(/^(AC\d+)\s*(?:→|->)\s*(.+)$/);
        const ids = m ? m[2].split(/[\s,]+/).filter(Boolean) : [];
        const assumptions = ids.filter((id) => /^L\d+$/.test(id));
        if (!m) errors.push('AC entry not in \x60AC<n>→S<n>,T<n>\x60 form: ' + entry);
        else if (assumptions.length) errors.push(m[1] + ' maps to assumption ' + assumptions.join(',') + ', but an L<n> line never stands in for an acceptance criterion: ' + entry);
        else if (!ids.every((id) => /^[ST]\d+$/.test(id))) errors.push(m[1] + ' must map to S<n>/T<n> ids only: ' + entry);
      }
      continue;
    }
    if (inSection && STEP_LIKE_RE.test(line) && !stepMatch(line)) {
      errors.push('line not in planner format (\x60S<n> <path> — <change>\x60, one path per line): ' + line);
    } else if (inSection && STEP_LIKE_RE.test(line) && /\bonly if\b/i.test(line)) {
      errors.push('conditional step: guards Check E fails when the condition does not happen; move the file into an L<n> line or the verification step: ' + line);
    }
  }
  return errors;
}
function planScope(text, implicit) {
  const named = new Set();
  const justified = [];
  let section = '';
  for (const raw of String(text).split(/\r?\n/)) {
    const line = raw.trim();
    const heading = /^##\s+(\w+)/.exec(line);
    if (heading) { section = heading[1]; continue; }
    if (/^(?:[-*]\s+)?\**L\d+\b/.test(line)) { justified.push(line); continue; }
    if (section !== 'Steps' && section !== 'Tests') continue;
    const m = stepMatch(line);
    if (m) named.add(m[2].replace(/\\/g, '/').replace(/^\.\//, ''));
  }
  const implied = (implicit || []).filter((r) => [...named].some((p) => r.when.test(p))).flatMap((r) => r.implies);
  return { paths: [...named, ...implied.map(String)], justified };
}
const inPlanScope = (scope, file) => scope.paths.some((p) => file === p || file.startsWith(p.replace(/\/+$/, '') + '/')) || scope.justified.some((l) => l.includes(file));
// The plan of a branch named after its issue number: .fediim/plans/<n>.md or archive/<n>.md.
const branchPlans = (branch, exists) => {
  const n = /(?:^|[\/-])#?(\d+)(?=-|$)/.exec(branch || '');
  return n ? ['.fediim/plans/' + n[1] + '.md', '.fediim/plans/archive/' + n[1] + '.md'].filter(exists) : [];
};

const fs = require('fs');
const path = require('path');
const cp = require('child_process');

let payload = {};
try {
  payload = JSON.parse(fs.readFileSync(0, 'utf-8'));
} catch (err) {
  // no payload: fall back to argv for the mode
}
const cwd = payload.cwd || (payload.workspacePaths || [])[0] || process.cwd();
const git = (args) => {
  try {
    return cp.execFileSync('git', args, { cwd, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim();
  } catch (e) {
    return '';
  }
};
function findRoot(start) {
  let dir = path.resolve(start);
  for (;;) {
    if (fs.existsSync(path.join(dir, '.git'))) return dir;
    const parent = path.dirname(dir);
    if (parent === dir) break;
    dir = parent;
  }
  return git(['rev-parse', '--show-toplevel']) || start;
}
const root = findRoot(cwd);
const exists = (rel) => fs.existsSync(path.join(root, rel));
const read = (rel) => { try { return fs.readFileSync(path.join(root, rel), 'utf8'); } catch (e) { return ''; } };

function activePlan(branch) {
  const byBranch = branchPlans(branch, exists)[0];
  if (byBranch) return byBranch;
  let best = '';
  let bestTime = -1;
  try {
    for (const f of fs.readdirSync(path.join(root, '.fediim', 'plans')).sort()) {
      if (!f.endsWith('.md')) continue;
      const t = fs.statSync(path.join(root, '.fediim', 'plans', f)).mtimeMs;
      if (t > bestTime) { best = '.fediim/plans/' + f; bestTime = t; }
    }
  } catch (e) {}
  return best;
}
function readSpecAnchor(planPath) {
  try {
    const raw = JSON.parse(read('.fediim/run/spec-anchor.json'));
    if (raw && (raw.goal || (Array.isArray(raw.acs) && raw.acs.length))) {
      return {
        ...(raw.issue !== undefined ? { issue: raw.issue } : {}),
        ...(raw.goal ? { goal: String(raw.goal).trim() } : {}),
        ...(Array.isArray(raw.acs) ? { acs: raw.acs.map(String).filter(Boolean).slice(0, 4) } : {}),
      };
    }
  } catch (e) {}
  if (!planPath) return null;
  const lines = read(planPath).split(/\r?\n/);
  let sec = '';
  let goal = '';
  const acs = [];
  for (const raw of lines) {
    const t = raw.trim();
    if (/^##\s+Goal\b/i.test(t)) { sec = 'goal'; continue; }
    if (/^##\s+(?:Acceptance Criteria|Definition of Done)\b/i.test(t)) { sec = 'acs'; continue; }
    if (/^##\s+/.test(t)) { sec = ''; continue; }
    if (!t) continue;
    if (sec === 'goal' && !goal) goal = t;
    else if (sec === 'acs' && acs.length < 4) acs.push(t.replace(/^[-*]\s*(?:\[[ x]\]\s*)?/, ''));
  }
  return goal || acs.length ? { ...(goal ? { goal } : {}), ...(acs.length ? { acs } : {}) } : null;
}
let verified = null;
try { verified = JSON.parse(read('.fediim/run/verified.json')); } catch (e) {}
function writeCheckpoint() {
  const branch = git(['symbolic-ref', '--short', '-q', 'HEAD']);
  const planPath = activePlan(branch);
  const specAnchor = readSpecAnchor(planPath);
  const checkpoint = {
    branch,
    planPath,
    diffStat: git(['diff', '--shortstat', 'HEAD']),
    verifiedTreeHash: verified && verified.treeHash ? verified.treeHash : '',
    lastTestLog: exists('.fediim/run/last-test.log') ? '.fediim/run/last-test.log' : '',
    ...(specAnchor ? { specAnchor } : {}),
  };
  try {
    fs.mkdirSync(path.join(root, '.fediim', 'run'), { recursive: true });
    const ignore = path.join(root, '.fediim', 'run', '.gitignore');
    if (!fs.existsSync(ignore)) fs.writeFileSync(ignore, '*\n');
    fs.writeFileSync(path.join(root, '.fediim', 'run', 'checkpoint.json'), JSON.stringify(checkpoint, null, 2) + '\n');
  } catch (e) {}
  return { branch, planPath, specAnchor };
}

if (process.argv[2] === 'checkpoint' || (process.argv[2] === undefined && payload.hook_event_name === 'PreCompact')) {
  writeCheckpoint();
  process.exit(0);
}

const MAX = 25;
const branch = git(['symbolic-ref', '--short', '-q', 'HEAD']);
const planPath = activePlan(branch);
const specAnchor = readSpecAnchor(planPath);
const stamp = makeStamp(require);
const verifiedState = !verified || !verified.treeHash ? 'none' : stamp.treeHash(root) === verified.treeHash ? 'match' : 'stale';
const planLines = planPath ? read(planPath).split(/\r?\n/) : [];
const stepOf = new Map();
let section = '';
for (const raw of planLines) {
  const line = raw.trim();
  const heading = /^##\s+(\w+)/.exec(line);
  if (heading) { section = heading[1]; continue; }
  if (section !== 'Steps' && section !== 'Tests') continue;
  const m = stepMatch(line);
  if (!m) continue;
  const p = m[2].replace(/\\/g, '/').replace(/^\.\//, '');
  stepOf.set(p, (stepOf.get(p) || []).concat(m[1]));
}
const dirty = git(['status', '--porcelain', '-uall']).split('\n').filter(Boolean)
  .map((l) => l.replace(/^\s*\S+\s+/, '').replace(/^.* -> /, ''))
  .filter((f) => !f.startsWith('.fediim/')).sort();
const dirtyLines = dirty.slice(0, 5).map((f) => {
  const ids = [...stepOf].filter(([p]) => f === p || f.startsWith(p.replace(/\/+$/, '') + '/')).flatMap(([, v]) => v);
  return 'dirty: ' + f + (ids.length ? ' -> ' + [...new Set(ids)].join(',') : '');
});
if (dirty.length > 5) dirtyLines.push('dirty: +' + (dirty.length - 5) + ' more');

const head = ['State Capsule (after compaction)', 'branch: ' + (branch || '(detached)'), 'plan: ' + (planPath || 'none')];
if (specAnchor && specAnchor.goal) head.push('goal: ' + specAnchor.goal.replace(/\s+/g, ' ').slice(0, 120));
const ac = planLines.filter((l) => /^AC:/.test(l.trim())).slice(0, 3).map((l) => l.trim());
const tail = [...dirtyLines, 'verified: ' + verifiedState];
if (specAnchor && Array.isArray(specAnchor.acs)) {
  for (const s of specAnchor.acs.slice(0, 2)) {
    if (head.length + ac.length + tail.length < MAX) ac.push('spec: ' + String(s).replace(/\s+/g, ' ').slice(0, 110));
  }
}
const cStart = planLines.findIndex((l) => /^##\s+Constraints\b/.test(l.trim()));
let constraints = [];
if (cStart >= 0) {
  let cEnd = planLines.findIndex((l, i) => i > cStart && /^##\s+/.test(l.trim()));
  if (cEnd < 0) cEnd = planLines.length;
  constraints = planLines.slice(cStart, cEnd).map((l) => l.trimEnd());
  while (constraints.length && constraints[constraints.length - 1] === '') constraints.pop();
}
const room = MAX - head.length - ac.length - tail.length;
if (constraints.length > room) constraints = room >= 2 ? [...constraints.slice(0, room - 1), '... see ' + planPath] : [];
console.log([...head, ...ac, ...constraints, ...tail].join('\n'));
process.exit(0);
