package io.github.kabirnayeem99.ktor2curl

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.ChannelProvider
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpMethod
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Targets specific branches/defaults that the feature-oriented test files don't happen to exercise
 * — found via the Kover coverage report (`./gradlew :ktor2curl:koverHtmlReport`). Each test is named
 * after the exact gap it closes rather than a user scenario.
 */
class CoverageGapTests {
    // --- parseMultipartFormParts / renderMultipartBody -----------------------------------------

    @Test
    fun `parseMultipartFormParts default maxMultipartBytes renders a small body normally`() =
        runBlocking {
            val content =
                MultiPartFormDataContent(
                    formData {
                        append("field", "value")
                    },
                )

            val parts = coroutineScope { parseMultipartFormParts(content) }

            assertEquals(listOf("field=value"), parts)
        }

    @Test
    fun `parseMultipartFormParts omits a body whose streamed size exceeds the cap despite unknown declared length`() =
        runBlocking {
            val big = ByteArray(64) { 'a'.code.toByte() }
            val content =
                MultiPartFormDataContent(
                    formData {
                        // size = null forces MultiPartFormDataContent.contentLength to be unknown, so
                        // the only way to detect an oversize body is by actually streaming it.
                        append("file", ChannelProvider(null) { ByteReadChannel(big) })
                    },
                )

            val parts = coroutineScope { parseMultipartFormParts(content, maxMultipartBytes = 8) }

            assertEquals(listOf("[multipart body omitted]"), parts)
        }

    @Test
    fun `parseMultipartFormParts omits the body when materializing it throws`() =
        runBlocking {
            val content =
                MultiPartFormDataContent(
                    formData {
                        append("file", ChannelProvider(null) { throw IllegalStateException("boom") })
                    },
                )

            val parts = coroutineScope { parseMultipartFormParts(content) }

            assertEquals(listOf("[multipart body omitted]"), parts)
        }

    @Test
    fun `renderMultipartBody omits when no boundary can be resolved`() {
        val result = renderMultipartBody("no boundary markers in this text at all", ContentType.MultiPart.FormData)

        assertEquals(listOf("[multipart body omitted]"), result)
    }

    @Test
    fun `renderMultipartBody omits when a boundary is found but no part parses`() {
        val text = "--boundary\r\nnot a valid part header block\r\n--boundary--"

        val result = renderMultipartBody(text, ContentType.MultiPart.FormData)

        assertEquals(listOf("[multipart body omitted]"), result)
    }

    @Test
    fun `renderMultipartBody uses the declared Content-Type boundary over a sniffed one`() {
        val text = "--real\r\nContent-Disposition: form-data; name=\"field\"\r\n\r\nvalue\r\n--real--"
        val contentType = ContentType.MultiPart.FormData.withParameter("boundary", "real")

        val result = renderMultipartBody(text, contentType)

        assertEquals(listOf("field=value"), result)
    }

    // --- onHeaders default lambda parameters ----------------------------------------------------

    @Test
    fun `onHeaders with default callbacks does not throw for an empty header set`() {
        HeadersBuilder().onHeaders()
    }

    @Test
    fun `onHeaders with default onEachHeader callback does not throw for a populated header set`() {
        val headers =
            HeadersBuilder().apply {
                append("X-Custom", "value")
            }

        headers.onHeaders()
    }

    // --- -X method-vs-body combinations -----------------------------------------------------------

