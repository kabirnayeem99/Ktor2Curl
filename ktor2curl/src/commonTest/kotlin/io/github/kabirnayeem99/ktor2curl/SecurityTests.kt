package io.github.kabirnayeem99.ktor2curl

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.HttpMethod
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class SecurityTests {
    @Test
    fun `shellQuoted should neutralize single quotes`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com")
                    method = HttpMethod.Post
                    setBody("user's-data")
                }

            val result = generateCurl(request)
            // Expecting: curl 'https://example.com' -d 'user'\''s-data'

            assertEquals("curl 'https://example.com' -d 'user'\\''s-data'", result)
        }

    @Test
    fun `shellQuoted should neutralize command substitution`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com")
                    method = HttpMethod.Post
                    setBody("$(id)")
                }

            val result = generateCurl(request)
            assertEquals("curl 'https://example.com' -d '$(id)'", result)
        }

    @Test
    fun `shellQuoted should neutralize backticks`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com")
                    method = HttpMethod.Post
                    setBody("`id`")
                }

            val result = generateCurl(request)
            assertEquals("curl 'https://example.com' -d '`id`'", result)
        }

    @Test
    fun `shellQuoted should neutralize pipes`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com")
                    method = HttpMethod.Post
                    setBody("data | malicious-cmd")
                }

            val result = generateCurl(request)
            assertEquals("curl 'https://example.com' -d 'data | malicious-cmd'", result)
        }

    @Test
    fun `shellQuoted should neutralize ampersands`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com")
                    method = HttpMethod.Post
                    setBody("data & malicious-cmd")
                }

            val result = generateCurl(request)
            assertEquals("curl 'https://example.com' -d 'data & malicious-cmd'", result)
        }

    @Test
    fun `shellQuoted should neutralize newlines`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com")
                    method = HttpMethod.Post
                    setBody("line1\nline2")
                }

            val result = generateCurl(request)
            // In single quotes, newlines are preserved as literal newlines.
            assertEquals("curl 'https://example.com' -d 'line1\nline2'", result)
        }

    @Test
    fun `headers should be safe from shell injection`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com")
                    method = HttpMethod.Get
                    headers.append("X-Injected", "$(id)")
                }

            val result = generateCurl(request)
            // Now it should produce: -H 'X-Injected: $(id)'
            assertTrue(result.contains("-H 'X-Injected: $(id)'"), "Header should be single-quoted and safe")
        }

    @Test
    fun `multipart form data should be shell quoted`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com")
                    method = HttpMethod.Post
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append("field", "value'with'quotes")
                                append("cmd", "$(whoami)")
                            },
                        ),
                    )
                }

            val result = generateCurl(request)

            assertTrue(result.contains("-F 'field=value'\\''with'\\''quotes'"))
            assertTrue(result.contains("-F 'cmd=$(whoami)'"))
        }
}

private fun assertTrue(
    actual: Boolean,
    message: String = "Expected true but was false",
) {
    if (!actual) throw AssertionError(message)
}

private fun assertFalse(
    actual: Boolean,
    message: String = "Expected false but was true",
) {
    if (actual) throw AssertionError(message)
}
