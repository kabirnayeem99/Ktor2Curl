package io.github.kabirnayeem99.ktor2curl

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.headers
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.HttpMethod
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Non-ASCII / Unicode round-tripping beyond the generic coverage already in
 * [KmpEncodingTests] (arbitrary multibyte text, emoji) and [MultipartEdgeCaseTests]
 * (non-ASCII multipart filenames). Focuses on specific scripts, bidi/zero-width control
 * characters, multi-codepoint emoji sequences, header values, query parameters, and
 * multipart field *names*.
 */
class InternationalizationTests {
    @Test
    fun `generateCurl preserves Arabic body text with diacritics`() =
        runBlocking {
            // مَرْحَبًا = "hello" fully voweled with tashkeel marks.
            val arabic = "{\"greeting\":\"مَرْحَبًا بِكَ فِي العَالَمِ\"}"
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody(arabic)
                }

            val result = generateCurl(request, excludedHeaders = emptySet())

            assertEquals(
                "curl 'https://example.com/api' --data-binary '$arabic'",
                result,
            )
        }

    @Test
    fun `generateCurl preserves Japanese body text mixing hiragana katakana kanji and fullwidth punctuation`() =
        runBlocking {
            // "Hello, world? Konnichiwa" mixing hiragana (こんにちは), katakana (ワールド),
            // kanji (世界), a fullwidth comma (、) and a fullwidth question mark (?).
            val japanese = "こんにちは、世界のワールドさん?"
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody(japanese)
                }

            val result = generateCurl(request, excludedHeaders = emptySet())

            assertEquals(
                "curl 'https://example.com/api' --data-binary '$japanese'",
                result,
            )
        }

    @Test
    fun `generateCurl preserves simplified and traditional Chinese body text`() =
        runBlocking {
            // Simplified: "I love programming, this is a test". Traditional: same meaning.
            val chinese = "简体：我爱编程，这是一个测试。繁體：我愛編程，這是一個測試。"
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody(chinese)
                }

            val result = generateCurl(request, excludedHeaders = emptySet())

            assertEquals(
                "curl 'https://example.com/api' --data-binary '$chinese'",
                result,
            )
        }

    @Test
    fun `generateCurl preserves Korean Hangul body text`() =
        runBlocking {
            val korean = "안녕하세요, 이것은 테스트 문장입니다."
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody(korean)
                }

            val result = generateCurl(request, excludedHeaders = emptySet())

            assertEquals(
                "curl 'https://example.com/api' --data-binary '$korean'",
                result,
            )
        }

    @Test
    fun `generateCurl preserves multi-codepoint ZWJ family emoji sequence byte for byte`() =
        runBlocking {
            // Family: man (U+1F468), woman (U+1F469), girl (U+1F467), boy (U+1F466), joined with
            // ZWJ (U+200D) between each member. Spelled out with \\u escapes (rather than the
            // literal glyph) since ZWJ is invisible in a text editor and this test's correctness
            // hinges on exactly which codepoints are present.
            val familyEmoji =
                "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66"
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody(familyEmoji)
                }

            val result = generateCurl(request, excludedHeaders = emptySet())
            val expected = "curl 'https://example.com/api' --data-binary '$familyEmoji'"

            assertEquals(expected, result)
        }

    @Test
    fun `generateCurl preserves skin tone modified emoji sequence byte for byte`() =
        runBlocking {
            // RAISED HAND (U+270B) + EMOJI MODIFIER FITZPATRICK TYPE-1-2 (U+1F3FB, light skin
            // tone). Spelled out with \\u escapes since the modifier is invisible on its own.
            val raisedHandLightSkin = "\u270B\uD83C\uDFFB"
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody(raisedHandLightSkin)
                }

            val result = generateCurl(request, excludedHeaders = emptySet())

            assertEquals(
                "curl 'https://example.com/api' --data-binary '$raisedHandLightSkin'",
                result,
            )
        }

    @Test
    fun `generateCurl does not strip bidi override and pop directional formatting control characters`() =
        runBlocking {
            // RLO (U+202E) ... PDF (U+202C), and LRO (U+202D) ... PDF (U+202C). Spelled out with
            // \\u escapes since these controls are invisible in a text editor and a stray one
            // could silently vanish (or be reintroduced wrong) without the reader noticing.
            val bidi = "before-\u202Ereversed-part\u202C-after-\u202Dforced-ltr\u202C-end"
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody(bidi)
                }

            val result = generateCurl(request, excludedHeaders = emptySet())

            assertEquals(
                "curl 'https://example.com/api' --data-binary '$bidi'",
                result,
            )
        }

    @Test
    fun `generateCurl does not strip standalone zero width characters`() =
        runBlocking {
            // ZWSP (U+200B), ZWNJ (U+200C), and ZWJ (U+200D) used standalone (not part of an
            // emoji sequence). Spelled out with \\u escapes since these are invisible.
            val zeroWidth = "left\u200Bmiddle\u200Cmore\u200Dright"
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody(zeroWidth)
                }

            val result = generateCurl(request, excludedHeaders = emptySet())

            assertEquals(
                "curl 'https://example.com/api' --data-binary '$zeroWidth'",
                result,
            )
        }

    @Test
    fun `generateCurl preserves mixed Arabic Latin and emoji in a single body`() =
        runBlocking {
            val mixed = "Order from Ahmed (أحمد) confirmed 🚀 — شكراً!"
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody(mixed)
                }

            val result = generateCurl(request, excludedHeaders = emptySet())

            assertEquals(
                "curl 'https://example.com/api' --data-binary '$mixed'",
                result,
            )
        }

    @Test
    fun `generateCurl preserves a non-ASCII query parameter verbatim not percent-encoded`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api?q=日本語")
                }

            val result = generateCurl(request, excludedHeaders = emptySet())

            // Ktor's HttpRequestBuilder.url(String) parses the raw string but does not re-encode
            // characters already present verbatim, so 日本語 survives as literal UTF-8 text rather
            // than being percent-encoded. Hardcoded literal expectation, not derived from a second
            // URLBuilder call, so a real regression here would actually be caught.
            assertEquals(
                "curl 'https://example.com/api?q=日本語'",
                result,
            )
        }

    @Test
    fun `generateCurl preserves non-ASCII header value verbatim in -H output`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    headers { append("X-Author", "Достоевский 日本語") }
                }

            val result = generateCurl(request, excludedHeaders = emptySet())

            assertEquals(
                "curl -H 'X-Author: Достоевский 日本語' 'https://example.com/api'",
                result,
            )
        }

    @Test
    fun `generateCurl renders non-ASCII multipart field name in -F output`() =
        runBlocking {
            val request =
                HttpRequestBuilder().apply {
                    url("https://example.com/api")
                    method = HttpMethod.Post
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append("名前", "田中太郎")
                            },
                        ),
                    )
                }

            val result = generateCurl(request, excludedHeaders = setOf("Content-Type"))

            assertEquals(
                "curl 'https://example.com/api' -F '名前=田中太郎'",
                result,
            )
        }
}
