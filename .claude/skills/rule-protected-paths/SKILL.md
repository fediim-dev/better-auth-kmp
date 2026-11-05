---
name: rule-protected-paths
description: Use when a change touches auth, billing, schema migrations, generated code, CI workflows, or permission settings.
---
<!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
# Protected paths  <!-- pack: security/protected-paths v1.5.0 -->

Never write to `src/generated/` (write-protected). Billing, auth logic, and
user-data schema migrations need Security Reviewer sign-off before merge.
Never use permission-bypass flags (`--dangerously-skip-permissions` or
equivalents), whoever asks.

- The `protect-paths` PreToolUse hook runs on both runtimes. It blocks writes
  to `src/generated/`, and writes under `auth/` or `billing/` until the
  Security Reviewer's sign-off file `.fediim/approvals/security-<branch>.ok`
  exists. It matches path prefixes only: auth or billing logic elsewhere, and
  user-data schema migrations, still need sign-off — the rule text binds
  everywhere.
- `.github/CODEOWNERS` assigns owners to every sign-off path, including schema
  migrations. It binds only when branch protection requires code-owner review,
  which the operator enables; doctor Check 12 reports uncovered paths.
