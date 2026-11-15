---
paths:
  - "gradle/libs.versions.toml"
  - "settings.gradle.kts"
  - "build-logic/**"
---
<!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
planning a change that adds new dependencies, Gradle plugins or repositories; before asking the operator to approve them

DO resolve every new artifact once (on a scratch branch add the alias, run the module's `dependencies` task, which really resolves, and one real js/wasmJs test task) before putting the dependency list to the operator — #8 learnt at code time that FDS was unresolvable from its declared repo and that the contingency plugin was required.
