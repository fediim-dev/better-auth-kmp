<!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
@AGENTS.md
@.fediim/memory/rules.md
@.claude/rules/planning-implementation-delegation.md
@.claude/rules/planning-model-fallback.md

# Claude Code specifics
- Hard-enforcement rules in AGENTS.md are additionally enforced by hooks in .claude/settings.json. Do not bypass hooks.
- Routine actions in this project: pushing feat/, fix/ and chore/ branches, creating labels, applying the in-progress and plan-* labels, and running the configured test and build commands. The ready, approved-to-start and needs-human-approval labels are human-only: when a hook blocks one, ask the operator and stop. Never retry it or investigate the hook.
