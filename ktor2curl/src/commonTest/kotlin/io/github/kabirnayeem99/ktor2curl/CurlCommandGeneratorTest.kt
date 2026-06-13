package io.github.kabirnayeem99.ktor2curl

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.headers
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.utils.EmptyContent
import io.ktor.content.ByteArrayContent
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.toByteArray
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GenerateCurlTests {
    @Test
    fun `generateCurl with GET request and no headers`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Get
                }

            val result = generateCurl(request, excludedHeaders = emptySet())

            assertEquals("curl 'https://example.com/api'", result)
        }

    @Test
    fun `generateCurl with POST request and body`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody("key=value")
                }

            val result = generateCurl(request, excludedHeaders = emptySet())

            assertEquals("curl 'https://example.com/api' -d 'key=value'", result)
        }

    @Test
    fun `generateCurl with headers and excluded headers`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Get
                    headers {
                        append("Authorization", "Bearer token")
                        append("User-Agent", "KtorClient")
                        append("Content-Type", "application/json")
                    }
                }

            val excludedHeaders = setOf("Authorization")
            val result =
                generateCurl(request, excludedHeaders = excludedHeaders)

            assertEquals(
                "curl -H 'Content-Type: application/json' -H 'User-Agent: KtorClient' 'https://example.com/api'",
                result,
            )
        }

    @Test
    fun `generateCurl with no Content-Type and onNoContentTypeHeader triggered`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody("key=value")
                }

            val result = generateCurl(request, excludedHeaders = emptySet())

            // Since there is no Content-Type header, onNoContentTypeHeader should trigger but not add anything
            assertEquals("curl 'https://example.com/api' -d 'key=value'", result)
        }

    @Test
    fun `generateCurl with Content-Type in body`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    contentType(ContentType.Application.Json)
                    setBody("{ \"key\": \"value\" }")
                }

            val result = generateCurl(request, excludedHeaders = emptySet())

            assertEquals(
                "curl -H 'Content-Type: application/json' 'https://example.com/api' -d '{ \"key\": \"value\" }'",
                result,
            )
        }

    @Test
    fun `generateCurl with multiple headers and excluded one`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    headers {
                        append("Authorization", "Bearer token")
                        append("Content-Type", "application/json")
                        append("User-Agent", "KtorClient")
                    }
                    setBody("{ \"key\": \"value\" }")
                }

            val excludedHeaders = setOf("Authorization")
            val result = generateCurl(request, excludedHeaders = excludedHeaders)

            assertEquals(
                "curl -H 'Content-Type: application/json' -H 'User-Agent: KtorClient' 'https://example.com/api' -d '{ \"key\": \"value\" }'",
                result,
            )
        }

    @Test
    fun `generateCurl with empty body`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody("")
                }

            val result = generateCurl(request, excludedHeaders = emptySet())

            // No body content is added to the curl command
            assertEquals("curl -X POST 'https://example.com/api'", result)
        }

    @Test
    fun `generateCurl with multipart text fields renders -F flags`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append("key", "value")
                                append("name", "ktor")
                            },
                        ),
                    )
                }

            val result = generateCurl(request, excludedHeaders = setOf("Content-Type"))

            assertEquals(
                "curl 'https://example.com/api' -F 'key=value' -F 'name=ktor'",
                result,
            )
        }

    @Test
    fun `generateCurl with multipart file field renders -F with filename and type`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append(
                                    "file",
                                    "imgbytes".toByteArray(),
                                    Headers.build {
                                        append(HttpHeaders.ContentType, "image/png")
                                        append(HttpHeaders.ContentDisposition, "filename=\"a.png\"")
                                    },
                                )
                            },
                        ),
                    )
                }

            val result = generateCurl(request, excludedHeaders = setOf("Content-Type"))

            assertEquals(
                "curl 'https://example.com/api' -F 'file=@a.png;type=image/png'",
                result,
            )
        }

    @Test
    fun `generateCurl with multiple values for the same header`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Get
                    headers {
                        append("Accept", "application/json")
                        append("Accept", "text/html")
                    }
                }

            val result = generateCurl(request, excludedHeaders = emptySet())

            // Standard HTTP combines multiple values with a comma.
            assertEquals(
                "curl -H 'Accept: application/json, text/html' 'https://example.com/api'",
                result,
            )
        }

    @Test
    fun `generateCurl joins multi-value Cookie header with semicolon`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Get
                    headers {
                        append("Cookie", "a=1")
                        append("Cookie", "b=2")
                    }
                }

            val result = generateCurl(request, excludedHeaders = emptySet())

            // Cookie is the RFC 6265 exception: cookie-pairs joined with "; ".
            assertEquals(
                "curl -H 'Cookie: a=1; b=2' 'https://example.com/api'",
                result,
            )
        }

    @Test
    fun `generateCurl joins multi-value Cookie case-insensitively with semicolon`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Get
                    headers {
                        append("cookie", "a=1")
                        append("cookie", "b=2")
                    }
                }

            val result = generateCurl(request, excludedHeaders = emptySet())

            assertEquals(
                "curl -H 'cookie: a=1; b=2' 'https://example.com/api'",
                result,
            )
        }

    @Test
    fun `generateCurl with masked headers`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Get
                    headers {
                        append("Authorization", "Bearer sensitive-token")
                        append("Cookie", "session=12345")
                        append("X-Public-Key", "public-value")
                    }
                }

            val result =
                generateCurl(
                    request,
                    maskedHeaders = setOf("Authorization", "Cookie"),
                )

            assertEquals(
                "curl -H 'Authorization: [masked]' -H 'Cookie: [masked]' -H 'X-Public-Key: public-value' 'https://example.com/api'",
                result,
            )
        }
}

