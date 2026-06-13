package io.github.kabirnayeem99.ktor2curl

import io.ktor.client.request.HttpRequestBuilder

class KtorToCurlConfig {
    /** Sink the rendered curl command is shipped to. No-op by default. */
    var logger: CurlLogger =
        object : CurlLogger {
            override suspend fun log(curl: String) = Unit
        }

    /** Predicate deciding whether a request is logged. Return false to skip it entirely. */
    var shouldLog: (HttpRequestBuilder) -> Boolean = { true }

    /** Header names dropped from output entirely. Matched case-insensitively. */
    var excludedHeaders: Set<String> = emptySet()

    /** Header names whose values are replaced with [maskPlaceholder]. Matched case-insensitively. */
    var maskedHeaders: Set<String> = emptySet()

    /** URL query-parameter names whose values are masked. Matched case-insensitively. */
    var maskedQueryParams: Set<String> = emptySet()

    /** JSON object keys whose values are deep-redacted in text bodies. Matched case-insensitively. */
    var maskedJsonKeys: Set<String> = emptySet()

    /** When true, `user:password@` credentials embedded in the URL are masked. */
    var maskUrlUserInfo: Boolean = true

    /** Replacement string used for every masked value. */
    var maskPlaceholder: String = "[masked]"

    /** Shell dialect the emitted command is quoted for. */
    var shell: CurlShell = CurlShell.Posix

    /** When set, text `-d` bodies longer than this are truncated with a trailing indicator. */
    var maxBodyLength: Int? = null

    /** When true, arguments are split across lines with `\` continuations for readability. */
    var multiLine: Boolean = false

    /** When true, injects `--compressed`. */
    var compressed: Boolean = false

    /** When true, injects `-L` (follow redirects). */
    var followRedirects: Boolean = false

    /** When true, injects `--insecure` (skip TLS certificate verification). */
    var insecure: Boolean = false

    /** When set (>= 0), injects `--connect-timeout <seconds>` — cap on the connection phase. */
    var connectTimeoutSeconds: Int? = null

    /** When set (>= 0), injects `--max-time <seconds>` — cap on the whole transfer. */
    var maxTimeSeconds: Int? = null

    /** When set (>= 0), injects `--retry <count>` — number of retries on transient failures. */
    var retry: Int? = null

    /** When false, all header arguments (and the inferred `Content-Type`) are omitted. */
    var showHeaders: Boolean = true

    /** When false, the `-d` / `-F` body argument is omitted. */
    var showBody: Boolean = true

    /**
     * Separator placed between curl components. When null, the separator is derived from
     * [multiLine]. When set, this string is used verbatim and overrides [multiLine].
     */
    var componentDelimiter: String? = null

    /**
     * Per-value header hook applied last in the header pipeline (after exclude/mask). Return a
     * rewritten value, or `null` to drop that value. Defaults to identity. See [HeaderTransformer].
     */
    var headerTransformer: HeaderTransformer = { _, value -> value }

    /** Adds [names] to [excludedHeaders]. */
    fun excludeHeader(vararg names: String) {
        excludedHeaders = excludedHeaders + names
    }

    /** Adds [names] to [maskedHeaders]. */
    fun maskHeader(vararg names: String) {
        maskedHeaders = maskedHeaders + names
    }

    /** Adds [names] to [maskedQueryParams]. */
    fun maskQueryParam(vararg names: String) {
        maskedQueryParams = maskedQueryParams + names
    }

    /** Adds [names] to [maskedJsonKeys]. */
    fun maskJsonKey(vararg names: String) {
        maskedJsonKeys = maskedJsonKeys + names
    }

    internal fun toOptions(): CurlOptions =
        CurlOptions(
            excludedHeaders = excludedHeaders,
            maskedHeaders = maskedHeaders,
            maskedQueryParams = maskedQueryParams,
            maskedJsonKeys = maskedJsonKeys,
            maskUrlUserInfo = maskUrlUserInfo,
            maskPlaceholder = maskPlaceholder,
            shell = shell,
            maxBodyLength = maxBodyLength,
            multiLine = multiLine,
            compressed = compressed,
            followRedirects = followRedirects,
            insecure = insecure,
            connectTimeoutSeconds = connectTimeoutSeconds,
            maxTimeSeconds = maxTimeSeconds,
            retry = retry,
            showHeaders = showHeaders,
            showBody = showBody,
            componentDelimiter = componentDelimiter,
            headerTransformer = headerTransformer,
        )
}
