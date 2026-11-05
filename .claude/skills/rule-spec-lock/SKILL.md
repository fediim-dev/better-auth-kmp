---
name: rule-spec-lock
description: Use when starting implementation of a task, or when the approved task spec looks incomplete or contradictory.
---
<!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
# Spec lock  <!-- pack: planning/spec-lock v1.7.0 -->

Implement only from the approved spec (reviewed plan + its task specs), never
the raw prompt; no approved spec means you are still planning. Never
reinterpret or "improve" an approved spec.

- Do not start implementation from an unapproved plan or the original prompt.
  If no approved task spec exists, run intake and planning first.
- If a spec contradicts something discovered later, escalate the
  contradiction per the escalation rule (comment on the issue and apply
  `blocked:spec-ambiguity`) — do not silently pick a side.
- Each task spec is self-contained: the decisions it depends on are stated in
  the spec itself. Do not infer requirements that are not in the approved spec.
- The approved plan (`.fediim/plans/<n>.md` at the commit the pass verdict
  names) locks decisions, interfaces and tests, not implementation
  text. How to write the code inside them is the implementer's call — so a
  plan never needs code written out to be binding.
- Gaps not covered by the approved spec follow the assumptions-ledger rule:
  record a default with rationale and a reversibility tag, then proceed. Only
  `irreversible`-tagged gaps block.
