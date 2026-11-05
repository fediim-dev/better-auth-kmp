---
name: rule-git-workflow
description: Use when creating a branch, committing, opening or finishing a pull request, or requesting review.
---
<!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
# Git & PR workflow  <!-- pack: planning/git-workflow v1.13.0 -->

One task = one issue = one branch = one PR. You decide what issue to pick next;
no `ready` label needed. Only `blocked` issues cannot be picked up (and keep
dependencies closed). Self-applying `ready` or clearing gates remains blocked
(hook-enforced) — never work around the hook.
- Merge policy: agent. The host agent MAY merge (allowed methods, never `--admin`)
  after an independent reviewer's pass and test/build passing locally at the
  PR head, cited on the PR (red CI blocks only if it ran the code), via `gh pr
  merge` so `Closes #n` fires. Reviewers are briefed read-only, never to merge.

## Branch, PR, and Definition of Done

- Branch names: `feat/<task-id>-<slug>` (`fix/`, `chore/` as applicable).
- PR title starts with the task id (e.g. `T07: <summary>`). PR body is
  `Closes #<n>`, the acceptance-criteria checklist, then
  `Plan: .fediim/plans/<n>.md` — the checklist IS the body; no essay around it.
- One logical change per PR. If the diff grows beyond the brief, split it.
- Commit messages: conventional, one line each. On a PR whose body says
  `Refs #<n>`, use `fix(#<n>): …` (or another scope), never a closing keyword
  (`Fixes #<n>`, `Closes #<n>`, `Resolves #<n>`) in any commit subject or body.
- Use the `gh` CLI for issue and PR operations.
- CI outage: a check that failed or never started without running the code
  (billing or spending limit, no runner, startup failure) is an outage, not a
  red result. Confirm with `gh pr checks <n>` / `gh run view <id>`, name the
  outage in the local-verification comment, and treat local test/build at the
  PR head as the gate. A check whose steps ran and failed still blocks; if you
  cannot tell which it is, it blocks. A check still `in_progress`/`queued` is
  cited as running, with no cause and no wait.
- Definition of Done: build and tests green, run once locally per the
  verification-gates rule; formatting applied by the static formatter; public
  symbols documented per the AI-first documentation rule; PR checklist
  complete. Only then request review.
- Merge gate: the verified PR head must contain `origin/main`
  (`git fetch origin main && git merge-base --is-ancestor origin/main HEAD`,
  hook-enforced on `gh pr merge`);
  if main advanced, merge `origin/main` into the branch and re-verify before
  merging.
- Label an issue `in-progress` when you start it; work one issue at a time
  **per agent** (an orchestrator may dispatch independent issues to parallel
  agents — see the parallel-dispatch rule).
- Plan phase: create the task branch, write `.fediim/plans/<n>.md` (planner
  format), commit `docs(plan): #<n>`, push, then comment on the issue with ONE
  link to the file at that commit plus a one-line summary, and add
  `plan-pending`. Never paste the plan text into comments. The plan reviewer
  replaces `plan-pending` with `plan-approved` — only then code. Revisions
  edit the file in place and commit; no revision comment. The plan file stays
  after merge (it is the assumptions ledger).
