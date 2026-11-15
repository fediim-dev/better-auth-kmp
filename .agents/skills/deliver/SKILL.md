---
name: deliver
description: Use when delivering a GitHub issue end-to-end, or when the operator runs /deliver.
---
<!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
# /deliver — deliver one issue end-to-end

Run ONE issue through select → plan → plan-review → code → verify → PR →
pr-review to the merge gate, then the Phase 9 retrospective. If a
required tool, label, or reviewer is unavailable, escalate (comment on the
issue, apply `blocked`) and stop — never improvise around a gate. A `blocked` run
still runs Phase 9 before stopping.

## Phase 1 — Select

1. Ensure labels exist: `gh label create <name>
   --force` for `ready`, `in-progress`, `plan-pending`, `plan-approved`,
   `blocked` and `needs-human-approval`.
2. List open issues. You decide what issue to pick next;
   no `ready` label is required. Never pick issues labelled `needs-human-approval`
   or `blocked`, or with open `Depends on #NN` dependencies.
3. Where a build sequence or roadmap defines order, take the lowest-sequence
   eligible issue; else the highest-impact open issue. Priority
   (`P0` > `P1` > `P2` > `P3`) breaks ties only.
4. Label it `in-progress`; work only this issue.

## Phase 2 — Plan

Run `node .fediim/tools/triage-task.mjs --issue <n>`; follow its `planMode`:
`none` (Tier 0–1) branches, skips plan and plan-review; `inline` (Tier 2) drafts the plan
yourself and `guards.mjs --check-plan .fediim/plans/<n>.md` replaces plan-review;
`planner` (Tier 3–4) continues below.

1. Plan ONLY from the approved task spec in the issue (spec-lock): no scope
   expansion or invented requirements.
   Run `node .fediim/tools/recall.mjs --phase plan "<issue terms>"`; give its
   DO/DON'T lines to the `planner`.
2. Draft breadth-first inline when this session already runs on `opus`/`pro` and
   the scope is ≤2 files; otherwise Claude Code invoke the `planner` subagent,
   Antigravity `invoke_subagent` (TypeName: 'planner', Role: 'Planner', Model: 'inherit', Workspace: 'inherit')
   with the issue text verbatim (not a gh command) and adopt the draft — you own it.
3. Include `Alternatives considered` (plan-breadth rule). List interpretations
   of ambiguity: reversible picks go to `## Assumptions`, irreversible ones
   are a human gate.
4. Name exact modules/files (`S<n>`), interfaces (`I<n>`) and tests (`T<n>`);
   no TBD, every step groundable in the working tree. Respect `CONSTRAINTS.md`;
   call out any touch of a protected or `autonomy: gated` path (human gate).
5. Create the branch now (`feat/<task-id>-<slug>`, `fix/`, `chore/`), write the
   plan to `.fediim/plans/<n>.md` in planner format, commit `docs(plan): #<n>`,
   push, comment on the issue linking that commit, and add
   `plan-pending`. Write NO code; commit only the plan file.

## Phase 3 — Plan review

Run `node .fediim/tools/dispatch-check.mjs --plan .fediim/plans/<n>.md`
(exit 1 → narrow plan or wait on the PR).

1. Invoke **plan-reviewer** — Claude Code
   `plan-reviewer` subagent; Antigravity `invoke_subagent`
   (TypeName: 'plan-reviewer', Role: 'Plan Reviewer', Model: 'inherit', Workspace: 'inherit')
   — never review your own plan.
2. Brief per the `fediim-rules` skill, `references/review-quorum.md` (load it): verbatim issue, plan
   file + commit SHA, iteration N of max 3 (1 + earlier
   `VERDICT: block — plan review` comments after `BUDGET: reset`; from N≥2
   the plan diff since last reviewed SHA + prior BLOCKING items).
3. Record the verdict: comment on the issue with first line
   `VERDICT: pass — plan review` or `VERDICT: block — plan review`, second line
   `Plan: <sha>`, then the reviewer's BLOCKING items citing plan ids.
4. `VERDICT: pass` → replace `plan-pending` with `plan-approved` and continue.
   `VERDICT: block` → edit the plan from BLOCKING items, commit,
   push and re-review, max 3 cycles; no revision comment (next verdict
   names the new SHA). If it cannot converge or carries
   `blocked`: comment on the issue, apply `blocked`, STOP.

## Phase 4 — Code

Run `node .fediim/tools/recall.mjs --phase code --plan .fediim/plans/<n>.md`;
give matched DO/DON'T lines to any implementer.

Who writes the code: load the `fediim-rules` skill and follow `references/implementation-delegation.md`.
Per dispatch: `node .fediim/tools/brief.mjs <plan> <ids>`; pass its path.

1. Stay on the Phase 2 branch and execute `.fediim/plans/<n>.md` (`I<n>`,
   `T<n>`, `S<n>` in order). One logical change in focused files; a diff outgrowing the plan splits the issue, not
   the PR. Record reversible gaps as new `L<n>` plan lines (assumptions-ledger
   rule).