class OnHeadersTests {
    @Test
    fun `onHeaders should invoke onEachHeader for all headers except excluded ones`() {
        val headersBuilder = HeadersBuilder()
        headersBuilder.append("Authorization", "Bearer token")
        headersBuilder.append("Content-Type", "application/json")
        headersBuilder.append("User-Agent", "KtorClient")

        val excludedHeaders = setOf("Authorization")
        val capturedHeaders = mutableListOf<Pair<String, List<String>>>()

        headersBuilder.onHeaders(
            onEachHeader = { key, values -> capturedHeaders.add(key to values) },
            excludedHeaders = excludedHeaders,
        )

        assertEquals(2, capturedHeaders.size)
        assertEquals("Content-Type", capturedHeaders[0].first)
        assertEquals(listOf("application/json"), capturedHeaders[0].second)
        assertEquals("User-Agent", capturedHeaders[1].first)
        assertEquals(listOf("KtorClient"), capturedHeaders[1].second)
    }

    @Test
    fun `onHeaders should invoke onNoContentTypeHeader when Content-Type is missing`() {
        val headersBuilder = HeadersBuilder()
        headersBuilder.append("Authorization", "Bearer token")
        headersBuilder.append("User-Agent", "KtorClient")

        var noContentTypeHeaderInvoked = false

        headersBuilder.onHeaders(
            onEachHeader = { _, _ -> },
            onNoContentTypeHeader = { noContentTypeHeaderInvoked = true },
            excludedHeaders = emptySet(),
        )

        assertTrue(noContentTypeHeaderInvoked)
    }

    @Test
    fun `onHeaders should not invoke onNoContentTypeHeader when Content-Type is present`() {
        val headersBuilder = HeadersBuilder()
        headersBuilder.append("Authorization", "Bearer token")
        headersBuilder.append("Content-Type", "application/json")
        headersBuilder.append("User-Agent", "KtorClient")

        var noContentTypeHeaderInvoked = false

        headersBuilder.onHeaders(
            onEachHeader = { _, _ -> },
            onNoContentTypeHeader = { noContentTypeHeaderInvoked = true },
            excludedHeaders = emptySet(),
        )

        assertFalse(noContentTypeHeaderInvoked)
    }

    @Test
    fun `onHeaders should exclude specified headers`() {
        val headersBuilder = HeadersBuilder()
        headersBuilder.append("Authorization", "Bearer token")
        headersBuilder.append("Content-Type", "application/json")
        headersBuilder.append("User-Agent", "KtorClient")

        val excludedHeaders = setOf("User-Agent", "Authorization")
        val capturedHeaders = mutableListOf<Pair<String, List<String>>>()

        headersBuilder.onHeaders(
            onEachHeader = { key, values -> capturedHeaders.add(key to values) },
            onNoContentTypeHeader = {},
            excludedHeaders = excludedHeaders,
        )

        assertEquals(1, capturedHeaders.size)
        assertEquals("Content-Type", capturedHeaders[0].first)
        assertEquals(listOf("application/json"), capturedHeaders[0].second)
    }

