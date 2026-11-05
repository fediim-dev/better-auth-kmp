// <!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
// fediim recall — rank agent lessons for the task at hand, or audit them.
// Usage: node .fediim/tools/recall.mjs [--phase plan|code|verify|pr] [--files a,b]
//          [--plan .fediim/plans/<n>.md] [--git-diff] [--assumptions] [--limit K] [terms...]
//        node .fediim/tools/recall.mjs --audit [--fix]
// Score = paths glob +5, phase +2, BM25 over description+body, +0.5*min(validated,3).
// Same inputs give byte-identical output; --audit reads today from FEDIIM_TODAY or the clock.
import { readFileSync, readdirSync, existsSync, writeFileSync, mkdirSync, renameSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { join } from 'node:path';
const STEP_RE = /^([ST]\d+)\s+\x60?([^\s\x60:]+)(?::\d+(?:-\d+)?)?\x60?(?:\s+\([^)]+\))?\s*(?:::|[—–-]\s)/;
const STEP_LIKE_RE = /^(?:[-*]\s+)?\**[ST]\d+\b/;
const PLAN_FILE_RE = /^\.fediim\/plans\/[^/]+\.md$/;
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
    }
  }
  return errors;
}

const LESSONS = '.fediim/memory/lessons';
const LEGACY = '.fediim/memory/lessons.md';
const PLANS = '.fediim/plans';
const VALUE_FLAGS = ['--phase', '--files', '--plan', '--limit'];
const BOOL_FLAGS = ['--git-diff', '--assumptions', '--audit', '--fix'];
const opt = {};
const query = [];
const args = process.argv.slice(2);
for (let i = 0; i < args.length; i++) {
  const a = args[i];
  if (VALUE_FLAGS.includes(a)) {
    if (args[i + 1] === undefined) usage();
    opt[a.slice(2)] = args[++i];
  } else if (BOOL_FLAGS.includes(a)) opt[a.slice(2)] = true;
  else if (a.startsWith('--')) usage();
  else query.push(a);
}
function usage() {
  console.error('usage: node recall.mjs [--phase plan|code|verify|pr] [--files a,b] [--plan <plan.md>] [--git-diff] [--assumptions] [--limit K] [terms...] | --audit [--fix]');
  process.exit(2);
}
const read = (p) => readFileSync(p, 'utf8');
const unq = (s) => s.trim().replace(/^(["'])(.*)\1$/, '$2');
const list = (v) => (v || '').replace(/^\[|\]$/g, '').split(',').map(unq).filter(Boolean);

function parseLesson(text) {
  const m = text.match(/^---\r?\n([\s\S]*?)\r?\n---\r?\n?([\s\S]*)$/);
  const fm = {};
  if (!m) return { fm, body: text };
  let last = '';
  for (const line of m[1].split(/\r?\n/)) {
    const kv = line.match(/^([\w-]+):\s*(.*)$/);
    if (kv) { last = kv[1]; fm[last] = kv[2].trim(); }
    else if (last && /^\s+-\s+/.test(line)) fm[last] += ',' + line.replace(/^\s+-\s+/, '');
  }
  return { fm, body: m[2] };
}

function lessonFiles() {
  if (!existsSync(LESSONS)) return [];
  return readdirSync(LESSONS, { withFileTypes: true }).filter((e) => e.isFile() && e.name.endsWith('.md')).map((e) => e.name).sort();
}

function ruleLines(body) {
  const rules = body.split(/\r?\n/).map((l) => l.trim().replace(/^[-*]\s+/, '')).filter((l) => /^(DO|DON'T)\b/.test(l));
  return rules.length ? rules : body.split(/\r?\n/).map((l) => l.trim()).filter(Boolean).slice(0, 1);
}

function loadDocs() {
  const docs = [];
  for (const name of lessonFiles()) {
    const { fm, body } = parseLesson(read(join(LESSONS, name)));
    docs.push({ id: LESSONS + '/' + name, text: (fm.description || '') + '\n' + body, phases: list(fm.phase), paths: list(fm.paths), validated: parseInt(fm.validated, 10) || 0, rules: ruleLines(body) });
  }
  // Legacy flat bullets stay searchable until doctor --fix migrates them.
  if (existsSync(LEGACY)) {
    read(LEGACY).split(/\r?\n/).filter((l) => /^- /.test(l)).forEach((l, i) => {
      docs.push({ id: LEGACY + '#' + (i + 1), text: l.slice(2), phases: [], paths: [], validated: 0, rules: [l.slice(2).trim()] });
    });
  }
  return docs;
}

const tokens = (s) => (String(s).toLowerCase().match(/[a-z0-9_]{2,}/g) || []);
function globRe(glob) {
  let re = '';
  for (let i = 0; i < glob.length; i++) {
    const c = glob[i];
    if (c === '*' && glob[i + 1] === '*') {
      if (glob[i + 2] === '/') { re += '(?:.*/)?'; i += 2; } else { re += '.*'; i += 1; }
    } else if (c === '*') re += '[^/]*';
    else if (c === '?') re += '[^/]';
    else re += c.replace(/[\\^$.+(){}|[\]]/g, '\\$&');
  }
  return new RegExp('^' + re + '$');
}
const globHit = (glob, file) => {
  const re = globRe(glob);
  return re.test(file) || (!glob.includes('/') && re.test(file.split('/').pop()));
};

function targetFiles() {
  const out = [];
  if (opt.files) out.push(...opt.files.split(',').map((f) => f.trim()).filter(Boolean));
  if (opt.plan) {
    for (const line of read(opt.plan).split(/\r?\n/)) {
      const m = stepMatch(line.trim());
      if (m) out.push(m[2]);
    }
  }
  if (opt['git-diff']) {
    try { out.push(...execFileSync('git', ['diff', '--name-only', 'HEAD'], { encoding: 'utf8' }).split('\n').filter(Boolean)); } catch {}
  }
  return [...new Set(out.map((f) => f.replace(/^\.\//, '')))].sort();
}

function assumptionTerms() {
  if (!existsSync(PLANS)) return [];
  const out = [];
  for (const name of readdirSync(PLANS).filter((n) => n.endsWith('.md')).sort()) {
    let inSection = false;
    for (const line of read(join(PLANS, name)).split(/\r?\n/)) {
      if (/^##\s+/.test(line)) inSection = /^##\s+Assumptions\b/.test(line);
      else if (inSection && line.trim()) out.push(line);
    }
  }
  return out;
}

function recall() {
  const limit = parseInt(opt.limit || '5', 10) || 5;
  const files = targetFiles();
  const q = tokens([...query, ...(opt.assumptions ? assumptionTerms() : [])].join(' '));
  const docs = loadDocs();
  const dt = docs.map((d) => tokens(d.text));
  const avg = dt.reduce((n, t) => n + t.length, 0) / (docs.length || 1) || 1;
  const df = (term) => dt.filter((t) => t.includes(term)).length;
  const scored = docs.map((d, i) => {
    const pathHit = d.paths.some((g) => files.some((f) => globHit(g, f)));
    const phaseHit = Boolean(opt.phase) && d.phases.includes(opt.phase);
    let bm25 = 0;
    for (const term of new Set(q)) {
      const f = dt[i].filter((t) => t === term).length;
      if (!f) continue;
      const n = df(term);
      bm25 += Math.log(1 + (docs.length - n + 0.5) / (n + 0.5)) * (f * 2.2) / (f + 1.2 * (0.25 + 0.75 * dt[i].length / avg));
    }
    const score = (pathHit ? 5 : 0) + (phaseHit ? 2 : 0) + bm25 + 0.5 * Math.min(d.validated, 3);
    return { d, score, relevant: pathHit || phaseHit || bm25 > 0 };
  }).filter((s) => s.relevant).sort((a, b) => b.score - a.score || (a.d.id < b.d.id ? -1 : 1));
  if (!scored.length) return console.log('recall: no matching lessons');
  for (const { d, score } of scored.slice(0, limit)) {
    console.log(d.id + ' (' + score.toFixed(2) + ')');
    for (const r of d.rules) console.log('  ' + r);
  }
}

function audit() {
  const fix = Boolean(opt.fix);
  const today = Date.parse((process.env.FEDIIM_TODAY || new Date().toISOString().slice(0, 10)) + 'T00:00:00Z');
  for (const name of lessonFiles()) {
    const path = join(LESSONS, name);
    let text = read(path);
    const { fm } = parseLesson(text);
    let validated = parseInt(fm.validated, 10) || 0;
    const evidence = unq(fm.evidence || '').split(';').filter((c) => /#\d+/.test(c)).length;
    if (evidence > validated) {
      console.log((fix ? 'FIXED' : 'SYNC') + ': ' + path + ' validated ' + validated + ' -> ' + evidence + (fix ? '' : ' (run --audit --fix)'));
      if (fix) {
        text = /^validated:.*$/m.test(text.split(/\r?\n---/)[0]) ? text.replace(/^validated:.*$/m, 'validated: ' + evidence) : text.replace(/\r?\n---/, '\nvalidated: ' + evidence + '\n---');
        writeFileSync(path, text);
      }
      validated = evidence;
    }
    const last = Date.parse(unq(fm['last-validated'] || '') + 'T00:00:00Z');
    if (validated === 1 && last && (today - last) / 864e5 > 60) {
      console.log((fix ? 'ARCHIVED' : 'EXPIRED') + ': ' + path + ' (validated: 1, last-validated ' + unq(fm['last-validated']) + ')' + (fix ? '' : ' (run --audit --fix)'));
      if (fix) {
        mkdirSync(join(LESSONS, 'archive'), { recursive: true });
        renameSync(path, join(LESSONS, 'archive', name));
      }
      continue;
    }
    if (validated >= 3) console.log('PROMOTE REQUIRED: ' + path + ' (validated: ' + validated + ')');
    if (!fm.phase) console.log('WARN: ' + path + ' has no phase: (add phase: [plan|code|verify|pr])');
  }
}

if (opt.audit) audit(); else recall();
