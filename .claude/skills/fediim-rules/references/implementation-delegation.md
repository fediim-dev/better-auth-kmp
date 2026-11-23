<!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
# Implementation delegation (Claude Code)  <!-- pack: planning/implementation-delegation v2.1.0 -->

Code changes estimated above `[delegation] implementLines` in `fediim.toml`
(default 100 changed lines) go to the `implementer` subagent; smaller ones you
make yourself. You plan, brief and review the diff.

- **Same-tier guard.** When the host session already runs on `sonnet`, keep
  sequential edits inline rather than spawning `implementer` (`sonnet` →
  `sonnet` pays handoff cost with no tier saving); still delegate
  `[mechanical]` batches to `haiku` or parallel worktrees.
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
- **Brief file.** Run `node .fediim/tools/brief.mjs <plan> <id>...` (when
  emitted) before each dispatch; it writes `.fediim/run/<n>/brief-<ids>.md`
  with the plan lines, constraints, lessons and the `Report:` path.
- **Dispatch recipe.** The dispatch contains (1) one line on where the step
  fits, (2) the brief path, introduced as "read first", (3) interfaces and
  decisions from earlier steps that the brief cannot know, (4) your resolution
  of any ambiguity. Exact values (numbers, strings, signatures, test cases)
  appear only in the brief.
- **Model per step.** Always name the model in the dispatch; an omitted one
  inherits the host's, usually the most expensive. Map the step's tag through
  `[delegation] tiers` in `fediim.toml` (defaults: `[mechanical]` →
  `claude-haiku-5-5`; untagged, standard → `claude-sonnet-5-5`);
  `[judgment]` stays with you. Send several `[mechanical]` steps of the same
  shape in one dispatch.
- **Status.** The implementer replies `STATUS: DONE | DONE_WITH_CONCERNS |
  NEEDS_CONTEXT | BLOCKED` and writes its report to the `Report:` path.
  NEEDS_CONTEXT: add the context and continue the same agent. BLOCKED: more
  context, one tier up (Fix loop), split the step, or, when the plan is wrong, an
  `L<n>` entry and the human gate.
- **Review.** Start from `git diff --stat` or `node .fediim/tools/review-package.mjs
  <plan> <base> <head>` (when emitted), check that every assigned `S<n>`,
  `I<n>`, and `T<n>` is present in the diff, and read closely only the risky
  parts. The implementer's report is not evidence; the diff and your one
  end-of-phase verification run are.
- **Fixes.** Send them back to the same implementer (SendMessage) so it keeps
  its context, unless the fix itself is under the limit.
- **Fix loop.** Rounds 1–3 resume the same implementer. Rounds 4–5 go to a
  fresh context one tier above whoever got stuck (mechanical → fresh standard
  subagent; standard → you or a fresh `opus` subagent), given the brief, the
  report file ("read the report file for what was tried") and the open
  findings. Then the budget ends the run.