    @Test
    fun `onHeaders should mask specified headers`() {
        val headersBuilder = HeadersBuilder()
        headersBuilder.append("Authorization", "Bearer token")
        headersBuilder.append("X-Secret", "my-secret")
        headersBuilder.append("Content-Type", "application/json")

        val maskedHeaders = setOf("Authorization", "X-Secret")
        val capturedHeaders = mutableListOf<Pair<String, List<String>>>()

        headersBuilder.onHeaders(
            onEachHeader = { key, values -> capturedHeaders.add(key to values) },
            maskedHeaders = maskedHeaders,
        )

        assertEquals(3, capturedHeaders.size)
        // Order is alphabetical: Authorization, Content-Type, X-Secret
        assertEquals("Authorization", capturedHeaders[0].first)
        assertEquals(listOf("[masked]"), capturedHeaders[0].second)

        assertEquals("Content-Type", capturedHeaders[1].first)
        assertEquals(listOf("application/json"), capturedHeaders[1].second)

        assertEquals("X-Secret", capturedHeaders[2].first)
        assertEquals(listOf("[masked]"), capturedHeaders[2].second)
    }

    @Test
    fun `onHeaders should handle empty headers`() {
        val headersBuilder = HeadersBuilder()

        var noContentTypeHeaderInvoked = false
        val capturedHeaders = mutableListOf<Pair<String, List<String>>>()

        headersBuilder.onHeaders(
            onEachHeader = { key, values -> capturedHeaders.add(key to values) },
            onNoContentTypeHeader = { noContentTypeHeaderInvoked = true },
            excludedHeaders = emptySet(),
        )

        assertTrue(noContentTypeHeaderInvoked)
        assertTrue(capturedHeaders.isEmpty())
    }
}

class OnRequestBodyTests {
    @Test
    fun `onRequestBody with TextContent Plain should capture the plain text body`() {
        val textContent = TextContent("sample text", ContentType.Text.Plain)
        var capturedBody = ""
        textContent.onRequestBody { capturedBody = it }
        assertEquals("sample text", capturedBody)
    }

    @Test
    fun `onRequestBody with TextContent XML should capture the XML body`() {
        val textContent =
            TextContent("<xml><message>sample xml</message></xml>", ContentType.Text.Xml)
        var capturedBody = ""
        textContent.onRequestBody { capturedBody = it }
        assertEquals("<xml><message>sample xml</message></xml>", capturedBody)
    }

    @Test
    fun `onRequestBody with ByteArrayContent should capture the byte array as string`() {
        val byteArrayContent = ByteArrayContent("byte array content".toByteArray())
        var capturedBody = ""
        byteArrayContent.onRequestBody { capturedBody = it }
        assertEquals("byte array content", capturedBody)
    }

    @Test
    fun `onRequestBody with EmptyContent should capture an empty string`() {
        val emptyContent = EmptyContent
        var capturedBody = ""
        emptyContent.onRequestBody { capturedBody = it }
        assertEquals("", capturedBody)
        assertFalse(capturedBody.isNotBlank())
    }

    @Test
    fun `onRequestBody with String body should capture the string body`() {
        val stringBody = "This is a string body"
        var capturedBody = ""
        stringBody.onRequestBody { capturedBody = it }
        assertEquals("This is a string body", capturedBody)
    }

    @Test
    fun `onRequestBody with unknown body type should capture the body's string representation`() {
        val unknownBody =
            object {
                override fun toString(): String {
                    return "Unknown Body Type"
                }
            }
        var capturedBody = ""
        unknownBody.onRequestBody {
            capturedBody = it
        }
        assertEquals("Unknown Body Type", capturedBody)
    }
}

class BinaryContentTests {
    @Test
    fun `onRequestBody with gzipped binary ByteArrayContent is omitted not garbled`() {
        // gzip magic header: 0x1f 0x8b 0x08, then a NUL byte.
        val gzip = byteArrayOf(0x1f, 0x8b.toByte(), 0x08, 0x00, 0x00, 0x00)
        val content = ByteArrayContent(gzip, ContentType.Application.GZip)
        var captured = ""
        content.onRequestBody { captured = it }
        assertEquals("[binary body omitted]", captured)
    }

