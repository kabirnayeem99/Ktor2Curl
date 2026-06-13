package io.github.kabirnayeem99.ktor2curl

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.HttpSendPipeline

val KtorToCurl =
    createClientPlugin("KtorToCurl", ::KtorToCurlConfig) {
        val logger = pluginConfig.logger
        val shouldLog = pluginConfig.shouldLog
        val options = pluginConfig.toOptions()
        // Intercept the send pipeline rather than using onRequest: onRequest fires in the request
        // pipeline's Before phase, ahead of the Transform phase where ContentNegotiation (and other
        // body converters) serialize the body. At that point request.body is still the raw object,
        // so a data-class body would render via toString() as "BrowseBody(...)" instead of its JSON.
        // By HttpSendPipeline.Monitoring the body is the final serialized OutgoingContent.
        client.sendPipeline.intercept(HttpSendPipeline.Monitoring) {
            val request = context
            if (shouldLog(request)) {
                val curl = generateCurl(request = request, options = options)
                if (curl.isNotBlank()) logger.log(curl)
            }
        }
    }

/**
 * Renders this request as a runnable `curl` command without installing the [KtorToCurl] plugin.
 * Useful for one-off debugging, tests, or custom logging pipelines.
 */
suspend fun HttpRequestBuilder.toCurl(options: CurlOptions = CurlOptions()): String = generateCurl(request = this, options = options)
