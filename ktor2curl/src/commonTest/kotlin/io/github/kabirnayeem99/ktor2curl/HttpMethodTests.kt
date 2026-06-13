package io.github.kabirnayeem99.ktor2curl

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.HttpMethod
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * HTTP method rendering: which methods force an explicit `-X` and how the method token is
 * normalized. curl infers GET (no body) / POST (body) implicitly, so `-X` should appear only for
 * everything else.
 */
class HttpMethodTests {
    private fun request(
        method: HttpMethod,
        body: String? = null,
    ) = HttpRequestBuilder().apply {
        url("https://example.com/api")
        this.method = method
        if (body != null) setBody(body)
    }

    @Test
    fun `PUT renders -X PUT`() =
        runBlocking {
            val result = generateCurl(request(HttpMethod.Put))
            assertEquals("curl -X PUT 'https://example.com/api'", result)
        }

    @Test
    fun `DELETE renders -X DELETE`() =
        runBlocking {
            val result = generateCurl(request(HttpMethod.Delete))
            assertEquals("curl -X DELETE 'https://example.com/api'", result)
        }

    @Test
    fun `PATCH renders -X PATCH`() =
        runBlocking {
            val result = generateCurl(request(HttpMethod.Patch))
            assertEquals("curl -X PATCH 'https://example.com/api'", result)
        }

    @Test
    fun `lowercase method is normalized to uppercase`() =
        runBlocking {
            val result = generateCurl(request(HttpMethod("get")))
            // GET with no body is the curl default, so it normalizes away rather than printing `-X get`.
            assertEquals("curl 'https://example.com/api'", result)
        }

    @Test
    fun `mixed-case non-default method is uppercased in -X`() =
        runBlocking {
            val result = generateCurl(request(HttpMethod("PaTcH")))
            assertTrue(result.contains("-X PATCH"), "expected -X PATCH, got: $result")
            assertFalse(result.contains("PaTcH"), "method must be normalized, got: $result")
        }

    @Test
    fun `-X omitted for GET without body`() =
        runBlocking {
            val result = generateCurl(request(HttpMethod.Get))
            assertFalse(result.contains("-X"), "GET without body must not emit -X, got: $result")
        }

    @Test
    fun `-X omitted for POST with body`() =
        runBlocking {
            val result = generateCurl(request(HttpMethod.Post, body = "key=value"))
            assertFalse(result.contains("-X"), "POST with body must not emit -X, got: $result")
        }

    @Test
    fun `-X present for PUT even with body`() =
        runBlocking {
            val result = generateCurl(request(HttpMethod.Put, body = "key=value"))
            assertTrue(result.contains("-X PUT"), "PUT must emit -X PUT, got: $result")
        }
}
