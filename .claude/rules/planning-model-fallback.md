<!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
# Model fallback (Claude Code)

Claude Code runs only Claude models. `fallbackModel` in `.claude/settings.json`
(`models.claudeFallback` in `fediim.toml`, default Sonnet 5.5 / `sonnet`)
covers overload (529), unavailable (404), and non-retryable server errors
automatically, including for subagents. Because `fallbackModel` never switches
on rate-limit (429) or quota errors, retry those once manually on the Claude
fallback (`sonnet`) and say so. Never switch to Gemini here:
`models.antigravityFallback` applies to Antigravity only.

Full procedure: load the `rule-model-fallback` skill.
