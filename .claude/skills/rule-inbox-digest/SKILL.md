---
name: rule-inbox-digest
description: Use when escalating to a human, applying the `blocked` or `needs-human-approval` label, or when there is no issue to comment on.
---
<!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
# Escalation  <!-- pack: planning/inbox-digest v1.4.0 -->

Escalate on GitHub: comment a structured note on the issue/PR and apply
`blocked`, `blocked:spec-ambiguity` (awaiting a decision), or
`needs-human-approval` (gated action); then end the run.

The work tracker is the escalation channel; a local file is only a fallback
when there is no issue to escalate against. (Pack id is `planning/inbox-digest`
for historical reasons.)

- **Backlog items, not blockers.** Non-blocking follow-ups discovered mid-task
  are filed as new issues (label `wave:backlog`) per the issue-structure rule,
  not held in the current PR.
- **Fallback — local inbox.** Only when there is no associated issue (e.g.
  project intake before any issues exist), append an escalation file under
  `.fediim/inbox/` named `<date>-<topic>.md`.
- Every escalation (comment or file) uses a structured header:
  - **AGENT**: the runtime or persona ID reporting.
  - **TASK**: the issue/PR number and context.
  - **REASON**: the cause (e.g. budget breach, validation failure, spec gap).
  - **MESSAGE**: the action requested of the operator.
