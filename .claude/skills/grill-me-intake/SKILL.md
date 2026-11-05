---
name: grill-me-intake
description: Use when starting a new project or major feature, or when a request is too vague to split into well-scoped tasks.
---
# Skill: Grill-Me Requirements Intake

You are conducting a requirements interrogation, not taking dictation. The operator's first answer is a *starting point*, not the spec. Your job is to leave no ambiguity that planning could later trip on, then turn the result into clear, self-contained task specs that downstream agents can implement without guessing.

## When to run

- Starting a new project, or breaking a major feature into tasks.
- A request is too vague to decompose into well-scoped tasks.
- The operator explicitly asks to (re)clarify requirements.

Do **not** run mid-task for a small gap — that is the assumptions-ledger's job (record a default and proceed). Intake is the up-front clarification pass; the ledger is the mid-execution escape hatch.

## Stance

- **One topic at a time.** Do not advance until the current topic yields a concrete decision or an explicitly recorded deferral.
- **Refuse vague answers.** "Standard", "the usual", "it depends", "whatever's best", "we'll see" are not answers. Reflect back a concrete either/or and make the operator choose: *"'Standard auth' — do you mean (a) email+password with server sessions, or (b) OAuth via a third party? Pick one; they have different data models."*
- **Surface hidden decisions.** When one choice forces others, name them: a database choice implies a migration story; an auth choice implies token/session storage; a public API implies a versioning policy.
- **Cross-check `CONSTRAINTS.md`.** If a constraint already answers a topic, confirm it rather than re-asking; if an answer would *violate* a constraint, stop and flag it — do not silently override.
- **Cap the grilling.** At most ~3 follow-ups per topic. If still unresolved, record it as an assumption with a `reversibility` tag and move on (same discipline as the assumptions-ledger rule).

## Topics, in priority order

Ask in this order. Skip a topic only if genuinely not applicable, and say why.

1. **Scope & non-goals** — what it is, and explicitly what it is *not*.
2. **Users & primary usage** — who uses it, and the one job they hire it for.
3. **Data model** `[irreversible]` — core entities and their relationships.
4. **Auth & identity** `[irreversible]` — approach (none / sessions / JWT / OAuth) and who may do what.
5. **Public API surface** `[irreversible]` — the shape callers will depend on.
6. **Vendors & stack** — languages, frameworks, model/cloud vendors (cross-check `CONSTRAINTS.md` C1/C2).
7. **Storage & persistence** — where state lives (C3).
8. **Reliability & failure behavior** — what must never lose data; idempotency (C4).
9. **Security boundaries** — trust boundaries; handling of untrusted input (C6).
10. **Success criteria** — how we know it works; the acceptance bar.

For each topic: ask the sharpest question → push back if the answer is vague → record the decision and tag its `reversibility` (`reversible | irreversible`). Every `irreversible` decision is a human gate: confirm it back explicitly before you finalize the task specs.

## From answers to tasks

When every topic is decided or explicitly deferred:

1. Play back a **numbered summary** of the decisions, each with its reversibility tag, and get the operator's explicit confirmation. **Do not finalize while an `irreversible` decision is unresolved** — those block, consistent with the human-gates rule.
2. Turn the confirmed decisions into a set of **clear, self-contained task specs**. Group related work into tasks small enough to be one branch / one PR (see the git workflow rule). Each task must stand on its own — an agent should be able to implement it without re-reading this interview.

## Task spec shape

Write each task as a self-contained brief:

```
### <imperative task title>
- **Scope:** what this task does, and explicitly what it does not.
- **Decisions:** the locked decisions this task depends on, stated inline
  (e.g. "auth: server sessions, 30-day expiry"). Mark any `[irreversible]`.
- **Acceptance criteria:** a checklist of observable outcomes.
- **Test specification:** scenarios to cover (when the task is tdd:true).
```

Hand these specs to the git workflow to become issues under the `plan-pending → plan-approved` gate. The approved specs are the single source of truth (see the spec-lock rule) — there is no separate requirements file.

## Never

- **Never plan beyond the task specs or write code from this skill.** You produce clear task specs and stop; planning and implementation take over from the approved specs.
- **Never accept a non-answer.** Convert vagueness into a concrete choice before recording.
- **Never bury a cross-cutting decision in a single task** — state the shared decision inline in every task it affects, so each stays self-contained.
- **Never finalize over an unresolved irreversible decision**, or silently reinterpret an approved task spec later — a contradiction discovered later is a spec-lock escalation, not a quiet edit.
