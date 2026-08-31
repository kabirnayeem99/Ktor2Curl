package io.github.kabirnayeem99.ktor2curl

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.ChannelProvider
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.IllegalHeaderValueException
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Phase 3 edge-case sweep: header/URL/JSON-masking boundary correctness, extreme-length inputs,
 * and CancellationException propagation. Builds on [SecurityTests] (shell-injection payloads) —
 * these cases target genuinely different angles instead of duplicating it.
 */
class InputRobustnessTests {
    // --- header value with embedded newline / CRLF -------------------------------------------------

    @Test
    fun `appending a header value with an embedded CRLF is rejected by Ktor before generateCurl ever sees it`() {
        // Ktor's HeadersBuilder.append validates values against control characters (RFC 9110 header
        // field syntax) and throws immediately — a CRLF header-injection attempt like
        // "value\r\nX-Injected: evil" never reaches generateCurl at all, so there is no rendering
        // behavior to test here beyond locking in that Ktor itself is the line of defense.
        val builder =
            HttpRequestBuilder().apply {
                url("https://example.com")
                method = HttpMethod.Get
            }

        assertFailsWith<IllegalHeaderValueException> {
            builder.headers.append("X-Custom", "value\r\nX-Injected: evil")
        }
    }

    @Test
    fun `appending a header value that is only a bare newline is rejected by Ktor before generateCurl ever sees it`() {
        val builder =
            HttpRequestBuilder().apply {
                url("https://example.com")
                method = HttpMethod.Get
            }

        assertFailsWith<IllegalHeaderValueException> {
            builder.headers.append("X-Only-Newline", "\n")
        }
    }

    // --- maskUrl catch-fallback path -----------------------------------------------------------------

    @Test
    fun `maskUrl still redacts credentials via best-effort fallback when the reparse throws`() =
        runBlocking {
            // Ktor's HttpRequestBuilder.url(String) validates the port during parsing, but setting
            // url.host directly bypasses that validation entirely, so a non-numeric "port" segment
            // can end up embedded in the host. request.url.buildString() then serializes
            // "example.com:abc" verbatim, and maskUrl's internal URLBuilder(raw) reparse of that
            // string throws (invalid port), landing in the regex-based maskUrlBestEffort fallback.
            val request =
                HttpRequestBuilder().apply {
                    url("https://user:pass@example.com/path")
                    url.host = "example.com:abc"
                }

            // Must not crash even though masking was requested and the URL is malformed.
            val result = generateCurl(request, CurlOptions(maskUrlUserInfo = true))

            assertTrue(result.startsWith("curl "), "command must still render, got: $result")
            // The masking feature exists specifically so secrets don't leak into logs — the fallback
            // must not defeat that purpose just because the precise URLBuilder path failed.
            assertTrue(
                !result.contains("user:pass@"),
                "credentials must not leak through the fallback path, got: $result",
            )
            assertTrue(
                result.contains("https://[masked]@example.com:abc/path"),
                "expected best-effort masked URL, got: $result",
            )
        }

    @Test
    fun `maskUrl best-effort fallback masks the whole userinfo even with a stray extra at sign`() =
        runBlocking {
            // A malformed userinfo containing its own unencoded "@" (e.g. "user@name:pass@host") must
            // not leave the real password exposed after only the first "@" is masked — the fallback
            // backtracks to the LAST "@" before the authority ends, catching the whole segment.
            val request =
                HttpRequestBuilder().apply {
                    url("https://user@name:pass@example.com/path")
                    url.host = "example.com:abc"
                }

            val result = generateCurl(request, CurlOptions(maskUrlUserInfo = true))

            assertTrue(!result.contains("pass@"), "the real credential must not survive the fallback, got: $result")
            assertTrue(
                result.contains("https://[masked]@example.com:abc/path"),
                "expected the whole userinfo segment masked, got: $result",
            )
        }

