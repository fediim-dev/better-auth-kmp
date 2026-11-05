---
description: planning a change that adds new dependencies, Gradle plugins or repositories; before asking the operator to approve them
phase: [plan]
paths: ["gradle/libs.versions.toml", "settings.gradle.kts", "build-logic/**"]
validated: 1
last-validated: 2026-11-05
evidence: "#8 (PR #19: FDS 0.1.4-SNAPSHOT 404 on GitHub Packages and the CMP plugin need for wasm Skiko were found only in the code phase, after operator approval)"
---
DO resolve every new artifact once (on a scratch branch add the alias, run the module's `dependencies` task, which really resolves, and one real js/wasmJs test task) before putting the dependency list to the operator — #8 learnt at code time that FDS was unresolvable from its declared repo and that the contingency plugin was required.
