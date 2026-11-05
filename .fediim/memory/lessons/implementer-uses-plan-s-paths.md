---
description: briefing the implementer subagent, or naming new source files during implementation of a plan with S-line paths
phase: [code]
paths: [".fediim/plans/*.md"]
validated: 1
last-validated: 2026-11-05
evidence: "#6 (PR #16: three platform authenticator files named differently from S16-S18 failed the plan-path guard after full verification)"
---
DO tell the implementer to create files at exactly the plan's S-line paths (rename the class, not the file) — guards.mjs fails any S path the diff does not touch, which cost a rename commit and a re-verify in PR #16.
