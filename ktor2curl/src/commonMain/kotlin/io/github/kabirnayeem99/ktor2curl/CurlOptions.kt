package io.github.kabirnayeem99.ktor2curl

/** Target shell the emitted curl command is meant to be pasted into — drives argument quoting. */
enum class CurlShell {
    /** POSIX sh/bash/zsh. Single-quote wrapping, `'` escaped as `'\''`. */
    Posix,

    /** Windows Command Prompt (cmd.exe). Double-quote wrapping, inner `"` escaped as `\"`. */
    WindowsCmd,

    /** Windows PowerShell. Single-quote wrapping, inner `'` doubled to `''`. */
    PowerShell,
}

/**
 * Transforms a single header value just before it is rendered. Receives the header [name] and one
 * [value] (headers may be multi-valued, so this is invoked per value). Return a replacement string
 * to rewrite the value (e.g. decode a Basic-Auth credential, truncate a bearer token), or `null` to
 * omit that value entirely. If every value of a header is omitted, the header disappears from the
 * output. Runs *after* [CurlOptions.excludedHeaders] and [CurlOptions.maskedHeaders], so a masked
 * header's secret is never exposed to the transformer.
 */
typealias HeaderTransformer = (name: String, value: String) -> String?

/**
 * Immutable rendering options for [toCurl] / the [KtorToCurl] plugin. All masking is applied
 * before quoting, and all header/query/JSON-key matching is case-insensitive.
 */
data class CurlOptions(
    /** Header names dropped from output entirely. */
    val excludedHeaders: Set<String> = emptySet(),
    /** Header names whose values are replaced with [maskPlaceholder]. */
    val maskedHeaders: Set<String> = emptySet(),
    /** Query-parameter names in the URL whose values are replaced with [maskPlaceholder]. */
    val maskedQueryParams: Set<String> = emptySet(),
    /** JSON object keys whose values are deep-redacted in text request bodies. */
    val maskedJsonKeys: Set<String> = emptySet(),
    /** When true, `user:password@` credentials embedded in the URL are masked. */
    val maskUrlUserInfo: Boolean = true,
    /** Replacement string used for every masked value. */
    val maskPlaceholder: String = "[masked]",
    /** Shell dialect the command is quoted for. */
    val shell: CurlShell = CurlShell.Posix,
    /** When set, text `-d` bodies longer than this are truncated with a trailing indicator. */
    val maxBodyLength: Int? = null,
    /** When true, arguments are split across lines with `\` continuations for log readability. */
    val multiLine: Boolean = false,
    /** When true, injects `--compressed`. */
    val compressed: Boolean = false,
    /** When true, injects `-L` (follow redirects). */
    val followRedirects: Boolean = false,
    /** When true, injects `--insecure` (skip TLS certificate verification). */
    val insecure: Boolean = false,
    /** When set (>= 0), injects `--connect-timeout <seconds>` — cap on the connection phase. */
    val connectTimeoutSeconds: Int? = null,
    /** When set (>= 0), injects `--max-time <seconds>` — cap on the whole transfer. */
    val maxTimeSeconds: Int? = null,
    /** When set (>= 0), injects `--retry <count>` — number of retries on transient failures. */
    val retry: Int? = null,
    /** When false, all header arguments (and the inferred `Content-Type`) are omitted. */
    val showHeaders: Boolean = true,
    /** When false, the `-d` / `-F` body argument is omitted. */
    val showBody: Boolean = true,
    /**
     * Separator placed between curl components. When null, the separator is derived from
     * [multiLine] (`" \\\n  "` if true, a single space otherwise). When set, this string is
     * used verbatim and overrides [multiLine].
     */
    val componentDelimiter: String? = null,
    /**
     * Per-value header hook applied last in the header pipeline. Return a rewritten value, or `null`
     * to drop that value. Defaults to identity (leave every value unchanged). See [HeaderTransformer].
     */
    val headerTransformer: HeaderTransformer = { _, value -> value },
)
