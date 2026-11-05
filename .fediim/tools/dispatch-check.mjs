// <!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
// fediim dispatch-check — may these issues run in parallel? (parallel-dispatch
// rule): each needs Files Affected paths, no undecided operator task, and no
// two may overlap — counting files .fediim/guards.json implicitSharedFiles
// implies, e.g. {"when": "^src/api/", "implies": ["openapi.yaml"]}.
// Usage: node .fediim/tools/dispatch-check.mjs <issue#> <issue#> [...]
//        node .fediim/tools/dispatch-check.mjs --from <issues.json>
//        node .fediim/tools/dispatch-check.mjs --plan <plan.md> [--prs <prs.json>]
// Exit 0 = dispatch in parallel (or no open-PR conflict), 1 = conflict/ineligible,
// 2 = bad input or a failed gh fetch.
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';

const usage = () => {
  console.error('usage: node dispatch-check.mjs <issue#> <issue#> [...] | --from <issues.json> | --plan <plan.md> [--prs <prs.json>]');
  process.exit(2);
};
const args = process.argv.slice(2);

// Equal paths overlap, and so does a directory with anything beneath it.
const overlaps = (a, b) => {
  const x = a.replace(/\/+$/, '');
  const y = b.replace(/\/+$/, '');
  return x === y || y.startsWith(x + '/') || x.startsWith(y + '/');
};

let implied = [];
const guardsPath = '.fediim/guards.json';
if (existsSync(guardsPath)) {
  try {
    const rules = JSON.parse(readFileSync(guardsPath, 'utf8')).implicitSharedFiles || [];
    implied = rules.map(r => ({ when: new RegExp(r.when), implies: r.implies.map(String) }));
  } catch (err) {
    console.error('[dispatch-check] ' + guardsPath + ' implicitSharedFiles: ' + String(err.message).split('\n')[0]);
    process.exit(2);
  }
}
const withImplied = (paths) => [...new Set([...paths, ...implied.filter(r => paths.some(p => r.when.test(p))).flatMap(r => r.implies)])];

