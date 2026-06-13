package io.github.kabirnayeem99.ktor2curl

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.utils.EmptyContent
import io.ktor.content.ByteArrayContent
import io.ktor.content.TextContent
import io.ktor.http.HeadersBuilder
import io.ktor.http.URLBuilder
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.writer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.io.readByteArray

internal suspend fun generateCurl(
    request: HttpRequestBuilder,
    options: CurlOptions,
): String {
    val method = request.method.value.uppercase()
    var hasBody = false

    // Header + URL + body arguments, in canonical order, each already quoted for the target shell.
    val tail = mutableListOf<String>()

    if (options.showHeaders) {
        request.headers.onHeaders(
            excludedHeaders = options.excludedHeaders,
            maskedHeaders = options.maskedHeaders,
            maskPlaceholder = options.maskPlaceholder,
            headerTransformer = options.headerTransformer,
            onNoContentTypeHeader = {
                // The inferred Content-Type is not a real wire header, but it is routed through the
                // transformer too so a hook that drops/rewrites "Content-Type" stays consistent.
                // Prefer the explicit header; otherwise fall back to a serialized TextContent body's
                // content type. ContentNegotiation moves Content-Type off request.headers and onto
                // the TextContent it produces, so for a serialized data-class body the type lives
                // only there — and Ktor still sends it on the wire, so the curl must show it.
                // Deliberately limited to TextContent: multipart manages its own Content-Type via
                // curl's -F (an explicit header with a stale boundary would break the request).
                val inferred = request.contentType() ?: (request.body as? TextContent)?.contentType
                val contentType = inferred?.toString().orEmpty()
                if (contentType.isNotBlank()) {
                    options.headerTransformer("Content-Type", contentType)?.let { transformed ->
                        tail += "-H " + "Content-Type: $transformed".quoted(options.shell)
                    }
                }
            },
            onEachHeader = { key, values ->
                val headerString = "$key: ${joinHeaderValues(key, values.distinct())}"
                tail += "-H " + headerString.quoted(options.shell)
            },
        )
    }

    tail += maskUrl(request.url.buildString(), options).quoted(options.shell)

    if (options.showBody) {
        val body = request.body
        if (body is MultiPartFormDataContent) {
            // coroutineScope only wraps the multipart materialization, which needs a scope to
            // drain the body channel; the rest of the command is built synchronously.
            coroutineScope { parseMultipartFormParts(body) }
                .forEach { part ->
                    tail += "-F " + part.quoted(options.shell)
                    hasBody = true
                }
        } else {
            body.onRequestBody { found ->
                val processed = truncateBody(redactJson(found, options), options.maxBodyLength)
                tail += "-d " + processed.quoted(options.shell)
                hasBody = true
            }
        }
    }

    // curl infers the method from body presence: no body defaults to GET, a -d/-F body defaults
    // to POST. Emit -X only when the method differs from that default, to cut redundant noise.
    val methodIsDefault = (method == "GET" && !hasBody) || (method == "POST" && hasBody)

    val args = mutableListOf("curl")
    if (!methodIsDefault) args += "-X $method"
    if (options.insecure) args += "--insecure"
    if (options.compressed) args += "--compressed"
    if (options.followRedirects) args += "-L"
    options.connectTimeoutSeconds?.takeIf { it >= 0 }?.let { args += "--connect-timeout $it" }
    options.maxTimeSeconds?.takeIf { it >= 0 }?.let { args += "--max-time $it" }
    options.retry?.takeIf { it >= 0 }?.let { args += "--retry $it" }
    args += tail

    val separator = options.componentDelimiter ?: if (options.multiLine) " \\\n  " else " "
    return args.joinToString(separator)
}

/** Backward-compatible entry point; prefer [generateCurl] with [CurlOptions]. */
internal suspend fun generateCurl(
    request: HttpRequestBuilder,
    excludedHeaders: Set<String> = setOf(),
    maskedHeaders: Set<String> = setOf(),
): String = generateCurl(request, CurlOptions(excludedHeaders = excludedHeaders, maskedHeaders = maskedHeaders))

