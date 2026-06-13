package io.github.kabirnayeem99.ktor2curl

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.utils.io.core.toByteArray
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MultipartEdgeCaseTests {
    @Test
    fun `detectBoundary should handle boundaries with trailing dashes and preamble`() {
        val multipartBody =
            """
            This is preamble text that should be ignored
            --boundary-123
            Content-Disposition: form-data; name="field"

            value
            --boundary-123--
            """.trimIndent()

        val boundary = detectBoundary(multipartBody)
        assertEquals("boundary-123", boundary)
    }

    @Test
    fun `detectBoundary should handle boundaries with leading spaces`() {
        // Use a string where the first line specifically starts with spaces and isn't affected by trimIndent as much
        val multipartBody = "  --boundary-with-spaces\nContent-Disposition: form-data; name=\"field\"\n\nvalue\n--boundary-with-spaces--"

        val boundary = detectBoundary(multipartBody)
        assertEquals("boundary-with-spaces", boundary)
    }

    @Test
    fun `detectBoundary should handle boundaries with trailing spaces`() {
        val multipartBody =
            """
            --boundary-trailing   
            Content-Disposition: form-data; name="field"

            value
            --boundary-trailing--
            """.trimIndent()

        val boundary = detectBoundary(multipartBody)
        assertEquals("boundary-trailing", boundary)
    }

    @Test
    fun `renderFormParts should handle non-ASCII characters in metadata`() {
        val boundary = "boundary"
        val multipartBody =
            """
            --boundary
            Content-Disposition: form-data; name="résumé"; filename="nâim.pdf"
            Content-Type: application/pdf

            binary-content
            --boundary--
            """.trimIndent()

        val parts = renderFormParts(multipartBody, boundary)
        assertEquals(1, parts.size)
        assertEquals("résumé=@nâim.pdf;type=application/pdf", parts[0])
    }

    @Test
    fun `generateCurl with non-ASCII filenames should be handled correctly`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com")
                    method = HttpMethod.Post
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append(
                                    "file",
                                    "content".toByteArray(),
                                    Headers.build {
                                        append(HttpHeaders.ContentType, "application/pdf")
                                        append(HttpHeaders.ContentDisposition, "filename=\"résumé.pdf\"")
                                    },
                                )
                            },
                        ),
                    )
                }

            val result = generateCurl(request, excludedHeaders = setOf("Content-Type"))

            assertTrue(result.contains("-F 'file=@résumé.pdf;type=application/pdf'"), "Non-ASCII filename not rendered correctly: $result")
        }

    @Test
    fun `renderFormParts should return empty list if boundary not found`() {
        val multipartBody = "Just some text without boundaries"
        val parts = renderFormParts(multipartBody, "missing-boundary")
        assertTrue(parts.isEmpty(), "Should return empty list for missing boundary")
    }
}
