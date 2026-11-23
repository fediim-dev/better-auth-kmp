// <!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
// fediim telemetry — post OpenInference spans (cost, tokens, cache, tool and delivery timing) to Arize Phoenix.
// Usage: node .fediim/tools/telemetry.mjs stop --runtime claude|antigravity   (Stop hook; payload on stdin)
//        node .fediim/tools/telemetry.mjs span TOOL|CHAIN <name> '<json attributes>'   (run.mjs, pr-prep.mjs, triage-task.mjs, review-package.mjs)
//        node .fediim/tools/telemetry.mjs flush   (replay the offline spool)
// Always exits 0 within ~2s and never prints to stdout. Spans carry counts, costs, model ids, ids and
// fediim labels only — never prompt, response or tool text, and no file paths from tool inputs.
// POST <endpoint>/v1/projects/<project>/spans with { data: [span...] }: the shape follows Phoenix's REST
// create-spans endpoint and is only exercised against a mock in tests. Ids are random: runtime data, not an emitted file.
// Endpoint precedence: $PHOENIX_HOST > ~/.fediim/host.toml [telemetry] endpoint > fediim.toml. $PHOENIX_API_KEY
// becomes a Bearer header. host.toml [telemetry.pricing.<model>] overrides input/output/cacheRead/cacheWrite.
// Offline: failed posts append to .fediim/run/telemetry-spool.jsonl (2 MB, oldest dropped) and the next
// successful post sends the spool first, in order. The Stop hook tails the transcript from
// .fediim/run/telemetry-cursor.json; Claude repeats message.id per content block, so the last line per id counts once.
// Antigravity transcripts are read as Claude-shaped lines or Gemini usageMetadata lines (unverified against a live agy).
import { appendFileSync, closeSync, existsSync, mkdirSync, openSync, readFileSync, readSync, statSync, writeFileSync } from 'node:fs';
import { homedir, totalmem } from 'node:os';
import { randomBytes } from 'node:crypto';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const PROJECT = "better-auth-kmp";
const MANIFEST_ENDPOINT = "http://localhost:6006";
const VERSION = "0.9.0";
const PRICING = {"claude-fable-5-2":{"input":8,"output":40,"cacheRead":0.2,"cacheWrite":10},"claude-opus-5-5":{"input":4,"output":20,"cacheRead":0.2,"cacheWrite":5},"claude-sonnet-5-5":{"input":2,"output":10,"cacheRead":0.2,"cacheWrite":2.5},"claude-haiku-5-5":{"input":0.1,"output":0.5,"cacheRead":0.01,"cacheWrite":0.125},"gemini-4-neon":{"input":3,"output":15,"cacheRead":0.15,"cacheWrite":3},"gemini-4-carbon":{"input":2,"output":10,"cacheRead":0.1,"cacheWrite":2},"gemini-4-argon":{"input":2,"output":10,"cacheRead":0.1,"cacheWrite":2},"gemini-3.8-flash":{"input":0.75,"output":3.75,"cacheRead":0.075,"cacheWrite":0.75},"gemini-4-hydrogen":{"input":1.5,"output":7.5,"cacheRead":0.075,"cacheWrite":1.5}};
const SPOOL_CAP = 2 * 1024 * 1024;
setTimeout(() => process.exit(0), 2000).unref();

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
const runDir = join(root, '.fediim', 'run');
const SPOOL = join(runDir, 'telemetry-spool.jsonl');
const CURSOR = join(runDir, 'telemetry-cursor.json');
const readText = (file) => { try { return readFileSync(file, 'utf8'); } catch { return ''; } };
const readJson = (file) => { try { return JSON.parse(readText(file)); } catch { return undefined; } };
const num = (v) => (Number.isFinite(Number(v)) ? Number(v) : 0);
const r6 = (x) => Math.round(x * 1e6) / 1e6;
const hex = (n) => randomBytes(n).toString('hex');