if (args[0] === '--plan') {
  const planPath = args[1];
  if (!planPath || (args[2] !== undefined && (args[2] !== '--prs' || !args[3])) || args.length > 4) usage();
  let planText;
  let prs;
  try {
    planText = readFileSync(planPath, 'utf8');
    prs = args[2] === '--prs'
      ? JSON.parse(readFileSync(args[3], 'utf8'))
      : JSON.parse(execFileSync('gh', ['pr', 'list', '--state', 'open', '--limit', '1000', '--json', 'number,files,headRefName,body,title'], { encoding: 'utf8' }));
  } catch (err) {
    console.error('[dispatch-check] ' + String(err.message).split('\n')[0]);
    process.exit(2);
  }
  if (!Array.isArray(prs)) usage();
  const planIssue = planText.match(/^#\s+.*#(\d+)\b/m)?.[1] || planPath.match(/(?:^|\/)(\d+)\.md$/)?.[1] || '';
  const stepPaths = [];
  let inSteps = false;
  for (const raw of planText.split(/\r?\n/)) {
    const line = raw.trim();
    if (/^##\s+Steps\b/i.test(line)) { inSteps = true; continue; }
    if (/^##\s+/.test(line)) { inSteps = false; continue; }
    if (!inSteps && !/^[-*\s]*\**S\d+\b/.test(line)) continue;
    const m = line.replace(/^[-*]\s+/, '').replace(/^\*\*(S\d+)\*\*/, '$1').match(/^S\d+\s+\x60?([^\s\x60:]+)(?::\d+(?:-\d+)?)?\x60?(?:\s+\([^)]+\))?\s*(?:::|[—–-]\s)/);
    if (m) stepPaths.push(m[1].replace(/\\/g, '/').replace(/^\.\//, ''));
  }
  const planPaths = withImplied(stepPaths);
  const problems = [];
  for (const pr of prs) {
    if (planIssue) {
      const ownBranch = new RegExp('(?:^|[/-])' + planIssue + '(?:$|[/-])').test(String(pr.headRefName || ''));
      const ownRef = new RegExp('\\b(?:close[sd]?|fix(?:e[sd])?|resolve[sd]?|refs?)\\s*:?\\s*(?:[\\w.-]+\\/[\\w.-]+)?#' + planIssue + '\\b', 'i').test(String(pr.body || '') + ' ' + String(pr.title || ''));
      if (String(pr.number) === planIssue || ownBranch || ownRef) continue;
    }
    const rawFiles = (pr.files || []).map(f => (typeof f === 'string' ? f : (f && f.path) || '')).filter(Boolean).map(p => p.replace(/\\/g, '/').replace(/^\.\//, ''));
    const prPaths = withImplied(rawFiles);
    const shared = planPaths.filter(p => prPaths.some(q => overlaps(p, q)));
    if (shared.length > 0) {
      problems.push('CONFLICT plan and PR #' + pr.number + ' — ' + [...new Set(shared)].join(', '));
    }
  }
  if (problems.length > 0) {
    for (const problem of problems) console.log(problem);
    console.log('dispatch-check: FAIL — narrow the plan or wait for the conflicting PR.');
    process.exit(1);
  }
  console.log('dispatch-check: OK — plan has no path conflicts with open PRs.');
  process.exit(0);
}

let issues;
try {
  if (args[0] === '--from') {
    if (!args[1]) usage();
    issues = JSON.parse(readFileSync(args[1], 'utf8'));
  } else {
    if (args.some(a => !/^#?\d+$/.test(a))) usage();
    issues = args.map(a => JSON.parse(execFileSync('gh', ['issue', 'view', a.replace('#', ''), '--json', 'number,body,comments'], { encoding: 'utf8' })));
  }
} catch (err) {
  console.error('[dispatch-check] ' + String(err.message).split('\n')[0]);
  process.exit(2);
}
if (!Array.isArray(issues) || issues.length < 2) {
  console.error('[dispatch-check] a parallel batch needs at least 2 issues.');
  process.exit(2);
}

// Lines under "## Files Affected" (optional suffix allowed) up to the next
// "## "; tags, list markers, backticks and comments are dropped and the first
// token of each line is the path.
function filesAffected(body) {
  const lines = String(body || '').replace(/<!--[\s\S]*?-->/g, '').split(/\r?\n/);
  const start = lines.findIndex(l => /^##\s+Files Affected\b/i.test(l.trim()));
  if (start === -1) return [];
  const paths = [];
  for (const raw of lines.slice(start + 1)) {
    if (/^##\s/.test(raw.trim())) break;
    const line = raw
      .replace(/\[(NEW|MODIFY|DELETE)\]/gi, ' ')
      .replace(/\x60/g, ' ')
      .replace(/^\s*(?:[-*+]|\d+\.)\s+/, '')
      .trim();
    const token = line.split(/\s+/)[0];
    if (token) paths.push(token.replace(/\\/g, '/').replace(/^\.\//, ''));
  }
  return paths;
}

// Unchecked "## Tasks" items that wait on the operator. They block at plan
// time, so the issue can't run until an "Operator decision" comment answers.
function undecidedTasks(issue) {
  const decided = (issue.comments || []).some(c => /^\W*Operator decision\b/i.test(String(c.body || '').trim()));
  if (decided) return [];
  const lines = String(issue.body || '').replace(/<!--[\s\S]*?-->/g, '').split(/\r?\n/);
  const start = lines.findIndex(l => /^##\s+Tasks\b/i.test(l.trim()));
  if (start === -1) return [];
  const found = [];
  for (const raw of lines.slice(start + 1)) {
    if (/^##\s/.test(raw.trim())) break;
    const task = raw.match(/^\s*[-*+]\s+\[ \]\s+(.*)$/);
    if (task && /^(?:\*\*)?(?:Decide\b|Gate first\b)|\b(?:operator confirms|needs a human decision|operator decision)\b/i.test(task[1])) {
      found.push(task[1].trim());
    }
  }
  return found;
}

const parsed = issues.map(i => {
  const declared = filesAffected(i.body);
  return { number: i.number, declared, paths: withImplied(declared), undecided: undecidedTasks(i) };
});
const problems = [];
for (const issue of parsed) {
  if (issue.declared.length === 0) problems.push('INELIGIBLE #' + issue.number + ' — no Files Affected paths');
  for (const task of issue.undecided) problems.push('INELIGIBLE #' + issue.number + ' — undecided operator task: ' + task);
}
for (let i = 0; i < parsed.length; i++) {
  for (let j = i + 1; j < parsed.length; j++) {
    const shared = parsed[i].paths.filter(p => parsed[j].paths.some(q => overlaps(p, q)));
    if (shared.length > 0) {
      problems.push('CONFLICT #' + parsed[i].number + ' and #' + parsed[j].number + ' — ' + [...new Set(shared)].join(', '));
    }
  }
}

if (problems.length > 0) {
  for (const problem of problems) console.log(problem);
  console.log('dispatch-check: FAIL — run the listed issues alone or serially.');
  process.exit(1);
}
console.log('dispatch-check: OK — ' + parsed.map(i => '#' + i.number).join(', ') + ' can run in parallel.');
