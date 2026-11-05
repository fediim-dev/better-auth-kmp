---
trigger: always_on
description: Use when a Claude bridge call or a Claude model fails, is missing, or is out of quota in Antigravity.
---
<!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
# Model fallback (Antigravity)

When Claude is unavailable — `claude` missing, auth failure, rate limit (429),
overload (529) or exhausted quota — run the same work on Gemini instead of
stopping: `pro` for planning personas, `flash` for all others. Say so.

Full procedure: load the `rule-model-fallback-antigravity` skill.
