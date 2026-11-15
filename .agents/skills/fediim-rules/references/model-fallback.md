<!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
# Model fallback (Antigravity)  <!-- pack: planning/model-fallback v3.0.0 -->

When Claude is unavailable — `claude` missing, auth failure, rate limit (429),
overload (529) or exhausted quota — run the same work on Gemini instead of
stopping: `pro` for planning personas, `flash` for all others. Say so.

## Procedure

1. **Recognise the failure.** A `claude -p` bridge that is not installed or
   exits non-zero with an auth, `rate_limit_error`/429, `overloaded_error`/529
   or quota error; or a session running on a Claude model that errors the same
   way.
2. **Fall back to Gemini.**
   - Subagent / bridge persona: dispatch the same brief via `invoke_subagent`
     (TypeName: `<persona-slug>`, Role: the persona, Model: `pro` for
     planner/plan-reviewer/evaluator, `flash` otherwise, Workspace: `inherit`).
   - Session model: continue on a Gemini model (`pro` for planning work).
3. **Report it:** `ℹ Model fallback: <persona> ran on Gemini <tier> (Claude: <error>)`.
4. **Keep the gate.** A review or plan review still has to happen on the
   fallback model; escalate only if the Gemini run also fails.

## Retry before failover

`agy` 1.5.0+ retries 429 and 5xx responses itself, honouring `Retry-After`.
Let those built-in retries run out before switching model; fall back to Gemini
only when the error persists after them.

## Claude 5.5 on Antigravity

Claude 5.5 can also run natively on Antigravity through Google Application
Default Credentials (ADC), not only through `claude -p` bridges. A native
Claude session that fails follows the same retry-then-fallback rule above.
