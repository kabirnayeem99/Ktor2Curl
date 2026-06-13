package io.github.kabirnayeem99.ktor2curl

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.headers
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.HttpMethod
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// ──────────────────────────────────────────────────────────────────────────────
// 1. maskPlaceholder — custom placeholder
// ──────────────────────────────────────────────────────────────────────────────

class MaskPlaceholderTests {
    @Test
    fun `custom maskPlaceholder replaces default masked placeholder for masked header`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Get
                    headers { append("Authorization", "Bearer secret") }
                }

            val options =
                CurlOptions(
                    maskedHeaders = setOf("Authorization"),
                    maskPlaceholder = "REDACTED",
                )
            val result = request.toCurl(options)

            assertTrue(result.contains("-H 'Authorization: REDACTED'"), "Expected REDACTED placeholder, got: $result")
            assertFalse(result.contains("-H 'Authorization: [masked]'"), "Should not contain default masked placeholder")
        }

    @Test
    fun `custom maskPlaceholder is used for masked query param`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api?session=abc123&page=2")
                    method = HttpMethod.Get
                }

            val options =
                CurlOptions(
                    maskedQueryParams = setOf("session"),
                    maskPlaceholder = "HIDDEN",
                )
            val result = request.toCurl(options)

            assertTrue(result.contains("session=HIDDEN"), "Expected custom placeholder, got: $result")
            assertFalse(result.contains("abc123"), "Secret value should not appear in output")
        }
}

// ──────────────────────────────────────────────────────────────────────────────
// 2. Windows quoting (CurlShell.WindowsCmd)
// ──────────────────────────────────────────────────────────────────────────────

class WindowsCmdQuotingTests {
    @Test
    fun `WindowsCmd shell wraps body in double quotes`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody("hello world")
                }

            val options = CurlOptions(shell = CurlShell.WindowsCmd)
            val result = request.toCurl(options)

            assertTrue(result.contains("-d \"hello world\""), "Expected double-quoted body, got: $result")
        }

    @Test
    fun `WindowsCmd shell escapes inner double quote as backslash-double-quote`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody("""say "hello"""")
                }

            val options = CurlOptions(shell = CurlShell.WindowsCmd)
            val result = request.toCurl(options)

            // Body: say "hello"  → -d "say \"hello\""
            assertTrue(result.contains("""-d "say \"hello\"" """.trim()), "Expected escaped inner quotes, got: $result")
        }

    @Test
    fun `WindowsCmd shell wraps URL in double quotes`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Get
                }

            val options = CurlOptions(shell = CurlShell.WindowsCmd)
            val result = request.toCurl(options)

            assertTrue(result.contains("\"https://example.com/api\""), "Expected double-quoted URL, got: $result")
        }
}

// ──────────────────────────────────────────────────────────────────────────────
// 3. PowerShell quoting (CurlShell.PowerShell)
// ──────────────────────────────────────────────────────────────────────────────

class PowerShellQuotingTests {
    @Test
    fun `PowerShell shell wraps body in single quotes`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody("hello world")
                }

            val options = CurlOptions(shell = CurlShell.PowerShell)
            val result = request.toCurl(options)

            assertTrue(result.contains("-d 'hello world'"), "Expected single-quoted body, got: $result")
        }

    @Test
    fun `PowerShell shell doubles inner single quote`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody("it's here")
                }

            val options = CurlOptions(shell = CurlShell.PowerShell)
            val result = request.toCurl(options)

            // Body: it's here → -d 'it''s here'
            assertTrue(result.contains("-d 'it''s here'"), "Expected doubled single quote, got: $result")
        }

    @Test
    fun `PowerShell shell doubles multiple inner single quotes`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody("it's a 'test'")
                }

            val options = CurlOptions(shell = CurlShell.PowerShell)
            val result = request.toCurl(options)

            // Body: it's a 'test'  → -d 'it''s a ''test'''
            assertTrue(result.contains("-d 'it''s a ''test'''"), "Expected doubled single quotes, got: $result")
        }
}

// ──────────────────────────────────────────────────────────────────────────────
// 4. Posix quoting — default sanity check
// ──────────────────────────────────────────────────────────────────────────────

