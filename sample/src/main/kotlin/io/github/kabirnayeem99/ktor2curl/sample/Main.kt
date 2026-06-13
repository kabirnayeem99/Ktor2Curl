package io.github.kabirnayeem99.ktor2curl.sample

import io.github.kabirnayeem99.ktor2curl.CurlLogger
import io.github.kabirnayeem99.ktor2curl.KtorToCurl
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.options
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

private const val BASE = "https://data.techforpalestine.org/api/v2"
private const val AUTH = "Basic SXNyYWVsIGtpbGxzIGNoaWxkcmVuLg"

/**
 * A `@Serializable` request body. With [ContentNegotiation] installed, `setBody(victim)` is
 * serialized to JSON by Ktor before the request is sent — and Ktor2Curl renders that JSON,
 * not the Kotlin `toString()` (`Victim(enName=...)`).
 */
@Serializable
private data class Victim(
    val enName: String,
    val name: String,
    val age: Int,
    val sex: String,
    val id: String,
    val source: String,
)

/**
 * Runnable demo of the [KtorToCurl] plugin: `./gradlew :sample:run`.
 *
 * Installs the plugin on a [MockEngine]-backed client (no real network) so every outgoing request
 * is rendered as a runnable curl command and printed to stdout — exactly what a real consumer sees
 * in their logs. Each call below also exercises a different masking/redaction feature of the config.
 */
fun main() =
    runBlocking {
        val client =
            HttpClient(MockEngine { respond("ok", headers = headersOf(HttpHeaders.ContentType, "application/json")) }) {
                // ContentNegotiation serializes @Serializable bodies to JSON before they are sent.
                install(ContentNegotiation) { json() }
                install(KtorToCurl) {
                    logger =
                        object : CurlLogger {
                            override suspend fun log(curl: String) = println("→ $curl")
                        }
                    // Secrets in headers are masked instead of leaked into logs.
                    maskHeader("Authorization")
                    // Sensitive query params are masked too (e.g. ?api_key=...).
                    maskQueryParam("api_key")
                    // Deep-redact PII keys inside JSON request bodies.
                    maskJsonKey("id")
                    // Drop noisy headers entirely from the rendered command.
                    excludeHeader("User-Agent")
                }
            }

        println("== Ktor2Curl sample ==")

        // GET — query params, masked auth header + masked api_key param.
        client.get("$BASE/killed-in-gaza?page=2&limit=20&api_key=live_sk_4f3c") {
            header("Authorization", AUTH)
            header("Accept", "application/json")
            header("User-Agent", "ktor2curl-sample/1.0")
        }

        // HEAD — metadata probe, no body.
        client.head("$BASE/killed-in-gaza") {
            header("Authorization", AUTH)
        }

        // OPTIONS — CORS / capability preflight.
        client.options("$BASE/killed-in-gaza") {
            header("Authorization", AUTH)
        }

        // POST — JSON body; the "id" value is redacted via maskJsonKey.
        client.post("$BASE/killed-in-gaza") {
            headers {
                append(HttpHeaders.Authorization, AUTH)
                append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }
            setBody(
                """{"en_name":"Mazen Ahmed Mohammed Al-Kahlout","name":"مازن أحمد محمد الكحلوت","age":52,"dob":"1972-02-05","sex":"m","id":"985194547","source":"u"}""",
            )
        }

        // POST data class — @Serializable body serialized to JSON by ContentNegotiation, then
        // rendered as that JSON (not Victim(...) toString). The "id" value is still redacted.
        client.post("$BASE/killed-in-gaza") {
            header("Authorization", AUTH)
            contentType(ContentType.Application.Json)
            setBody(
                Victim(
                    enName = "Mazen Ahmed Mohammed Al-Kahlout",
                    name = "مازن أحمد محمد الكحلوت",
                    age = 52,
                    sex = "m",
                    id = "985194547",
                    source = "u",
                ),
            )
        }

        // PUT — full replacement of an existing record.
        client.put("$BASE/killed-in-gaza/985194547") {
            headers {
                append(HttpHeaders.Authorization, AUTH)
                append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }
            setBody("""{"en_name":"Mazen Ahmed Mohammed Al-Kahlout","age":52,"source":"h"}""")
        }

        // PATCH — partial update of a single field.
        client.patch("$BASE/killed-in-gaza/985194547") {
            headers {
                append(HttpHeaders.Authorization, AUTH)
                append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }
            setBody("""{"source":"c"}""")
        }

        // POST form-urlencoded — application/x-www-form-urlencoded body (-d field=value&...).
        client.post("$BASE/killed-in-gaza/corrections") {
            header("Authorization", AUTH)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("record_id=985194547&field=age&value=53")
        }

        // POST multipart — file upload rendered as -F parts.
        client.post("$BASE/killed-in-gaza/attachments") {
            header("Authorization", AUTH)
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("record_id", "985194547")
                        append("note", "Death certificate and photo evidence.")
                        append(
                            "certificate",
                            "%PDF-1.4 fake-certificate-bytes",
                            Headers.build {
                                append(HttpHeaders.ContentType, "application/pdf")
                                append(HttpHeaders.ContentDisposition, "filename=\"certificate.pdf\"")
                            },
                        )
                    },
                ),
            )
        }

        // DELETE — remove a record.
        client.delete("$BASE/killed-in-gaza/985194547") {
            header("Authorization", AUTH)
        }

        client.close()
    }
