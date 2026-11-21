// <!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
// fediim pr-body-lint — catches PR-body claims that drifted from the code.
// Usage: node .fediim/tools/pr-body-lint.mjs <body-file|-> [--plan <plan>] [--fix]
// Fails (exit 1) on a path:line citation whose file is missing at HEAD or whose line is out of
// range, and on a citation whose backticked snippet sits on exactly one other line (shifted) or
// whose file moved (one tracked file of that name). --fix rewrites those two in the body file.
// Bare names untracked at HEAD (host:port) are not citations; fenced blocks are skipped.
// Warnings only (exit unchanged): a snippet found on no line or several, a "## Tests" promise
// ("PR body" / "verification (") whose quoted subject is absent, "noted on" / "filed as"
// without #<n> or an issue/PR URL, unticked "- [ ]" items. Exit 2 = bad input.
import { execFileSync } from 'node:child_process';
import { readFileSync, writeFileSync } from 'node:fs';

const args = process.argv.slice(2);
const FIX = args.includes('--fix');
const planAt = args.indexOf('--plan');
const planPath = planAt >= 0 ? args[planAt + 1] : '';
const bodyPath = args.find((a, i) => !a.startsWith('--') && !(planAt >= 0 && i === planAt + 1));
if (!bodyPath || (planAt >= 0 && !planPath)) { console.error('usage: node pr-body-lint.mjs <body-file|-> [--plan <plan>] [--fix]'); process.exit(2); }
if (FIX && bodyPath === '-') { console.error('pr-body-lint: --fix needs a body file, not stdin'); process.exit(2); }
const read = (p) => { try { return readFileSync(p === '-' ? 0 : p, 'utf8'); } catch { console.error('pr-body-lint: cannot read ' + p); process.exit(2); } };
const body = read(bodyPath);
const plan = planPath ? read(planPath) : '';

const failures = [];
const warnings = [];
const fixes = [];
const fail = (msg) => failures.push(msg);

