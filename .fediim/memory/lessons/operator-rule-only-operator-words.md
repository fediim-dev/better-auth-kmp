---
description: writing an operator rule via remember-rule, or choosing a version scheme / identifier format (snapshot, alpha/beta/rc numbering) the operator has not spelled out
phase: [plan]
validated: 2
last-validated: 2026-11-06
evidence: "#32; #36"
---
DON'T fill gaps in an operator instruction or a versioning scheme with your own default (digit padding, `-SNAPSHOT`) — ask once with proposed defaults: #32 put main on a `-SNAPSHOT` the operator never wanted (undone in #36), and #36's rules.md/README line invented the `-alphaNN`/`-betaNN`/`-rcN` digit format, which PR review blocked.
