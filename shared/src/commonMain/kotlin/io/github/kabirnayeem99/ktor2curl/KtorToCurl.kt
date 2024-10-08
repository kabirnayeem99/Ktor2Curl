package io.github.kabirnayeem99.ktor2curl

import io.ktor.client.plugins.api.createClientPlugin

val KtorToCurl = createClientPlugin("KtorToCurlPlugin", ::KtorToCurlConfig) {
    val converter = pluginConfig.converter
    val excludedHeaders = pluginConfig.excludedHeaders
    val maskedHeaders = pluginConfig.maskedHeaders
    onRequest { request, _ ->
        val curl = generateCurl(request, excludedHeaders, maskedHeaders)
        if (curl.isNotBlank()) converter.log(curl)
    }
}