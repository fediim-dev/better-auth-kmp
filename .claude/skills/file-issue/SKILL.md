---
name: file-issue
description: Use when creating a GitHub issue, or when turning a follow-up discovered mid-task into a backlog item.
---
<!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
# /file-issue — file one structured GitHub issue

File ONE GitHub issue carrying the full mandatory structure from the
issue-structure rule: Goal, Tasks, Definition of Done, Priority — plus Files
Affected and Related where known. Never file a bare one-liner: an issue
another agent cannot implement without asking questions is a defect. Work
through the phases in order; the full rulebook in `AGENTS.md` still applies.

## Phase 1 — Gather (before touching gh)

1. **Goal** — the outcome and why, in 1–3 sentences. Describe the result,
   not the steps. If you cannot state the goal, ask the operator ONE
   clarifying question instead of filing a vague issue.
2. **Tasks** — a `- [ ]` checklist of concrete steps, each independently
   checkable. 2–8 items is the normal range; derive every item from the
   goal.
3. **Definition of Done / Acceptance Criteria** — verifiable conditions a
   reviewer checks: tests pass, a command succeeds, an observable behavior.
   Not vibes. If a criterion cannot be checked objectively, rewrite it
   until it can.
4. **Priority** — exactly one of `P0`/`P1`/`P2`/`P3`: P0 outage or hard
   blocker, P1 critical path or security-sensitive, P2 normal work (the
   default), P3 cleanup. Undecided means P2.
5. **Files Affected** — real paths from the working tree, each tagged
   `[NEW]`, `[MODIFY]`, or `[DELETE]`. Check the tree first (`ls`, grep) —
   never guess paths.
   Files Affected is required for parallel-dispatch eligibility: an issue
   without it always runs alone.
6. **Related** — linked issues, PRs, or spec files. Declare hard ordering
   explicitly as `Depends on #NN`.
7. **Session Profile** — run `fediim session catalog`, then write a
   `## Session Profile` section with a ```toml fenced block. Prefer
   `profile = "<name>"` when a saved profile fits; otherwise list only the
   needed `skills`, `integrations`, `packs`, plus `model`, `effort` and a
   one-line `brief` that does not start with `-`.

## Phase 2 — Compose the body

Use exactly these section headings, in this order — they match
`.github/ISSUE_TEMPLATE/task.md` and the CI structure audit:

    ## Goal
    ## Tasks
    ## Definition of Done / Acceptance Criteria
    ## Priority
    ## Files Affected (optional)
    ## Related (optional)
    ## Session Profile (optional)

Title: imperative, ≤ 70 chars, prefixed with the task id where the roadmap
defines one (e.g. `T07: <summary>`).

## Phase 3 — File it

1. Ensure the labels exist (idempotent, safe to repeat):
   `gh label create "priority:P2" --force` — likewise for the chosen tier,
   and `wave:backlog` when filing a mid-task follow-up.
2. Create the issue:
   `gh issue create --title "<title>" --body "<body>" --label "priority:PX"`
   — add `--label "wave:backlog"` for follow-ups discovered mid-task.
3. NEVER self-apply `ready` or `approved-to-start`, and never remove
   `needs-human-approval` — those labels are human-only (git-workflow
   rule); the issue enters the queue when a human marks it `ready`.

## Phase 4 — Verify (the issue is not filed until this passes)

1. `gh issue view <n>` — confirm all four mandatory sections are present
   with content and exactly one `priority:*` label is set. Fix by
   `gh issue edit <n> --body …` if anything is missing.
2. The CI audit labels structurally thin issues `needs-structure` with a
   checklist comment; if that happens to your issue, complete the body —
   the audit clears the label on edit.
3. Report one line: `Filed #<n> (PX): <title>.`
