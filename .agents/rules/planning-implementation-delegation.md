---
trigger: always_on
description: Use when about to write or change code and deciding whether to make the change yourself or hand it to the implementer subagent.
---
<!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
# Implementation delegation (Antigravity)

Code changes estimated above `[delegation] implementLines` in `fediim.toml`
(default 100 changed lines) go to the `implementer` subagent via
`invoke_subagent` (TypeName: 'implementer', Role: 'Implementer', Model: per step tag,
Workspace: 'share');
smaller ones you make yourself. You plan, brief and review the diff.

Full procedure: load the `fediim-rules` skill and read `references/implementation-delegation.md`.
