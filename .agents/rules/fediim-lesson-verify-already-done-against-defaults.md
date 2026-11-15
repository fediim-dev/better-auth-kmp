---
trigger: glob
globs: "**/*.gradle.kts, **/.github/workflows/*"
description: planning marks an issue task "already done" or "no change needed" because a setting is absent from a file (env, credentials, flags, inherited config)
---
<!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
planning marks an issue task "already done" or "no change needed" because a setting is absent from a file (env, credentials, flags, inherited config)

DON'T mark a task already satisfied because the setting is missing from the file; check what the tool inherits by default (Gradle Exec inherits the whole process env) and what the caller (CI workflow) sets. An absent override is not an absent value.