// Minimal host.toml reader (zero deps): [telemetry] endpoint and [telemetry.pricing.<model>] numbers.
function readHost() {
  const out = { endpoint: '', pricing: {} };
  let section = '';
  for (const raw of readText(join(homedir(), '.fediim', 'host.toml')).split('\n')) {
    const line = raw.replace(/\s+#.*$/, '').trim();
    const head = line.match(/^\[(.+)\]$/);
    if (head) { section = head[1].trim(); continue; }
    const kv = line.match(/^([A-Za-z]+)\s*=\s*(.+)$/);
    if (!kv) continue;
    const value = kv[2].trim().replace(/^"(.*)"$/, '$1');
    if (section === 'telemetry' && kv[1] === 'endpoint') out.endpoint = value;
    else if (section.startsWith('telemetry.pricing.') && /^(input|output|cacheRead|cacheWrite)$/.test(kv[1]) && Number.isFinite(Number(value))) {
      (out.pricing[section.slice(18).replace(/^"(.*)"$/, '$1')] ??= {})[kv[1]] = Number(value);
    }
  }
  return out;
}
const host = readHost();
let endpoint = process.env.PHOENIX_HOST || host.endpoint || MANIFEST_ENDPOINT;
if (!/^https?:\/\//.test(endpoint)) endpoint = 'http://' + endpoint;
endpoint = endpoint.replace(/\/+$/, '');

const labels = {};
for (const pair of (process.env.OTEL_RESOURCE_ATTRIBUTES || readText(join(root, '.fediim', 'otel-attributes')).trim()).split(',')) {
  const i = pair.indexOf('=');
  if (i > 0) labels[pair.slice(0, i).trim()] = pair.slice(i + 1).trim();
}
const common = { ...labels, 'openinference.project.name': PROJECT, 'service.name': 'fediim', 'fediim.version': VERSION };

function priceOf(model) {
  const table = { ...PRICING };
  for (const [m, o] of Object.entries(host.pricing)) table[m] = { ...(table[m] || {}), ...o };
  const key = Object.keys(table).filter((k) => model.includes(k)).sort((a, b) => b.length - a.length)[0];
  const p = key && table[key];
  return p && ['input', 'output', 'cacheRead', 'cacheWrite'].every((f) => typeof p[f] === 'number') ? p : null;
}

function makeSpan(name, kind, trace, parent, start, end, attrs, ok = true) {
  return {
    name,
    context: { trace_id: trace, span_id: hex(8) },
    parent_id: parent,
    span_kind: kind,
    start_time: new Date(start).toISOString(),
    end_time: new Date(end).toISOString(),
    status_code: ok ? 'OK' : 'ERROR',
    attributes: { ...common, 'openinference.span.kind': kind, ...attrs },
  };
}

function ensureRunDir() {
  mkdirSync(runDir, { recursive: true });
  if (!existsSync(join(runDir, '.gitignore'))) writeFileSync(join(runDir, '.gitignore'), '*\n');
}
function readSpoolSlice() {
  if (!existsSync(SPOOL)) return { spans: [], bytes: 0 };
  let buf;
  try { buf = readFileSync(SPOOL); } catch { return { spans: [], bytes: 0 }; }
  const end = buf.lastIndexOf(10) + 1;
  if (end <= 0) return { spans: [], bytes: 0 };
  const spans = buf.subarray(0, end).toString('utf8').split('\n').filter(Boolean).flatMap((l) => { try { return [JSON.parse(l)]; } catch { return []; } });
  return { spans, bytes: end };
}

function spool(spans) {
  ensureRunDir();
  appendFileSync(SPOOL, spans.map((s) => JSON.stringify(s) + '\n').join(''));
  const text = readText(SPOOL);
  let total = Buffer.byteLength(text);
  if (total <= SPOOL_CAP) return;
  const lines = text.split('\n').filter(Boolean);
  let drop = 0;
  while (total > SPOOL_CAP && drop < lines.length - 1) total -= Buffer.byteLength(lines[drop++]) + 1;
  writeFileSync(SPOOL, lines.slice(drop).join('\n') + '\n');
}

async function post(spans) {
  const ctl = new AbortController();
  const timer = setTimeout(() => ctl.abort(), 1500);
  try {
    const res = await fetch(endpoint + '/v1/projects/' + encodeURIComponent(PROJECT) + '/spans', {
      method: 'POST',
      headers: { 'content-type': 'application/json', ...(process.env.PHOENIX_API_KEY ? { authorization: 'Bearer ' + process.env.PHOENIX_API_KEY } : {}) },
      body: JSON.stringify({ data: spans }),
      signal: ctl.signal,
    });
    return res.ok;
  } catch { return false; } finally { clearTimeout(timer); }
}

// The spool goes first in the same body, so replay is lossless and ordered.
async function send(spans) {
  const { spans: queued, bytes: sentBytes } = readSpoolSlice();
  if (queued.length + spans.length === 0) return;
  if (await post([...queued, ...spans])) {
    if (sentBytes > 0) {
      try {
        const cur = readFileSync(SPOOL);
        writeFileSync(SPOOL, cur.length <= sentBytes ? '' : cur.subarray(sentBytes));
      } catch {}
    }
  } else if (spans.length) spool(spans);
}

function usageOf(o) {
  const m = o.message || {};
  const u = m.usage || o.usage;
  if (u) return { input: num(u.input_tokens), output: num(u.output_tokens), cacheRead: num(u.cache_read_input_tokens), cacheWrite: num(u.cache_creation_input_tokens) };
  if (o.input_tokens !== undefined || o.output_tokens !== undefined) {
    return {
      input: num(o.input_tokens),
      output: num(o.output_tokens),
      cacheRead: num(o.cache_read_tokens ?? o.cache_read_input_tokens),
      cacheWrite: num(o.cache_write_tokens ?? o.cache_creation_tokens ?? o.cache_creation_input_tokens),
    };
  }
  const g = o.usageMetadata || m.usageMetadata;
  if (!g) return null;
  const cached = num(g.cachedContentTokenCount);
  return { input: Math.max(0, num(g.promptTokenCount) - cached), output: num(g.candidatesTokenCount) + num(g.thoughtsTokenCount), cacheRead: cached, cacheWrite: 0 };
}

function llmAttrs(sid, model, u) {
  const prompt = u.input + u.cacheRead + u.cacheWrite;
  const a = {
    'session.id': sid,
    'llm.model_name': model,
    'llm.token_count.prompt': prompt,
    'llm.token_count.completion': u.output,
    'llm.token_count.total': prompt + u.output,
    'llm.token_count.prompt_details.cache_read': u.cacheRead,
    'llm.token_count.prompt_details.cache_write': u.cacheWrite,
    'fediim.cache_hit_ratio': prompt ? r6(u.cacheRead / prompt) : 0,
  };
  const provider = /claude/i.test(model) ? 'anthropic' : /gemini/i.test(model) ? 'google' : '';
  if (provider) a['llm.provider'] = provider;
  const p = priceOf(model);
  if (p) {
    const promptCost = (u.input * p.input + u.cacheRead * p.cacheRead + u.cacheWrite * p.cacheWrite) / 1e6;
    const completionCost = (u.output * p.output) / 1e6;
    a['llm.cost.prompt'] = r6(promptCost);
    a['llm.cost.completion'] = r6(completionCost);
    a['llm.cost.total'] = r6(promptCost + completionCost);
    a['fediim.cache_savings_usd'] = r6((u.cacheRead * (p.input - p.cacheRead)) / 1e6);
  }
  return a;
}

async function stop() {
  let payload = {};
  try {
    payload = JSON.parse(await new Promise((done) => {
      let d = '';
      process.stdin.setEncoding('utf8').on('data', (c) => { d += c; }).on('end', () => done(d)).on('error', () => done(''));
    }));
  } catch {}
  const file = String(payload.transcript_path || payload.transcriptPath || '');
  const sid = String(payload.session_id || payload.conversationId || payload.sessionId || labels['session.id'] || '');
  if (!file || !existsSync(file)) return;
  const cursor = readJson(CURSOR) || {};
  const size = statSync(file).size;
  const from = num(cursor[file]) <= size ? num(cursor[file]) : 0;
  const buf = Buffer.alloc(size - from);
  const fd = openSync(file, 'r');
  readSync(fd, buf, 0, buf.length, from);
  closeSync(fd);
  const end = buf.lastIndexOf(10) + 1;
  if (end === 0) return;
  const byId = new Map();
  let errors = 0;
  let truncations = 0;
  buf.subarray(0, end).toString('utf8').split('\n').forEach((line, i) => {
    let o;
    try { o = JSON.parse(line); } catch { return; }
    if (o.isCompactSummary || o.subtype === 'compact_boundary' || o.type === 'COMPACT' || o.type === 'CONTEXT_TRUNCATED') truncations++;
    if (o.status === 'ERROR') errors++;
    const content = o.message && o.message.content;
    if (Array.isArray(content)) for (const b of content) if (b && b.type === 'tool_result' && b.is_error === true) errors++;
    const u = usageOf(o);
    const m = o.message || {};
    const model = String(m.model || o.model || o.modelVersion || '');
    if (!u || model === '<synthetic>') return;
    byId.set(String(m.id || o.id || o.uuid || o.responseId || (o.step_index !== undefined ? 'step-' + o.step_index : 'line-' + i)), { u, model, ts: Date.parse(o.timestamp || o.created_at) || Date.now() });
  });
  const calls = [...byId.values()];
  // Advance the cursor before the network call: the 2s hard exit may fire mid-send, and a
  // re-read chunk would double-count cost (a failed send is spooled, not lost).
  ensureRunDir();
  writeFileSync(CURSOR, JSON.stringify({ ...cursor, [file]: from + end }) + '\n');
  if (calls.length > 0) {
    const trace = hex(16);
    const turn = hex(8);
    const times = calls.map((c) => c.ts);
    const agent = makeSpan('fediim.turn', 'AGENT', trace, null, Math.min(...times), Math.max(...times), {
      'session.id': sid, 'fediim.runtime': runtime, 'fediim.llm_calls': calls.length, 'fediim.step_errors': errors, 'fediim.context_truncations': truncations,
    }, errors === 0);
    agent.context.span_id = turn;
    await send([agent, ...calls.map((c) => makeSpan('llm', 'LLM', trace, turn, c.ts, c.ts, llmAttrs(sid, c.model, c.u)))]);
  }
}

async function toolOrChain([kind, name, json]) {
  const attrs = JSON.parse(json || '{}');
  const now = Date.now();
  if (kind === 'TOOL') attrs['fediim.host_tier'] ??= (Number(process.env.FEDIIM_HOST_MEM_BYTES) || totalmem()) > 20 * 1024 ** 3 ? '64gb' : '16gb';
  const ok = attrs['fediim.exit_code'] === undefined || attrs['fediim.exit_code'] === 0;
  await send([makeSpan(name, kind, hex(16), null, now - num(attrs['fediim.duration_ms']), now, attrs, ok)]);
}

const [cmd, ...rest] = process.argv.slice(2);
const runtime = rest[rest.indexOf('--runtime') + 1] === 'antigravity' ? 'antigravity' : 'claude';
try {
  if (cmd === 'stop') await stop();
  else if (cmd === 'span' && (rest[0] === 'TOOL' || rest[0] === 'CHAIN')) await toolOrChain(rest);
  else if (cmd === 'flush') await send([]);
} catch (e) {
  console.error('telemetry: ' + (e instanceof Error ? e.message : e));
}
process.exit(0);