2. TDD where the task requires it: failing test first, running only that test.
3. Never touch protected paths without the recorded approval marker; never
   add a runtime dependency — both `autonomy: gated` (human).
4. Compile only while coding (build command); tests run once, in Phase 5.

## Phase 5 — Verify

1. Run `triage-task.mjs --verify-diff --declared <tier>`; if `promoted`, Tier 0–1
   needs `.fediim/plans/<n>.md`, Tier 4 the security-reviewer. Then
   `node .fediim/tools/pr-prep.mjs --issue <n>`: archives the plan (read it from
   `archive/`; `.fediim/plans/**` is docs), formats, lints, runs static guards and build + test
   once (verification-gates rule; a docs-only diff skips it), stamps the tree. Commit
   the result. Red → fix the root cause within the attempt budget (budgets rule), else escalate and end.
2. NEVER weaken, skip, or delete a test, guard, or hook to go green; that
   fails the run: comment on the issue, apply `blocked`.

## Phase 6 — Pull request

1. Grep `docs/`, README, API specs, docstrings and E2E specs for each changed
   route, screen, old wording or public identifier; fix stale prose in the
   same commit. Then `gh pr create`: title starts with the task id, body is
   `Closes #<n>`, `Tier: <n>`, the acceptance-criteria checklist, and
   `Plan: .fediim/plans/archive/<n>.md`.
2. Comment pr-prep's `Local verification:` block (docs-only: `skipped`)
   on the PR; never wait on CI.

## Phase 7 — PR review

1. Pick this PR's reviewers from the eligibility list (load the `fediim-rules` skill, `references/review-quorum.md`). A PR reviewer (triage's level pick) is
   always required, dispatched only after the `Local verification:` comment;
   never review or approve your own PR. Dispatch the Phase 5 triage `reviewers`; an L2 fix loop escalates to `deep-reviewer` at cycle 3.
2. Claude Code: invoke each listed slug. Antigravity: one `invoke_subagent` per slug, Workspace: 'inherit':
   (TypeName: 'pr-reviewer-lite', Role: 'PR Reviewer Lite', Model: 'flash'),
   (TypeName: 'pr-reviewer', Role: 'PR Reviewer', Model: 'inherit'),
   (TypeName: 'deep-reviewer', Role: 'Deep Reviewer', Model: 'flash').
   The **security-reviewer** (TypeName: 'security-reviewer', Role: 'Security Reviewer', Model: 'inherit') is required only when that list marks the PR
   security-relevant; otherwise do not dispatch it. It may run earlier; a
   commit from its notes needs re-verifying before the pr-reviewer.
3. Brief each reviewer: issue spec,
   `.fediim/plans/archive/<n>.md`, PR number, the local verification comment and
   iteration N of max 5 (1 + earlier `VERDICT: block — PR review` comments
   after `BUDGET: reset`). Input: `gh pr diff <n>`; from N≥2 only the delta
   since the last reviewed SHA + prior BLOCKING items.
   N≥2: `node .fediim/tools/review-package.mjs <plan> <sha> <head>`.
4. Record the cycle: comment on the PR with first line
   `VERDICT: pass — PR review` or
   `VERDICT: block — PR review`, then each reviewer's verdict and BLOCKING items.
5. Review verdicts:
   - All dispatched reviewers must return `VERDICT: pass` → merge gate (Tier 4:
     first add `Security review: VERDICT: pass` to the PR body).
   - `VERDICT: block` from any reviewer → fix from BLOCKING items, run `[commands.format]` on every fix commit, compile only, push and re-review,
     at most 5 cycles (4–5: tier up, `fediim-rules` skill, `references/budgets.md`); on the final cycle
     without a pass, or once the PR carries `blocked`: comment the attempt
     history on the PR, apply `blocked`, STOP.

## Phase 8 — Merge gate (delivery ends here)

1. If non-doc commits landed after the verified SHA, or `git fetch origin
   main && git merge-base --is-ancestor origin/main HEAD` fails (merge
   `origin/main` in), run `node .fediim/tools/pr-prep.mjs` at the PR head, comment
   it. Red → fix, re-review.
2. You MAY merge with an allowed method (`gh pr merge <n> --merge|--squash|--rebase`,
   never `--admin`) only when ALL hold: the independent pr-review verdict is
   pass; local verification is green at the PR head and cited on the PR (a
   CI check blocks only if its steps ran and failed; an outage (billing, no runner,
   startup failure) is named on the PR, local checks stand); the issue/PR is not labelled
   `safety-critical` or `needs-human-approval`; and no `autonomy: gated`
   path was touched. The merge is yours: never delegate it to the
   pr-reviewer.
3. If any condition fails, comment that the PR is ready and request
   human merge.
4. Delivery is complete at this gate; continue to
   Phase 9.

## Phase 9 — Retrospect (then the run ends)

1. When this run hit friction (any `VERDICT: block`, failed verify/guard step,
   operator correction, or ended `blocked`), run the `retro` skill. On a clean first-try run, skip `retro`.
2. STOP — never select the next issue in the same run; start a new
   `/deliver` run.
