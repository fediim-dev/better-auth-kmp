---
name: rule-model-fallback
description: Use when a subagent or model fails with a rate limit (429), quota, model_not_found, or overload during a run.
---
<!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
# Model fallback (Claude Code)  <!-- pack: planning/model-fallback v2.3.1 -->

Claude Code runs only Claude models. `fallbackModel` in `.claude/settings.json`
(`models.claudeFallback` in `fediim.toml`, default Sonnet 5.5 / `sonnet`)
covers overload (529), unavailable (404), and non-retryable server errors
automatically, including for subagents. Because `fallbackModel` never switches
on rate-limit (429) or quota errors, retry those once manually on the Claude
fallback (`sonnet`) and say so. Never switch to Gemini here:
`models.antigravityFallback` applies to Antigravity only.

## Procedure

1. **Recognise manual-retry failures.** `rate_limit_error` / HTTP 429,
   `insufficient_quota`, or a subagent that "terminated early due to an API
   error" from rate limits or quota (overload / 529 and `model_not_found` / 404
   are already handled by `fallbackModel` in `.claude/settings.json`).
2. **Retry once on the Claude fallback (`sonnet` / Sonnet 5.5).**
   - Subagent: dispatch the same brief to the same persona with the model
     overridden to `sonnet`. If the persona can't be overridden, dispatch a
     general-purpose subagent on `sonnet` with the persona's instructions
     and brief.
   - `claude -p`: rerun once with `--model sonnet` (do not rely on
     `--fallback-model`, which never switches on 429 or quota errors).
   - Bridges to Antigravity (`agy -p`): if `agy` is missing or fails, run the
     brief as a Claude Code subagent on `sonnet`.
3. **Report it** in the task comment or final report:
   `ℹ Model fallback: <persona> ran on <model ID that actually ran> (was <model>: <error>)`.
   Name the alias (`sonnet`) when the model ID isn't known.
4. **Escalate** only if the fallback retry also fails; never skip the gate
   the subagent was running (review, audit, plan review).
