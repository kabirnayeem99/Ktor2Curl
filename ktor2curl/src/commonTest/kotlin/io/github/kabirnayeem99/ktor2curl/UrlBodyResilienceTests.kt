package io.github.kabirnayeem99.ktor2curl

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.HttpMethod
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** URL preservation and body-read resilience: the render must survive a body that fails to materialize. */
class UrlBodyResilienceTests {
    @Test
    fun `query parameters are preserved in order`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api?a=1&b=2&c=3")
                    method = HttpMethod.Get
                }

            val result = generateCurl(request)

            assertEquals("curl 'https://example.com/api?a=1&b=2&c=3'", result)
        }

    @Test
    fun `https scheme and host are rendered verbatim`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://api.example.com:8443/v1/resource")
                    method = HttpMethod.Get
                }

            val result = generateCurl(request)

            assertEquals("curl 'https://api.example.com:8443/v1/resource'", result)
        }

    /** A body object whose string materialization throws — must yield a placeholder, never crash. */
    private class ThrowingBody {
        override fun toString(): String = throw RuntimeException("simulated body read failure")
    }

    @Test
    fun `body read failure emits a placeholder instead of crashing`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody(ThrowingBody())
                }

            val result = generateCurl(request)

            assertTrue(result.contains("[body read failed]"), "expected body placeholder, got: $result")
            assertTrue(result.startsWith("curl "), "command must still render, got: $result")
        }
}