class PosixQuotingSanityTests {
    @Test
    fun `default Posix shell escapes inner single quote with backslash sequence`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody("user's data")
                }

            // Default options use Posix
            val result = request.toCurl()

            // user's data → 'user'\''s data'
            assertEquals("curl 'https://example.com/api' -d 'user'\\''s data'", result)
        }

    @Test
    fun `explicit Posix shell option behaves same as default`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody("don't break")
                }

            val posixResult = request.toCurl(CurlOptions(shell = CurlShell.Posix))
            val defaultResult = request.toCurl(CurlOptions())

            assertEquals(posixResult, defaultResult)
        }
}

// ──────────────────────────────────────────────────────────────────────────────
// 5. URL userinfo masking
// ──────────────────────────────────────────────────────────────────────────────

class UrlUserInfoMaskingTests {
    @Test
    fun `maskUrlUserInfo=true by default removes original credentials from URL output`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://user:secret@example.com/api")
                    method = HttpMethod.Get
                }

            val result = request.toCurl(CurlOptions(maskUrlUserInfo = true))

            // The original password must not appear verbatim.
            assertFalse(result.contains(":secret@"), "Original password should not appear in output")
            // The host must still be present.
            assertTrue(result.contains("example.com"), "Host should still be present in output")
        }

    @Test
    fun `maskUrlUserInfo=true uses URL-safe custom placeholder for credentials`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://user:secret@example.com/api")
                    method = HttpMethod.Get
                }

            // Use a URL-safe placeholder that won't be percent-encoded by URLBuilder.
            val options = CurlOptions(maskUrlUserInfo = true, maskPlaceholder = "HIDDEN")
            val result = request.toCurl(options)

            assertFalse(result.contains(":secret@"), "Password should not appear in output")
            assertTrue(result.contains("HIDDEN"), "Expected custom placeholder in URL")
        }

    @Test
    fun `maskUrlUserInfo=false leaves user and password intact in URL`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://user:secret@example.com/api")
                    method = HttpMethod.Get
                }

            val result = request.toCurl(CurlOptions(maskUrlUserInfo = false))

            assertTrue(result.contains("user"), "Username should appear in output when masking disabled")
            assertTrue(result.contains("secret"), "Password should appear in output when masking disabled")
        }

    @Test
    fun `URL without userinfo is unaffected by maskUrlUserInfo=true`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Get
                }

            val result = request.toCurl(CurlOptions(maskUrlUserInfo = true))

            assertTrue(result.contains("https://example.com/api"), "Clean URL should be unchanged")
        }
}

// ──────────────────────────────────────────────────────────────────────────────
// 6. Query param masking
// ──────────────────────────────────────────────────────────────────────────────

