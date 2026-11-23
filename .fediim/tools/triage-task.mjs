// <!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
// fediim triage-task — classify a task into Tier 0..4 (same classifier as guards.mjs).
// Usage: node .fediim/tools/triage-task.mjs --issue <issue.json | body.md | number>
//        node .fediim/tools/triage-task.mjs --verify-diff [--base <sha>] [--head <sha>] [--declared <tier>]
// Output: JSON { tier, planMode, planReview, reviewLevel, reviewers, ... }; --issue reads the
// Files Affected section and labels; --verify-diff reads git diff --numstat (base defaults to
// BASE_SHA, else merge-base origin/main). Tier 0 docs; 1 <=2 non-test files and <=40 lines in one
// module; 2 larger single-module; 3 >5 files or cross-module; 4 auth/billing/secrets/migrations/
// hooks/CI/protected paths.
import { execFileSync } from 'node:child_process';
import { readFileSync, existsSync } from 'node:fs';

const PROTECTED_PREFIXES = ["auth/","billing/"];
const BLOCKED_PREFIXES = ["src/generated/"];
const SELECTED_REVIEWERS = ["deep-reviewer","pr-reviewer","pr-reviewer-lite","security-reviewer"];
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
const SECURITY_PATH_RE = /(^|\/)(auth|authn|authz|authentication|authorization|oauth2?|rbac|tenan\w+|billing|payments?|secrets?|credentials?|migrations?)(\/|[.\-_]|$)|^[.]github\/(workflows|actions)\/|(^|\/)(jenkinsfile|[.]gitlab-ci[.]ya?ml)$|^[.](husky|githooks)\/|^[.](claude|agents|fediim)\/(hooks|settings)/i;
// Generic "hooks" dirs (React src/hooks) are not security paths; only git and agent hook dirs are.
const SECURITY_LABELS = ['security', 'safety-critical', 'needs-human-approval'];
const MODULE_ROOTS = ['src', 'lib', 'app', 'apps', 'packages', 'modules', 'internal', 'pkg', 'sources', 'services'];
const moduleOf = (file) => {
  const parts = file.split('/');
  if (parts.length === 1) return '.';
  return MODULE_ROOTS.includes(parts[0].toLowerCase()) && parts.length > 2 ? parts[0] + '/' + parts[1] : parts[0];
};
const isPlanFile = (file) => /^[.]fediim\/plans\//.test(file);
const isDocFile = (file) => DOC_RE.test(file) || isPlanFile(file);
const touchesProtected = (file) => {
  const lower = file.toLowerCase();
  return PROTECTED_PREFIXES.some((p) => lower.startsWith(p)) || BLOCKED_PREFIXES.some((p) => lower.startsWith(p));
};
function classify(rows, labels) {
  const security = [];
  const counted = new Map();
  for (const { file, lines } of rows) {
    if (touchesProtected(file) || (!isDocFile(file) && !TEST_PATH_RE.test(file) && SECURITY_PATH_RE.test(file))) security.push(file);
    if (isDocFile(file) || TEST_PATH_RE.test(file)) continue;
    counted.set(file, (counted.get(file) || 0) + lines);
  }
  const labelHit = (labels || []).some((l) => SECURITY_LABELS.includes(String(l).toLowerCase()));
  const nonTestFiles = counted.size;
  const nonTestLines = [...counted.values()].reduce((a, b) => a + b, 0);
  const modules = [...new Set([...counted.keys()].map(moduleOf))].sort();
  let tier;
  if (security.length > 0 || labelHit) tier = 4;
  else if (rows.length > 0 && nonTestFiles === 0 && rows.every((r) => isDocFile(r.file))) tier = 0;
  else if (nonTestFiles > 5 || modules.length > 1) tier = 3;
  else if (nonTestFiles <= 2 && nonTestLines <= 40) tier = 1;
  else tier = 2;
  return { tier, nonTestFiles, nonTestLines, modules, security: [...new Set(security)].sort() };
}
const PLAN_MODE = ['none', 'none', 'inline', 'planner', 'planner'];
const PLAN_REVIEW = ['none', 'none', 'check-plan', 'plan-reviewer L2', 'plan-reviewer L3'];
const LEVEL = ['L1', 'L1', 'L2', 'L2', 'L3'];
const REVIEWER_BY_LEVEL = { L1: 'pr-reviewer-lite', L2: 'pr-reviewer', L3: 'deep-reviewer' };
function plan(tier, extra) {
  const level = LEVEL[tier];
  const pick = (slug) => (SELECTED_REVIEWERS.includes(slug) ? slug : 'pr-reviewer');
  const reviewers = [pick(REVIEWER_BY_LEVEL[level])];
  if (tier === 4 && SELECTED_REVIEWERS.includes('security-reviewer')) reviewers.push('security-reviewer');
  return { tier, planMode: PLAN_MODE[tier], planReview: PLAN_REVIEW[tier], reviewLevel: level, reviewers, ...extra };
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

const args = process.argv.slice(2);
const flag = (name) => { const i = args.indexOf(name); return i < 0 ? undefined : args[i + 1]; };
const die = (msg) => { console.error('[triage-task] ' + msg); process.exit(2); };
const git = (a) => execFileSync('git', a, { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 }).trim();
// M91: one CHAIN span per classification.
const show = (p, extra) => {
  emitTelemetry('CHAIN', 'triage-task', { 'fediim.tier': p.tier, 'fediim.plan_mode': p.planMode, 'fediim.review_level': p.reviewLevel, ...extra });
  console.log(JSON.stringify(p, null, 2));
};

function issueRows(body) {
  const rows = [];
  let inSection = false;
  for (const raw of String(body).split(/\r?\n/)) {
    const line = raw.trim();
    if (/^##\s+/.test(line)) { inSection = /^##\s+Files Affected\b/i.test(line); continue; }
    if (!inSection) continue;
    const m = /^(?:[-*]\s+)?(?:\[(?:NEW|MODIFY|DELETE)\]\s*)?[\x60]?([\w@.\/-]+\/[\w@.\/-]+|[\w@-]+[.][\w]+)[\x60]?/i.exec(line);
    if (m) rows.push({ file: m[1], lines: 0 });
  }
  return rows;
}

if (args.includes('--issue')) {
  const src = flag('--issue');
  if (!src) die('--issue needs a file or issue number');
  let body;
  let labels = [];
  if (existsSync(src)) {
    const text = readFileSync(src, 'utf8');
    if (text.trimStart().startsWith('{')) {
      const data = JSON.parse(text);
      const issue = data.issue || data;
      body = String(issue.body || '');
      labels = (issue.labels || []).map((l) => (typeof l === 'string' ? l : l.name));
    } else {
      body = text;
    }
  } else if (/^\d+$/.test(src)) {
    const data = JSON.parse(execFileSync('gh', ['issue', 'view', src, '--json', 'body,labels'], { encoding: 'utf8' }));
    body = String(data.body || '');
    labels = (data.labels || []).map((l) => l.name);
  } else {
    die('no such file or issue number: ' + src);
  }
  const rows = issueRows(body);
  const c = classify(rows, labels);
  show(plan(c.tier, { files: rows.length, modules: c.modules, security: c.security }), /^\d+$/.test(src) ? { 'fediim.issue': Number(src) } : {});
} else if (args.includes('--verify-diff')) {
  const head = flag('--head') || process.env.HEAD_SHA || 'HEAD';
  let base = flag('--base') || process.env.BASE_SHA || '';
  if (!base) { base = resolveBaseRef(git, head); if (!base) die('cannot resolve diff base: pass --base or set BASE_SHA'); }
  const rows = git(['diff', '--numstat', '--no-renames', base, head]).split('\n').filter(Boolean).map((row) => {
    const [add, del, ...rest] = row.split('\t');
    return { file: rest.join('\t'), lines: (Number(add) || 0) + (Number(del) || 0) };
  });
  const c = classify(rows, []);
  const extra = { nonTestFiles: c.nonTestFiles, nonTestLines: c.nonTestLines, modules: c.modules, security: c.security };
  const declared = flag('--declared');
  if (declared !== undefined) {
    extra.declared = Number(declared);
    extra.promoted = c.tier > Number(declared);
  }
  show(plan(c.tier, extra), { 'fediim.pr_loc': rows.reduce((n, r) => n + r.lines, 0), 'fediim.pr_files': rows.length });
} else {
  die('usage: --issue <file|number> | --verify-diff [--base <sha>] [--head <sha>] [--declared <tier>]');
}
