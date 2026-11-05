// <!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
// fediim issue-structure audit — validates a GitHub issue event payload
// against the mandatory structure (issue-structure rule): the four required
// section headings, at least one task checkbox, exactly one priority label.
import { readFileSync } from 'node:fs';

const REQUIRED_HEADINGS = [
  '## Goal',
  '## Tasks',
  '## Definition of Done',
  '## Priority',
];

const payloadPath = process.argv[2] || process.env.GITHUB_EVENT_PATH;
if (!payloadPath) {
  console.error('[issue-audit] no payload: pass a path or set GITHUB_EVENT_PATH.');
  process.exit(2);
}
const payload = JSON.parse(readFileSync(payloadPath, 'utf8'));
const issue = payload.issue;
if (!issue) {
  console.error('[issue-audit] payload has no issue object.');
  process.exit(2);
}

const body = String(issue.body || '');
const lines = body.split('\n').map(l => l.trim());
const missing = [];

for (const heading of REQUIRED_HEADINGS) {
  if (!lines.some(l => l === heading || l.startsWith(heading + ' ') || l.startsWith(heading + ' /'))) {
    missing.push('MISSING: ' + heading + ' section');
  }
}
if (!lines.some(l => l.startsWith('- [ ]') || l.startsWith('- [x]'))) {
  missing.push('MISSING: at least one "- [ ]" task item under ## Tasks');
}
const priorityLabels = (issue.labels || [])
  .map(l => (typeof l === 'string' ? l : l.name))
  .filter(name => /^priority:P[0-3]$/.test(String(name)));
if (priorityLabels.length !== 1) {
  missing.push('MISSING: exactly one priority:P0–P3 label (found ' + priorityLabels.length + ')');
}

if (missing.length > 0) {
  console.log('issue-audit: FAIL — this issue does not carry the mandatory structure.');
  for (const m of missing) console.log(m);
  console.log('');
  console.log('Fill the sections from .github/ISSUE_TEMPLATE/task.md (Goal / Tasks /');
  console.log('Definition of Done / Priority) and set one priority label; editing the');
  console.log('issue re-runs this audit and clears the needs-structure label.');
  process.exit(1);
}
console.log('issue-audit: PASS — mandatory structure present.');
