package io.github.kabirnayeem99.ktor2curl.sample

import io.github.kabirnayeem99.ktor2curl.CurlLogger
import io.github.kabirnayeem99.ktor2curl.KtorToCurl
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking

/**
 * Runnable demo of the [KtorToCurl] plugin: `./gradlew :sample:run`.
 *
 * Installs the plugin on a [MockEngine]-backed client (no real network) so every outgoing request
 * is rendered as a runnable curl command and printed to stdout — exactly what a real consumer sees
 * in their logs.
 */
fun main() =
    runBlocking {
        val client =
            HttpClient(MockEngine { respond("ok", headers = headersOf(HttpHeaders.ContentType, "application/json")) }) {
                install(KtorToCurl) {
                    logger =
                        object : CurlLogger {
                            override suspend fun log(curl: String) = println("→ $curl")
                        }
                    // Secrets in headers are masked instead of leaked into logs.
                    maskHeader("Authorization")
                }
            }

        println("== Ktor2Curl sample ==")

        client.get("https://api.example.com/users?page=2&limit=20") {
            header("Authorization", "Bearer super-secret-token")
            header("Accept", "application/json")
        }

        client.post("https://api.example.com/users") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Ada","role":"admin"}""")
        }

        client.delete("https://api.example.com/users/42") {
            header("Authorization", "Bearer super-secret-token")
        }

        client.close()
    }
