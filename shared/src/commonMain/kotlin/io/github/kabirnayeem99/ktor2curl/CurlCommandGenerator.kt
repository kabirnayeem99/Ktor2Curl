package io.github.kabirnayeem99.ktor2curl

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.utils.EmptyContent
import io.ktor.content.ByteArrayContent
import io.ktor.content.TextContent
import io.ktor.http.HeadersBuilder
import io.ktor.http.contentType
import kotlinx.coroutines.coroutineScope

internal suspend fun generateCurl(
    request: HttpRequestBuilder,
    excludedHeaders: Set<String> = setOf(),
    maskedHeaders: Set<String> = setOf(),
): String {
    return coroutineScope {
        buildString {
            append("curl -X ${request.method.value}")

            request.headers.onHeaders(
                excludedHeaders = excludedHeaders,
                maskedHeaders = maskedHeaders,
                onNoContentTypeHeader = {
                    val contentType = request.contentType()?.contentType ?: ""
                    if (contentType.isNotBlank()) append(" -H \"Content-Type: ${contentType}\"")
                },
                onEachHeader = { key, values ->
                    append(" -H \"$key: ${values.joinToString("; ")}\"")
                },
            )

            append(" \"${request.url.buildString()}\"")

            request.body.onRequestBody { body -> append(" -d '$body'") }
        }
    }
}

internal fun HeadersBuilder.onHeaders(
    onEachHeader: (key: String, values: List<String>) -> Unit = { _, _ -> },
    onNoContentTypeHeader: () -> Unit = {},
    excludedHeaders: Set<String> = setOf(),
    maskedHeaders: Set<String> = setOf(),
) {
    val headers = entries()
    if (headers.isEmpty()) {
        onNoContentTypeHeader()
        return
    }

    var containsContentType = false
    headers.filterNot { h -> excludedHeaders.contains(h.key) }.forEach { (key, values) ->
        if (maskedHeaders.contains(key)) {
            onEachHeader(key, listOf("[masked]"))
        } else {
            onEachHeader(key, values)
        }
        if (key == "Content-Type") containsContentType = true
    }
    if (!containsContentType) onNoContentTypeHeader()
}

internal fun Any.onRequestBody(onBodyFound: (String) -> Unit) {
    val body = when (this) {
        is TextContent -> {
            val bytes = bytes()
            bytes.decodeToString(0, 0 + bytes.size)
        }

        is ByteArrayContent -> {
            val bytes = bytes()
            bytes.decodeToString(0, 0 + bytes.size)
        }

        is EmptyContent -> ""
        is MultiPartFormDataContent -> "[request body omitted]"
        is String -> this
        else -> toString()
    }
    if (body.isNotBlank()) onBodyFound(body)
}