class QueryParamMaskingTests {
    @Test
    fun `maskedQueryParams masks matching param value and leaves others intact`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api?session=abc&page=1")
                    method = HttpMethod.Get
                }

            // Use a URL-safe placeholder to avoid percent-encoding artefacts.
            val options = CurlOptions(maskedQueryParams = setOf("session"), maskPlaceholder = "MASKED")
            val result = request.toCurl(options)

            assertFalse(result.contains("session=abc"), "Session value should be masked")
            assertTrue(result.contains("session=MASKED"), "Expected masked session param")
            assertTrue(result.contains("page=1"), "Non-masked param should be intact")
        }

    @Test
    fun `maskedQueryParams default bracket placeholder renders verbatim not percent-encoded`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api?session=abc&page=1")
                    method = HttpMethod.Get
                }

            // Default maskPlaceholder "[masked]" contains '[' and ']'. It must render verbatim — the
            // encoded setters bypass URLBuilder's percent-encoding so output stays human-readable.
            val options = CurlOptions(maskedQueryParams = setOf("session"))
            val result = request.toCurl(options)

            assertFalse(result.contains("session=abc"), "Session value should be masked")
            assertTrue(result.contains("session=[masked]"), "Expected verbatim placeholder, got: $result")
            assertFalse(result.contains("%5Bmasked%5D"), "Placeholder must not be percent-encoded, got: $result")
            assertTrue(result.contains("page=1"), "Non-masked param should be intact")
        }

    @Test
    fun `userinfo default bracket placeholder renders verbatim not percent-encoded`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://user:secret@example.com/api")
                    method = HttpMethod.Get
                }

            val result = request.toCurl(CurlOptions(maskUrlUserInfo = true))

            assertFalse(result.contains("secret"), "Password should be masked")
            assertTrue(result.contains("[masked]:[masked]@example.com"), "Expected verbatim placeholder, got: $result")
            assertFalse(result.contains("%5Bmasked%5D"), "Placeholder must not be percent-encoded, got: $result")
        }

    @Test
    fun `maskedQueryParams matching is case-insensitive — config uppercase URL lowercase`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api?session=abc&page=1")
                    method = HttpMethod.Get
                }

            // Config has "Session" (capital S), URL has "session" (lowercase)
            val options = CurlOptions(maskedQueryParams = setOf("Session"), maskPlaceholder = "MASKED")
            val result = request.toCurl(options)

            assertFalse(result.contains("session=abc"), "Session value should be masked even with case mismatch")
            assertTrue(result.contains("session=MASKED"), "Expected masked value after case-insensitive match")
            assertTrue(result.contains("page=1"), "Non-masked param should be intact")
        }

    @Test
    fun `maskedQueryParams matching is case-insensitive — config lowercase URL uppercase`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api?SESSION=abc&page=1")
                    method = HttpMethod.Get
                }

            // Config has "session" (lowercase), URL has "SESSION" (uppercase)
            val options = CurlOptions(maskedQueryParams = setOf("session"), maskPlaceholder = "MASKED")
            val result = request.toCurl(options)

            assertFalse(result.contains("SESSION=abc"), "Session value should be masked even with case mismatch")
            assertTrue(result.contains("page=1"), "Non-masked param should be intact")
        }

    @Test
    fun `maskedQueryParams with empty set leaves URL unchanged`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api?session=abc&page=1")
                    method = HttpMethod.Get
                }

            val result = request.toCurl(CurlOptions(maskedQueryParams = emptySet()))

            assertTrue(result.contains("session=abc"), "Param should appear unmasked with empty maskedQueryParams")
        }
}

// ──────────────────────────────────────────────────────────────────────────────
// 7. maxBodyLength — truncation
// ──────────────────────────────────────────────────────────────────────────────

class MaxBodyLengthTests {
    @Test
    fun `body longer than maxBodyLength is truncated with ellipsis indicator`() =
        runBlocking {
            val body = "a".repeat(100)
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody(body)
                }

            val options = CurlOptions(maxBodyLength = 10)
            val result = request.toCurl(options)

            // The truncated body is "aaaaaaaaaa…[truncated]" (10 'a' chars + indicator)
            assertTrue(result.contains("aaaaaaaaaa…[truncated]"), "Expected truncated body with ellipsis indicator, got: $result")
            assertFalse(result.contains("a".repeat(11)), "Should not contain more than maxBodyLength chars of original body")
        }

    @Test
    fun `body at exactly maxBodyLength is NOT truncated`() =
        runBlocking {
            val body = "a".repeat(10)
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody(body)
                }

            val options = CurlOptions(maxBodyLength = 10)
            val result = request.toCurl(options)

            assertTrue(result.contains(body), "Body at limit should not be truncated")
            assertFalse(result.contains("[truncated]"), "No truncation indicator expected at limit")
        }

    @Test
    fun `body shorter than maxBodyLength is NOT truncated`() =
        runBlocking {
            val body = "short body"
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody(body)
                }

            val options = CurlOptions(maxBodyLength = 100)
            val result = request.toCurl(options)

            assertTrue(result.contains(body), "Short body should not be truncated")
            assertFalse(result.contains("[truncated]"), "No truncation indicator expected for short body")
        }

    @Test
    fun `maxBodyLength=null disables truncation`() =
        runBlocking {
            val body = "a".repeat(1000)
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody(body)
                }

            val options = CurlOptions(maxBodyLength = null)
            val result = request.toCurl(options)

            assertFalse(result.contains("[truncated]"), "No truncation indicator expected when maxBodyLength is null")
        }

    @Test
    fun `truncation uses U+2026 ellipsis character not three dots`() =
        runBlocking {
            val body = "a".repeat(20)
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody(body)
                }

            val options = CurlOptions(maxBodyLength = 5)
            val result = request.toCurl(options)

            // Exactly U+2026 (…), not "..."
            assertTrue(result.contains("…[truncated]"), "Expected U+2026 ellipsis, got: $result")
            assertFalse(result.contains("...[truncated]"), "Should use U+2026 not three dots")
        }
}