    @Test
    fun `onRequestBody with PNG image bytes is omitted not garbled`() {
        // PNG signature: 0x89 P N G CR LF 0x1A LF.
        val png =
            byteArrayOf(
                0x89.toByte(),
                'P'.code.toByte(),
                'N'.code.toByte(),
                'G'.code.toByte(),
                0x0D,
                0x0A,
                0x1A,
                0x0A,
            )
        val content = ByteArrayContent(png, ContentType.Image.PNG)
        var captured = ""
        content.onRequestBody { captured = it }
        assertEquals("[binary body omitted]", captured)
    }

    @Test
    fun `onRequestBody with invalid UTF-8 bytes is omitted not garbled`() {
        // 0xFF 0xFE are not valid UTF-8 lead bytes -> decode yields replacement chars.
        val content = ByteArrayContent(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x41))
        var captured = ""
        content.onRequestBody { captured = it }
        assertEquals("[binary body omitted]", captured)
    }

    @Test
    fun `onRequestBody with valid UTF-8 ByteArrayContent is decoded normally`() {
        // Control: plain text must NOT be misclassified as binary.
        val content = ByteArrayContent("héllo wörld".toByteArray())
        var captured = ""
        content.onRequestBody { captured = it }
        assertEquals("héllo wörld", captured)
    }

    @Test
    fun `generateCurl with binary body emits placeholder not garbled bytes`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/upload")
                    method = HttpMethod.Post
                    setBody(ByteArrayContent(byteArrayOf(0x1f, 0x8b.toByte(), 0x08, 0x00), ContentType.Application.GZip))
                }

            val result = generateCurl(request, excludedHeaders = setOf("Content-Type"))

            assertEquals(
                "curl 'https://example.com/upload' -d '[binary body omitted]'",
                result,
            )
        }
}

class ChannelContentTests {
    @Test
    fun `onRequestBody with ReadChannelContent is omitted not toString fallback`() {
        val content =
            object : OutgoingContent.ReadChannelContent() {
                override fun readFrom() = ByteReadChannel(byteArrayOf(1, 2, 3))
            }
        var captured = ""
        content.onRequestBody { captured = it }
        assertEquals("[streaming body omitted]", captured)
    }

    @Test
    fun `onRequestBody with WriteChannelContent is omitted not toString fallback`() {
        val content =
            object : OutgoingContent.WriteChannelContent() {
                override suspend fun writeTo(channel: io.ktor.utils.io.ByteWriteChannel) {
                    channel.flushAndClose()
                }
            }
        var captured = ""
        content.onRequestBody { captured = it }
        assertEquals("[streaming body omitted]", captured)
    }
}

class KmpEncodingTests {
    @Test
    fun `toByteArray then decodeToString round-trips multibyte UTF-8 identically`() {
        // Must behave the same on JVM and iOS/Native (run via commonTest on both targets).
        val original = "héllo wörld — ünïcödé"
        assertEquals(original, original.toByteArray().decodeToString())
    }

    @Test
    fun `toByteArray then decodeToString round-trips emoji identically`() {
        val original = "🚀✅🌍"
        assertEquals(original, original.toByteArray().decodeToString())
    }

    @Test
    fun `generateCurl preserves non-ASCII header values across platforms`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Get
                    headers { append("X-Note", "café — naïve") }
                }

            val result = generateCurl(request, excludedHeaders = emptySet())

            assertEquals(
                "curl -H 'X-Note: café — naïve' 'https://example.com/api'",
                result,
            )
        }

    @Test
    fun `generateCurl preserves non-ASCII body across platforms`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody("{\"city\":\"São Paulo\"}")
                }

            val result = generateCurl(request, excludedHeaders = emptySet())

            assertEquals(
                "curl 'https://example.com/api' -d '{\"city\":\"São Paulo\"}'",
                result,
            )
        }
}

class WhitespaceBodyTests {
    @Test
    fun `onRequestBody with whitespace-only body does not invoke callback`() {
        var invoked = false
        "   \t \n ".onRequestBody { invoked = true }
        assertFalse(invoked)
    }

    @Test
    fun `generateCurl with whitespace-only body omits the -d flag`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody("   ")
                }

            val result = generateCurl(request, excludedHeaders = emptySet())

            assertEquals("curl -X POST 'https://example.com/api'", result)
        }
}
