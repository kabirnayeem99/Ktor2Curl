package io.github.kabirnayeem99.ktor2curl

import io.ktor.client.plugins.api.createClientPlugin

val KtorToCurl = createClientPlugin("KtorToCurlPlugin", ::KtorToCurlConfig) {
    val converter = pluginConfig.converter
    val excludedHeaders = pluginConfig.excludedHeaders
    val maskedHeaders = pluginConfig.maskedHeaders
    onRequest { request, _ ->
        var curl = generateCurl(
            request = request,
            maskedHeaders = maskedHeaders,
            excludedHeaders = excludedHeaders,
        )
        if (curl.isNotBlank()) converter.log(curl)
    }
}