// ──────────────────────────────────────────────────────────────────────────────
// 8. multiLine — line-continuation format
// ──────────────────────────────────────────────────────────────────────────────

class MultiLineTests {
    @Test
    fun `multiLine=true joins arguments with backslash-newline-two-spaces`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Get
                    headers { append("X-Custom", "value") }
                }

            val options = CurlOptions(multiLine = true)
            val result = request.toCurl(options)

            assertTrue(result.contains(" \\\n  "), "Expected line-continuation separator, got: $result")
            assertTrue(result.startsWith("curl"), "Command must start with curl")
        }

    @Test
    fun `multiLine=false by default uses single space separator`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Get
                }

            val result = request.toCurl(CurlOptions(multiLine = false))

            assertFalse(result.contains("\\\n"), "No line continuations expected for multiLine=false")
        }

    @Test
    fun `multiLine=true output starts with curl on first line`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody("""{"key":"value"}""")
                    headers { append("Content-Type", "application/json") }
                }

            val options = CurlOptions(multiLine = true)
            val result = request.toCurl(options)

            val firstLine = result.lines().first()
            assertTrue(firstLine.startsWith("curl"), "First line must start with curl, got: $firstLine")
        }

    @Test
    fun `multiLine=true with POST body places each arg on its own continuation`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody("body")
                }

            val options = CurlOptions(multiLine = true)
            val result = request.toCurl(options)

            // With body, POST is default so no -X. Segments: curl / 'url' / -d 'body'
            // joined with " \\\n  "
            val lines = result.split(" \\\n  ")
            assertTrue(lines.size >= 2, "Expected at least 2 segments for multi-line, got: ${lines.size}")
        }
}

// ──────────────────────────────────────────────────────────────────────────────
// 9. compressed / followRedirects flags
// ──────────────────────────────────────────────────────────────────────────────

class FlagInjectionTests {
    @Test
    fun `compressed=true injects --compressed flag`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Get
                }

            val result = request.toCurl(CurlOptions(compressed = true))

            assertTrue(result.contains("--compressed"), "Expected --compressed flag, got: $result")
        }

    @Test
    fun `compressed=false by default does not inject --compressed`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Get
                }

            val result = request.toCurl(CurlOptions(compressed = false))

            assertFalse(result.contains("--compressed"), "Should not contain --compressed when disabled")
        }

    @Test
    fun `followRedirects=true injects -L flag`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Get
                }

            val result = request.toCurl(CurlOptions(followRedirects = true))

            assertTrue(result.contains("-L"), "Expected -L flag, got: $result")
        }

    @Test
    fun `followRedirects=false by default does not inject -L`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Get
                }

            val result = request.toCurl(CurlOptions(followRedirects = false))

            assertFalse(result.contains(" -L"), "Should not contain -L when disabled")
        }

    @Test
    fun `both compressed and followRedirects inject both flags`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Get
                }

            val result = request.toCurl(CurlOptions(compressed = true, followRedirects = true))

            assertTrue(result.contains("--compressed"), "Expected --compressed flag")
            assertTrue(result.contains("-L"), "Expected -L flag")
        }

    @Test
    fun `--compressed appears after -X and before URL`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Delete
                }

            val result = request.toCurl(CurlOptions(compressed = true))

            val xPos = result.indexOf("-X DELETE")
            val compressedPos = result.indexOf("--compressed")
            val urlPos = result.indexOf("'https://example.com/api'")

            assertTrue(xPos < compressedPos, "--compressed should come after -X DELETE")
            assertTrue(compressedPos < urlPos, "--compressed should come before URL")
        }

    @Test
    fun `-L appears after -X and before URL`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Delete
                }

            val result = request.toCurl(CurlOptions(followRedirects = true))

            val xPos = result.indexOf("-X DELETE")
            val lPos = result.indexOf(" -L")
            val urlPos = result.indexOf("'https://example.com/api'")

            assertTrue(xPos < lPos, "-L should come after -X DELETE")
            assertTrue(lPos < urlPos, "-L should come before URL")
        }
}

