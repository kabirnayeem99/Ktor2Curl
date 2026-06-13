package io.github.kabirnayeem99.ktor2curl

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder

val KtorToCurl =
    createClientPlugin("KtorToCurl", ::KtorToCurlConfig) {
        val logger = pluginConfig.logger
        val shouldLog = pluginConfig.shouldLog
        val options = pluginConfig.toOptions()
        onRequest { request, _ ->
            if (!shouldLog(request)) return@onRequest
            val curl = generateCurl(request = request, options = options)
            if (curl.isNotBlank()) logger.log(curl)
        }
    }

/**
 * Renders this request as a runnable `curl` command without installing the [KtorToCurl] plugin.
 * Useful for one-off debugging, tests, or custom logging pipelines.
 */
suspend fun HttpRequestBuilder.toCurl(options: CurlOptions = CurlOptions()): String = generateCurl(request = this, options = options)
