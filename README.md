# Ktor2Curl: A Ktor Plugin for Generating cURL Commands

Transform Ktor HTTP requests into runnable `curl` commands for debugging and logging. Pure Kotlin library supporting KMP (Android, iOS, JVM) and standard Android projects.

Inspired by [Ok2Curl](https://github.com/mrmike/Ok2Curl) for OkHttp.

## Features

- **Multi-platform**: Works on Android, iOS, JVM, and KMP projects
- **Zero configuration**: Install, add to client config, done
- **Sensitive data masking**: Redact auth tokens, API keys, and custom headers
- **Flexible logging**: Custom logger interface - send to Logcat, console, HTTP, files
- **Multipart handling**: Renders form-data and file uploads correctly
- **Header control**: Include, exclude, or mask specific headers per request

## Requirements

- **Kotlin**: 2.0+
- **Ktor**: 3.0+
- **Gradle**: 7.0+

## Installation

### Kotlin Multiplatform Projects (Recommended)

Add to your `build.gradle.kts`:

```kotlin
val commonMain by getting {
    dependencies {
        implementation("io.github.kabirnayeem99:ktor2curl:2.0.0")
    }
}
```

Supports all KMP targets: Android, iOS (arm64/x64/simulator), JVM, JS, WASM.

### Android Projects Only

#### Kotlin DSL (`build.gradle.kts`)

```kotlin
dependencies {
    implementation("io.github.kabirnayeem99:ktor2curl:2.0.0")
}
```

#### Groovy DSL (`build.gradle`)

```groovy
dependencies {
    implementation 'io.github.kabirnayeem99:ktor2curl:2.0.0'
}
```

### Verify Installation

After adding the dependency, run:

```bash
./gradlew build
```

If you hit Kotlin version mismatch, ensure your project uses Kotlin 2.0+ (see [Troubleshooting](#troubleshooting)).

## Quick Start

### Basic Setup (2 minutes)

```kotlin
val client = HttpClient(CIO) {
    install(KtorToCurl) {
        converter = object : CurlLogger {
            override fun log(curl: String) {
                println(curl)  // or use Android Log.d(), your logging framework, etc.
            }
        }
    }
}

// Every request via this client now logs a curl command
client.post("https://api.example.com/users") {
    headers {
        append(HttpHeaders.Authorization, "Bearer YOUR_TOKEN")
        append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    }
    setBody("""{"name":"Alice"}""")
}
```

**Output:**
```bash
curl -X POST \
  https://api.example.com/users \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{"name":"Alice"}'
```

### With Sensitive Data Masking

Hide API keys, tokens, and custom auth headers from logs:

```kotlin
val client = HttpClient(CIO) {
    install(KtorToCurl) {
        converter = object : CurlLogger {
            override fun log(curl: String) {
                Log.d("API", curl)
            }
        }
        maskedHeaders = setOf(
            HttpHeaders.Authorization,
            "X-API-Key",
            "X-Session-Token"
        )
    }
}
```

**Output:**
```bash
curl -X POST \
  https://api.example.com/users \
  -H "Authorization: [omitted]" \
  -H "Content-Type: application/json" \
  --data '{"name":"Alice"}'
```

### Hide Unnecessary Headers

Remove User-Agent, Content-Length, or other headers from logs:

```kotlin
val client = HttpClient(CIO) {
    install(KtorToCurl) {
        converter = object : CurlLogger {
            override fun log(curl: String) {
                Log.d("API", curl)
            }
        }
        excludedHeaders = setOf(
            HttpHeaders.UserAgent,
            HttpHeaders.ContentLength
        )
        maskedHeaders = setOf(HttpHeaders.Authorization)
    }
}
```

### Real-World Example: Protected API

```kotlin
client.post("https://data.techforpalestine.org/api/v2/killed-in-gaza.min.json") {
    headers {
        append(HttpHeaders.Authorization, "Basic SXNyYWVsIGtpbGxzIGNoaWxkcmVuLg")
        append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    }
    setBody("""{"en_name":"Mazen Ahmed Mohammed Al-Kahlout","name":"مازن أحمد محمد الكحلوت","age":52,"dob":"1972-02-05","sex":"m","id":"985194547","source":"u"}""")
}
```

**Output:**
```bash
curl -X POST \
  https://data.techforpalestine.org/api/v2/killed-in-gaza.min.json \
  -H "Authorization: [omitted]" \
  -H "Content-Type: application/json" \
  --data '{"en_name":"Mazen Ahmed Mohammed Al-Kahlout","name":"مازن أحمد محمد الكحلوت","age":52,"dob":"1972-02-05","sex":"m","id":"985194547","source":"u"}'
```

## Configuration Reference

| Property | Type | Default | Purpose |
|----------|------|---------|---------|
| `converter` | `CurlLogger` | Required | Implementation to handle curl output (log it, send it, store it) |
| `maskedHeaders` | `Set<String>` | `emptySet()` | Headers to replace with `[omitted]` (auth, tokens, keys) |
| `excludedHeaders` | `Set<String>` | `emptySet()` | Headers to skip entirely in output |

## Advanced Usage

### Custom Logger Implementation

Send curl commands to a remote service, database, or aggregation tool:

```kotlin
class RemoteCurlLogger : CurlLogger {
    override fun log(curl: String) {
        // Send to your backend, analytics, or debug dashboard
        analyticsService.trackRequest(curl)
    }
}

val client = HttpClient(CIO) {
    install(KtorToCurl) {
        converter = RemoteCurlLogger()
    }
}
```

### Multipart/Form-Data Support

File uploads and form fields render correctly:

```kotlin
client.post("https://api.example.com/upload") {
    setBody(MultiPartFormDataContent(
        formData {
            append("file", InputProvider { "file content".byteInputStream() }, 
                    Headers.build { append(HttpHeaders.ContentDisposition, "filename=data.txt") })
            append("field", "value")
        }
    ))
}
```

**Output:**
```bash
curl -X POST \
  https://api.example.com/upload \
  -F "file=@data.txt" \
  -F "field=value"
```

## Troubleshooting

### Issue: "Dependency not found" or "Module resolution error"

**Cause:** Kotlin version mismatch or incorrect repository.

**Fix:**
- Ensure Kotlin ≥ 2.0: `kotlin = "2.3.0"` or later in `plugins` block
- Check repository is available:
  ```kotlin
  repositories {
      mavenCentral()
      google()
  }
  ```
- Verify dependency version exists on [Maven Central](https://mvnrepository.com/artifact/io.github.kabirnayeem99/ktor2curl)

### Issue: Curl command not logged

**Cause:** Logger implementation not called.

**Fix:**
- Verify `converter` is set in plugin config
- Check your logger is enabled (not filtered by Log level)
- Confirm plugin is installed **before** making requests:
  ```kotlin
  val client = HttpClient {
      install(KtorToCurl) { ... }  // Must be before other requests
  }
  ```
- Ensure request actually succeeds (failed requests may not log)

### Issue: Sensitive data appears in logs

**Cause:** Header not in `maskedHeaders` set.

**Fix:**
- Use exact header name (case-sensitive if using string literals):
  ```kotlin
  maskedHeaders = setOf(
      "Authorization",      // ✓ Correct
      "X-Custom-Token",
      HttpHeaders.Authorization  // ✓ Also correct (from Ktor)
  )
  ```

### Issue: Headers still appear despite `excludedHeaders`

**Cause:** Header name mismatch (case/spelling).

**Fix:**
- Enable debug logging in your plugin and compare exact header names in requests
- Use `HttpHeaders.HEADER_NAME` constants from Ktor when possible

### Issue: Gradle build fails on iOS (KMP)

**Cause:** Kotlin < 2.3.0 incompatible with Ktor 3.5 on Native.

**Fix:**
- Upgrade to Kotlin ≥ 2.3.0:
  ```kotlin
  plugins {
      kotlin("multiplatform") version "2.3.0"
  }
  ```

## Contributing

Contributions welcome. Before submitting:

1. **Fork the repo** (click "Fork" on GitHub or run: `git clone --recursive https://github.com/YOUR_USERNAME/ktor2curl.git`)
2. **Create a branch** for your feature:
   ```bash
   git checkout -b feature/add-cookie-masking
   ```
3. **Write tests** in `ktor2curl/src/commonTest/` (see existing test structure)
4. **Run tests locally:**
   ```bash
   ./gradlew :ktor2curl:jvmTest --console=plain
   ```
5. **Format code** (hooks auto-run pre-commit):
   ```bash
   ./gradlew ktlintFormat
   ```
6. **Push and create a Pull Request** with a clear description of your change

### How to Fork

1. Visit the [GitHub repo](https://github.com/kabirnayeem99/Ktor2Curl)
2. Click **Fork** (top-right corner)
3. Clone your fork:
   ```bash
   git clone https://github.com/YOUR_USERNAME/Ktor2Curl.git
   cd Ktor2Curl
   ```
4. Add upstream to stay in sync:
   ```bash
   git remote add upstream https://github.com/kabirnayeem99/Ktor2Curl.git
   git fetch upstream
   ```
5. Create your feature branch and start coding:
   ```bash
   git checkout -b feature/your-idea
   ```

### Testing Your Changes

Run the full test suite to verify no regressions:

```bash
./gradlew :ktor2curl:jvmTest :ktor2curl:testDebugUnitTest :ktor2curl:iosSimulatorArm64Test
```

Or just JVM for quick feedback:

```bash
./gradlew :ktor2curl:jvmTest --console=plain
```

## Support

- [Ktor Client Documentation](https://ktor.io/docs/client-index.html)
- [Issues](https://github.com/kabirnayeem99/Ktor2Curl/issues)
- [Discussions](https://github.com/kabirnayeem99/Ktor2Curl/discussions)

## License

MIT - See LICENSE file for details.