// ──────────────────────────────────────────────────────────────────────────────
// 10. redactJson — JSON body key masking
// ──────────────────────────────────────────────────────────────────────────────

class RedactJsonTests {
    @Test
    fun `maskedJsonKeys masks simple top-level string value`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody("""{"username":"alice","password":"secret123"}""")
                }

            val options = CurlOptions(maskedJsonKeys = setOf("password"))
            val result = request.toCurl(options)

            assertFalse(result.contains("secret123"), "Password value should be masked")
            assertTrue(result.contains(""""password":"[masked]""""), "Expected masked password key, got: $result")
            assertTrue(result.contains(""""username":"alice""""), "Non-masked key should be intact")
        }

    @Test
    fun `maskedJsonKeys masks deeply nested string value`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody("""{"a":{"password":"x"}}""")
                }

            val options = CurlOptions(maskedJsonKeys = setOf("password"))
            val result = request.toCurl(options)

            assertFalse(result.contains(""""password":"x""""), "Nested password should be masked")
            assertTrue(result.contains(""""password":"[masked]""""), "Expected masked nested password, got: $result")
        }

    @Test
    fun `maskedJsonKeys handles escaped quote inside string value`() =
        runBlocking {
            // JSON body: {"password":"a\"b"}  (value is: a"b)
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody("""{"password":"a\"b"}""")
                }

            val options = CurlOptions(maskedJsonKeys = setOf("password"))
            val result = request.toCurl(options)

            assertFalse(result.contains("""a\"b"""), "Escaped-quote value should be masked")
            assertTrue(result.contains(""""password":"[masked]""""), "Expected masked value even with escaped quotes, got: $result")
        }

    @Test
    fun `maskedJsonKeys masks non-string numeric scalar value`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody("""{"password":123}""")
                }

            val options = CurlOptions(maskedJsonKeys = setOf("password"))
            val result = request.toCurl(options)

            assertFalse(result.contains(":123"), "Numeric password should be masked")
            assertTrue(result.contains(""""password":"[masked]""""), "Expected masked numeric value, got: $result")
        }

    @Test
    fun `maskedJsonKeys matching is case-insensitive`() =
        runBlocking {
            // Config has "Password" (capital P), JSON has "password" (lowercase)
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody("""{"password":"secret"}""")
                }

            val options = CurlOptions(maskedJsonKeys = setOf("Password"))
            val result = request.toCurl(options)

            assertFalse(result.contains("secret"), "Password should be masked with case-insensitive match")
        }

    @Test
    fun `maskedJsonKeys does NOT mangle object value — only scalars are masked`() =
        runBlocking {
            // The value is a nested object; the regex only matches scalars (string/number/bool/null).
            // So the nested object should remain intact.
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody("""{"password":{"nested":"value"}}""")
                }

            val options = CurlOptions(maskedJsonKeys = setOf("password"))
            val result = request.toCurl(options)

            // The object value should NOT be replaced — the regex doesn't match object values.
            assertTrue(result.contains("""{"nested":"value"}"""), "Object value should NOT be mangled by scalar-only regex, got: $result")
        }

    @Test
    fun `maskedJsonKeys masks boolean true value`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody("""{"password":true}""")
                }

            val options = CurlOptions(maskedJsonKeys = setOf("password"))
            val result = request.toCurl(options)

            assertFalse(result.contains(":true"), "Boolean password value should be masked")
            assertTrue(result.contains(""""password":"[masked]""""), "Expected masked boolean value, got: $result")
        }

    @Test
    fun `maskedJsonKeys masks null value`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody("""{"password":null}""")
                }

            val options = CurlOptions(maskedJsonKeys = setOf("password"))
            val result = request.toCurl(options)

            assertFalse(result.contains(":null"), "Null password value should be masked")
            assertTrue(result.contains(""""password":"[masked]""""), "Expected masked null value, got: $result")
        }

    @Test
    fun `maskedJsonKeys with empty set leaves body unchanged`() =
        runBlocking {
            val body = """{"password":"secret"}"""
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody(body)
                }

            val result = request.toCurl(CurlOptions(maskedJsonKeys = emptySet()))

            assertTrue(result.contains("secret"), "Body should be unchanged with empty maskedJsonKeys")
        }
}

// ──────────────────────────────────────────────────────────────────────────────
// 12. insecure / connect-timeout / max-time / retry flags
// ──────────────────────────────────────────────────────────────────────────────

class MissingFlagTests {
    @Test
    fun `insecure=true injects --insecure flag`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Get
                }

            val result = request.toCurl(CurlOptions(insecure = true))

            assertTrue(result.contains("--insecure"), "Expected --insecure flag, got: $result")
        }

    @Test
    fun `insecure=false by default does not inject --insecure`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Get
                }

            val result = request.toCurl(CurlOptions(insecure = false))

            assertFalse(result.contains("--insecure"), "Should not contain --insecure when disabled")
        }

    @Test
    fun `connectTimeoutSeconds injects --connect-timeout with value`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Get
                }

            val result = request.toCurl(CurlOptions(connectTimeoutSeconds = 5))

            assertTrue(result.contains("--connect-timeout 5"), "Expected --connect-timeout 5, got: $result")
        }

    @Test
    fun `connectTimeoutSeconds=null does not inject --connect-timeout`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Get
                }

            val result = request.toCurl(CurlOptions(connectTimeoutSeconds = null))

            assertFalse(result.contains("--connect-timeout"), "Should not contain --connect-timeout when null")
        }

    @Test
    fun `negative connectTimeoutSeconds is not injected`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Get
                }

            val result = request.toCurl(CurlOptions(connectTimeoutSeconds = -1))

            assertFalse(result.contains("--connect-timeout"), "Negative timeout should be skipped, got: $result")
        }

    @Test
    fun `maxTimeSeconds injects --max-time with value`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Get
                }

            val result = request.toCurl(CurlOptions(maxTimeSeconds = 30))

            assertTrue(result.contains("--max-time 30"), "Expected --max-time 30, got: $result")
        }

    @Test
    fun `maxTimeSeconds=null does not inject --max-time`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Get
                }

            val result = request.toCurl(CurlOptions(maxTimeSeconds = null))

            assertFalse(result.contains("--max-time"), "Should not contain --max-time when null")
        }

    @Test
    fun `retry injects --retry with count`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Get
                }

            val result = request.toCurl(CurlOptions(retry = 3))

            assertTrue(result.contains("--retry 3"), "Expected --retry 3, got: $result")
        }

    @Test
    fun `retry=0 is injected as --retry 0`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Get
                }

            val result = request.toCurl(CurlOptions(retry = 0))

            assertTrue(result.contains("--retry 0"), "Expected --retry 0, got: $result")
        }

    @Test
    fun `retry=null does not inject --retry`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Get
                }

            val result = request.toCurl(CurlOptions(retry = null))

            assertFalse(result.contains("--retry"), "Should not contain --retry when null")
        }

    @Test
    fun `all new flags appear after -X and before URL`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Delete
                }

            val result =
                request.toCurl(
                    CurlOptions(insecure = true, connectTimeoutSeconds = 5, maxTimeSeconds = 30, retry = 3),
                )

            val xPos = result.indexOf("-X DELETE")
            val urlPos = result.indexOf("'https://example.com/api'")
            listOf("--insecure", "--connect-timeout 5", "--max-time 30", "--retry 3").forEach { flag ->
                val pos = result.indexOf(flag)
                assertTrue(pos in (xPos + 1) until urlPos, "$flag should sit between -X and URL, got: $result")
            }
        }
}

