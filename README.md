# Ktor2Curl

Turns Ktor HTTP requests into runnable `curl` commands for debugging and logging. Pure Kotlin,
works on Android, iOS, and JVM (KMP).

Inspired by [Ok2Curl](https://github.com/mrmike/Ok2Curl) for OkHttp.

## Features

- Zero-config install: add the plugin, done
- Mask sensitive headers (tokens, API keys) before they hit your logs
- Exclude noisy headers entirely
- Correct multipart/form-data and file-upload rendering
- Bring your own logger: console, Logcat, remote sink, anything

## Requirements

Kotlin 2.3+, Ktor 3.5+, Gradle 7.0+.

## Install

```kotlin
dependencies {
    implementation("io.github.kabirnayeem99:ktor2curl:3.0.1")
}
```

Same snippet for KMP (`commonMain`) and Android-only projects.

## Quick Start

```kotlin
val client = HttpClient(CIO) {
    install(KtorToCurl) {
        logger = CurlLogger.console() // or Log.d(), a file sink, anything
    }
}

client.post("https://api.example.com/users") {
    headers.append(HttpHeaders.Authorization, "Bearer YOUR_TOKEN")
    contentType(ContentType.Application.Json)
    setBody("""{"name":"Alice"}""")
}
```

```bash
curl -H 'Authorization: Bearer YOUR_TOKEN' -H 'Content-Type: application/json' 'https://api.example.com/users' --data-binary '{"name":"Alice"}'
```

### Masking sensitive headers

```kotlin
install(KtorToCurl) {
    logger = CurlLogger.console()
    maskedHeaders = setOf(HttpHeaders.Authorization, "X-API-Key")
    excludedHeaders = setOf(HttpHeaders.UserAgent) // dropped entirely, not just masked
}
```

```bash
curl -H 'Authorization: [masked]' -H 'Content-Type: application/json' 'https://api.example.com/users' --data-binary '{"name":"Alice"}'
```

## Configuration

| Property          | Type          | Default      | Purpose                                |
|--------------------|---------------|--------------|-----------------------------------------|
| `logger`           | `CurlLogger`  | No-op        | Where rendered curl commands go         |
| `maskedHeaders`     | `Set<String>` | `emptySet()` | Replace values with `[masked]`          |
| `excludedHeaders`   | `Set<String>` | `emptySet()` | Drop headers entirely                   |
| `maskedQueryParams` | `Set<String>` | `emptySet()` | Mask query-param values                 |
| `maskedJsonKeys`    | `Set<String>` | `emptySet()` | Deep-redact JSON body fields            |
| `shell`             | `CurlShell`   | `Posix`      | Quoting dialect (Posix/PowerShell/Cmd)  |

Full reference with a runnable example per option (timeouts, retries, truncation, shell dialect,
custom logger, multipart, and more): [`docs/API.md`](docs/API.md).

## Troubleshooting

- **Dependency won't resolve**: make sure `mavenCentral()` is in `repositories {}` and Kotlin is
  2.3+.
- **Nothing gets logged**: confirm `logger` is set, the plugin is installed before any requests
  are made, and the request actually completes (failed requests may not reach the send pipeline).
- **A header isn't masked**: header names match case-insensitively, but check for typos; prefer
  `HttpHeaders.*` constants over string literals.
- **iOS build fails**: Kotlin must be 2.3.0+ (older versions can't consume Ktor 3.5's Darwin klib
  ABI).

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md).

## Support

[Ktor docs](https://ktor.io/docs/client-index.html) |
[Issues](https://github.com/kabirnayeem99/Ktor2Curl/issues) |
[Discussions](https://github.com/kabirnayeem99/Ktor2Curl/discussions)

## License

MIT. See [LICENSE](LICENSE).
