---
description: bumping a dependency version, docs search for the old version string, KDoc comments naming a library version
phase: [plan]
paths: ["gradle/libs.versions.toml", "**/*.kt"]
validated: 1
last-validated: 2026-11-06
evidence: "#20"
---
DO run the docs search for an old dependency version over source files (`git grep '<old version>'` repo-wide, not just README/docs) and re-check each gap claim it backs against the new version — plan #20 cycle 1 blocked on two BetterAuthDialog.kt KDoc lines naming FDS 0.1.4 that a README-only search missed.