// ──────────────────────────────────────────────────────────────────────────────
// 13. showHeaders / showBody component selectors
// ──────────────────────────────────────────────────────────────────────────────

class ComponentSelectorTests {
    @Test
    fun `showHeaders=false omits all header arguments`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Get
                    headers { append("X-Custom", "value") }
                }

            val result = request.toCurl(CurlOptions(showHeaders = false))

            assertFalse(result.contains("-H"), "No header args expected, got: $result")
            assertFalse(result.contains("X-Custom"), "Header should be omitted, got: $result")
            assertTrue(result.contains("'https://example.com/api'"), "URL should still be present")
        }

    @Test
    fun `showHeaders=false also omits inferred Content-Type header`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody("body")
                }

            val result = request.toCurl(CurlOptions(showHeaders = false))

            assertFalse(result.contains("Content-Type"), "Inferred Content-Type should be omitted, got: $result")
            assertTrue(result.contains("-d 'body'"), "Body should still be present when only headers are hidden")
        }

    @Test
    fun `showHeaders=true by default keeps header arguments`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Get
                    headers { append("X-Custom", "value") }
                }

            val result = request.toCurl(CurlOptions())

            assertTrue(result.contains("-H 'X-Custom: value'"), "Header should be present by default, got: $result")
        }

    @Test
    fun `showBody=false omits the -d body argument`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody("secret-payload")
                }

            val result = request.toCurl(CurlOptions(showBody = false))

            assertFalse(result.contains("-d"), "Body arg should be omitted, got: $result")
            assertFalse(result.contains("secret-payload"), "Body content should not appear, got: $result")
        }

    @Test
    fun `showBody=false on POST still emits -X POST since no inferred body`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody("body")
                }

            val result = request.toCurl(CurlOptions(showBody = false))

            // With the body hidden, curl can no longer infer POST from a -d, so -X POST is emitted.
            assertTrue(result.contains("-X POST"), "Expected -X POST when body hidden, got: $result")
        }

    @Test
    fun `showHeaders=false and showBody=false leaves only curl and URL`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody("body")
                    headers { append("X-Custom", "value") }
                }

            val result = request.toCurl(CurlOptions(showHeaders = false, showBody = false))

            assertFalse(result.contains("-H"), "No headers expected")
            assertFalse(result.contains("-d"), "No body expected")
            assertTrue(result.contains("'https://example.com/api'"), "URL must remain")
        }
}

