# ktor2curl API Reference

Full reference for every `KtorToCurlConfig` / `CurlOptions` option. Each section is a standalone,
runnable example — see the [README](../README.md) for a quick start.

## Install the plugin

```kotlin
import io.github.kabirnayeem99.ktor2curl.KtorToCurl
import io.github.kabirnayeem99.ktor2curl.CurlLogger
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

val client = HttpClient(CIO) {
    install(KtorToCurl) {
        logger = CurlLogger.console()
    }
}
```

Every request sent through `client` now logs a curl command.

## Custom logger

Implement `CurlLogger` to send curl output anywhere: Logcat, a file, a remote debug dashboard.

```kotlin
import io.github.kabirnayeem99.ktor2curl.CurlLogger
import io.github.kabirnayeem99.ktor2curl.KtorToCurl

class RemoteCurlLogger : CurlLogger {
    override suspend fun log(curl: String) {
        analyticsService.trackRequest(curl)
    }
}

val client = HttpClient(CIO) {
    install(KtorToCurl) {
        logger = RemoteCurlLogger()
    }
}
```

## Mask sensitive headers

Replace a header's value with `[masked]` instead of dropping it, so the header is still visible
but the secret is not.

```kotlin
install(KtorToCurl) {
    logger = CurlLogger.console()
    maskedHeaders = setOf(HttpHeaders.Authorization, "X-API-Key")
}
```

```bash
curl -H 'Authorization: [masked]' 'https://api.example.com/users'
```

## Exclude headers entirely

Drop noisy headers (User-Agent, Content-Length) from the rendered output.

```kotlin
install(KtorToCurl) {
    logger = CurlLogger.console()
    excludedHeaders = setOf(HttpHeaders.UserAgent, HttpHeaders.ContentLength)
}
```

## Mask query parameters

```kotlin
install(KtorToCurl) {
    logger = CurlLogger.console()
    maskedQueryParams = setOf("token", "api_key")
}
```

A request to `https://api.example.com/search?token=secret&q=kotlin` renders with
`token=[masked]&q=kotlin`.

## Mask JSON body fields

Deep-redacts matching keys inside a JSON request body, at any nesting depth.

```kotlin
install(KtorToCurl) {
    logger = CurlLogger.console()
    maskedJsonKeys = setOf("password", "ssn")
}
```

A body of `{"user":"alice","password":"hunter2"}` renders with `"password":"[masked]"`.

## Custom placeholder

```kotlin
install(KtorToCurl) {
    logger = CurlLogger.console()
    maskedHeaders = setOf(HttpHeaders.Authorization)
    maskPlaceholder = "<redacted>"
}
```

## Shell dialect

Pick the quoting style for the shell you paste the output into.

```kotlin
import io.github.kabirnayeem99.ktor2curl.CurlShell

install(KtorToCurl) {
    logger = CurlLogger.console()
    shell = CurlShell.PowerShell // or CurlShell.Posix (default), CurlShell.WindowsCmd
}
```

## Truncate long bodies

```kotlin
install(KtorToCurl) {
    logger = CurlLogger.console()
    maxBodyLength = 2000 // characters; longer bodies get a trailing "...[truncated]" indicator
}
```

## Cap multipart rendering size

Multipart bodies larger than this are not read into memory; they render as
`[multipart body omitted]` instead.

```kotlin
install(KtorToCurl) {
    logger = CurlLogger.console()
    maxMultipartBytes = 512 * 1024 // 512 KiB, default is 1 MiB
}
```

## Add curl flags

```kotlin
install(KtorToCurl) {
    logger = CurlLogger.console()
    insecure = true          // --insecure
    compressed = true        // --compressed
    followRedirects = true   // -L
    connectTimeoutSeconds = 5   // --connect-timeout 5
    maxTimeSeconds = 30         // --max-time 30
    retry = 3                   // --retry 3
}
```

## Multi-line output

Split arguments across lines with `\` continuations, for readability in logs.

```kotlin
install(KtorToCurl) {
    logger = CurlLogger.console()
    multiLine = true
}
```

## Hide headers or body entirely

```kotlin
install(KtorToCurl) {
    logger = CurlLogger.console()
    showHeaders = false // omit all -H arguments
    showBody = false    // omit --data-binary / -F
}
```

## Rewrite header values with a transformer

Runs after masking and exclusion, so a masked header's real value is never exposed to the
transformer. Return `null` to drop a value.

```kotlin
install(KtorToCurl) {
    logger = CurlLogger.console()
    headerTransformer = { name, value ->
        if (name == "Authorization") value.removePrefix("Bearer ") else value
    }
}
```

## Skip logging some requests

```kotlin
install(KtorToCurl) {
    logger = CurlLogger.console()
    shouldLog = { request -> !request.url.encodedPath.startsWith("/health") }
}
```

## Render without installing the plugin

`toCurl()` is a suspend extension on `HttpRequestBuilder` for one-off renders, tests, or custom
pipelines.

```kotlin
import io.github.kabirnayeem99.ktor2curl.toCurl
import io.github.kabirnayeem99.ktor2curl.CurlOptions
import io.ktor.client.request.HttpRequestBuilder

val request = HttpRequestBuilder().apply {
    url("https://api.example.com/users")
    method = HttpMethod.Post
    setBody("""{"name":"Alice"}""")
}

println(request.toCurl())
println(request.toCurl(CurlOptions(maskedHeaders = setOf("Authorization"))))
```

## Multipart / file uploads

Renders correctly out of the box, no extra config needed.

```kotlin
client.post("https://api.example.com/upload") {
    setBody(MultiPartFormDataContent(formData {
        append("file", InputProvider { "file content".byteInputStream() },
            Headers.build { append(HttpHeaders.ContentDisposition, "filename=data.txt") })
        append("field", "value")
    }))
}
```

```bash
curl 'https://api.example.com/upload' -F 'file=@data.txt' -F 'field=value'
```
