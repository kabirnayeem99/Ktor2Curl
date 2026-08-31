package io.github.kabirnayeem99.ktor2curl

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.content.ByteArrayContent
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Phase 4 (final): scale (large bodies) and lifecycle (one plugin instance handling many
 * requests) coverage, plus one more binary-body magic-byte case not already exercised in
 * [GenerateCurlTests].
 */
class ScaleAndLifecycleTests {
    // ──────────────────────────────────────────────────────────────────────
    // Large body truncation at scale
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `large body is truncated to exactly maxBodyLength plus indicator`() =
        runBlocking {
            // ~1 MiB body.
            val bigBody = "x".repeat(1_000_000)
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody(bigBody)
                }

            val maxLength = 500_000
            val options = CurlOptions(maxBodyLength = maxLength)
            val result = request.toCurl(options)

            val expectedTruncated = "x".repeat(maxLength) + "…[truncated]"
            assertTrue(
                result.contains("--data-binary '$expectedTruncated'"),
                "Expected body truncated to exactly $maxLength chars plus indicator",
            )
            assertFalse(
                result.contains("x".repeat(maxLength + 1)),
                "Truncated output must not contain more than maxBodyLength raw chars",
            )
        }

    @Test
    fun `large body without maxBodyLength renders in full uncut`() =
        runBlocking {
            // ~2 MiB body.
            val bigBody = "y".repeat(2_000_000)
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody(bigBody)
                }

            val options = CurlOptions(maxBodyLength = null)
            val result = request.toCurl(options)

            assertFalse(result.contains("[truncated]"), "No truncation expected when maxBodyLength is null")
            assertTrue(result.contains(bigBody), "Full body must appear uncut in the output")
        }

    // ──────────────────────────────────────────────────────────────────────
    // Single plugin instance, multiple requests — maskedJsonKeyRegexes cache reuse
    // ──────────────────────────────────────────────────────────────────────

    private class RecordingLogger : CurlLogger {
        val logged = mutableListOf<String>()

        override suspend fun log(curl: String) {
            logged += curl
        }
    }

    @Test
    fun `maskedJsonKeyRegexes is computed once and the same instance is reused across calls`() {
        // CurlOptions is immutable, so masking *correctness* across many requests would hold even
        // if the regex list were rebuilt every time — that only proves redactJson works, not that
        // the `by lazy` cache is doing anything. This test targets the cache itself: two reads of
        // the same CurlOptions instance's internal maskedJsonKeyRegexes must be the identical List,
        // not merely equal lists rebuilt from scratch each time.
        val options = CurlOptions(maskedJsonKeys = setOf("password", "token"))

        val first = options.maskedJsonKeyRegexes
        val second = options.maskedJsonKeyRegexes

        assertTrue(first === second, "maskedJsonKeyRegexes must be cached (by lazy), not recomputed per access")
    }

    @Test
    fun `one installed plugin instance masks json keys correctly across multiple independent requests`() =
        runBlocking {
            val recorder = RecordingLogger()
            val engine = MockEngine { respond("ok", headers = headersOf(HttpHeaders.ContentType, "text/plain")) }
            val client =
                HttpClient(engine) {
                    install(KtorToCurl) {
                        logger = recorder
                        maskedJsonKeys = setOf("password", "token")
                    }
                }

            client.post("https://example.com/api/1") { setBody("""{"username":"alice","password":"secret1"}""") }
            client.post("https://example.com/api/2") { setBody("""{"username":"bob","password":"secret2"}""") }
            client.post("https://example.com/api/3") { setBody("""{"token":"tok-3","username":"carol"}""") }
            client.post("https://example.com/api/4") { setBody("""{"username":"dave"}""") }
            client.post("https://example.com/api/5") { setBody("""{"token":"tok-5","password":"secret5"}""") }

            assertEquals(5, recorder.logged.size)

            assertTrue(recorder.logged[0].contains(""""password":"[masked]""""))
            assertTrue(recorder.logged[0].contains(""""username":"alice""""))
            assertFalse(recorder.logged[0].contains("secret1"))

            assertTrue(recorder.logged[1].contains(""""password":"[masked]""""))
            assertTrue(recorder.logged[1].contains(""""username":"bob""""))
            assertFalse(recorder.logged[1].contains("secret2"))

            assertTrue(recorder.logged[2].contains(""""token":"[masked]""""))
            assertTrue(recorder.logged[2].contains(""""username":"carol""""))
            assertFalse(recorder.logged[2].contains("tok-3"))

            assertTrue(recorder.logged[3].contains(""""username":"dave""""))
            assertFalse(recorder.logged[3].contains("[masked]"), "Request with no masked keys should stay untouched")

            assertTrue(recorder.logged[4].contains(""""token":"[masked]""""))
            assertTrue(recorder.logged[4].contains(""""password":"[masked]""""))
            assertFalse(recorder.logged[4].contains("tok-5"))
            assertFalse(recorder.logged[4].contains("secret5"))
        }

    // ──────────────────────────────────────────────────────────────────────
    // Binary body: ZIP local-file-header magic bytes (distinct from gzip/PNG already covered)
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `generateCurl with zip magic bytes body emits binary omitted placeholder`() =
        runBlocking {
            // "PK\x03\x04" local file header signature followed by non-printable bytes.
            val zipBytes =
                byteArrayOf(
                    // "PK\x03\x04"
                    0x50, 0x4B, 0x03, 0x04,
                    0x14, 0x00, 0x00, 0x00, 0x08, 0x00,
                )
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/upload")
                    method = HttpMethod.Post
                    setBody(ByteArrayContent(zipBytes, ContentType.Application.Zip))
                }

            val result = generateCurl(request)

            assertEquals(
                "curl 'https://example.com/upload' --data-binary '[binary body omitted]'",
                result,
            )
        }

    @Test
    fun `PDF header body is mostly printable ASCII and is NOT flagged binary by looksBinary`() =
        runBlocking {
            // "%PDF-1.4\n" is entirely printable ASCII (plus a LF, which looksBinary() allows), so
            // the control/NUL-byte heuristic in looksBinary() does not trigger on it. This documents
            // that the binary heuristic is a best-effort signature/control-byte sniff, not a real
            // file-format detector: a PDF's *header* alone looks like ordinary text. (Real PDFs
            // contain compressed/binary stream data further in, which would trip the heuristic — this
            // test only exercises the header bytes.)
            val pdfHeader = "%PDF-1.4\n".encodeToByteArray()
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/upload")
                    method = HttpMethod.Post
                    setBody(ByteArrayContent(pdfHeader, ContentType.Application.Pdf))
                }

            val result = generateCurl(request, excludedHeaders = emptySet())

            // Renders as the literal decoded text, NOT the binary-omitted placeholder.
            assertFalse(result.contains("[binary body omitted]"), "PDF header alone should not trip the binary heuristic")
            assertTrue(result.contains("--data-binary '%PDF-1.4"), "Expected literal PDF header text in output, got: $result")
        }

    @Test
    fun `looksBinary only scans the first 512 bytes so a later NUL byte is not detected`() =
        runBlocking {
            // 600 printable bytes, then a NUL at index 550 — past looksBinary()'s take(512) window.
            // This documents (not asserts as desirable) a real detection-boundary limitation: a
            // binary marker outside the first 512 bytes renders as raw, undetected text instead of
            // the "[binary body omitted]" placeholder.
            val bytes = ByteArray(600) { 'a'.code.toByte() }
            bytes[550] = 0x00
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/upload")
                    method = HttpMethod.Post
                    setBody(ByteArrayContent(bytes, ContentType.Application.OctetStream))
                }

            val result = generateCurl(request)

            assertFalse(
                result.contains("[binary body omitted]"),
                "a NUL byte past the 512-byte scan window is not detected, got: $result",
            )
        }
}
