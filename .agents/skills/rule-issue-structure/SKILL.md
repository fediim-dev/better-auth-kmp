---
name: rule-issue-structure
description: Use when filing, triaging, or choosing the next GitHub issue to work on.
---
<!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
# Issue structure & selection  <!-- pack: planning/issue-structure v1.5.0 -->

File every issue via `file-issue` (Antigravity: `/file-issue`) with Goal,
Tasks, DoD, and exactly one `priority:P0`–`P3` label. Work the lowest-sequence
eligible issue first; priority only breaks ties.

The git-workflow rule owns the branch/PR/label lifecycle; this rule owns how
issues are *shaped* and *chosen*. The `file-issue` skill / `/file-issue`
workflow walks through gathering, composing, filing (`gh issue create` with
labels), and verifying an issue; the `fediim-issue-audit` CI action labels
structurally thin issues `needs-structure` until their body is completed.

## Required issue structure

Every filed issue MUST contain these sections, in this order. When filing via
`gh issue create --body …`, reproduce them — the structure is mandatory
regardless of entry point.

1. **Goal** — the outcome and why, in 1–3 sentences. Describe the result, not
   the steps.
2. **Tasks** — a `- [ ]` checklist of the concrete steps.
3. **Definition of Done / Acceptance Criteria** — verifiable conditions a
   reviewer checks (tests pass, a command succeeds, an observable behavior).
   Not vibes.
4. **Priority** — exactly one of `P0`/`P1`/`P2`/`P3`, with the matching
   `priority:*` label set.

Optional but encouraged: **Files Affected** (paths tagged `[NEW]`/`[MODIFY]`/
`[DELETE]`) and **Related** (linked issues, PRs, or spec files).

## Priority (importance)

Set exactly one priority label per issue.

- `priority:P0` — Critical. Drop everything: outage, data loss, security hole,
  or a hard blocker for many other issues.
- `priority:P1` — High. Foundational, security-sensitive, or on the critical
  path. Do soon.
- `priority:P2` — Medium. Normal scheduled work. **The default.**
- `priority:P3` — Low. Cleanup, nice-to-have, backlog.

If you cannot decide, it is `P2`. Reserve `P0` for genuine "stop other work"
situations.

## Picking the next issue

You decide what issue to pick next; no `ready` label is required. Only `blocked`
issues cannot be picked up. Where a build sequence or roadmap defines order,
take the lowest-sequence eligible issue whose declared dependencies
(`Depends on #NN`) are all closed — then use priority to break ties. Never
start an issue whose dependency is open, even at higher priority.