/**
 * Materializes the multipart wire bytes (writing into a throwaway channel so the real
 * request body is never consumed) and reparses them into curl `-F` argument strings:
 * text fields render as `name=value`, file fields as `name=@filename` (with `;type=<ct>`
 * when a per-part Content-Type is present). On any failure a single `[multipart body
 * omitted]` placeholder is returned, since file/streaming parts may be one-shot.
 *
 * Bodies larger than [MAX_MULTIPART_BYTES] are not materialized — rendering a curl line for
 * a multi-megabyte upload is useless and reading it all into a String would risk OOM, so the
 * placeholder is returned instead. Known oversize lengths short-circuit before the channel is
 * opened; unknown lengths are read with a hard cap.
 */
internal suspend fun CoroutineScope.parseMultipartFormParts(content: MultiPartFormDataContent): List<String> {
    content.contentLength?.let { length ->
        if (length > MAX_MULTIPART_BYTES) return listOf(MULTIPART_OMITTED)
    }
    val rendered =
        try {
            val job = writer(Dispatchers.Default) { content.writeTo(channel) }
            // Read one byte past the cap so an exactly-at-limit body still renders while anything
            // larger is detected and dropped.
            val channel = job.channel
            val bytes = channel.readRemaining(MAX_MULTIPART_BYTES + 1).readByteArray()
            if (bytes.size > MAX_MULTIPART_BYTES) {
                channel.cancel(null)
                return listOf(MULTIPART_OMITTED)
            }
            val text = bytes.decodeToString()
            val boundary = content.contentType.parameter("boundary") ?: detectBoundary(text)
            if (boundary.isNullOrBlank()) return listOf(MULTIPART_OMITTED)
            renderFormParts(text, boundary)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            return listOf(MULTIPART_OMITTED)
        }
    return rendered.ifEmpty { listOf(MULTIPART_OMITTED) }
}

private const val MULTIPART_OMITTED = "[multipart body omitted]"

/** Upper bound on multipart bytes materialized for curl rendering. Larger bodies are omitted. */
internal const val MAX_MULTIPART_BYTES = 1L * 1024 * 1024

/**
 * Quotes [this] for safe embedding in the target [shell].
 * - [CurlShell.Posix]: single quotes neutralize `$`, backticks, `\`; inner `'` → `'\''`.
 * - [CurlShell.PowerShell]: single quotes; inner `'` doubled to `''`.
 * - [CurlShell.WindowsCmd]: double quotes; inner `"` → `\"`. (cmd `%`-expansion is not escaped;
 *   it is rare in HTTP payloads and fully escaping it is dialect-fragile.)
 */
internal fun String.quoted(shell: CurlShell): String =
    when (shell) {
        CurlShell.Posix -> "'" + replace("'", "'\\''") + "'"
        CurlShell.PowerShell -> "'" + replace("'", "''") + "'"
        CurlShell.WindowsCmd -> "\"" + replace("\"", "\\\"") + "\""
    }

private const val TRUNCATION_INDICATOR = "…[truncated]"

/** Truncates [body] to [max] chars (when set and exceeded), appending [TRUNCATION_INDICATOR]. */
private fun truncateBody(
    body: String,
    max: Int?,
): String = if (max != null && max >= 0 && body.length > max) body.take(max) + TRUNCATION_INDICATOR else body

/**
 * Masks `user:password@` credentials and any [CurlOptions.maskedQueryParams] in [raw]. Skips the
 * URLBuilder round-trip entirely when there is nothing to mask, so untouched URLs render verbatim.
 */
private fun maskUrl(
    raw: String,
    options: CurlOptions,
): String {
    val maskUserInfo = options.maskUrlUserInfo && raw.contains('@')
    if (!maskUserInfo && options.maskedQueryParams.isEmpty()) return raw
    return try {
        val builder = URLBuilder(raw)
        // Write the placeholder through the *encoded* setters so it renders verbatim. The plain
        // setters percent-encode it, turning the default "[masked]" into an unreadable
        // "%5Bmasked%5D" — wrong for a tool whose whole point is human-readable output.
        if (maskUserInfo) {
            if (!builder.encodedUser.isNullOrEmpty()) builder.encodedUser = options.maskPlaceholder
            if (!builder.encodedPassword.isNullOrEmpty()) builder.encodedPassword = options.maskPlaceholder
        }
        if (options.maskedQueryParams.isNotEmpty()) {
            val masked = options.maskedQueryParams.mapTo(HashSet()) { it.lowercase() }
            builder.encodedParameters.names()
                .filter { masked.contains(it.lowercase()) }
                .forEach { builder.encodedParameters[it] = options.maskPlaceholder }
        }
        builder.buildString()
    } catch (_: Throwable) {
        raw
    }
}

