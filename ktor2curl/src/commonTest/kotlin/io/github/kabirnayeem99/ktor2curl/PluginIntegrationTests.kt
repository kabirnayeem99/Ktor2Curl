package io.github.kabirnayeem99.ktor2curl

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end coverage of the [KtorToCurl] Ktor plugin: install it on a real [HttpClient] backed by
 * [MockEngine], fire a request, and assert the configured [CurlLogger] receives the rendered curl.
 * This is the only path that exercises the plugin's onRequest hook and the [KtorToCurlConfig.shouldLog]
 * predicate (both invisible to the direct `generateCurl` tests).
 */
class PluginIntegrationTests {
    /** Collects every curl string the plugin emits. */
    private class RecordingLogger : CurlLogger {
        val logged = mutableListOf<String>()

        override suspend fun log(curl: String) {
            logged += curl
        }
    }

    private fun client(configure: KtorToCurlConfig.() -> Unit): Pair<HttpClient, RecordingLogger> {
        val recorder = RecordingLogger()
        val engine = MockEngine { respond("ok", headers = headersOf(HttpHeaders.ContentType, "text/plain")) }
        val client =
            HttpClient(engine) {
                install(KtorToCurl) {
                    logger = recorder
                    configure()
                }
            }
        return client to recorder
    }

    @Test
    fun `installed plugin logs curl for a GET request`() =
        runBlocking {
            val (client, recorder) = client { }

            client.get("https://example.com/api")

            assertEquals(1, recorder.logged.size)
            // Ktor adds a default `Accept: */*` header during the request pipeline; the plugin runs
            // afterward (send pipeline), so the rendered curl reflects what is actually sent.
            assertEquals("curl -H 'Accept: */*' 'https://example.com/api'", recorder.logged.single())
        }

    @Test
    fun `installed plugin logs curl with body for a POST request`() =
        runBlocking {
            val (client, recorder) = client { }

            client.post("https://example.com/api") { setBody("key=value") }

            assertEquals(1, recorder.logged.size)
            assertTrue(
                recorder.logged.single().endsWith("-d 'key=value'"),
                "expected -d body, got: ${recorder.logged.single()}",
            )
        }

    /** Mimics ContentNegotiation: serializes the data class to JSON in the same request-pipeline
     * Transform phase a real content converter uses, so the plugin must observe the transformed
     * body, not the raw object. */
    private data class JsonBody(val id: Int, val name: String)

    private val fakeNegotiation =
        createClientPlugin("FakeJsonNegotiation") {
            client.requestPipeline.intercept(HttpRequestPipeline.Transform) { body ->
                if (body is JsonBody) {
                    proceedWith(
                        TextContent("""{"id":${body.id},"name":"${body.name}"}""", ContentType.Application.Json),
                    )
                }
            }
        }

    @Test
    fun `data class body is rendered as its serialized content not toString`() =
        runBlocking {
            val recorder = RecordingLogger()
            val engine = MockEngine { respond("ok", headers = headersOf(HttpHeaders.ContentType, "text/plain")) }
            val client =
                HttpClient(engine) {
                    install(fakeNegotiation)
                    install(KtorToCurl) { logger = recorder }
                }

            client.post("https://example.com/api") { setBody(JsonBody(7, "ktor")) }

            val curl = recorder.logged.single()
            assertTrue(
                curl.contains("""-d '{"id":7,"name":"ktor"}'"""),
                "expected serialized JSON body, got: $curl",
            )
            assertTrue(!curl.contains("JsonBody"), "data class toString leaked into curl: $curl")
        }

    @Test
    fun `shouldLog returning false suppresses logging entirely`() =
        runBlocking {
            val (client, recorder) = client { shouldLog = { false } }

            client.get("https://example.com/api")

            assertTrue(recorder.logged.isEmpty(), "shouldLog=false must log nothing, got: ${recorder.logged}")
        }

    @Test
    fun `shouldLog predicate filters by request URL`() =
        runBlocking {
            val (client, recorder) = client { shouldLog = { it.url.host == "allowed.com" } }

            client.get("https://blocked.com/api")
            client.get("https://allowed.com/api")

            assertEquals(1, recorder.logged.size)
            assertEquals("curl -H 'Accept: */*' 'https://allowed.com/api'", recorder.logged.single())
        }
}