    @Test
    fun `maskUrl best-effort fallback also redacts a matching query param`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/path?token=secret123&keep=me")
                    url.host = "example.com:abc"
                }

            val result = generateCurl(request, CurlOptions(maskedQueryParams = setOf("token")))

            assertTrue(!result.contains("secret123"), "masked query param value must not leak, got: $result")
            assertTrue(result.contains("token=[masked]"), "expected masked token param, got: $result")
            assertTrue(result.contains("keep=me"), "unrelated param must survive untouched, got: $result")
        }

    // --- maskedJsonKeys boundary correctness -----------------------------------------------------------

    @Test
    fun `maskedJsonKeys does not redact a key that merely starts with the configured key`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com")
                    method = HttpMethod.Post
                    setBody("""{"password":"secret","password_confirm":"abc"}""")
                }

            val options = CurlOptions(maskedJsonKeys = setOf("password"))
            val result = generateCurl(request, options)

            assertTrue(result.contains(""""password":"[masked]""""), "exact key must be redacted, got: $result")
            assertTrue(
                result.contains(""""password_confirm":"abc""""),
                "a longer key that only shares a prefix must NOT be redacted, got: $result",
            )
        }

    // --- extremely long values are not truncated (no maxBodyLength-style cap applies) --------------------

    @Test
    fun `extremely long header value is preserved verbatim without truncation`() =
        runBlocking {
            val longValue = "v".repeat(50_000)
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com")
                    method = HttpMethod.Get
                    headers.append("X-Long", longValue)
                }

            val result = generateCurl(request)

            assertTrue(result.contains("-H 'X-Long: $longValue'"), "long header value must not be truncated")
        }

    @Test
    fun `extremely long query parameter value is preserved verbatim without truncation`() =
        runBlocking {
            // A single very-long value, not many params: Ktor's own URL parser caps the number of
            // query parameters it will parse from a string (silently drops the rest past that count),
            // which is a Ktor-level parsing limit unrelated to generateCurl — this test isolates the
            // thing actually under test, that generateCurl itself applies no length cap to the URL.
            val longValue = "v".repeat(50_000)
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api?token=$longValue")
                    method = HttpMethod.Get
                }

            val result = generateCurl(request)

            assertTrue(result.contains(longValue), "long query value must not be truncated, got length ${result.length}")
        }

    // --- CancellationException must propagate, not be swallowed into a placeholder --------------------

    /** A body whose toString() simulates cancellation mid-materialization, not a generic failure. */
    private class CancellingBody {
        override fun toString(): String = throw CancellationException("simulated cancellation during body read")
    }

    @Test
    fun `CancellationException during body read is rethrown not swallowed into body read failed`() {
        val request =
            HttpRequestBuilder().apply {
                url("https://example.com/api")
                method = HttpMethod.Post
                setBody(CancellingBody())
            }

        assertFailsWith<CancellationException> {
            runBlocking { generateCurl(request) }
        }
    }

    @Test
    fun `CancellationException during multipart materialization is rethrown not swallowed into multipart body omitted`() {
        val content =
            MultiPartFormDataContent(
                formData {
                    append(
                        "file",
                        ChannelProvider(null) { throw CancellationException("simulated cancellation during multipart read") },
                    )
                },
            )

        assertFailsWith<CancellationException> {
            runBlocking { coroutineScope { parseMultipartFormParts(content) } }
        }
    }

    // --- shouldLog throwing: locks in actual (not necessarily desired) behavior ------------------------

    private class RecordingLogger : CurlLogger {
        val logged = mutableListOf<String>()

        override suspend fun log(curl: String) {
            logged += curl
        }
    }

    @Test
    fun `shouldLog predicate that throws propagates out of the request instead of being swallowed`() =
        runBlocking {
            val recorder = RecordingLogger()
            val engine = MockEngine { respond("ok", headers = headersOf(HttpHeaders.ContentType, "text/plain")) }
            val client =
                HttpClient(engine) {
                    install(KtorToCurl) {
                        logger = recorder
                        shouldLog = { throw IllegalStateException("boom from shouldLog") }
                    }
                }

            // The plugin installs on HttpSendPipeline.Monitoring with no try/catch around
            // shouldLog(request), so an exception there is not caught anywhere in the plugin and
            // propagates straight out of the client call — the request never completes and nothing
            // is logged. This test locks in that actual behavior.
            assertFailsWith<IllegalStateException> {
                client.get("https://example.com/api")
            }
            assertTrue(recorder.logged.isEmpty(), "nothing should have been logged before the throw, got: ${recorder.logged}")
        }
}