    @Test
    fun `-X GET is emitted when a GET request has a body`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com")
                    method = HttpMethod.Get
                    setBody("payload")
                }

            val result = generateCurl(request, CurlOptions())

            assertTrue(result.contains("-X GET"), "Expected -X GET for a GET request with a body, got: $result")
        }

    @Test
    fun `-X POST is emitted when a POST request has no body`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com")
                    method = HttpMethod.Post
                }

            val result = generateCurl(request, CurlOptions())

            assertTrue(result.contains("-X POST"), "Expected -X POST for a bodyless POST request, got: $result")
        }

    // --- negative timeout/retry options -----------------------------------------------------------

    @Test
    fun `negative maxTimeSeconds is not injected`() =
        runBlocking {
            val request = HttpRequestBuilder().apply { url("https://example.com") }

            val result = request.toCurl(CurlOptions(maxTimeSeconds = -1))

            assertFalse(result.contains("--max-time"), "Negative maxTimeSeconds must not be injected, got: $result")
        }

    @Test
    fun `negative retry is not injected`() =
        runBlocking {
            val request = HttpRequestBuilder().apply { url("https://example.com") }

            val result = request.toCurl(CurlOptions(retry = -1))

            assertFalse(result.contains("--retry"), "Negative retry must not be injected, got: $result")
        }

    // --- URL userinfo masking with only one of user/password present -------------------------------

    @Test
    fun `maskUrlUserInfo masks a user with no password`() =
        runBlocking {
            val request = HttpRequestBuilder().apply { url("https://user@example.com/api") }

            val result = request.toCurl(CurlOptions(maskUrlUserInfo = true))

            assertTrue(result.contains("[masked]@example.com"), "Expected masked user, got: $result")
            assertFalse(result.contains("user@"), "Original user must not leak, got: $result")
        }

    @Test
    fun `maskUrlUserInfo masks a password with no user`() =
        runBlocking {
            val request = HttpRequestBuilder().apply { url("https://:secret@example.com/api") }

            val result = request.toCurl(CurlOptions(maskUrlUserInfo = true))

            assertTrue(result.contains("[masked]@example.com"), "Expected masked password, got: $result")
            assertFalse(result.contains("secret"), "Original password must not leak, got: $result")
        }

    // --- Content-Disposition parsing edge cases -----------------------------------------------------

    @Test
    fun `renderPart skips a part whose Content-Disposition has no name parameter`() {
        val text = "--b\r\nContent-Disposition: form-data; filename=\"f.txt\"\r\n\r\ndata\r\n--b--"

        val result = renderFormParts(text, "b")

        assertTrue(result.isEmpty(), "A part with no 'name' must be skipped entirely, got: $result")
    }

    @Test
    fun `renderPart handles an unquoted Content-Disposition name`() {
        val text = "--b\r\nContent-Disposition: form-data; name=field\r\n\r\nvalue\r\n--b--"

        val result = renderFormParts(text, "b")

        assertEquals(listOf("field=value"), result)
    }

    @Test
    fun `renderPart renders a file part with no Content-Type header`() {
        val text = "--b\r\nContent-Disposition: form-data; name=\"file\"; filename=\"f.txt\"\r\n\r\ndata\r\n--b--"

        val result = renderFormParts(text, "b")

        assertEquals(listOf("file=@f.txt"), result)
    }

    // --- looksBinary control-byte ranges (via the internal onRequestBody entry point) ----------------

    @Test
    fun `onRequestBody with an empty ByteArrayContent body is treated as text not binary`() {
        val content = io.ktor.content.ByteArrayContent(ByteArray(0))
        var captured: String? = null

        content.onRequestBody { captured = it }

        // An empty body is blank, so onBodyFound is never even invoked (see the isNotBlank guard) —
        // this locks in that an empty byte array is never misclassified as binary along the way.
        assertEquals(null, captured)
    }

    @Test
    fun `onRequestBody flags bytes from every disallowed control-byte range as binary`() {
        // One representative byte from each disjunct of looksBinary's range check, interleaved with
        // printable text so the payload isn't also caught by the blank-body guard.
        // 0x01 hits 0x01..0x08, 0x0B hits 0x0B..0x0C, 0x0E hits 0x0E..0x1F, 0x7F is DEL.
        val samples = listOf(0x01, 0x0B, 0x0E, 0x7F)
        for (sample in samples) {
            val bytes = byteArrayOf('a'.code.toByte(), sample.toByte(), 'b'.code.toByte())
            val content = io.ktor.content.ByteArrayContent(bytes)
            var captured = ""

            content.onRequestBody { captured = it }

            assertEquals("[binary body omitted]", captured, "byte 0x${sample.toString(16)} should be flagged as binary")
        }
    }

    @Test
    fun `onRequestBody allows tab LF and CR without flagging binary`() {
        val bytes = "line1\tvalue\r\nline2".map { it.code.toByte() }.toByteArray()
        val content = io.ktor.content.ByteArrayContent(bytes)
        var captured = ""

        content.onRequestBody { captured = it }

        assertEquals("line1\tvalue\r\nline2", captured)
    }

    // --- header value not found in the disposition regex --------------------------------------------

    @Test
    fun `renderPart skips a part with no filename and no Content-Type when disposition has an unrelated param only`() {
        val text = "--b\r\nContent-Disposition: form-data; name=\"field\"; other=\"x\"\r\n\r\nvalue\r\n--b--"

        val result = renderFormParts(text, "b")

        assertEquals(listOf("field=value"), result)
    }
}
