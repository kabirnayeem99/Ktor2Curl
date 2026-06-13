# Changelog

All notable changes to Ktor2Curl are documented here. See [Semantic Versioning](https://semver.org/).

## [2.0.0] - 2026-06-13

### Breaking Changes

#### API Renames
- **`converter` → `logger`**: Configuration property renamed for clarity. Update all plugin installations.
  ```kotlin
  // Before
  install(KtorToCurl) {
      converter = object : CurlLogger { ... }
  }
  
  // After
  install(KtorToCurl) {
      logger = object : CurlLogger { ... }
  }
  ```

#### Async Logger Interface
- **`CurlLogger.log()` is now `suspend fun`**: Enables async dispatch to logging frameworks, remote services, or databases.
  ```kotlin
  // Before
  override fun log(curl: String) = Unit
  
  // After
  override suspend fun log(curl: String) {
      Log.d("API", curl)  // or dispatch to coroutine scope
  }
  ```
  Migration: Wrap in a coroutine scope or launch async task in your logger implementation.

#### Minimum Version Requirements
- **Kotlin 2.3.21+** (was 2.0+): Required for iOS Native compiler compatibility with Ktor 3.5.0.
- **Ktor 3.5.0+** (was 3.0+): Update `ktor-client-core` dependency.
- **Gradle 7.0+** (unchanged): No new requirement.

### Features

#### Enhanced Configuration
New properties for fine-grained curl output control:
- **`shouldLog: (HttpRequestBuilder) -> Boolean`** - Skip logging specific requests (e.g., health checks).
- **`maskedQueryParams: Set<String>`** - Redact URL query param values (e.g., `api_key`).
- **`maskedJsonKeys: Set<String>`** - Deep-redact JSON body keys (e.g., `password`, `token`).
- **`maskUrlUserInfo: Boolean`** - Hide `user:password@` in URL (default: true).
- **`maskPlaceholder: String`** - Custom redaction placeholder (default: `[masked]`).
- **`shell: CurlShell`** - Target shell dialect (Posix, Bash, Zsh, PowerShell, Cmd).
- **`maxBodyLength: Int?`** - Truncate text bodies over N bytes with indicator.
- **`multiLine: Boolean`** - Split arguments across lines with `\` for readability.
- **`compressed: Boolean`** - Inject `--compressed` flag.
- **`followRedirects: Boolean`** - Inject `-L` flag.
- **`insecure: Boolean`** - Inject `--insecure` flag (skip TLS verification).
- **`connectTimeoutSeconds: Int?`** - Inject `--connect-timeout`.
- **`maxTimeSeconds: Int?`** - Inject `--max-time`.
- **`retry: Int?`** - Inject `--retry` for transient failure handling.
- **`showHeaders: Boolean`** - Include/exclude all headers (default: true).
- **`showBody: Boolean`** - Include/exclude request body (default: true).
- **`componentDelimiter: String?`** - Custom separator between curl components.
- **`headerTransformer: HeaderTransformer`** - Hook to rewrite headers after mask/exclude.

#### Helper Methods
Convenience builders for configuration:
- **`excludeHeader(vararg names: String)`** - Add to exclusion list.
- **`maskHeader(vararg names: String)`** - Add to masking list.
- **`maskQueryParam(vararg names: String)`** - Add to query param masking.
- **`maskJsonKey(vararg names: String)`** - Add to JSON key masking.

#### Comprehensive Test Coverage
- **654-line core test suite** - `generateCurl`, header handling, body encoding, escaping.
- **1,375-line options tests** - Flag combinations, edge cases, multipart boundaries.
- **85-line HTTP method tests** - GET, POST, PUT, PATCH, DELETE, HEAD.
- **110-line multipart tests** - Form data, file uploads, nested boundaries.
- **91-line integration tests** - End-to-end plugin verification.
- **152-line security tests** - Token masking, header exclusion, sensitive data handling.
- **62-line resilience tests** - Malformed input, edge case encoding.

#### Multipart/Form-Data Support
Renders file uploads and form fields correctly:
```kotlin
client.post("https://api.example.com/upload") {
    setBody(MultiPartFormDataContent(
        formData {
            append("file", InputProvider { ... }, Headers.build { ... })
            append("field", "value")
        }
    ))
}

// Output:
// curl -X POST ... -F "file=@filename" -F "field=value"
```

#### KMP Support
Tested on all major platforms:
- **Android** (all API levels via commonTest)
- **iOS** (arm64, x64, simulator)
- **JVM** (Java 21+)
- **JS** (supported, untested)
- **WASM** (supported, untested)

#### Developer Infrastructure

##### Agent Framework
- **Builder agent** - Runs jvmTest + testDebugUnitTest gates, blocks on failure.
- **Code-reviewer agent** - Adversarial review (curl escaping, coroutine leaks, ergonomics).
- **Tester agent** - Library-consumer mindset, edge cases, adversarial scenarios.

##### CI/CD Pipelines
- **GitHub Actions** (`ci.yml`) - Multi-target test matrix (jvmTest, testDebugUnitTest, iOS sim).
- **Maven Central publish** (`publish.yml`) - Release automation.
- **Local simulation** (`scripts/ci-local.sh`) - Replicate CI flow locally.

##### Git Hooks
- **Pre-commit** - Runs `ktlintFormat` (non-blocking auto-fix).
- **Pre-push** - Runs full test suite, blocks push on failure.

##### Documentation
- **CLAUDE.md** - Project guide (layout, build, git hooks, agent routing).
- **AGENTS.md** - Agent framework overview.
- **README.md** - Complete rewrite with setup, troubleshooting, fork guide.

### Fixes

#### GitHub Actions Compatibility
- **Removed JetBrains vendor pin** from `gradle-daemon-jvm.properties`. CI runners use temurin/eclipse JDK, not JetBrains Toolchain. Now accepts any vendor for Java 21+.
- **Added gradle wrapper jar** to git tracking for offline builds.

#### Documentation
- Fixed typo: "Kotllin" → "Kotlin".
- Removed em-dashes and emojis for plain-text compatibility.
- Restored original API example (data.techforpalestine.org) with masked output.

### Dependencies

#### Upgraded
- **Kotlin** 2.0+ → 2.3.21 (iOS Native ABI 2.3.0)
- **Ktor** 3.0+ → 3.5.0
- **Gradle** 8.11.1 (wrapper updated)
- **Android Gradle Plugin** 8.7.0
- **KotlinX IO** (internal, for byte stream handling)

#### Unchanged
- **Gradle** minimum: 7.0

### Deprecated

None. This is a major version bump; breaking changes are direct, not deprecated.

### Removed

None. All v1.x functionality preserved (under new API).

### Known Issues

- **JS/WASM targets**: Untested on actual JS/WASM environments. Core logic is platform-agnostic; file an issue if problems arise.
- **Perfetto stdlib**: Large reference (~6,670 lines) included; can be pruned if not needed for your project.

### Migration Guide

#### From v1.x to v2.0.0

**Step 1: Update Dependencies**
```kotlin
plugins {
    kotlin("multiplatform") version "2.3.21"  // was any 2.0+
}

dependencies {
    commonMain {
        implementation("io.ktor:ktor-client-core:3.5.0")  // was 3.0+
        implementation("io.github.kabirnayeem99:ktor2curl:2.0.0")  // was 1.x
    }
}
```

**Step 2: Rename `converter` → `logger`**
```kotlin
val client = HttpClient {
    install(KtorToCurl) {
        logger = object : CurlLogger {  // was: converter
            override suspend fun log(curl: String) {  // now suspend
                println(curl)
            }
        }
    }
}
```

**Step 3: Handle Async Logging** (if needed)
If your logger needs to dispatch async:
```kotlin
class AsyncCurlLogger : CurlLogger {
    override suspend fun log(curl: String) {
        withContext(Dispatchers.IO) {
            analyticsService.track(curl)
        }
    }
}
```

**Step 4: Use New Features** (optional)
Leverage v2.0 capabilities:
```kotlin
install(KtorToCurl) {
    logger = MyLogger()
    maskedQueryParams = setOf("api_key", "token")
    maskedJsonKeys = setOf("password", "secret")
    shouldLog = { request -> !request.url.pathSegments.contains("health") }
    multiLine = true  // pretty-print curl
}
```

### Contributors

- Naimul Kabir (kabirnayeem.99@gmail.com)

### Links

- [GitHub Repository](https://github.com/kabirnayeem99/Ktor2Curl)
- [Maven Central](https://mvnrepository.com/artifact/io.github.kabirnayeem99/ktor2curl)
- [Issue Tracker](https://github.com/kabirnayeem99/Ktor2Curl/issues)
- [Discussions](https://github.com/kabirnayeem99/Ktor2Curl/discussions)

---

## [1.1.0] - Previous Release

(Archived; see git tag `v1.1.0` for details.)

---

## How to Report Issues

Found a bug or have a feature request? Please open an [issue](https://github.com/kabirnayeem99/Ktor2Curl/issues) with:
- Kotlin and Ktor versions
- Minimal reproducible example
- Expected vs. actual behavior