const git = (a) => { try { return execFileSync('git', a, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'], maxBuffer: 1 << 26 }); } catch { return null; } };
const files = new Map();
function headLines(path) {
  if (!files.has(path)) files.set(path, git(['show', 'HEAD:' + path])?.replace(/\n$/, '').split('\n') ?? null);
  return files.get(path);
}
let tracked = null;
const trackedAtHead = () => (tracked ??= (git(['ls-tree', '-r', '--name-only', 'HEAD']) ?? '').split('\n').filter(Boolean));

// A path with an extension, ":", a line (and optional "-end"); never the tail of a URL or longer path.
const CITE = /(?<![\w./:-])((?:\.\/)?[\w.-]+(?:\/[\w.-]+)*\.[A-Za-z][A-Za-z0-9]*):(\d+)(?:-(\d+))?/g;
// What may sit between a citation and the snippet it labels; "," and ";" end the pairing, so in
// "a.ts:1 <x>, b.ts:2 <y>" b.ts:2 labels y, not x.
const GAP = /^[\s()[\]:—–>-]*(?:\b(?:at|in|on|see|from|is)\b[\s()[\]:—–>-]*)*$/i;
const norm = (s) => s.replace(/\s+/g, ' ').trim();

function pairedSnippet(text, spans, m) {
  // The citation may itself sit in a backtick span; the snippet is the nearest other span.
  const own = spans.find((s) => s.start <= m.index && m.index < s.end);
  const start = own ? own.start : m.index;
  const end = own ? own.end : m.index + m[0].length;
  const others = spans.filter((s) => s !== own && !new RegExp(CITE.source).test(s.text));
  return others.filter((s) => s.end <= start && GAP.test(text.slice(s.end, start))).pop()
    || others.find((s) => s.start >= end && GAP.test(text.slice(end, s.start)));
}

let cites = 0;
let fenced = false;
const out = body.split('\n').map((raw, i) => {
  const cr = raw.endsWith('\r') ? '\r' : '';
  let text = cr ? raw.slice(0, -1) : raw;
  if (/^\s*(\x60\x60\x60|~~~)/.test(text)) { fenced = !fenced; return raw; }
  if (fenced) return raw;
  const where = 'body:' + (i + 1) + ': ';
  const spans = [...text.matchAll(/\x60([^\x60]+)\x60/g)].map((m) => ({ start: m.index, end: m.index + m[0].length, text: m[1] }));
  const edits = [];
  for (const m of text.matchAll(CITE)) {
    let path = m[1].replace(/^\.\//, '');
    const from = Number(m[2]);
    const to = m[3] ? Number(m[3]) : from;
    let lines = headLines(path);
    // ponytail: bare untracked names (api.example.com:443) are skipped, so a bare-name typo is not caught.
    if (!lines && !path.includes('/')) continue;
    cites++;
    if (!lines) {
      const name = path.split('/').pop();
      const same = trackedAtHead().filter((f) => f === name || f.endsWith('/' + name));
      if (same.length !== 1) { fail(where + m[0] + ': ' + path + ' does not exist at HEAD' + (same.length ? '; did you mean ' + same.slice(0, 5).join(' or ') + '?' : '')); continue; }
      path = same[0];
      lines = headLines(path);
    }
    const snippet = pairedSnippet(text, spans, m);
    const inRange = from >= 1 && to >= from && to <= lines.length;
    let at = from;
    if (!inRange || (snippet && !norm(lines.slice(from - 1, to).join('\n')).includes(norm(snippet.text)))) {
      const hits = snippet ? lines.flatMap((l, j) => (norm(l).includes(norm(snippet.text)) ? [j + 1] : [])) : [];
      if (hits.length === 1) at = hits[0];
      else if (!inRange) { fail(where + m[0] + ': line out of range (' + path + ' has ' + lines.length + ' lines at HEAD)'); continue; }
      else { warnings.push(where + m[0] + ': snippet ' + JSON.stringify(snippet.text) + ' is not on that line at HEAD (' + (hits.length ? 'on ' + hits.length + ' lines' : 'on no line') + ')'); continue; }
    }
    if (at === from && path === m[1].replace(/^\.\//, '')) continue;
    const fixed = path + ':' + at + (m[3] ? '-' + (at + to - from) : '');
    if (FIX) { edits.push([m.index, m[0].length, fixed]); fixes.push(where + m[0] + ' -> ' + fixed); }
    else fail(where + m[0] + ': should be ' + fixed + ' at HEAD (run with --fix)');
  }
  for (const [index, length, fixed] of edits.reverse()) text = text.slice(0, index) + fixed + text.slice(index + length);
  for (const c of text.matchAll(/\b(noted on|filed as)\b(.{0,80})/gi)) {
    if (!/#\d+|\/(?:issues|pull)\/\d+/.test(c[2].split(/\.(?:\s|$)/)[0])) warnings.push(where + '"' + c[1] + '" has no issue number (#<n>): ' + norm(text));
  }
  if (/^\s*[-*]\s+\[ \]/.test(text)) warnings.push(where + 'unticked: ' + norm(text.replace(/^\s*[-*]\s+\[ \]\s*/, '')));
  return text + cr;
});
if (fixes.length > 0) writeFileSync(bodyPath, out.join('\n'));

// Promises in the plan's "## Tests": the quoted/backticked subject should appear in the body.
let promises = 0;
if (plan) {
  let section = '';
  for (const raw of plan.split(/\r?\n/)) {
    const line = raw.trim();
    const heading = /^##\s+(\w+)/.exec(line);
    if (heading) { section = heading[1]; continue; }
    if (section !== 'Tests' || !/PR body|verification \(/i.test(line)) continue;
    const subjects = [...line.matchAll(/\x60([^\x60]+)\x60|"([^"]+)"|(?<!\w)'([^']+)'(?!\w)/g)].map((s) => s[1] || s[2] || s[3]).filter((s) => !/\//.test(s) && !/\.[A-Za-z]{1,5}$/.test(s));
    for (const subject of subjects) {
      promises++;
      if (!body.toLowerCase().includes(subject.toLowerCase())) warnings.push('plan promises ' + JSON.stringify(subject) + ' in the PR body, which does not contain it: ' + line);
    }
  }
}

for (const f of fixes) console.error('fixed: ' + f);
for (const f of failures) console.error('FAIL: ' + f);
for (const w of warnings) console.error('warning: ' + w);
console.log('pr-body-lint: ' + cites + ' citation(s), ' + promises + ' plan promise(s), ' + fixes.length + ' fixed, ' + failures.length + ' failure(s), ' + warnings.length + ' warning(s)');
process.exit(failures.length > 0 ? 1 : 0);
