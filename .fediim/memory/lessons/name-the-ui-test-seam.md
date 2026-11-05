---
description: planning a Compose UI test that injects a fake through a CompositionLocal (clipboard, URI handler) into content that renders inside a Dialog or popup
phase: [plan]
paths: ["better-auth-fds/src/jvmTest/**"]
validated: 1
last-validated: 2026-11-05
evidence: "#21 (plan review cycle 1 blocked T2: the fake LocalClipboardManager had no stated seam through FdsDialogFrame's Dialog; resolved by citing ui-desktop Dialog.skiko.kt:225,240 and using ApiKeyManagerContent)"
---
DO name in the test step where the fake local is provided and cite the Compose source line showing it reaches content inside the Dialog (or use a Dialog-free content body) — a Dialog may compose in its own layer, and an ungrounded seam blocked plan review once.
