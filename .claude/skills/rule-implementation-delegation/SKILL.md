---
name: rule-implementation-delegation
description: Use when about to write or change code and deciding whether to make the change yourself or hand it to the implementer subagent.
---
<!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
# Implementation delegation (Claude Code)  <!-- pack: planning/implementation-delegation v1.3.0 -->

Code changes estimated above `[delegation] implementLines` in `fediim.toml`
(default 100 changed lines) go to the `implementer` subagent; smaller ones you
make yourself. You plan, brief and review the diff.

- **Estimate & warm-context check.** Count added plus deleted lines from the
  plan before writing code (`implementLines = 0` means always delegate). Keep
  edits inline in your warm session when ≤`implementLines` or when editing a
  single file already loaded in your context (≤150 lines) so a cold subagent
  does not pay to re-read it; delegate when >`implementLines` across files or
  applying mechanical changes across ≥3 files.
- **Crossing the limit.** If an edit you are making yourself grows past the
  limit, stop and delegate the rest.
- **Brief.** Pass `.fediim/plans/<n>.md` and the target `S<n>`, `I<n>`, `T<n>`,
  and `L<n>` IDs (including `file:line` / `file:start-end` anchors so the
  subagent opens exact slices without searching). Never paraphrase a written plan
  into prose — the implementer executes the plan IDs and reports completed IDs.
  Add DO/DON'T rules matched by `recall.mjs --phase code` (when emitted).
- **Review.** Start from `git diff --stat`, check that every assigned `S<n>`,
  `I<n>`, and `T<n>` is present in the diff, and read closely only the risky
  parts. The implementer's report is not evidence; the diff and your one
  end-of-phase verification run are.
- **Fixes.** Send them back to the same implementer (SendMessage) so it keeps
  its context, unless the fix itself is under the limit.
