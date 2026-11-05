---
description: planning marks an issue task "already done" or "no change needed" because a setting is absent from a file (env, credentials, flags, inherited config)
phase: [plan]
paths: ["**/*.gradle.kts", ".github/workflows/*"]
validated: 1
last-validated: 2026-11-05
evidence: "#13 (PR #23: plan S4 said npmCi had no GITHUB_TOKEN because the script had no environment() call; Gradle Exec inherits the process env and CI sets the token — the security review caught it, which cost a fix commit, a re-verify and a second review)"
---
DON'T mark a task already satisfied because the setting is missing from the file; check what the tool inherits by default (Gradle Exec inherits the whole process env) and what the caller (CI workflow) sets. An absent override is not an absent value.
