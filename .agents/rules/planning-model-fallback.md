---
trigger: always_on
description: Use when a subagent, bridge or model call fails with a rate limit (429), quota, overload, model_not_found, or a missing Claude bridge.
---
<!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
# Model fallback (Antigravity)

When Claude is unavailable — `claude` missing, auth failure, rate limit (429),
overload (529) or exhausted quota — run the same work on Gemini instead of
stopping: `pro` for planning personas, `flash` for all others. Say so.

Full procedure: load the `fediim-rules` skill and read `references/model-fallback.md`.
