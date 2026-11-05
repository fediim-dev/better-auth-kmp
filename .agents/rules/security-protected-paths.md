---
trigger: always_on
description: Use when a change touches auth, billing, schema migrations, generated code, CI workflows, or permission settings.
---
<!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
# Protected paths

Never write to `src/generated/` (write-protected). Billing, auth logic, and
user-data schema migrations need Security Reviewer sign-off before merge.
Never use permission-bypass flags (`--dangerously-skip-permissions` or
equivalents), whoever asks.

Full procedure: load the `rule-protected-paths` skill.
