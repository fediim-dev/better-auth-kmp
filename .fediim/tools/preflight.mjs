// <!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
// fediim preflight — run before the verify phase so a loaded machine or a concurrent Gradle
// build does not burn a retry.
// Usage: node .fediim/tools/preflight.mjs
// Warns (exit unchanged) when the 1-minute load per core is above 0.7 (and lists the 3 busiest
// processes). Waits up to FEDIIM_PREFLIGHT_WAIT seconds (default 180) while another Gradle build
// (wrapper or CLI client; an idle daemon is not a build) runs, then warns if it still does.
// Then runs each check of the optional .fediim/preflight.json { "checks": [{ "name", "cmd",
// "hint" }] } via sh -c; exit 1 if any fails.
// Tests stub the sources: FEDIIM_PREFLIGHT_LOAD=<1-min load average>, FEDIIM_PREFLIGHT_GRADLE=0|1.
import { execFileSync, spawnSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { cpus, loadavg } from 'node:os';

const warn = (msg) => console.error('warning: ' + msg);
const out = (cmd, args) => { try { return execFileSync(cmd, args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }); } catch { return null; } };

const load = process.env.FEDIIM_PREFLIGHT_LOAD !== undefined ? Number(process.env.FEDIIM_PREFLIGHT_LOAD) : loadavg()[0];
const perCore = load / Math.max(1, cpus().length);
if (perCore > 0.7) {
  warn('1-minute load is ' + load.toFixed(2) + ' (' + perCore.toFixed(2) + ' per core, above 0.7); timings and timeouts will be unreliable');
  // Sorted here: "ps -r" sorts on macOS but means "running only" on Linux.
  const ps = out('ps', ['-Ao', 'pcpu,args']) ?? '';
  const top = ps.split('\n').map((l) => /^\s*(\d+(?:\.\d+)?)\s+(.*)$/.exec(l)).filter(Boolean)
    .sort((a, b) => Number(b[1]) - Number(a[1])).slice(0, 3);
  for (const m of top) console.error('  ' + m[1] + '% ' + m[2].slice(0, 100));
}

const stub = process.env.FEDIIM_PREFLIGHT_GRADLE;
// A Gradle daemon idles ~3h after every build, so only a live client (gradlew, the wrapper or CLI
// main class) means a concurrent build. pgrep is missing on some systems: no answer = not running.
const gradleBuilds = () => stub !== undefined ? (stub === '1' ? ['stub'] : []) : (out('pgrep', ['-f', 'gradlew|GradleWrapperMain|org.gradle.launcher.GradleMain']) ?? '').split('\n').filter((p) => p && Number(p) !== process.pid && Number(p) !== process.ppid);
// Waiting costs no agent turn; a build that collides with this one costs a retry. Load is not
// waited on: it is a 1-minute average that other agents on the machine keep high.
const waitMs = Number(process.env.FEDIIM_PREFLIGHT_WAIT ?? 180) * 1000;
const started = Date.now();
let others = gradleBuilds();
while (others.length > 0 && Date.now() - started < waitMs) {
  Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, Math.min(5000, waitMs - (Date.now() - started)));
  others = gradleBuilds();
}
if (Date.now() - started >= 1000) console.error('preflight: waited ' + Math.round((Date.now() - started) / 1000) + 's for another Gradle build');
if (others.length > 0) warn('another Gradle build is running (' + others.length + ' process(es)); a concurrent build can lock or slow this one');

let checks = [];
if (existsSync('.fediim/preflight.json')) {
  try { checks = JSON.parse(readFileSync('.fediim/preflight.json', 'utf8')).checks ?? []; } catch (e) { console.error('preflight: .fediim/preflight.json is not valid JSON: ' + e.message); process.exit(2); }
  if (!Array.isArray(checks) || checks.some((c) => !c || typeof c.name !== 'string' || typeof c.cmd !== 'string')) { console.error('preflight: .fediim/preflight.json needs { "checks": [{ "name", "cmd", "hint" }] }'); process.exit(2); }
}
let failed = 0;
for (const c of checks) {
  if (spawnSync('sh', ['-c', c.cmd], { stdio: 'inherit' }).status !== 0) { failed++; console.error('FAIL: ' + c.name + (c.hint ? ' — ' + c.hint : '')); }
}
console.log('preflight: ' + (failed ? failed + ' of ' + checks.length + ' check(s) failed' : 'ok (' + checks.length + ' check(s))'));
process.exit(failed ? 1 : 0);
