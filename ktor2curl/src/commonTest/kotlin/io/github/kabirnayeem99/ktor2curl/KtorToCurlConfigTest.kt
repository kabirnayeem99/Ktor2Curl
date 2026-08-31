package io.github.kabirnayeem99.ktor2curl

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Direct unit coverage of [KtorToCurlConfig]: its field defaults, the default no-op [CurlLogger],
 * the `excludeHeader`/`maskHeader`/`maskQueryParam`/`maskJsonKey` convenience mutators, and the
 * `toOptions()` mapping. [PluginIntegrationTests] exercises the config only through the plugin's
 * `install` DSL, which never touches most of these fields directly — this file constructs and
 * mutates [KtorToCurlConfig] on its own so every property assignment and mutator actually runs.
 */
class KtorToCurlConfigTest {
    @Test
    fun `default logger is a no-op that does not throw`() =
        runBlocking {
            val config = KtorToCurlConfig()
            config.logger.log("curl 'https://example.com'")
        }

    @Test
    fun `defaults are null or false for the optional flags`() {
        val config = KtorToCurlConfig()

        assertNull(config.maxBodyLength)
        assertEquals(false, config.multiLine)
        assertEquals(false, config.compressed)
        assertEquals(false, config.followRedirects)
        assertEquals(false, config.insecure)
        assertNull(config.connectTimeoutSeconds)
        assertNull(config.maxTimeSeconds)
        assertNull(config.retry)
        assertNull(config.componentDelimiter)
    }

    @Test
    fun `optional flags can be overridden and flow through toOptions`() {
        val config =
            KtorToCurlConfig().apply {
                maxBodyLength = 100
                multiLine = true
                compressed = true
                followRedirects = true
                insecure = true
                connectTimeoutSeconds = 5
                maxTimeSeconds = 30
                retry = 3
                componentDelimiter = " | "
            }

        val options = config.toOptions()

        assertEquals(100, options.maxBodyLength)
        assertEquals(true, options.multiLine)
        assertEquals(true, options.compressed)
        assertEquals(true, options.followRedirects)
        assertEquals(true, options.insecure)
        assertEquals(5, options.connectTimeoutSeconds)
        assertEquals(30, options.maxTimeSeconds)
        assertEquals(3, options.retry)
        assertEquals(" | ", options.componentDelimiter)
    }

    @Test
    fun `excludeHeader adds to excludedHeaders`() {
        val config = KtorToCurlConfig()

        config.excludeHeader("X-One", "X-Two")

        assertEquals(setOf("X-One", "X-Two"), config.excludedHeaders)
    }

    @Test
    fun `maskHeader adds to maskedHeaders`() {
        val config = KtorToCurlConfig()

        config.maskHeader("Authorization")

        assertEquals(setOf("Authorization"), config.maskedHeaders)
    }

    @Test
    fun `maskQueryParam adds to maskedQueryParams`() {
        val config = KtorToCurlConfig()

        config.maskQueryParam("token")

        assertEquals(setOf("token"), config.maskedQueryParams)
    }

    @Test
    fun `maskJsonKey adds to maskedJsonKeys`() {
        val config = KtorToCurlConfig()

        config.maskJsonKey("password")

        assertEquals(setOf("password"), config.maskedJsonKeys)
    }
}
