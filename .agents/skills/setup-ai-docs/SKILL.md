---
name: setup-ai-docs
description: Use when setting up, configuring, or troubleshooting the AI-First Documentation standard (ai-docs) in this repository (TypeScript, Kotlin/KMP, Python, Go), configuring compiler extractors, or establishing manifest publishing for internal libraries.
---
# Skill: Set Up AI-First Documentation (ai-docs)

Repository: [ai-first-documentation](https://github.com/fediim-dev/ai-first-documentation) (`ai-first-docs`)

This skill guides the installation, configuration, and verification of the AI-First Documentation Standard (`ai-docs`) in a project. Conforms to `spec/ai-first-documentation-spec.md` v1.0.

The standard eliminates RAG fragmentation, bounds agent context windows, and prevents hallucinations by compiling source code and docstrings into tiered context manifests (root L0 `ai-context.md`, module `<module>/ai-context.md`, and `llms.txt`), serving slices via `ai-docs query` / `ai-docs mcp`, and publishing manifests inside library artifacts.

---

## Procedure

### Step 1: Install or Verify the `ai-docs` CLI

Verify first: `ai-docs --help` must list `compile`, `lint`, `query` and `mcp`.

1. **Homebrew:**
   ```bash
   brew tap fediim-dev/ai-first-documentation https://github.com/fediim-dev/ai-first-documentation
   brew install fediim-dev/ai-first-documentation/ai-docs
   ```
   Releases up to v0.0.2 ship only the JarScanner jar under the name `ai-docs` (no `compile`/`lint`/`query`/`mcp`). If `ai-docs --help` prints `Usage: JarScanner`, uninstall it and build from source until a CLI-bundle release (`ai-docs-<version>-<os>-<arch>.tar.gz`) exists.
2. **From Go source (Go ≥ 1.18; `orchestrator/` is its own Go module):**
   ```bash
   git clone https://github.com/fediim-dev/ai-first-documentation && cd ai-first-documentation/orchestrator
   CGO_ENABLED=0 go build -o ../ai-docs .                              # Linux
   CGO_ENABLED=1 go build -ldflags="-linkmode=external" -o ../ai-docs .  # macOS Sonoma+
   ```
   The binary finds its extractors in `tools/` next to it, so keep it in the checkout and put that directory on `PATH` (or symlink the binary). For TypeScript modules, build the TS extractor once:
   ```bash
   (cd tools/typescript && npm install && npm run build)
   ```

---

### Step 2: Determine Repository Topology & Configure Language Extractors

Identify whether this repository is an **Internal Library / Shared SDK** (producer: publishes `ai-context.json` inside artifacts and optionally sets `emitSkill: true`) or an **Application / Service** (consumer & first-party: navigates via root `ai-context.md`, `<module>/ai-context.md`, and `ai-docs query` / `ai-docs mcp`).

#### A. Kotlin Multiplatform (KMP) & Kotlin/JVM (`"language": "jvm"`)
1. In `settings.gradle.kts`, add GitHub Packages (authenticated even for reads):
   ```kotlin
   dependencyResolutionManagement { repositories {
       maven {
           url = uri("https://maven.pkg.github.com/fediim-dev/ai-first-documentation")
           credentials {
               username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
               password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
           }
       }
   } }
   ```
2. In `gradle/libs.versions.toml`:
   ```toml
   [plugins]
   ksp = { id = "com.google.devtools.ksp", version = "2.3.12" }

   [libraries]
   ai-docs-annotations = { module = "com.fediim.docs:ai-annotations", version = "0.0.2" }
   ai-docs-compiler = { module = "com.fediim.docs:ksp-compiler", version = "0.0.2" }
   ```
3. In the target module's `build.gradle.kts`:
   - Apply KSP plugin: `alias(libs.plugins.ksp)`
   - **Crucial KMP scoping guardrail**: Add `ai-annotations` strictly to `jvmMain.dependencies`, never `commonMain` (`ai-annotations` is a JVM artifact; putting it in `commonMain` breaks iOS/JS/Wasm metadata resolution):
     ```kotlin
     kotlin {
         sourceSets {
             jvmMain.dependencies {
                 implementation(libs.ai.docs.annotations)
             }
         }
     }
     dependencies {
         add("kspJvm", libs.ai.docs.compiler)
     }
     ksp {
         arg("rootDir", rootProject.projectDir.absolutePath)
     }
     ```
4. **Internal Library Artifact Packaging**:
   Embed `ai-context.json` into the published JAR under `META-INF/`:
   ```kotlin
   tasks.named<Jar>("jvmJar") {
       from(layout.buildDirectory.file("ai-docs/module.json")) {
           into("META-INF")
           rename { "ai-context.json" }
       }
   }
   ```

#### B. TypeScript (`"language": "typescript"`), Python (`"language": "python"`), & Go (`"language": "go"`)
- Extractors (`ts-morph` for TypeScript, stdlib `ast` for Python, `go/ast` for Go) are invoked directly by `ai-docs compile`.
- Ensure docstrings follow §3 of the spec: one-sentence summary on line 1 (≤ 300 chars), contract tags (`[Invariant]`, `[SideEffects]`, `[Anti-Pattern]`, `[Recipe]`), and no restated parameter/return types.
- For published npm libraries, include `"ai-context.json"` in `"files"` of `package.json`; for Python packages, include `ai_context.json` in package data in `pyproject.toml`.

---

### Step 3: Initialize & Configure `ai-docs.config.json`

Run `ai-docs init` at the repository root and edit `ai-docs.config.json`. Do not pass `--install-skills`: it overwrites `.claude/skills/manifest-navigation/SKILL.md`, which fediim generates.
```json
{
  "projectName": "my-project",
  "summary": "One-line high-level description of what this project or library provides",
  "modules": [
    {
      "path": "core",
      "language": "jvm",
      "buildTask": ":core:compileKotlinJvm",
      "summary": "Core domain logic and public contracts",
      "emitSkill": true,
      "skillName": "my-core-sdk"
    }
  ],
  "excludePaths": [
    "**/node_modules/**",
    "**/build/**",
    "**/dist/**",
    "**/*Test.kt",
    "**/*_test.go",
    "**/*.test.ts"
  ],
  "dependencies": [],
  "lint": {
    "minCoverage": 80.0,
    "failOnViolation": false,
    "failOnEmptyL1Summary": false,
    "checkRecipes": false
  },
  "budgets": {
    "l0": 500,
    "entry": 2000,
    "l1PerModule": 4000,
    "l2PerModule": 20000,
    "l3PerModule": 4000
  }
}
```

> **Skill Emission Guardrail**: When setting `emitSkill: true`, ensure `skillName` does not collide with an existing human-authored skill in `.agents/skills/` or `.claude/skills/`. If a domain skill already exists, omit `emitSkill` or suffix `skillName` with `-api`.

---

### Step 4: Brownfield / Existing Codebase Strategy

When adding `ai-docs` to an existing codebase with legacy, undocumented code:

1. **Scoped Onboarding**: Register only shared/core modules in `modules` initially.
2. **Bootstrap L1 Index with `ai-docs summarize`**:
   ```bash
   ai-docs summarize --dry-run   # preview
   ai-docs summarize             # write hash-keyed ai-summaries.json
   ```
   Generates `[ai]` summaries cached by content hash in `ai-summaries.json` (human docstrings always take precedence).
3. **Soft-Gate Coverage Initially**:
   Keep `failOnViolation: false` (the default above) until core public symbols are annotated; then set it to `true` for CI. `checkRecipes: true` needs each language's toolchain. Enforce strict contracts on *new* code via `contract-first-coding`.

---

### Step 5: Compilation, Verification, Query & MCP Server

1. **Compile Manifests**:
   ```bash
   ai-docs compile
   ```
   Verify outputs:
   - Root `ai-context.md`: **L0-only** project map with module pointers `(see <module>/ai-context.md)` and external library pointers (entry budget ≤ 2,000 tokens, `l0` ≤ 500 tokens).
   - `<module>/ai-context.md`: module-scoped L1 symbol index (with `file:line` or `file:start-end` spans) + L2 signatures + L3 guardrails/recipes (`l1PerModule` ≤ 4,000, `l2PerModule` ≤ 20,000, `l3PerModule` ≤ 4,000).
   - `llms.txt`: L0 + merged L1 projection for external ecosystem tooling.
2. **Run Lint Conformance**:
   ```bash
   ai-docs lint
   ```
   Verifies V1 (SAKL-JSON schema), V2 (coverage), V3 (tier token budgets), V4 (recipe compilation), V5 (staleness), V6 (over-documentation), and V7 (repo-root `file:line` / `file:start-end` location integrity).
3. **Verify On-Demand Slice Queries & MCP Server**:
   - CLI slice queries:
     ```bash
     ai-docs query module <path> [--tier 1|2|3]
     ai-docs query symbol <name> [--tier 1|2|3]
     ai-docs query search <query> [--tier 1|2|3] [--limit 20]
     ```
   - Register the stdio MCP server (`query_module`, `query_symbol`, `search_symbols`) so coding agents fetch slices on demand:
     - Claude Code (`.mcp.json`):
       ```bash
       claude mcp add --transport stdio --scope project ai-docs -- ai-docs mcp
       ```
     - Antigravity (`.agents/mcp_config.json`):
       ```json
       {
         "mcpServers": {
           "ai-docs": { "command": "ai-docs", "args": ["mcp"] }
         }
       }
       ```
4. **Install Git Hooks**:
   From the target repository, run `<ai-first-documentation checkout>/git-hooks/install-hooks.sh` so commits recompile changed modules offline (`ai-docs compile --offline`) and stage updated manifests without blocking commits. The hooks look only for `./orchestrator` (with Go) or an executable `./ai-docs` at the repository root, not `PATH`: symlink the binary there and gitignore it, or the hooks warn and skip.
