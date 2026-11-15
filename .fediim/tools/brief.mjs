// <!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
// fediim brief — write the implementer brief for plan steps to a file.
// Usage: node .fediim/tools/brief.mjs <plan> <id>...      (ids: S1 I2 T3 L1)
// Writes .fediim/run/<n>/brief-<ids joined by "-">.md and prints its path and
// line count: title + AC line, the ## Constraints section when present, each
// requested line verbatim, ## Lessons from recall.mjs (omitted when absent or
// empty), and the Report: path. Exit 1 = unknown id, 2 = bad input.
import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';

const TOOL = 'brief';
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

const [plan, ...ids] = process.argv.slice(2);
if (!plan || ids.length === 0 || !existsSync(plan)) {
  console.error('usage: node brief.mjs <plan> <id>...   (plan must exist)');
  process.exit(2);
}
const dir = runDir(plan);
const lines = readFileSync(plan, 'utf8').split(/\r?\n/);

const parts = [];
const title = lines.find((l) => /^#\s/.test(l));
const ac = lines.find((l) => /^AC:/.test(l.trim()));
parts.push([title, ac].filter((l) => l !== undefined).map((l) => l.trimEnd()).join('\n'));

const start = lines.findIndex((l) => /^##\s+Constraints\b/.test(l.trim()));
if (start >= 0) {
  let end = lines.findIndex((l, i) => i > start && /^##\s+/.test(l.trim()));
  if (end < 0) end = lines.length;
  parts.push(lines.slice(start, end).join('\n').trimEnd());
}

const picked = ids.map((id) => {
  const re = new RegExp('^(?:[-*]\\s+)?\\**' + id + '\\b');
  const line = lines.find((l) => re.test(l.trim()));
  if (line === undefined) {
    console.error('[brief] ' + plan + ' has no line ' + id);
    process.exit(1);
  }
  return line.trimEnd();
});
parts.push(picked.join('\n'));

if (existsSync('.fediim/tools/recall.mjs')) {
  try {
    const out = execFileSync('node', ['.fediim/tools/recall.mjs', '--phase', 'code', '--plan', plan], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).replace(/^\n+/, '').trimEnd();
    if (out) parts.push('## Lessons\n' + out);
  } catch {}
}

const key = ids.join('-');
parts.push('Report: ' + dir + '/report-' + key + '.md');
const file = dir + '/brief-' + key + '.md';
const text = parts.filter(Boolean).join('\n\n') + '\n';
writeFileSync(file, text);
console.log(file + ' (' + (text.split('\n').length - 1) + ' lines)');
