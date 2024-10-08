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
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.utils.io.core.toByteArray
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GenerateCurlTests {
    @Test
    fun `generateCurl with GET request and no headers`() = runBlocking {
        val request = HttpRequestBuilder().apply {
            url("https://example.com/api")
            method = HttpMethod.Get
        }

        val result = generateCurl(request, excludedHeaders = emptySet())

        assertEquals("curl -X GET \"https://example.com/api\"", result)
    }

    @Test
    fun `generateCurl with POST request and body`() = runBlocking {
        val request = HttpRequestBuilder().apply {
            url("https://example.com/api")
            method = HttpMethod.Post
            setBody("key=value")
        }

        val result = generateCurl(request, excludedHeaders = emptySet())

        assertEquals("curl -X POST \"https://example.com/api\" -d 'key=value'", result)
    }

    @Test
    fun `generateCurl with headers and excluded headers`() = runBlocking {
        val request = HttpRequestBuilder().apply {
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
            "curl -X GET -H \"User-Agent: KtorClient\" -H \"Content-Type: application/json\" \"https://example.com/api\"",
            result
        )
    }

    @Test
    fun `generateCurl with no Content-Type and onNoContentTypeHeader triggered`() = runBlocking {
        val request = HttpRequestBuilder().apply {
            url("https://example.com/api")
            method = HttpMethod.Post
            setBody("key=value")
        }

        val result = generateCurl(request, excludedHeaders = emptySet())

        // Since there is no Content-Type header, onNoContentTypeHeader should trigger but not add anything
        assertEquals("curl -X POST \"https://example.com/api\" -d 'key=value'", result)
    }

    @Test
    fun `generateCurl with Content-Type in body`() = runBlocking {
        val request = HttpRequestBuilder().apply {
            url("https://example.com/api")
            method = HttpMethod.Post
            contentType(ContentType.Application.Json)
            setBody("{ \"key\": \"value\" }")
        }

        val result = generateCurl(request, excludedHeaders = emptySet())

        assertEquals(
            "curl -X POST -H \"Content-Type: application/json\" \"https://example.com/api\" -d '{ \"key\": \"value\" }'",
            result
        )
    }

    @Test
    fun `generateCurl with multiple headers and excluded one`() = runBlocking {
        val request = HttpRequestBuilder().apply {
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
            "curl -X POST -H \"Content-Type: application/json\" -H \"User-Agent: KtorClient\" \"https://example.com/api\" -d '{ \"key\": \"value\" }'",
            result
        )
    }

    @Test
    fun `generateCurl with empty body`() = runBlocking {
        val request = HttpRequestBuilder().apply {
            url("https://example.com/api")
            method = HttpMethod.Post
            setBody("")
        }

        val result = generateCurl(request, excludedHeaders = emptySet())

        // No body content is added to the curl command
        assertEquals("curl -X POST \"https://example.com/api\"", result)
    }

    @Test
    fun `generateCurl with multiple values for the same header`() = runBlocking {
        val request = HttpRequestBuilder().apply {
            url("https://example.com/api")
            method = HttpMethod.Get
            headers {
                append("Accept", "application/json")
                append("Accept", "text/html")
            }
        }

        val result = generateCurl(request, excludedHeaders = emptySet())

        // Headers with multiple values should join them with "; "
        assertEquals(
            "curl -X GET -H \"Accept: application/json; text/html\" \"https://example.com/api\"",
            result
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
            excludedHeaders = excludedHeaders
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
            excludedHeaders = emptySet()
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
            excludedHeaders = emptySet()
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
            excludedHeaders = excludedHeaders
        )

        assertEquals(1, capturedHeaders.size)
        assertEquals("Content-Type", capturedHeaders[0].first)
        assertEquals(listOf("application/json"), capturedHeaders[0].second)
    }

    @Test
    fun `onHeaders should handle empty headers`() {
        val headersBuilder = HeadersBuilder()

        var noContentTypeHeaderInvoked = false
        val capturedHeaders = mutableListOf<Pair<String, List<String>>>()

        headersBuilder.onHeaders(
            onEachHeader = { key, values -> capturedHeaders.add(key to values) },
            onNoContentTypeHeader = { noContentTypeHeaderInvoked = true },
            excludedHeaders = emptySet()
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
    fun `onRequestBody with MultiPartFormDataContent should omit the request body`() {
        val formData = MultiPartFormDataContent(formData {
            append("key", "value")
        })
        var capturedBody = ""
        formData.onRequestBody { capturedBody = it }
        assertEquals("[request body omitted]", capturedBody)
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
        val unknownBody = object {
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