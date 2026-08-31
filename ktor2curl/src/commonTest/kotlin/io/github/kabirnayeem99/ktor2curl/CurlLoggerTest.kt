package io.github.kabirnayeem99.ktor2curl

import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class CurlLoggerTest {
    @Test
    fun `console logger writes without throwing`() =
        runBlocking {
            CurlLogger.console().log("curl 'https://example.com'")
        }
}
