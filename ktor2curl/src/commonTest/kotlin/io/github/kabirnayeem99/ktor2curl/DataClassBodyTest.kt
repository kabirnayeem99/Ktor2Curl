package io.github.kabirnayeem99.ktor2curl

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Reproduction of the real-world bug report: a `@Serializable` data-class body, serialized by
 * Ktor's [ContentNegotiation] plugin, was being rendered as its Kotlin `toString()`
 * ("BrowseBody(context=Context(...))") instead of the JSON actually sent on the wire.
 *
 * The plugin now runs on the send pipeline (after ContentNegotiation's Transform phase), so the
 * body it observes is the serialized [io.ktor.http.content.TextContent], not the raw object.
 */
class DataClassBodyTest {
    @Serializable
    private data class Client(
        val clientName: String,
        val clientVersion: String,
        val userAgent: String,
        val gl: String,
        val hl: String,
        val visitorData: String,
    )

    @Serializable
    private data class Context(val client: Client)

    @Serializable
    private data class FormData(val selectedValues: List<String>)

    @Serializable
    private data class BrowseBody(
        val context: Context,
        val browseId: String,
        val formData: FormData,
    )

    private class RecordingLogger : CurlLogger {
        val logged = mutableListOf<String>()

        override suspend fun log(curl: String) {
            logged += curl
        }
    }

    @Test
    fun `serializable data class body is rendered as JSON not toString`() =
        runBlocking {
            val recorder = RecordingLogger()
            val engine = MockEngine { respond("{}", headers = headersOf(HttpHeaders.ContentType, "application/json")) }
            val client =
                HttpClient(engine) {
                    install(ContentNegotiation) { json() }
                    install(KtorToCurl) { logger = recorder }
                }

            val body =
                BrowseBody(
                    context =
                        Context(
                            client =
                                Client(
                                    clientName = "WEB_REMIX",
                                    clientVersion = "1.20220606.03.00",
                                    userAgent =
                                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                                            "(KHTML, like Gecko) Chrome/74.0.3729.157 Safari/537.36",
                                    gl = "VN",
                                    hl = "vi-VN",
                                    visitorData = "CgtsZG1ySnZiQWtSbyiMjuGSBg%3D%3D",
                                ),
                        ),
                    browseId = "VLPL_pRLf9CPBPEvMTXA4rb-RNECtiH9C2NP",
                    formData = FormData(selectedValues = listOf("VN")),
                )

            client.post("https://music.youtube.com/youtubei/v1/browse?prettyPrint=false&alt=json") {
                contentType(ContentType.Application.Json)
                header("X-Goog-Api-Format-Version", "1")
                header("X-YouTube-Client-Name", "1")
                header("X-YouTube-Client-Version", "1.20220606.03.00")
                header("x-origin", "https://music.youtube.com")
                setBody(body)
            }

            assertEquals(1, recorder.logged.size, "expected exactly one logged curl")
            val curl = recorder.logged.single()

            // The raw data-class toString must NOT leak into the rendered command.
            assertTrue(!curl.contains("BrowseBody("), "data class toString leaked into curl: $curl")

            // The body must be the JSON ContentNegotiation produced.
            val expectedJson =
                """{"context":{"client":{"clientName":"WEB_REMIX","clientVersion":"1.20220606.03.00",""" +
                    """"userAgent":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 """ +
                    """(KHTML, like Gecko) Chrome/74.0.3729.157 Safari/537.36","gl":"VN","hl":"vi-VN",""" +
                    """"visitorData":"CgtsZG1ySnZiQWtSbyiMjuGSBg%3D%3D"}},""" +
                    """"browseId":"VLPL_pRLf9CPBPEvMTXA4rb-RNECtiH9C2NP",""" +
                    """"formData":{"selectedValues":["VN"]}}"""
            assertTrue(curl.contains("-d '$expectedJson'"), "expected JSON -d body, got: $curl")

            // The custom headers and the URL are present.
            assertTrue(curl.contains("-H 'Content-Type: application/json'"), "missing Content-Type: $curl")
            assertTrue(curl.contains("-H 'X-YouTube-Client-Name: 1'"), "missing client-name header: $curl")
            assertTrue(curl.contains("-H 'x-origin: https://music.youtube.com'"), "missing x-origin header: $curl")
            assertTrue(
                curl.contains("'https://music.youtube.com/youtubei/v1/browse?prettyPrint=false&alt=json'"),
                "missing URL: $curl",
            )
        }
}