// ──────────────────────────────────────────────────────────────────────────────
// 14. componentDelimiter
// ──────────────────────────────────────────────────────────────────────────────

class ComponentDelimiterTests {
    @Test
    fun `componentDelimiter overrides default space separator`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody("body")
                }

            val result = request.toCurl(CurlOptions(componentDelimiter = " | "))

            assertTrue(result.contains(" | "), "Expected custom delimiter, got: $result")
            assertEquals("curl | 'https://example.com/api' | -d 'body'", result)
        }

    @Test
    fun `componentDelimiter overrides multiLine when both set`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Get
                }

            val result = request.toCurl(CurlOptions(multiLine = true, componentDelimiter = " ~ "))

            assertTrue(result.contains(" ~ "), "Custom delimiter should win over multiLine, got: $result")
            assertFalse(result.contains(" \\\n  "), "multiLine separator should not appear when delimiter set")
        }

    @Test
    fun `componentDelimiter=null falls back to single space by default`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Get
                }

            val result = request.toCurl(CurlOptions(componentDelimiter = null))

            assertEquals("curl 'https://example.com/api'", result)
        }

    @Test
    fun `componentDelimiter=null with multiLine still uses line continuation`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Get
                }

            val result = request.toCurl(CurlOptions(componentDelimiter = null, multiLine = true))

            assertTrue(result.contains(" \\\n  "), "Expected multiLine separator when delimiter is null, got: $result")
        }
}

// ──────────────────────────────────────────────────────────────────────────────
// 15. headerTransformer — flexible per-value header hook
// ──────────────────────────────────────────────────────────────────────────────