/**
 * Deep-redacts JSON values for [CurlOptions.maskedJsonKeys] anywhere in [body], at any nesting
 * depth. Regex-based (no JSON parser dependency): matches `"key": <string|number|bool|null>` and
 * replaces the value with a quoted [CurlOptions.maskPlaceholder]. Keys match case-insensitively.
 */
private fun redactJson(
    body: String,
    options: CurlOptions,
): String {
    if (options.maskedJsonKeys.isEmpty()) return body
    var result = body
    for (key in options.maskedJsonKeys) {
        val escaped = Regex.escape(key)
        val regex =
            Regex(
                """("$escaped"\s*:\s*)("(?:\\.|[^"\\])*"|-?\d+(?:\.\d+)?|true|false|null)""",
                RegexOption.IGNORE_CASE,
            )
        result = regex.replace(result) { match -> match.groupValues[1] + "\"${options.maskPlaceholder}\"" }
    }
    return result
}

/** Headers whose multiple values are joined with `; ` instead of the HTTP-standard `, `. */
private val SEMICOLON_JOINED_HEADERS = setOf("Cookie")

/**
 * Joins multiple values of one header. Per RFC 9110 field values combine with a comma;
 * the [Cookie] header is the exception (RFC 6265 §5.4 separates cookie-pairs with `; `).
 * Matching is case-insensitive.
 */
private fun joinHeaderValues(
    key: String,
    values: List<String>,
): String {
    val separator = if (SEMICOLON_JOINED_HEADERS.any { it.equals(key, ignoreCase = true) }) "; " else ", "
    return values.joinToString(separator)
}

internal fun detectBoundary(text: String): String? =
    text
        .lineSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith("--") }
        ?.removePrefix("--")
        ?.trimEnd('-')
        ?.trim()
        ?.takeIf { it.isNotBlank() }

internal fun renderFormParts(
    text: String,
    boundary: String,
): List<String> {
    val delimiter = "--$boundary"
    return text
        .split(delimiter)
        .map { it.trim('\r', '\n') }
        .filter { it.isNotEmpty() && it != "--" }
        .mapNotNull { renderPart(it) }
}

internal fun renderPart(segment: String): String? {
    val separator =
        when {
            segment.contains("\r\n\r\n") -> "\r\n\r\n"
            segment.contains("\n\n") -> "\n\n"
            else -> return null
        }
    val headerBlock = segment.substringBefore(separator)
    val partBody = segment.substringAfter(separator).trimEnd('\r', '\n')

    val headers = headerBlock.split("\r\n", "\n")
    val disposition = headers.firstOrNull { it.startsWith("Content-Disposition", true) } ?: return null
    val name = extractParam(disposition, "name") ?: return null
    val filename = extractParam(disposition, "filename")
    val contentType =
        headers
            .firstOrNull { it.startsWith("Content-Type", true) }
            ?.substringAfter(':')
            ?.trim()

    val value =
        if (filename != null) {
            buildString {
                append('@')
                append(filename)
                // curl's -F type= wants the bare media type, not charset/boundary params.
                val mediaType = contentType?.substringBefore(';')?.trim()
                if (!mediaType.isNullOrBlank()) append(";type=$mediaType")
            }
        } else {
            partBody
        }
    return "$name=$value"
}

// Matches a Content-Disposition parameter: attr name in group 1, quoted value in group 2,
// bare unquoted value in group 3. Compiled once instead of per extractParam call.
private val DISPOSITION_PARAM = Regex("""(\w+)\s*=\s*(?:"([^"]*)"|([^;,\s]+))""", RegexOption.IGNORE_CASE)

