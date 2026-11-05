# AI-first docstring templates

Reference for the `inline-ai-doc-encoder` skill: per-language templates, a
worked example at target quality, and an anti-example. Keep `SKILL.md`'s
one rule in mind: document only what the signature cannot express.

## Templates

**TypeScript / JavaScript (JSDoc):**
```typescript
/**
 * <One sentence: what + when to use.>
 *
 * <Non-obvious contracts: units, threading, lifecycle, errors.>
 * [Invariant] <property callers may rely on>
 * [Anti-Pattern] <forbidden usage — why>
 * @deprecated [Migrate: use NewService instead]
 * [Recipe] <title>
 * ```ts
 * const service = new Service({ timeoutMillis: 5000 });
 * ```
 */
```

**Kotlin / Java (KDoc / Javadoc):**
```kotlin
/**
 * <One sentence: what + when to use.>
 *
 * <Non-obvious contracts only: units, threading, lifecycle, errors.>
 * [Invariant] <property callers may rely on>
 * [Anti-Pattern] <forbidden usage — why>
 * @deprecated [Migrate: use NewService instead]
 * [Recipe] <title>
 * ```
 * val service = Service(timeoutMillis = 5000)
 * ```
 */
```

**Python (PEP 257):**
```python
"""<One sentence: what + when to use.>

<Non-obvious contracts only: units, threading, lifecycle, errors.>
[Invariant] <property callers may rely on>
[Anti-Pattern] <forbidden usage — why>
[Recipe] <title>
>>> service = Service(timeout_millis=5000)
"""
```

**Rust (Rustdoc):**
```rust
/// <One sentence: what + when to use.>
///
/// <Non-obvious contracts only: units, safety, lifecycle.>
/// [Invariant] <property callers may rely on>
/// [Anti-Pattern] <forbidden usage — why>
///
/// # Errors
/// Returns [`Error::Corrupt`] if the underlying journal cannot be parsed.
///
/// # Panics
/// Panics if `capacity_bytes` is zero.
///
/// # Example
/// ```rust
/// let cache = Cache::new(path, 1024 * 1024)?;
/// ```
```

**Go (Godoc):**
```go
// <SymbolName> <one sentence: what + when to use>.
//
// Contracts: units, concurrency, error behavior.
// [Invariant] <property callers may rely on>.
// [Anti-Pattern] <forbidden usage — why>.
// Returns ErrNotFound if the key does not exist.
// Safe for concurrent use by multiple goroutines.
```

## Worked example (target quality)

```kotlin
/**
 * Disk-backed LRU cache for HTTP responses; one instance per cache directory.
 *
 * Thread-safe. maxSize is bytes; eviction is asynchronous best-effort.
 * Throws IOException on a corrupt journal and recovers by clearing the cache.
 *
 * [Anti-Pattern] Never share one directory between two Cache instances — corrupts the journal.
 * [Recipe] Attach to a client
 * ```
 * val client = OkHttpClient.Builder().cache(Cache(dir, 50L * 1024 * 1024)).build()
 * ```
 */
class Cache(directory: Path, maxSize: Long) : Closeable
```

Every line states something the signature cannot. ~70 tokens. Anti-example — what NOT to produce:

```kotlin
/**
 * This class represents a cache. It is used to cache things.
 * @param directory the directory          // restates the type — delete
 * @param maxSize the max size             // no unit given — the one fact that mattered
 */
```

## How this skill is installed

fediim's `coding/ai-first-docs` pack installs this skill and also emits the standing rule into the project's `AGENTS.md`, so it applies to all agent work automatically — no manual copy needed. If you are using this skill outside fediim, copy the summary below into your `CLAUDE.md` / `AGENTS.md`:

> When creating or modifying any public symbol, follow `inline-ai-doc-encoder/SKILL.md` (AI-First Documentation): one-sentence summary first line; non-obvious contracts (units, threading, lifecycle, errors); `[Anti-Pattern]` for known traps; one compiling `[Recipe]` per component; never restate what the signature already says; zero docs for trivial members.
