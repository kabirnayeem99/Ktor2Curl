# Ktor2Curl — Project Guide

Kotlin Multiplatform **library**: a Ktor 3.5 client plugin (`KtorToCurl`) that renders outgoing HTTP requests as runnable `curl` commands, for logging/debugging.

**Not an Android app.** It has an `android` target but no UI. Ignore Compose / XR / billing / navigation skills in `.agents/skills/` — irrelevant here. Only `tdd` and `testing-setup` are relevant.

## Layout
- Module `:ktor2curl` (dir `ktor2curl/`) — the published KMP library.
- `ktor2curl/src/commonMain/.../ktor2curl/`
  - `CurlCommandGenerator.kt` — core: `generateCurl`, header handling, body → `-d`/`-F` (multipart) rendering.
  - `KtorToCurl.kt` — the Ktor `createClientPlugin` entry.
  - `KtorToCurlConfig.kt` — config (converter, excluded/masked headers).
  - `CurlLogger.kt` — output sink interface.
- `ktor2curl/src/commonTest/.../CurlCommandGeneratorTest.kt` — kotlin.test.
- Module `:sample` (dir `sample/`) — runnable JVM console demo: `./gradlew :sample:run`.
- Targets: `android`, `jvm`, `iosX64`, `iosArm64`, `iosSimulatorArm64`. Package: `io.github.kabirnayeem99.ktor2curl`.

## Build & test
- `./gradlew :ktor2curl:jvmTest --console=plain` — fastest test gate (JVM-backed `commonTest`).
- `./gradlew :ktor2curl:testDebugUnitTest --console=plain` — Android-unit run of the same `commonTest`.
- `commonTest` also runs via `:ktor2curl:iosSimulatorArm64Test`.
- JDK 21 (jetbrains toolchain). Gradle 8.11.1 (wrapper jar present & working).
- `internal suspend` functions are tested directly via `runBlocking`.

## Git hooks (ktlint + tests)
Version-controlled in `.githooks/`. One-time install per clone (hooksPath isn't committed):
`git config core.hooksPath .githooks`.
- **pre-commit** → `./gradlew ktlintFormat` (auto-fixes all Kotlin + `*.gradle.kts`). Non-blocking; formatted files are NOT auto-staged — review & `git add`.
- **pre-push** → runs tests (`jvmTest` + `testDebugUnitTest`; iOS sim added only on macOS). Blocks push on failure.
- ktlint config in `.editorconfig` (`max-line-length` disabled — long expected-output literals in tests).
- Backtick test names must NOT contain `(` `)` `,` — iOS Native compiler rejects them (JVM tolerates).

## KMP gate
Both targets build & test green. Run iOS too for cross-platform verification:
`./gradlew :ktor2curl:jvmTest :ktor2curl:testDebugUnitTest :ktor2curl:iosSimulatorArm64Test`.
(Kotlin is `2.3.21` — required so the iOS Native compiler can consume ktor 3.5.0's Darwin klib,
ABI 2.3.0. Don't drop below 2.3.x or the iOS target stops compiling.)

## Code-intelligence MCP servers (prefer for navigation — saves tokens)
- **jcodemunch** + **lean-ctx** are connected. Use them over raw `Read`/`grep`/`ls` for finding and understanding code. Only `Read` the exact file you're about to edit.
- Quick start: `resolve_repo {path:"."}` → `plan_turn {repo, query}` → `search_symbols` / `get_symbol_source`, or `ctx_read` (map/signatures mode first).
- **Ignore jcodemunch "dead code" %** (it reports ~78%). False positive: this is a library — public API is consumed externally and `internal` fns are exercised only by `commonTest`. The analyzer sees neither. Do not delete code on that signal.

## Agents (sequential — NO parallel fan-out; fan-out burns too many tokens on a 10-file repo)
Defined in `.claude/agents/`. Orchestrator = the main thread. Delegate one at a time, only when it earns its cost:

| Agent | Model | When to delegate |
|---|---|---|
| `tester` | sonnet | Need test coverage / repro / edge-case hunting. Library-consumer + adversarial mindset. Uses `tdd`, `testing-setup` skills. |
| `code-reviewer` | sonnet | After a change is written, before build. Adversarial flaw hunt (curl escaping, body consumption, coroutine leaks, API ergonomics). Read-only. |
| `builder` | haiku | Merge gate. Runs `:ktor2curl:jvmTest`, returns pass/fail receipt. Mechanical, cheap. |

Typical loop: **write change → code-reviewer → apply fixes → tester (if coverage needed) → builder gate → repeat until green.** Run agents in sequence, not concurrently. Skip an agent when the change is trivial.

All three agents are wired with jcodemunch + lean-ctx tools for cheap navigation and told the build workaround above.
