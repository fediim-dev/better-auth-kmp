---
name: rule-review-quorum
description: Use when a plan or pull request is ready for review, or when performing a plan or PR review.
---
<!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
# Review quorum  <!-- pack: planning/review-quorum v1.12.0 -->

Authors never review their own work: plans → plan-reviewer, diffs →
pr-reviewer; work advances or merges only on a recorded `pass` verdict from an
isolated reviewer subagent (never in the author's own turn).

- The reviewer emits a structured pass/block verdict from an isolated subagent
  context:
  - **Implementer on Claude Code** → invoke the reviewer persona via its native
    subagent definition (`.claude/agents/<reviewer>.md`).
  - **Implementer on Antigravity** → invoke the reviewer persona via
    `invoke_subagent` (`TypeName: '<reviewer>'`, `.agents/agents/<reviewer>.md`),
    running in an isolated subagent conversation.
- **Recording.** After each review cycle the host (never the reviewer) posts
  one comment — on the issue for a plan review, on the PR for a PR review —
  whose first line is exactly `VERDICT: pass — plan review`,
  `VERDICT: block — plan review`, `VERDICT: pass — PR review`, or
  `VERDICT: block — PR review`, followed by each reviewer's verdict and the
  OPEN ITEMS. With several reviewers, the cycle is `block` if any blocks. This
  comment is the recorded verdict gates close on. A plan-review comment's
  second line names the reviewed plan commit (`Plan: <sha>`); OPEN ITEMS cite
  plan ids instead of quoting the plan.
- A `pass` advances the work per the verification-gates and human-gates rules.
  A `block` returns it to the author with OPEN ITEMS, bounded by the budgets
  rule's forced-convergence limits.
- For higher-assurance changes implemented on Claude Code you MAY add a second,
  decorrelated reviewer on the other runtime to catch correlated failure
  modes. This second reviewer is opt-in, not the default.
- **Persona & reviewer eligibility (smart ROI triage).** Spawn a subagent only when its model/isolation benefit outweighs the cold-context handoff:
  - `planner` is conditional: dispatch when the host session runs on a non-planning model (`sonnet`/`flash`) or the issue spans ≥3 modules / >`implementLines`; when the host is already on `opus`/`pro` (Gemini 4 Argon) and the scope is ≤2 files, draft `.fediim/plans/<n>.md` inline to avoid reading the same files twice on the planning model.
  - `plan-reviewer` is universal when selected: every plan is reviewed in an isolated context; the drafter never approves its own plan.
  - `pr-reviewer` is universal: every PR must pass acceptance-criteria and verification-gate review in an isolated context.
  - `security-reviewer` is conditional: required for PRs touching authentication, sessions or tokens, authorization (including moving, renaming, or wrapping an existing authorization, tenant, or scope check in a refactor), data handling, billing, secrets, schema migrations, input validation or injection risks, protected paths (including `.github/workflows/` and CI guards), or issues labelled `security` or `safety-critical`; explicitly skipped for UI presentation, styling, layout, copy, or docs changes.
  - Only dispatched reviewers must return `VERDICT: pass`.
- **Verdicts are comments.** Reviewer subagents never run `gh pr review
  --approve` or `--request-changes` (`gh` shares the author's identity); the
  host's `VERDICT: pass|block — PR review` comment is the canonical record.
  Approvals required by branch protection come from a human or a separately
  configured reviewer bot, outside fediim.
- **Review-fix verification.** Cite `file:line` or a test name in the
  re-review brief for any behaviour sentence or snippet added or reworded in a
  fix cycle, including a reviewer's suggested wording.
- When two reviewers disagree and the bounded review cycle cannot converge,
  that is a human gate (see the human-gates and escalation rules).
