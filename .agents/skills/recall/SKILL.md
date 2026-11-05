---
name: recall
description: Use when planning or designing a change, after a build, test, or toolchain error, or when asked what was learned or assumed before.
---
# Skill: Recall

Operator rules (`.fediim/memory/rules.md`) are already loaded every session —
don't search for them.

1. Run `node .fediim/tools/recall.mjs [--phase plan|code|verify|pr]
   [--files a,b | --plan .fediim/plans/<n>.md | --git-diff] [--assumptions]
   "<2–4 keywords from the task or error>"`. It ranks lessons and prints each
   one's DO/DON'T lines; no match: widen the keywords once, then proceed.
2. Apply the printed rules as guidance. Operator rules and `CONSTRAINTS.md`
   win; a conflict with a hard rule in `AGENTS.md` escalates.

Read-only: lessons are written by `retro`, operator rules by `remember-rule`.
