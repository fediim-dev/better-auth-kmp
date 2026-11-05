---
trigger: always_on
description: Use when about to write or change code on Antigravity and deciding whether to make the change yourself or dispatch the implementer subagent.
---
<!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
# Implementation delegation (Antigravity)

Code changes estimated above `[delegation] implementLines` in `fediim.toml`
(default 100 changed lines) go to the `implementer` subagent via
`invoke_subagent` (TypeName: 'implementer', Role: 'Implementer', Model: 'flash', Workspace: 'inherit');
smaller ones you make yourself. You plan, brief and review the diff.

Full procedure: load the `rule-implementation-delegation-antigravity` skill.