private fun extractParam(
    disposition: String,
    key: String,
): String? {
    val match =
        DISPOSITION_PARAM
            .findAll(disposition)
            .firstOrNull { it.groupValues[1].equals(key, ignoreCase = true) } ?: return null
    val quoted = match.groups[2]?.value
    return (quoted ?: match.groupValues[3]).trim()
}

internal fun HeadersBuilder.onHeaders(
    onEachHeader: (key: String, values: List<String>) -> Unit = { _, _ -> },
    onNoContentTypeHeader: () -> Unit = {},
    excludedHeaders: Set<String> = setOf(),
    maskedHeaders: Set<String> = setOf(),
    maskPlaceholder: String = "[masked]",
    headerTransformer: HeaderTransformer = { _, value -> value },
) {
    val headers = entries()
    if (headers.isEmpty()) {
        onNoContentTypeHeader()
        return
    }

    // HTTP header names are case-insensitive (RFC 9110 §5.1). Normalize the config sets to
    // lowercase so a configured "authorization" still masks a wire "Authorization" — a
    // case-sensitive Set lookup would silently leak the secret.
    val excluded = excludedHeaders.mapTo(HashSet()) { it.lowercase() }
    val masked = maskedHeaders.mapTo(HashSet()) { it.lowercase() }

    var containsContentType = false
    headers
        .filterNot { h -> excluded.contains(h.key.lowercase()) }
        .sortedBy { it.key }
        .forEach { (key, values) ->
            // Masking wins before the transformer: a masked header is collapsed to the placeholder
            // so the real secret is never handed to the (untrusted) transformer hook.
            val baseValues = if (masked.contains(key.lowercase())) listOf(maskPlaceholder) else values
            val transformed = baseValues.mapNotNull { headerTransformer(key, it) }
            // A header whose every value was dropped (transformer returned null) is omitted entirely.
            if (transformed.isNotEmpty()) onEachHeader(key, transformed)
            if (key.equals("Content-Type", ignoreCase = true)) containsContentType = true
        }
    if (!containsContentType) onNoContentTypeHeader()
}

internal fun Any.onRequestBody(onBodyFound: (String) -> Unit) {
    val body =
        try {
            when (this) {
                is TextContent -> bytes().decodeBodyOrBinary()

                is ByteArrayContent -> bytes().decodeBodyOrBinary()

                is EmptyContent -> ""

                // LocalFileContent and any streaming/channel body are ReadChannelContent /
                // WriteChannelContent. They can't be inlined into a runnable -d without consuming
                // the real send body (or, for files, a path we don't have in commonMain), so emit
                // a clear placeholder instead of a garbled toString() class name.
                is OutgoingContent.ReadChannelContent -> STREAMING_OMITTED

                is OutgoingContent.WriteChannelContent -> STREAMING_OMITTED

                is String -> this

                else -> toString()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Materializing the body (in-memory bytes() / toString()) must never crash the whole
            // curl render — emit a placeholder so the rest of the command still logs.
            BODY_READ_FAILED
        }

    // A whitespace-only body carries no meaningful payload; treat it like an empty body and
    // omit the -d flag entirely (locked by WhitespaceBodyTests).
    if (body.isNotBlank()) onBodyFound(body)
}

private const val BINARY_OMITTED = "[binary body omitted]"
private const val STREAMING_OMITTED = "[streaming body omitted]"
private const val BODY_READ_FAILED = "[body read failed]"

/**
 * Decodes [this] as UTF-8 text, but returns [BINARY_OMITTED] when the bytes look binary
 * (NUL / control bytes) or decode to the Unicode replacement char. Prevents garbled curl
 * output for gzipped, protobuf, image, or other non-textual payloads.
 */
private fun ByteArray.decodeBodyOrBinary(): String {
    if (looksBinary()) return BINARY_OMITTED
    val decoded = decodeToString()
    return if (decoded.contains('�')) BINARY_OMITTED else decoded
}

private fun ByteArray.looksBinary(): Boolean {
    if (isEmpty()) return false
    return take(512).any { b ->
        val u = b.toInt() and 0xFF
        // Allow tab (0x09), LF (0x0A), CR (0x0D); flag NUL and other C0/C1 control bytes.
        u == 0x00 || u in 0x01..0x08 || u in 0x0B..0x0C || u in 0x0E..0x1F || u == 0x7F
    }
}
