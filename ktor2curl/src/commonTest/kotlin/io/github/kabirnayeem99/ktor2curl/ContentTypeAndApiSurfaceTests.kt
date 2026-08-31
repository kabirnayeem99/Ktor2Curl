package io.github.kabirnayeem99.ktor2curl

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 1: real-world content-type shapes a library consumer actually sends (form-urlencoded,
 * JSON array roots, unicode-escaped JSON, pretty-printed JSON, GraphQL, CSV, HTML), plus direct
 * coverage of the two public entry points that previously had zero direct tests: the
 * [HttpRequestBuilder.toCurl] extension and the legacy 2-arg [generateCurl] overload.
 */
class ContentTypeAndApiSurfaceTests {
    @Test
    fun `form urlencoded body with a single field renders via data-binary`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody("username=alice")
                }

            val result = generateCurl(request, CurlOptions())

            assertEquals(
                "curl -H 'Content-Type: application/x-www-form-urlencoded' 'https://example.com/api' " +
                    "--data-binary 'username=alice'",
                result,
            )
        }

    @Test
    fun `form urlencoded body with multiple fields renders via data-binary`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody("username=alice&password=hunter2&remember=true")
                }

            val result = generateCurl(request, CurlOptions())

            assertEquals(
                "curl -H 'Content-Type: application/x-www-form-urlencoded' 'https://example.com/api' " +
                    "--data-binary 'username=alice&password=hunter2&remember=true'",
                result,
            )
        }

    @Test
    fun `form urlencoded body with repeated keys renders verbatim`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody("tag=kotlin&tag=ktor&tag=curl")
                }

            val result = generateCurl(request, CurlOptions())

            assertEquals(
                "curl -H 'Content-Type: application/x-www-form-urlencoded' 'https://example.com/api' " +
                    "--data-binary 'tag=kotlin&tag=ktor&tag=curl'",
                result,
            )
        }

    @Test
    fun `json array root body renders verbatim`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    contentType(ContentType.Application.Json)
                    setBody("""[{"a":1},{"a":2}]""")
                }

            val result = generateCurl(request, CurlOptions())

            assertEquals(
                "curl -H 'Content-Type: application/json' 'https://example.com/api' " +
                    "--data-binary '[{\"a\":1},{\"a\":2}]'",
                result,
            )
        }

    @Test
    fun `json body with nested array of objects renders verbatim`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    contentType(ContentType.Application.Json)
                    setBody("""{"items":[{"id":1,"tags":["a","b"]},{"id":2,"tags":[]}]}""")
                }

            val result = generateCurl(request, CurlOptions())

            assertEquals(
                "curl -H 'Content-Type: application/json' 'https://example.com/api' " +
                    "--data-binary '{\"items\":[{\"id\":1,\"tags\":[\"a\",\"b\"]},{\"id\":2,\"tags\":[]}]}'",
                result,
            )
        }

    @Test
    fun `json body with unicode escape sequences is preserved verbatim`() =
        runBlocking {
            // The body literally contains the characters backslash-u-0-0-e-9 (a \u-escaped "é"),
            // not an actual é character — this must pass through the generator byte-for-byte.
            val jsonBody = "{\"name\":\"A\\u00e9B\"}"
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    contentType(ContentType.Application.Json)
                    setBody(jsonBody)
                }

            val result = generateCurl(request, CurlOptions())

            assertEquals(
                "curl -H 'Content-Type: application/json' 'https://example.com/api' --data-binary '$jsonBody'",
                result,
            )
            assertEquals(true, result.contains("\\u00e9"))
        }

    @Test
    fun `pretty printed multiline json body uses data-binary and preserves newlines verbatim`() =
        runBlocking {
            val prettyJson = "{\n  \"a\": 1,\n  \"b\": 2\n}"
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    contentType(ContentType.Application.Json)
                    setBody(prettyJson)
                }

            val result = generateCurl(request, CurlOptions())

            assertEquals(
                "curl -H 'Content-Type: application/json' 'https://example.com/api' --data-binary '$prettyJson'",
                result,
            )
            // --data-binary (not -d) is required: -d silently strips CR/LF, which would desync the
            // rendered command from the actual multi-line body sent on the wire.
            assertEquals(true, result.contains("--data-binary"))
            assertEquals(false, result.contains(" -d "))
            assertEquals(true, result.contains("\n  \"a\": 1"))
        }

    @Test
    fun `pretty printed json body with CRLF line endings preserves them verbatim`() =
        runBlocking {
            val prettyJson = "{\r\n  \"a\": 1\r\n}"
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    contentType(ContentType.Application.Json)
                    setBody(prettyJson)
                }

            val result = generateCurl(request, CurlOptions())

            assertEquals(
                "curl -H 'Content-Type: application/json' 'https://example.com/api' --data-binary '$prettyJson'",
                result,
            )
            assertEquals(true, result.contains("\r\n"))
        }

    @Test
    fun `graphql shaped post body renders verbatim`() =
        runBlocking {
            val graphqlBody =
                """{"query":"query GetUser(${'$'}id: ID!) { user(id: ${'$'}id) { name email } }",""" +
                    """"variables":{"id":"42"}}"""
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/graphql")
                    method = HttpMethod.Post
                    contentType(ContentType.Application.Json)
                    setBody(graphqlBody)
                }

            val result = generateCurl(request, CurlOptions())

            assertEquals(
                "curl -H 'Content-Type: application/json' 'https://example.com/graphql' --data-binary '$graphqlBody'",
                result,
            )
        }

    @Test
    fun `csv text body with quoted comma and embedded newline is treated as ordinary text`() =
        runBlocking {
            val csvBody = "name,note\r\n\"Doe, Jane\",\"Line one\nLine two\"\r\n"
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/upload")
                    method = HttpMethod.Post
                    contentType(ContentType.Text.CSV)
                    setBody(csvBody)
                }

            val result = generateCurl(request, CurlOptions())

            assertEquals(
                "curl -H 'Content-Type: text/csv' 'https://example.com/upload' --data-binary '$csvBody'",
                result,
            )
        }

    @Test
    fun `html text body with nested tags script and entities renders verbatim`() =
        runBlocking {
            val htmlBody =
                "<html><body><p>Tom &amp; Jerry</p><script>alert('hi')</script></body></html>"
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/render")
                    method = HttpMethod.Post
                    contentType(ContentType.Text.Html)
                    setBody(htmlBody)
                }

            val result = generateCurl(request, CurlOptions())

            // The single quote inside alert('hi') must be POSIX-escaped as '\'' by the default shell.
            // Hardcoded (not derived via String.quoted()) so a broken quoting implementation can't
            // pass by producing the same wrong output on both sides of the assertion.
            val expectedQuotedBody =
                "'<html><body><p>Tom &amp; Jerry</p><script>alert('\\''hi'\\'')</script></body></html>'"
            assertEquals(
                "curl -H 'Content-Type: text/html' 'https://example.com/render' --data-binary $expectedQuotedBody",
                result,
            )
        }

    @Serializable
    private data class Address(val city: String, val zip: String)

    @Serializable
    private data class Order(val id: Int, val address: Address, val items: List<String>)

    private class RecordingLogger : CurlLogger {
        val logged = mutableListOf<String>()

        override suspend fun log(curl: String) {
            logged += curl
        }
    }

    @Test
    fun `data class body with nested data class and list field is rendered as real JSON`() =
        runBlocking {
            val recorder = RecordingLogger()
            val engine = MockEngine { respond("{}", headers = headersOf(HttpHeaders.ContentType, "application/json")) }
            val client =
                HttpClient(engine) {
                    install(ContentNegotiation) { json() }
                    install(KtorToCurl) { logger = recorder }
                }

            val order = Order(id = 7, address = Address(city = "Dhaka", zip = "1207"), items = listOf("book", "pen"))

            client.post("https://example.com/orders") {
                contentType(ContentType.Application.Json)
                setBody(order)
            }

            assertEquals(1, recorder.logged.size, "expected exactly one logged curl")
            val curl = recorder.logged.single()

            assertTrue(!curl.contains("Order("), "data class toString leaked into curl: $curl")
            assertTrue(
                curl.contains(
                    """--data-binary '{"id":7,"address":{"city":"Dhaka","zip":"1207"},"items":["book","pen"]}'""",
                ),
                "expected real JSON with nested object and list, got: $curl",
            )
        }

    @Test
    fun `toCurl extension with default options matches generateCurl output`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    contentType(ContentType.Application.Json)
                    setBody("""{"a":1}""")
                }

            val viaExtension = request.toCurl()
            val viaGenerateCurl = generateCurl(request, CurlOptions())

            assertEquals(viaGenerateCurl, viaExtension)
            assertEquals(
                "curl -H 'Content-Type: application/json' 'https://example.com/api' --data-binary '{\"a\":1}'",
                viaExtension,
            )
        }

    @Test
    fun `toCurl extension with custom options matches generateCurl output`() =
        runBlocking {
            // Body contains a single quote so PowerShell's doubling ('' , not POSIX's '\'') is
            // actually exercised, not just declared via the shell option.
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    contentType(ContentType.Application.Json)
                    setBody("""{"note":"it's fine"}""")
                    headers.append("Authorization", "Bearer secret-token")
                }

            val options = CurlOptions(excludedHeaders = setOf("Authorization"), shell = CurlShell.PowerShell)
            val viaExtension = request.toCurl(options)
            val viaGenerateCurl = generateCurl(request, options)

            assertEquals(viaGenerateCurl, viaExtension)
            assertEquals(false, viaExtension.contains("secret-token"))
            assertEquals(
                "curl -H 'Content-Type: application/json' 'https://example.com/api' --data-binary " +
                    "'{\"note\":\"it''s fine\"}'",
                viaExtension,
            )
        }

    @Test
    fun `form urlencoded body built via FormDataContent renders via data-binary with no inferred Content-Type`() =
        runBlocking {
            // FormDataContent is a ByteArrayContent, not TextContent, and no explicit Content-Type
            // header was set — the inferred-Content-Type fallback in generateCurl is deliberately
            // limited to TextContent, so unlike a serialized ContentNegotiation body, this body
            // renders with NO -H Content-Type line at all. This is a real, deliberate asymmetry
            // between the setBody(String) path (tests above) and the real FormDataContent API.
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody(
                        io.ktor.client.request.forms.FormDataContent(
                            io.ktor.http.Parameters.build {
                                append("username", "alice")
                                append("remember", "true")
                            },
                        ),
                    )
                }

            val result = generateCurl(request, CurlOptions())

            assertEquals(
                "curl 'https://example.com/api' --data-binary 'username=alice&remember=true'",
                result,
            )
        }

    @Test
    fun `maskedJsonKeys on graphql body also redacts an occurrence inside a string value`() =
        runBlocking {
            // redactJson is regex-based, not a real JSON parse: a masked key name that also appears
            // as `"key": value` shape inside a string value (here, embedded inside the GraphQL query
            // text itself) gets redacted too. This locks in that documented tradeoff.
            val graphqlBody =
                """{"query":"query { user { "id": 1 } }","variables":{"id":"42"}}"""
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/graphql")
                    method = HttpMethod.Post
                    contentType(ContentType.Application.Json)
                    setBody(graphqlBody)
                }

            val result = generateCurl(request, CurlOptions(maskedJsonKeys = setOf("id")))

            assertEquals(2, Regex(""""\[masked\]"""").findAll(result).count())
            assertEquals(false, result.contains("\"id\": 1") || result.contains("\"id\":\"42\""))
        }

    @Test
    fun `legacy two arg generateCurl overload matches CurlOptions based overload`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    contentType(ContentType.Application.Json)
                    setBody("""{"secret":"abc","name":"bob"}""")
                    headers.append("Authorization", "Bearer secret-token")
                    headers.append("X-Api-Key", "abc123")
                }

            val excluded = setOf("Authorization")
            val masked = setOf("X-Api-Key")

            val legacyResult = generateCurl(request, excludedHeaders = excluded, maskedHeaders = masked)
            val explicitOptionsResult =
                generateCurl(request, CurlOptions(excludedHeaders = excluded, maskedHeaders = masked))

            assertEquals(explicitOptionsResult, legacyResult)
            assertEquals(false, legacyResult.contains("secret-token"))
            assertEquals(true, legacyResult.contains("X-Api-Key: [masked]"))
        }

    @Test
    fun `legacy generateCurl overload with default empty sets matches CurlOptions defaults`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Get
                }

            val legacyResult = generateCurl(request)
            val explicitOptionsResult = generateCurl(request, CurlOptions())

            assertEquals(explicitOptionsResult, legacyResult)
            assertEquals("curl 'https://example.com/api'", legacyResult)
        }
}
