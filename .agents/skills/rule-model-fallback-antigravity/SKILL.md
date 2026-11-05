---
name: rule-model-fallback-antigravity
description: Use when a Claude bridge call or a Claude model fails, is missing, or is out of quota in Antigravity.
---
<!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
# Model fallback (Antigravity)  <!-- pack: planning/model-fallback-antigravity v1.1.0 -->

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