class HeaderTransformerTests {
    @Test
    fun `transformer rewrites a header value`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Get
                    headers { append("X-Env", "production") }
                }

            val options =
                CurlOptions(
                    headerTransformer = { name, value -> if (name == "X-Env") value.uppercase() else value },
                )
            val result = request.toCurl(options)

            assertTrue(result.contains("-H 'X-Env: PRODUCTION'"), "Expected rewritten value, got: $result")
        }

    @Test
    fun `transformer truncates a long bearer token`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Get
                    headers { append("Authorization", "Bearer abcdefghijklmnopqrstuvwxyz") }
                }

            val options =
                CurlOptions(
                    headerTransformer = { name, value ->
                        if (name.equals("Authorization", ignoreCase = true) && value.length > 13) {
                            value.take(13) + "…"
                        } else {
                            value
                        }
                    },
                )
            val result = request.toCurl(options)

            assertTrue(result.contains("-H 'Authorization: Bearer abcdef…'"), "Expected truncated token, got: $result")
            assertFalse(result.contains("uvwxyz"), "Truncated suffix should not appear, got: $result")
        }

    @Test
    fun `transformer decodes a Basic-Auth credential`() =
        runBlocking {
            // "dXNlcjpwYXNz" is base64("user:pass"). The transformer maps it to a readable form.
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Get
                    headers { append("Authorization", "Basic dXNlcjpwYXNz") }
                }

            val options =
                CurlOptions(
                    headerTransformer = { _, value ->
                        if (value == "Basic dXNlcjpwYXNz") "Basic user:pass (decoded)" else value
                    },
                )
            val result = request.toCurl(options)

            assertTrue(result.contains("-H 'Authorization: Basic user:pass (decoded)'"), "Expected decoded value, got: $result")
        }

    @Test
    fun `transformer returning null omits the header entirely`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Get
                    headers {
                        append("X-Keep", "yes")
                        append("X-Drop", "remove-me")
                    }
                }

            val options =
                CurlOptions(
                    headerTransformer = { name, value -> if (name == "X-Drop") null else value },
                )
            val result = request.toCurl(options)

            assertFalse(result.contains("X-Drop"), "Dropped header should be absent, got: $result")
            assertTrue(result.contains("-H 'X-Keep: yes'"), "Kept header should remain, got: $result")
        }

    @Test
    fun `transformer dropping all values of a multi-value header removes it`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Get
                    headers {
                        append("X-Multi", "a")
                        append("X-Multi", "b")
                    }
                }

            val options = CurlOptions(headerTransformer = { name, _ -> if (name == "X-Multi") null else "x" })
            val result = request.toCurl(options)

            assertFalse(result.contains("X-Multi"), "Header should vanish when all values dropped, got: $result")
        }

    @Test
    fun `transformer runs after masking — sees placeholder not the secret`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Get
                    headers { append("Authorization", "Bearer super-secret") }
                }

            var seenValue: String? = null
            val options =
                CurlOptions(
                    maskedHeaders = setOf("Authorization"),
                    headerTransformer = { _, value ->
                        seenValue = value
                        value
                    },
                )
            val result = request.toCurl(options)

            assertEquals("[masked]", seenValue, "Transformer must receive the masked placeholder, not the secret")
            assertFalse(result.contains("super-secret"), "Secret must never appear, got: $result")
        }

    @Test
    fun `transformer can drop a masked header completely`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Get
                    headers { append("Authorization", "Bearer x") }
                }

            val options =
                CurlOptions(
                    maskedHeaders = setOf("Authorization"),
                    headerTransformer = { name, _ -> if (name.equals("Authorization", true)) null else "x" },
                )
            val result = request.toCurl(options)

            assertFalse(result.contains("Authorization"), "Masked header dropped by transformer should be absent, got: $result")
        }

    @Test
    fun `transformer is applied to inferred Content-Type header`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody("body")
                }

            // No explicit Content-Type header; it is inferred from the text body as text/plain.
            val options =
                CurlOptions(headerTransformer = { name, _ -> if (name == "Content-Type") null else "x" })
            val result = request.toCurl(options)

            assertFalse(result.contains("Content-Type"), "Inferred Content-Type dropped by transformer should be absent, got: $result")
            assertTrue(result.contains("-d 'body'"), "Body should still render")
        }

    @Test
    fun `default transformer leaves headers unchanged`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Get
                    headers { append("X-Custom", "value") }
                }

            val result = request.toCurl(CurlOptions())

            assertTrue(result.contains("-H 'X-Custom: value'"), "Default identity transformer should not alter headers, got: $result")
        }
}

// ──────────────────────────────────────────────────────────────────────────────
// 11. shouldLog predicate — SKIPPED
// Note: shouldLog is part of KtorToCurlConfig and is evaluated inside the
// KtorToCurl Ktor plugin's onRequest hook — it requires a full HttpClient with
// the plugin installed and a live engine. It is not reachable via the public
// HttpRequestBuilder.toCurl() extension or the internal generateCurl() function.
// Testing it requires an integration-style test with HttpClient + mock engine,
// which is outside the scope of commonTest unit tests using runBlocking only.
// ──────────────────────────────────────────────────────────────────────────────
