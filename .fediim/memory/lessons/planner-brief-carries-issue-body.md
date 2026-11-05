---
description: dispatching the planner or plan-reviewer subagent (Read/Grep/Glob only, no gh) for a GitHub issue
phase: [plan]
validated: 1
last-validated: 2026-11-05
evidence: "#7 (planner run returned an escalation: it could not run gh issue view and found no local spec)"
---
DO save `gh issue view <n> --json body` to a scratchpad file and give its absolute path in the planner and plan-reviewer briefs — those subagents have no shell or gh, so a brief that says "run gh issue view" wastes a full planner run.
